package dev.linqfy.bigCasares.modules.alexscaves.core.magnetism;

import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettings.MagnetismSettings;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockRegistry;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.NoteBlockService;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectService;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType;
import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetForce.Polarity;
import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetForce.Vec;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Pulls and pushes magnetic entities near neodymium blocks with the mod's
 * {@code MagnetUtil} rules. Chunks are indexed from snapshots off the main
 * thread; the per-tick work is bounded by {@code max-entities-per-tick}.
 */
public final class MagnetismService {
    private static final List<String> FERROMAGNETIC_HINTS = List.of(
        "IRON", "NETHERITE", "CHAIN", "RAIL", "BUCKET", "COMPASS", "ANVIL", "MINECART", "SHEARS",
        "CAULDRON", "HOPPER", "LANTERN");

    private final Plugin plugin;
    private final MagnetismSettings settings;
    private final NoteBlockService blocks;
    private final ModEffectService effects;
    private final MagnetIndex index = new MagnetIndex();
    /** Tesla bulbs found by the same chunk scan (polarity unused). */
    private final MagnetIndex teslaIndex = new MagnetIndex();
    private final Set<BlockData> teslaStates = new HashSet<>();
    private final Map<UUID, Vec> deltas = new HashMap<>();
    private final Map<BlockData, Polarity> polarities = new HashMap<>();
    /** Magnet blocks: neodymium-like only while they get a redstone signal. */
    private final Map<BlockData, Polarity> poweredMagnetStates = new HashMap<>();
    /** Latest scan per chunk (main thread). A scan result is published only if it is still the latest. */
    private final Map<ChunkRef, Long> generations = new HashMap<>();
    private final Set<ChunkRef> scanning = new HashSet<>();
    private long generationCounter;
    private int playerOffset;
    private volatile boolean closed;

    public MagnetismService(Plugin plugin, MagnetismSettings settings, NoteBlockService blocks, ModEffectService effects) {
        this.plugin = plugin;
        this.settings = settings;
        this.blocks = blocks;
        this.effects = effects;
        ModBlockRegistry registry = blocks.registry();
        for (String variant : registry.variants("tesla_bulb")) {
            blocks.blockData("tesla_bulb", variant).ifPresent(teslaStates::add);
        }
        for (String color : List.of("scarlet", "azure")) {
            Polarity polarity = color.equals("scarlet") ? Polarity.SCARLET : Polarity.AZURE;
            for (String block : List.of(color + "_neodymium_node", color + "_neodymium_pillar", "block_of_" + color + "_neodymium")) {
                for (String variant : registry.variants(block)) {
                    blocks.blockData(block, variant).ifPresent(data -> polarities.put(data, polarity));
                }
            }
            for (String variant : registry.variants(color + "_magnet")) {
                blocks.blockData(color + "_magnet", variant).ifPresent(data -> poweredMagnetStates.put(data, polarity));
            }
        }
    }

    public MagnetIndex index() {
        return index;
    }

    public MagnetIndex teslaIndex() {
        return teslaIndex;
    }

    /** Powered magnet blocks act like neodymium while they have a redstone signal. */
    public void setPoweredMagnet(Block block, Polarity polarity, boolean powered) {
        if (powered) {
            index.add(block.getWorld().getUID(), new MagnetIndex.Magnet(block.getX(), block.getY(), block.getZ(), polarity));
        } else {
            index.remove(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    public Optional<Polarity> polarityOf(BlockData data) {
        return Optional.ofNullable(polarities.get(data));
    }

    /**
     * Indexes a chunk from a snapshot on an async thread. The result is published back on the main
     * thread, and dropped when the chunk unloaded, the module stopped, or a newer scan of the same
     * chunk started (a block changed meanwhile), so an old snapshot never overwrites newer data.
     */
    public void indexChunk(Chunk chunk) {
        if (closed) {
            return;
        }
        UUID world = chunk.getWorld().getUID();
        ChunkRef ref = new ChunkRef(world, chunk.getX(), chunk.getZ());
        long generation = ++generationCounter;
        generations.put(ref, generation);
        if (!containsAnyState(chunk)) {
            scanning.remove(ref);
            index.removeChunk(world, ref.x(), ref.z());
            teslaIndex.removeChunk(world, ref.x(), ref.z());
            return;
        }
        scanning.add(ref);
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (closed) {
                return;
            }
            ScanResult result = scan(snapshot, ref, minY, maxY);
            if (closed || !plugin.isEnabled()) {
                return;
            }
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> publish(ref, generation, result));
            } catch (IllegalStateException | IllegalArgumentException disabled) {
                // Plugin disabled while scanning: nothing to publish to.
            }
        });
    }

    private boolean containsAnyState(Chunk chunk) {
        for (Set<BlockData> states : List.of(polarities.keySet(), teslaStates, poweredMagnetStates.keySet())) {
            for (BlockData state : states) {
                if (chunk.contains(state)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ScanResult scan(ChunkSnapshot snapshot, ChunkRef ref, int minY, int maxY) {
        List<MagnetIndex.Magnet> magnets = new ArrayList<>();
        List<MagnetIndex.Magnet> teslas = new ArrayList<>();
        List<MagnetIndex.Magnet> magnetBlocks = new ArrayList<>();
        for (int y = minY; y < maxY; y++) {
            if (snapshot.isSectionEmpty((y - minY) >> 4)) {
                y += 15 - ((y - minY) & 15);
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockData data = snapshot.getBlockData(x, y, z);
                    int worldX = (ref.x() << 4) + x;
                    int worldZ = (ref.z() << 4) + z;
                    Polarity polarity = polarities.get(data);
                    if (polarity != null) {
                        magnets.add(new MagnetIndex.Magnet(worldX, y, worldZ, polarity));
                    } else if (teslaStates.contains(data)) {
                        teslas.add(new MagnetIndex.Magnet(worldX, y, worldZ, Polarity.SCARLET));
                    } else {
                        Polarity magnetPolarity = poweredMagnetStates.get(data);
                        if (magnetPolarity != null) {
                            magnetBlocks.add(new MagnetIndex.Magnet(worldX, y, worldZ, magnetPolarity));
                        }
                    }
                }
            }
        }
        return new ScanResult(magnets, teslas, magnetBlocks);
    }

    private void publish(ChunkRef ref, long generation, ScanResult result) {
        if (closed || !Objects.equals(generations.get(ref), generation)) {
            return;
        }
        scanning.remove(ref);
        World world = plugin.getServer().getWorld(ref.world());
        if (world == null || !world.isChunkLoaded(ref.x(), ref.z())) {
            return;
        }
        index.putChunk(ref.world(), ref.x(), ref.z(), result.magnets());
        teslaIndex.putChunk(ref.world(), ref.x(), ref.z(), result.teslas());
        // Powered magnets are not part of the snapshot data: read their signal now.
        for (MagnetIndex.Magnet magnet : result.magnetBlocks()) {
            if (world.getBlockAt(magnet.x(), magnet.y(), magnet.z()).isBlockIndirectlyPowered()) {
                index.add(ref.world(), magnet);
            }
        }
    }

    public void unloadChunk(UUID world, int chunkX, int chunkZ) {
        ChunkRef ref = new ChunkRef(world, chunkX, chunkZ);
        generations.remove(ref);
        scanning.remove(ref);
        index.removeChunk(world, chunkX, chunkZ);
        teslaIndex.removeChunk(world, chunkX, chunkZ);
    }

    public void onBlockPlaced(Block block) {
        polarityOf(block.getBlockData()).ifPresent(polarity -> index.add(block.getWorld().getUID(),
            new MagnetIndex.Magnet(block.getX(), block.getY(), block.getZ(), polarity)));
        if (teslaStates.contains(block.getBlockData())) {
            teslaIndex.add(block.getWorld().getUID(), new MagnetIndex.Magnet(block.getX(), block.getY(), block.getZ(), Polarity.SCARLET));
        }
        rescanIfScanning(block);
    }

    public void onBlockRemoved(Block block) {
        index.remove(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        teslaIndex.remove(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        rescanIfScanning(block);
    }

    /** A scan already running read the chunk before this change: start a newer one instead. */
    private void rescanIfScanning(Block block) {
        if (scanning.contains(new ChunkRef(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4))) {
            indexChunk(block.getChunk());
        }
    }

    public void tick() {
        if (!settings.enabled() || index.chunkCount() == 0) {
            deltas.clear();
            return;
        }
        int budget = settings.maxEntitiesPerTick();
        double scan = settings.scanRadius();
        Set<UUID> seen = new HashSet<>();
        // Start from a different player every tick so a spent budget never starves the same ones.
        List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        if (!players.isEmpty()) {
            Collections.rotate(players, -(playerOffset++ % players.size()));
        }
        for (Player player : players) {
            World world = player.getWorld();
            if (index.near(world.getUID(), player.getX(), player.getY(), player.getZ(), scan + MagnetForce.RANGE).isEmpty()) {
                deltas.remove(player.getUniqueId());
                continue;
            }
            List<Entity> candidates = new ArrayList<>(player.getNearbyEntities(scan, scan, scan));
            candidates.add(player);
            for (Entity entity : candidates) {
                if (budget <= 0) {
                    return;
                }
                if (!couldBeMagnetic(entity) || !seen.add(entity.getUniqueId())) {
                    continue;
                }
                // Every entity looked at costs budget, magnetic or not: reading equipment is the expensive part.
                budget--;
                if (isMagnetic(entity)) {
                    applyTo(entity);
                }
            }
        }
        deltas.keySet().retainAll(seen);
    }

    private void applyTo(Entity entity) {
        Vec position = new Vec(entity.getX(), entity.getY() + entity.getHeight() / 2.0, entity.getZ());
        Vec pull = Vec.ZERO;
        for (MagnetIndex.Magnet magnet : index.near(entity.getWorld().getUID(), position.x(), position.y(), position.z(),
            MagnetForce.RANGE)) {
            pull = pull.add(MagnetForce.pull(position, new Vec(magnet.x() + 0.5, magnet.y() + 0.5, magnet.z() + 0.5),
                magnet.polarity()));
        }
        Vec previous = deltas.getOrDefault(entity.getUniqueId(), Vec.ZERO);
        if (pull.equals(Vec.ZERO) && previous.length() < 1e-3) {
            deltas.remove(entity.getUniqueId());
            return;
        }
        Vec applied = MagnetForce.accumulate(previous, pull);
        deltas.put(entity.getUniqueId(), applied.scale(MagnetForce.DECAY));
        Vector velocity = entity.getVelocity().add(new Vector(applied.x(), applied.y(), applied.z()));
        entity.setVelocity(velocity);
        entity.setFallDistance(0);
    }

    private record ChunkRef(UUID world, int x, int z) {
    }

    private record ScanResult(List<MagnetIndex.Magnet> magnets, List<MagnetIndex.Magnet> teslas,
                              List<MagnetIndex.Magnet> magnetBlocks) {
    }

    /** Type-only check, before anything is read from the entity. */
    static boolean couldBeMagnetic(Entity entity) {
        return entity instanceof LivingEntity || entity instanceof Item || entity instanceof Minecart;
    }

    boolean isMagnetic(Entity entity) {
        if (entity instanceof Player player && (player.getGameMode() == GameMode.SPECTATOR || player.isFlying())) {
            return false;
        }
        if (entity instanceof Item item) {
            return isFerromagnetic(item.getItemStack());
        }
        if (entity instanceof IronGolem || entity instanceof Minecart) {
            return true;
        }
        if (entity instanceof LivingEntity living) {
            if (effects.has(living, ModEffectType.MAGNETIZING)) {
                return true;
            }
            EntityEquipment equipment = living.getEquipment();
            if (equipment != null) {
                for (ItemStack item : equipment.getArmorContents()) {
                    if (isFerromagnetic(item)) {
                        return true;
                    }
                }
                return isFerromagnetic(equipment.getItemInMainHand()) || isFerromagnetic(equipment.getItemInOffHand());
            }
        }
        return false;
    }

    /** Approximation of the mod's {@code ferromagnetic_items} tag by vanilla material names. */
    public static boolean isFerromagnetic(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        return isFerromagneticMaterial(item.getType().name());
    }

    static boolean isFerromagneticMaterial(String material) {
        String name = material.toUpperCase(Locale.ROOT);
        if (name.contains("NUGGET") && !name.contains("IRON")) {
            return false;
        }
        for (String hint : FERROMAGNETIC_HINTS) {
            if (name.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        closed = true;
        generations.clear();
        scanning.clear();
        index.clear();
        teslaIndex.clear();
        deltas.clear();
    }
}
