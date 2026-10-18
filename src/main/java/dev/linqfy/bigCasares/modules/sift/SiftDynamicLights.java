package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.dimension.runtime.DimensionExtension;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Dynamic light for the Sift's Singer and for an Echo Golem that carries a Soul Block. In the mod a client mod
 * (LambDynamicLights) lights the surroundings of the Singer's head at level 15 and of a carried Soul Block at level
 * 14, falling off with distance. A vanilla client cannot do that, so this extension does it on the server with a
 * single {@code minecraft:light} block per source, placed where the source is and moved when it moves
 * ({@link SiftLightLedger} holds the rules).
 *
 * <p><b>Never a stray light block.</b> A light block is placed only in air and removed only if it still is a light
 * block. It is removed when the source moves on, despawns, dies, stops carrying, is farther than
 * {@value #RANGE} blocks from every player or is not among the {@value #MAX_LIGHTS} nearest sources; when the chunk
 * unloads ({@link ChunkUnloadEvent}, before the chunk is saved); and when the dimension deactivates or the plugin
 * disables (the cleanup action). Every cell the extension lights is also marked in its chunk's persistent data
 * and unmarked when the light goes, which is the only thing persisted: if the server dies with lights placed, the
 * saved chunk has both the block and its mark, and the next {@link ChunkLoadEvent} (and this extension's
 * activation, for chunks already loaded) removes the leftover light and clears the mark.</p>
 *
 * <p><b>Cost.</b> One pass every {@value #INTERVAL_TICKS} ticks over the sources of the Singers and golems; a
 * source whose cell did not change costs two block reads; one that moved costs two block writes.</p>
 *
 * <p>Differences: light blocks light the server too (a Singer keeps Siftslate Growth alive around her, snow does not
 * settle in her light), a light block shows as a marker to players holding a light item, the light does not fall off
 * from a point as the mod's does but is the block light of a level-15 or level-14 source, and a source inside solid
 * ground (a Singer rising or sinking) lights nothing until its head is in the open.</p>
 */
final class SiftDynamicLights implements DimensionExtension, Listener {

    /** A moving light: who, where and how bright. */
    record Source(UUID id, Location at, int level) {
    }

    static final long INTERVAL_TICKS = 2L;
    static final int MAX_LIGHTS = 24;
    static final double RANGE = 64.0;

    private final JavaPlugin plugin;
    private final List<Supplier<List<Source>>> providers;
    private final SiftChunkMarks marks = new SiftChunkMarks(new NamespacedKey(SiftBlocks.NS, "dynamic_lights"));
    private final BlockData[] lights = new BlockData[16];
    private World world;
    private SiftLightLedger ledger;

    SiftDynamicLights(JavaPlugin plugin, List<Supplier<List<Source>>> providers) {
        this.plugin = plugin;
        this.providers = List.copyOf(providers);
    }

    @Override
    public void onActivate(ActiveDimension active) {
        world = active.world();
        ledger = new SiftLightLedger(new WorldCells());
        for (Chunk chunk : world.getLoadedChunks()) {
            sweep(chunk);
        }
        active.registrations().registerListener("sift-dynamic-lights", this);
        active.registrations().scheduleRepeating("sift-dynamic-lights", this::run, INTERVAL_TICKS, INTERVAL_TICKS);
        active.registrations().ownCleanup("sift-dynamic-lights-clear", () -> {
            SiftLightLedger old = ledger;
            ledger = null;
            if (old != null) {
                old.releaseAll();
            }
            world = null;
        });
    }

    private void run() {
        SiftLightLedger current = ledger;
        World sift = world;
        if (current == null || sift == null) {
            return;
        }
        try {
            Map<UUID, Source> sources = new HashMap<>();
            for (Supplier<List<Source>> provider : providers) {
                for (Source source : provider.get()) {
                    if (source.at().getWorld() != null && source.at().getWorld().equals(sift)) {
                        sources.put(source.id(), source);
                    }
                }
            }
            Map<UUID, Double> distances = new HashMap<>();
            List<Player> players = sift.getPlayers();
            for (Source source : sources.values()) {
                double nearest = Double.MAX_VALUE;
                for (Player player : players) {
                    nearest = Math.min(nearest, player.getLocation().distanceSquared(source.at()));
                }
                distances.put(source.id(), nearest);
            }
            Set<UUID> shining = SiftLightLedger.nearest(distances, RANGE * RANGE, MAX_LIGHTS);
            current.releaseExcept(shining);
            for (UUID id : shining) {
                Source source = sources.get(id);
                Location at = source.at();
                current.update(id, SiftLightLedger.candidates(sift.getUID(), at.getX(), at.getY(), at.getZ()), source.level());
            }
        } catch (RuntimeException failure) {
            // A light that cannot be moved must not stay behind: drop them all and try again next pass.
            plugin.getLogger().log(Level.WARNING, "[TheSift] Dynamic lights failed; removing them", failure);
            current.releaseAll();
        }
    }

    /** Before the chunk is saved: its lights go (their sources are unloading with it or no longer in range). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkUnload(ChunkUnloadEvent event) {
        SiftLightLedger current = ledger;
        if (current != null && event.getWorld().equals(world)) {
            current.releaseChunk(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
        }
    }

    /** A crash can leave a light block and its mark in a saved chunk: remove them. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.getWorld().equals(world)) {
            sweep(event.getChunk());
        }
    }

    private void sweep(Chunk chunk) {
        long[] left = marks.positions(chunk);
        if (left.length == 0) {
            return;
        }
        for (long packed : left) {
            SiftPos at = SiftPos.of(packed);
            Block block = chunk.getWorld().getBlockAt(at.x(), at.y(), at.z());
            if (block.getType() == Material.LIGHT) {
                block.setBlockData(Bukkit.createBlockData(Material.AIR), false);
            }
        }
        marks.clear(chunk);
        plugin.getLogger().info("[TheSift] Removed " + left.length + " leftover light block(s) in chunk " + chunk.getX() + ", "
            + chunk.getZ());
    }

    private BlockData light(int level) {
        int clamped = Math.max(0, Math.min(15, level));
        if (lights[clamped] == null) {
            Light data = (Light) Material.LIGHT.createBlockData();
            data.setLevel(clamped);
            lights[clamped] = data;
        }
        return lights[clamped];
    }

    /** The ledger's view of the Sift world. */
    private final class WorldCells implements SiftLightLedger.Gateway {

        private Block blockOf(SiftLightLedger.Cell cell) {
            World sift = world;
            if (sift == null || !sift.getUID().equals(cell.world()) || cell.y() < sift.getMinHeight()
                || cell.y() >= sift.getMaxHeight() || !sift.isChunkLoaded(cell.chunkX(), cell.chunkZ())) {
                return null;
            }
            return sift.getBlockAt(cell.x(), cell.y(), cell.z());
        }

        @Override
        public boolean isFree(SiftLightLedger.Cell cell) {
            Block block = blockOf(cell);
            return block != null && block.getType().isAir();
        }

        @Override
        public boolean isLight(SiftLightLedger.Cell cell, int level) {
            Block block = blockOf(cell);
            return block != null && block.getType() == Material.LIGHT && block.getBlockData() instanceof Light data
                && data.getLevel() == level;
        }

        @Override
        public boolean place(SiftLightLedger.Cell cell, int level) {
            Block block = blockOf(cell);
            if (block == null || !(block.getType().isAir() || block.getType() == Material.LIGHT)) {
                return false;
            }
            // The mark goes first: a light block must never exist without it.
            marks.mark(block, true);
            block.setBlockData(light(level), false);
            return true;
        }

        @Override
        public void remove(SiftLightLedger.Cell cell) {
            Block block = blockOf(cell);
            if (block == null) {
                return;
            }
            if (block.getType() == Material.LIGHT) {
                block.setBlockData(Bukkit.createBlockData(Material.AIR), false);
            }
            marks.mark(block, false);
        }
    }
}
