package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The phase-2 flood: real water source blocks, layer by layer, only on air cells of a watertight layer of the room
 * (see {@link EnlilFloodPlanner}). Every changed block is journaled in memory with its original data and restored in
 * {@link #restore()} (called from {@code stop()}, which also runs on plugin disable). Each batch is also written ahead
 * to {@code plugins/BigCasares/mythicdungeons/enlil-flood-<boss>.txt} ({@link EnlilFloodJournal}) before it is
 * placed; if that write fails nothing more is flooded. After a crash the module (at enable) and every new Enlil fight
 * remove the leftover sources ({@link #recoverOrphans}). Sources are placed without physics, so they never spread;
 * removing them with physics lets any water that leaked through a broken wall dry up.
 */
final class EnlilFlood {

    private static final Set<UUID> LIVE = ConcurrentHashMap.newKeySet();
    /** Journals restored or dried in this run: their air may not be saved yet, so they are not deleted until a later start. */
    private static final Set<Path> FRESH = ConcurrentHashMap.newKeySet();
    /** Same folder as the datapack backups of the module: {@code plugins/BigCasares/mythicdungeons/}. */
    static final String FOLDER = "mythicdungeons";
    private static final int PLACED_PER_TICK = 40;

    private final Logger logger;
    private final UUID bossId;
    private final World world;
    private final EnlilArena arena;
    private final int startX;
    private final int startZ;
    private final EnlilFloodJournal file;
    private final Map<Block, BlockData> journal = new LinkedHashMap<>();
    private final Deque<Block> pending = new ArrayDeque<>();
    private int layersPlanned;
    private boolean leaked;
    private boolean restored;

    EnlilFlood(JavaPlugin plugin, UUID bossId, World world, EnlilArena arena, int startX, int startZ) {
        this(folder(plugin), plugin.getLogger(), bossId, world, arena, startX, startZ);
    }

    EnlilFlood(Path folder, Logger logger, UUID bossId, World world, EnlilArena arena, int startX, int startZ) {
        this.logger = logger;
        this.bossId = bossId;
        this.world = world;
        this.arena = arena;
        this.startX = startX;
        this.startZ = startZ;
        this.file = new EnlilFloodJournal(folder, bossId);
        LIVE.add(bossId);
    }

    /** Plans the layers up to {@code layers}; returns {@code true} when a new layer started to fill. */
    boolean raiseTo(int layers) {
        boolean started = false;
        while (!restored && !leaked && layersPlanned < Math.min(layers, EnlilTuning.FLOOD_LAYERS)) {
            int y = arena.floorY() + layersPlanned;
            Optional<List<EnlilFloodPlanner.Cell>> cells = EnlilFloodPlanner.plan(arena, startX, startZ,
                (x, z) -> world.getBlockAt(x, y, z).isEmpty());
            layersPlanned++;
            if (cells.isEmpty()) {
                leaked = true;
                logger.warning("[Mazmorras] Enlil: la sala no es estanca en y=" + y
                    + "; no se inunda (" + world.getName() + " " + startX + "," + startZ + ").");
                return started;
            }
            for (EnlilFloodPlanner.Cell cell : cells.get()) {
                pending.add(world.getBlockAt(cell.x(), y, cell.z()));
            }
            started = true;
        }
        return started;
    }

    /**
     * Places the next batch of the layer being filled (the water pours in from the walls). The batch is journaled on
     * disk first; when that fails the flood stops there (marked as leaked) and nothing of the batch is placed.
     */
    void tick() {
        if (restored || leaked) {
            return;
        }
        List<Block> batch = new ArrayList<>(PLACED_PER_TICK);
        while (batch.size() < PLACED_PER_TICK && !pending.isEmpty()) {
            Block block = pending.poll();
            if (block.isEmpty() && !journal.containsKey(block) && !batch.contains(block)) {
                batch.add(block);
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        List<EnlilFloodJournal.Cell> cells = new ArrayList<>(batch.size());
        for (Block block : batch) {
            cells.add(new EnlilFloodJournal.Cell(world.getName(), block.getX(), block.getY(), block.getZ()));
        }
        if (!file.append(cells)) {
            leaked = true;
            pending.clear();
            logger.warning("[Mazmorras] Enlil: no se pudo escribir el diario de la inundacion ("
                + file.path() + "); no se inunda mas.");
            return;
        }
        for (Block block : batch) {
            journal.put(block, block.getBlockData());
            block.setType(Material.WATER, false);
        }
    }

    boolean leaked() {
        return leaked;
    }

    int layers() {
        return layersPlanned;
    }

    /**
     * Puts back every journaled block that is still our water; idempotent. The journal on disk is not deleted (the
     * chunks may not be saved yet): it is marked restored and a later start deletes it ({@link #recoverOrphans}).
     */
    void restore() {
        if (restored) {
            return;
        }
        restored = true;
        pending.clear();
        for (Map.Entry<Block, BlockData> entry : journal.entrySet()) {
            Block block = entry.getKey();
            if (block.getType() == Material.WATER) {
                block.setBlockData(entry.getValue(), true);
            }
        }
        journal.clear();
        try {
            Path marked = file.markRestored();
            if (marked != null) {
                FRESH.add(marked);
            }
        } catch (IOException failure) {
            // the active journal stays: a later recovery dries whatever is still water and handles it
            logger.log(Level.WARNING, "[Mazmorras] Enlil: no se pudo marcar el diario de la inundacion", failure);
        }
        LIVE.remove(bossId);
    }

    private static Path folder(JavaPlugin plugin) {
        return plugin.getDataFolder().toPath().resolve(FOLDER);
    }

    /**
     * Removes the water of floods whose fight did not end cleanly (a crash), or that came back because the chunks
     * were not saved after a clean restore: only cells listed in a journal of an Enlil that is not alive in this run
     * and that are still water. A journal of an unloaded world is kept (see {@link EnlilFloodJournal#recover}).
     */
    static void recoverOrphans(JavaPlugin plugin) {
        recoverOrphans(folder(plugin), plugin.getLogger(), cells(plugin.getServer()));
    }

    static void recoverOrphans(Path folder, Logger logger, EnlilFloodJournal.Cells cells) {
        try {
            EnlilFloodJournal.Recovery recovery = EnlilFloodJournal.recover(folder, Set.copyOf(LIVE), FRESH, cells);
            if (recovery.dried() > 0) {
                logger.info("[Mazmorras] Enlil: se sacaron " + recovery.dried()
                    + " bloques de agua de una pelea vieja.");
            }
        } catch (IOException failure) {
            logger.log(Level.WARNING, "[Mazmorras] Enlil: no se pudieron revisar inundaciones viejas", failure);
        }
    }

    private static EnlilFloodJournal.Cells cells(Server server) {
        return new EnlilFloodJournal.Cells() {
            @Override
            public EnlilFloodJournal.State state(EnlilFloodJournal.Cell cell) {
                World world = server.getWorld(cell.world());
                if (world == null) {
                    return EnlilFloodJournal.State.NO_WORLD;
                }
                return world.getBlockAt(cell.x(), cell.y(), cell.z()).getType() == Material.WATER
                    ? EnlilFloodJournal.State.WATER : EnlilFloodJournal.State.OTHER;
            }

            @Override
            public void dry(EnlilFloodJournal.Cell cell) {
                World world = server.getWorld(cell.world());
                if (world != null) {
                    world.getBlockAt(cell.x(), cell.y(), cell.z()).setType(Material.AIR, true);
                }
            }
        };
    }
}
