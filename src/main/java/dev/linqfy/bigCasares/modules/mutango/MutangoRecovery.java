package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Lo que se arregla despues de una caida: devuelve los bloques que la grieta y las ondas dejaron sacados (el diario
 * MutangoBlockJournal) y sube a quien entra incrustado en bloques.
 */
final class MutangoRecovery implements Listener {

    private final JavaPlugin plugin;
    private final MutangoBlockJournal journal;
    /** Bloques por devolver que estan en chunks sin cargar, por chunk (se devuelven cuando carga). */
    private final Map<String, List<MutangoBlockJournal.Entry>> waiting = new HashMap<>();

    private final MutangoTasks tasks;
    /** Cuando hacer el guardado con flush: espera 1 minuto desde lo primero devuelto y no se repite antes de 5 minutos. */
    private final MutangoSavePolicy savePolicy = new MutangoSavePolicy(60_000L, 300_000L);

    MutangoRecovery(JavaPlugin plugin, MutangoBlockJournal journal, MutangoTasks tasks) {
        this.plugin = plugin;
        this.tasks = tasks;
        this.journal = journal;
    }

    /**
     * Si el servidor se cayo con la grieta abierta o las ondas arrancando bloques, devuelve los que faltan (los que
     * siguen siendo aire o la luz verde de la grieta; si alguien puso algo ahi, se respeta) y tacha los que el disco ya
     * tiene bien. Se llama al arrancar: nadie pelea todavia (Neguechen retomada tiene la grieta ya cerrada). Solo toca los
     * chunks que ya estan cargados (no carga ninguno de forma sincrona al arrancar): los bloques de los demas siguen
     * anotados y se comprueban cuando su chunk carga.
     */
    void restoreLeftoverBlocks() {
        int restored = 0;
        int waitingForChunk = 0;
        for (MutangoBlockJournal.Entry entry : journal.entries()) {
            World world = plugin.getServer().getWorld(entry.world());
            if (world == null) {
                // ese mundo no esta cargado ahora: queda anotado y se comprueba cuando cargue (otro plugin lo puede cargar
                // despues de este modulo); su chunk se identifica por mundo, asi que no se confunde con otros
                waiting.computeIfAbsent(chunkKey(entry.world(), entry.x() >> 4, entry.z() >> 4),
                    key -> new ArrayList<>()).add(entry);
                continue;
            }
            if (!world.isChunkLoaded(entry.x() >> 4, entry.z() >> 4)) {
                waiting.computeIfAbsent(chunkKey(entry.world(), entry.x() >> 4, entry.z() >> 4),
                    key -> new ArrayList<>()).add(entry);
                waitingForChunk++;
                continue;
            }
            restored += verifyOrRestore(world, entry);
        }
        flush();
        if (restored > 0) {
            plugin.getLogger().warning("Se devolvieron " + restored + " bloques que quedaron sacados (el servidor se cerro con "
                + "la grieta o las ondas de las manos abiertas).");
        }
        if (waitingForChunk > 0) {
            plugin.getLogger().info("[Mutango] " + waitingForChunk + " bloques por devolver estan en chunks sin cargar: se "
                + "comprueban cuando cada chunk cargue.");
        }
    }

    /**
     * Un chunk carga: se comprueban los bloques anotados que tiene. Los que habian quedado esperando el arranque y los que
     * se devolvieron en memoria y todavia no tenian evidencia de guardado. Lo que el disco trae es la prueba: si el hueco
     * esta ahi, se devuelve otra vez; si el bloque esta bien (o lo cambio alguien), se tacha.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        List<MutangoBlockJournal.Entry> entries = new ArrayList<>();
        List<MutangoBlockJournal.Entry> leftover = waiting.remove(chunkKey(world.getUID(), chunkX, chunkZ));
        if (leftover != null) {
            entries.addAll(leftover);
        }
        entries.addAll(journal.awaitingInChunk(world.getUID(), chunkX, chunkZ));
        if (entries.isEmpty()) {
            return;
        }
        int restored = 0;
        for (MutangoBlockJournal.Entry entry : entries) {
            restored += verifyOrRestore(world, entry);
        }
        flush();
        if (restored > 0) {
            plugin.getLogger().info("[Mutango] Se devolvieron " + restored + " bloques que el disco trajo con el hueco, en un "
                + "chunk que recien cargo.");
        }
    }

    /**
     * Comprueba ese bloque: si es aire o la luz de la grieta (el hueco), lo devuelve en memoria y sigue anotado hasta que
     * haya evidencia de guardado; si ya esta bien (o alguien puso otra cosa), se tacha. Devuelve 1 si lo cambio.
     */
    private int verifyOrRestore(World world, MutangoBlockJournal.Entry entry) {
        try {
            Block block = world.getBlockAt(entry.x(), entry.y(), entry.z());
            Material now = block.getType();
            if (now.isAir() || now == Material.VERDANT_FROGLIGHT) {
                block.setBlockData(plugin.getServer().createBlockData(entry.data()), false);
                journal.restored(entry.world(), entry.x(), entry.y(), entry.z());
                return 1;
            }
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning("Bloque por devolver ilegible: " + entry);
        }
        journal.release(entry.world(), entry.x(), entry.y(), entry.z());
        return 0;
    }

    /**
     * Cada tanto: si hay bloques devueltos en memoria esperando evidencia de guardado, hace UN guardado del mundo que espera
     * a que terminen las escrituras (World#save(true)) y, cuando vuelve, tacha los que estaban devueltos antes de empezar.
     * WorldSaveEvent no sirve de evidencia: Paper lo dispara al entrar a guardar, antes de escribir los chunks.
     */
    void tickSave() {
        long now = System.currentTimeMillis();
        for (World world : plugin.getServer().getWorlds()) {
            UUID id = world.getUID();
            List<MutangoBlockJournal.Entry> snapshot = journal.awaiting(id);
            if (!savePolicy.due(id, !snapshot.isEmpty(), now)) {
                continue;
            }
            try {
                world.save(true);
            } catch (RuntimeException failed) {
                plugin.getLogger().warning("[Mutango] No se pudo guardar el mundo " + world.getName() + ": " + failed);
                continue; // sigue anotado: se reintenta
            }
            savePolicy.saved(id, now);
            journal.releaseSaved(snapshot);
        }
    }

    // ---- cambios legitimos despues de devolver un bloque (no son huecos de la pelea: no se vuelven a reponer)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        touched(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        touched(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        touchedAll(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        touchedAll(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::touched);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::touched);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Cow) {
            return; // Neguechen (una vaca) lanza este evento solo para preguntar a las protecciones: no cambia nada
        }
        touched(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        touched(event.getBlock());
    }

    private void touchedAll(List<Block> blocks, BlockFace direction) {
        if (!journal.hasAwaiting()) {
            return;
        }
        for (Block block : blocks) {
            touched(block);
            touched(block.getRelative(direction));
        }
    }

    private void touched(Block block) {
        if (journal.hasAwaiting()) {
            journal.touched(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private void flush() {
        try {
            journal.flush();
        } catch (IllegalStateException failed) {
            plugin.getLogger().warning(failed.getMessage());
        }
    }

    private static String chunkKey(UUID world, int chunkX, int chunkZ) {
        return world + "|" + chunkX + "|" + chunkZ;
    }

    /**
     * Si al entrar el jugador quedo incrustado en bloques (salio mientras estaba dentro de la grieta o de un hueco de las
     * ondas y despues se devolvieron los bloques), lo sube a la superficie, igual que a los que estan conectados.
     */
    private void rescueIfStuck(Player player) {
        if (!player.isOnline() || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Location at = player.getLocation();
        Block feet = at.getBlock();
        Block head = player.getEyeLocation().getBlock();
        if (feet.isPassable() || head.isPassable()) {
            return;
        }
        Location up = at.clone();
        up.setY(MutangoGround.y(at) + 1.1);
        player.teleport(up);
        player.setFallDistance(0);
        player.sendMessage("§7Habías quedado dentro de unos bloques: te subí a la superficie.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tasks.later("rescue-if-stuck", () -> rescueIfStuck(player), 5L);
    }
}
