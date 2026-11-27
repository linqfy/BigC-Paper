package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockRegistry;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Packet-level remap of module blocks (see {@link ClientRemapTable}). The server
 * keeps its states (note block, tripwire, chorus plant); Java players receive a borrowed vanilla state instead: dry,
 * for note block states that are not full cubes (a note block hides the faces around it), and wet, for waterloggable
 * blocks standing in water ({@link WaterloggedBlocks}). The pack layer draws the module model on the borrowed states;
 * real vanilla blocks in a borrowed state are sent with a canonical value that looks the same. Bedrock players keep
 * the server states (Geyser's custom block mappings).
 * <p>
 * Inert unless {@code blocks.packet-remap} is on and packetevents is installed. Chunk packets are only decoded for
 * chunks in a {@link RemapChunkIndex} (filled by a palette check when a chunk loads, and by every block change that
 * carries a remapped state) or with waterlogged positions; every other chunk packet is passed on after reading its
 * position.
 */
public final class PacketRemapService implements Listener {
    public static final String TABLE_FILE = "alexscaves/client-remap.txt";
    public static final String PACKET_EVENTS = "packetevents";

    private final Plugin plugin;
    private final ModBlockRegistry registry;
    private final boolean enabled;
    private final RemapChunkIndex index = new RemapChunkIndex();
    private final WaterloggedIndex wetIndex = new WaterloggedIndex();
    private final RemapStats stats = new RemapStats();
    private ClientRemapTable table = ClientRemapTable.empty();
    private List<BlockData> scanStates = List.of();
    private Set<BlockData> wetCapable = Set.of();
    private ServerInternals.ChunkProbe scanProbe;
    private StateIdRemap remap;
    private StateIdRemap wet;
    private WaterloggedBlocks waterlogged;
    private ServerInternals internals;
    private BedrockPlayers bedrock;
    private AutoCloseable packetListener;
    private AutoCloseable miningListener;
    private MiningPredictionSync mining;
    private final List<org.bukkit.scheduler.BukkitTask> miningTasks = new ArrayList<>();
    private Map<BlockData, BlockData> dryClient = Map.of();
    private Map<BlockData, BlockData> wetClient = Map.of();
    private List<BlockData> borrowedSamples = List.of();
    private volatile boolean active;
    private String status = "apagada";

    public PacketRemapService(Plugin plugin, ModBlockRegistry registry, boolean enabled) {
        this.plugin = plugin;
        this.registry = registry;
        this.enabled = enabled;
    }

    /** Whether packetevents is installed and enabled (it loads before BigCasares: plugin.yml softdepend). */
    public static boolean packetEventsPresent(Plugin plugin) {
        Plugin packetEvents = plugin.getServer().getPluginManager().getPlugin(PACKET_EVENTS);
        return packetEvents != null && packetEvents.isEnabled();
    }

    public static ClientRemapTable loadTable(Plugin plugin) {
        try (InputStream input = plugin.getResource(TABLE_FILE)) {
            if (input == null) {
                throw new IllegalStateException("Falta " + TABLE_FILE + " en el JAR");
            }
            return ClientRemapTable.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("No se pudo leer " + TABLE_FILE, failure);
        }
    }

    public void start(BukkitRuntimeRegistrations registrations) {
        if (!enabled) {
            status = "apagada (blocks.packet-remap: false)";
            return;
        }
        if (!packetEventsPresent(plugin)) {
            status = "inactiva: falta el plugin packetevents";
            plugin.getLogger().warning("[AlexsCaves] blocks.packet-remap está activo pero falta packetevents: los bloques "
                + "se mandan como note blocks.");
            return;
        }
        table = loadTable(plugin).forRegistry(registry);
        List<String> mismatches = table.mismatches(registry);
        if (!mismatches.isEmpty()) {
            throw new IllegalStateException("client-remap.txt no coincide con blocks.yml: " + mismatches);
        }
        if (table.isEmpty()) {
            status = "inactiva: ningún bloque con estado de cliente";
            return;
        }
        try {
            internals = ServerInternals.lookup(plugin.getServer().getClass().getClassLoader());
            resolve();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            status = "inactiva: no se pudieron leer los ids de estado del servidor";
            plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Reescritura por paquetes apagada: " + status, failure);
            return;
        }
        try {
            startPacketLayer(registrations);
        } catch (RuntimeException | LinkageError failure) {
            // A broken packetevents must not roll back the rest of the modules: the blocks go out as note blocks.
            rollbackStart();
            status = "inactiva: falló la integración con packetevents";
            plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Reescritura por paquetes apagada: " + status, failure);
        }
    }

    private void startPacketLayer(BukkitRuntimeRegistrations registrations) {
        // The plugin's own loader sees Floodgate and Geyser (plugin.yml softdepend); the server's does not.
        bedrock = BedrockPlayers.lookup(plugin.getClass().getClassLoader());
        registrations.registerListener("alexs-caves-packet-remap", this);
        scanProbe = internals.probe(scanStates);
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scan(chunk);
            }
        }
        if (!wet.isEmpty()) {
            waterlogged = new WaterloggedBlocks(plugin, wetIndex, wetCapable::contains, internals.probe(wetCapable));
            registrations.registerListener("alexs-caves-waterlogged", waterlogged);
            waterlogged.start();
        }
        packetListener = PacketEventsRemap.register(new PacketEventsRemap.Context(remap, wet, index, wetIndex, stats,
            bedrock, plugin.getLogger(), PacketRemapService::worldOf));
        startMiningSync(registrations);
        active = true;
        status = "activa: " + table.entries().size() + " estados (" + wetCapable.size() + " con estado en agua), "
            + table.normalize().size() + " estados prestados, Bedrock detectado por " + String.join(" + ", bedrock.methods());
        plugin.getLogger().info("[AlexsCaves] Reescritura por paquetes " + status + " (packetevents "
            + PacketEventsRemap.serverVersionName() + ", " + index.size() + " chunks cargados marcados, "
            + wetIndex.size() + " bloques en agua).");
    }

    /** Undoes whatever {@link #startPacketLayer} registered before failing. */
    private void rollbackStart() {
        active = false;
        stopMiningSync();
        AutoCloseable listener = packetListener;
        packetListener = null;
        if (listener != null) {
            try {
                listener.close();
            } catch (Exception | LinkageError ignored) {
                // Already failing; the status line tells the story.
            }
        }
        if (waterlogged != null) {
            try {
                waterlogged.stop();
            } catch (RuntimeException ignored) {
                // Already failing.
            }
            org.bukkit.event.HandlerList.unregisterAll(waterlogged);
            waterlogged = null;
        }
        org.bukkit.event.HandlerList.unregisterAll(this);
        index.clear();
    }

    /**
     * Dry remap: server id -> dry client id, and every borrowed client id -> its canonical id. Wet remap: the id a
     * waterloggable state is sent with after the dry remap -> its wet client id.
     */
    private void resolve() {
        Map<Integer, Integer> ids = new HashMap<>();
        Map<Integer, Integer> wetIds = new HashMap<>();
        Set<BlockData> scan = new LinkedHashSet<>();
        Set<BlockData> capable = new HashSet<>();
        Map<BlockData, BlockData> dryStates = new HashMap<>();
        Map<BlockData, BlockData> wetStates = new HashMap<>();
        int disagreements = 0;
        for (Map.Entry<String, String> normal : table.normalize().entrySet()) {
            BlockData borrowed = Bukkit.createBlockData(normal.getKey());
            BlockData canonical = Bukkit.createBlockData(normal.getValue());
            ids.put(internals.stateId(borrowed), internals.stateId(canonical));
            scan.add(borrowed);
            disagreements += disagreements(borrowed) + disagreements(canonical);
        }
        for (ClientRemapTable.Entry entry : table.entries()) {
            BlockData server = Bukkit.createBlockData(entry.serverState());
            int sent = internals.stateId(server);
            if (entry.dry() != null) {
                dryStates.put(server, Bukkit.createBlockData(entry.dry()));
                int dry = internals.stateId(Bukkit.createBlockData(entry.dry()));
                ids.put(sent, dry);
                scan.add(server);
                sent = dry;
            }
            if (entry.wet() != null) {
                wetStates.put(server, Bukkit.createBlockData(entry.wet()));
                wetIds.put(sent, internals.stateId(Bukkit.createBlockData(entry.wet())));
                capable.add(server);
            }
            disagreements += disagreements(server);
        }
        if (disagreements > 0) {
            // The wire uses the server's ids; packetevents' own table only matters for its WrappedBlockState API.
            plugin.getLogger().warning("[AlexsCaves] Reescritura por paquetes: packetevents no coincide con el servidor en "
                + disagreements + " id(s) de estado; se usan los del servidor.");
        }
        remap = StateIdRemap.of(ids);
        wet = StateIdRemap.of(wetIds);
        scanStates = List.copyOf(scan);
        wetCapable = Set.copyOf(capable);
        dryClient = Map.copyOf(dryStates);
        wetClient = Map.copyOf(wetStates);
        Map<org.bukkit.Material, BlockData> samples = new HashMap<>();
        for (BlockData client : dryStates.values()) {
            samples.putIfAbsent(client.getMaterial(), client);
        }
        for (BlockData client : wetStates.values()) {
            samples.putIfAbsent(client.getMaterial(), client);
        }
        borrowedSamples = List.copyOf(samples.values());
    }

    /**
     * Mining prediction fix. A failure here must not take the remap down: the
     * blocks then only flicker while mined.
     */
    private void startMiningSync(BukkitRuntimeRegistrations registrations) {
        try {
            MiningFactors factors = new MiningFactors();
            PacketEventsMining sender = PacketEventsMining.create(factors, bedrock, plugin.getLogger());
            mining = new MiningPredictionSync(plugin, this::clientState, factors, sender, this::borrowedSamples);
            miningListener = sender.register();
            registrations.registerListener("alexs-caves-mining-sync", mining);
            miningTasks.add(registrations.scheduleRepeating("alexs-caves-mining-sync-tick", mining::tick, 20L, 20L));
            miningTasks.add(registrations.scheduleRepeating("alexs-caves-mining-sync-scan", mining::scan, 4L, 4L));
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Predicción de minado apagada (los bloques parpadean "
                + "al picarlos)", failure);
            stopMiningSync();
        }
    }

    private void stopMiningSync() {
        MiningPredictionSync current = mining;
        mining = null;
        try {
            if (current != null) {
                current.shutdown();
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[AlexsCaves] No se pudo restaurar el minado de los jugadores", failure);
        } finally {
            if (current != null) {
                org.bukkit.event.HandlerList.unregisterAll(current);
            }
            for (org.bukkit.scheduler.BukkitTask task : miningTasks) {
                task.cancel();
            }
            miningTasks.clear();
        }
        AutoCloseable listener = miningListener;
        miningListener = null;
        if (listener != null) {
            try {
                listener.close();
            } catch (Exception failure) {
                plugin.getLogger().log(Level.WARNING, "[AlexsCaves] No se pudo quitar el listener de minado", failure);
            }
        }
    }

    /** One borrowed dry state per material, for the instabreak pre-check. */
    private List<BlockData> borrowedSamples() {
        return borrowedSamples;
    }

    /** The state a Java client sees at {@code block}, or null when it is not a remapped block. */
    private BlockData clientState(Block block) {
        BlockData server = block.getBlockData();
        BlockData wetState = wetClient.get(server);
        if (wetState != null && wetIndex.contains(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ())) {
            return wetState;
        }
        return dryClient.get(server);
    }

    private int disagreements(BlockData data) {
        try {
            return PacketEventsRemap.packetEventsId(data) != internals.stateId(data) ? 1 : 0;
        } catch (RuntimeException unknown) {
            return 1;
        }
    }

    private static PacketEventsRemap.PlayerWorld worldOf(Object player) {
        try {
            if (player instanceof Player online) {
                World world = online.getWorld();
                return new PacketEventsRemap.PlayerWorld(world.getUID(), world.getMinHeight());
            }
            return null;
        } catch (RuntimeException leaving) {
            return null;
        }
    }

    /** A module block vanished without a player break (explosion): a waterlogged one leaves water. */
    public void onBlockRemoved(Block block) {
        WaterloggedBlocks listener = waterlogged;
        if (active && listener != null) {
            listener.removed(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        scan(event.getChunk());
    }

    /**
     * A module block placed in a loaded chunk that was never marked (no palette hit at load, no remapped block
     * change sent yet) would leave the chunk unrewritten for the next player: check it before it is sent.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChunkLoad(PlayerChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        if (active && scanProbe != null && !index.contains(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
            scan(chunk);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        index.unmark(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        index.clearWorld(event.getWorld().getUID());
    }

    /** Palette check: module note blocks with a dry client state, or real vanilla blocks in a borrowed state. */
    private void scan(Chunk chunk) {
        long start = System.nanoTime();
        if (scanProbe.mayHold(chunk) && index.mark(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ())) {
            stats.chunksMarkedByScan.increment();
        }
        stats.chunkScans.increment();
        stats.chunkScanNanos.add(System.nanoTime() - start);
    }

    public boolean active() {
        return active;
    }

    public void shutdown() {
        active = false;
        stopMiningSync();
        AutoCloseable listener = packetListener;
        packetListener = null;
        if (listener != null) {
            try {
                listener.close();
            } catch (Exception failure) {
                plugin.getLogger().log(Level.WARNING, "[AlexsCaves] No se pudo quitar el listener de packetevents", failure);
            }
        }
        index.clear();
        if (waterlogged != null) {
            waterlogged.stop();
            waterlogged = null;
        }
        if (bedrock != null) {
            bedrock.clear();
        }
    }

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("§7Bloques por paquetes: §f" + status);
        if (active) {
            lines.add("§8 - §7chunks marcados: §f" + index.size() + "§7, bloques en agua cargados: §f" + wetIndex.size());
            for (String line : stats.lines()) {
                lines.add("§8 - §7" + line);
            }
        }
        return lines;
    }

    public void resetStats() {
        stats.reset();
    }

    /**
     * {@code /alexscaves remap bench}: builds the real chunk packets of the loaded chunks around {@code center} and
     * runs them through the listener's code (see {@link PacketEventsRemap#bench}).
     */
    public List<String> bench(Location center, int radius, int rounds) {
        if (!active) {
            return List.of("La reescritura por paquetes no está activa: " + status);
        }
        World world = center.getWorld();
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        List<PacketEventsRemap.BenchChunk> chunks = new ArrayList<>();
        try {
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (world.isChunkLoaded(x, z)) {
                        chunks.add(new PacketEventsRemap.BenchChunk(x, z,
                            internals.chunkPacketBody(world, world.getChunkAt(x, z))));
                    }
                }
            }
            return PacketEventsRemap.bench(chunks, world.getMinHeight(), world.getMaxHeight() - world.getMinHeight(),
                remap, index, rounds);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Falló el bench de la reescritura por paquetes", failure);
            return List.of("Falló el bench: " + failure);
        }
    }
}
