package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the module's structures stand, for the mod's per-structure rules ({@code spawn_overrides}): while a chunk
 * generates, the structure pieces placed in it are queued (worldgen threads cannot write chunk data), and when the new
 * chunk loads their boxes are saved in the chunk's persistent data. Each box is a structure kind and its block bounds
 * clipped to the chunk. A structure that reaches into a neighbouring chunk which had already loaded is saved there the
 * first time a spawn asks about that chunk.
 */
public final class StructureMarks implements Listener {
    static final NamespacedKey KEY = new NamespacedKey("bigcasares", "ac_structures");
    private static final Map<String, List<int[]>> PENDING = new ConcurrentHashMap<>();

    /** Structure kinds that have spawn rules. */
    public enum Kind {
        GINGERBREAD_TOWN, LICOWITCH_TOWER, ABYSSAL_RUINS, VOLCANIC_CORE, AMBER_MONOLITH
    }

    private static String key(UUID world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    /** Called from worldgen: a piece of {@code kind} occupies this box inside chunk ({@code chunkX}, {@code chunkZ}). */
    static void queue(UUID world, int chunkX, int chunkZ, Kind kind, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        PENDING.computeIfAbsent(key(world, chunkX, chunkZ), ignored -> java.util.Collections.synchronizedList(new ArrayList<>()))
            .add(new int[] {kind.ordinal(), minX, minY, minZ, maxX, maxY, maxZ});
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        flush(event.getChunk());
    }

    /** Saves the boxes queued for a loaded chunk (main thread). */
    private static void flush(Chunk chunk) {
        List<int[]> boxes = PENDING.remove(key(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()));
        if (boxes == null || boxes.isEmpty()) {
            return;
        }
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        int[] old = data.getOrDefault(KEY, PersistentDataType.INTEGER_ARRAY, new int[0]);
        int[] merged;
        synchronized (boxes) {
            merged = merge(old, boxes);
        }
        if (merged.length != old.length) {
            data.set(KEY, PersistentDataType.INTEGER_ARRAY, merged);
        }
    }

    /**
     * The saved boxes with the new ones added, each box once: a piece is queued by every chunk of it that generates.
     * Pure logic.
     */
    static int[] merge(int[] old, List<int[]> boxes) {
        java.util.Set<List<Integer>> seen = new java.util.HashSet<>();
        for (int i = 0; i + 6 < old.length; i += 7) {
            seen.add(java.util.Arrays.stream(old, i, i + 7).boxed().toList());
        }
        int[] merged = java.util.Arrays.copyOf(old, old.length + boxes.size() * 7);
        int at = old.length;
        for (int[] box : boxes) {
            if (seen.add(java.util.Arrays.stream(box).boxed().toList())) {
                System.arraycopy(box, 0, merged, at, 7);
                at += 7;
            }
        }
        return java.util.Arrays.copyOf(merged, at);
    }

    /** Saved boxes of a kind in a loaded chunk, each {@code {minX, minY, minZ, maxX, maxY, maxZ}}. */
    public static List<int[]> boxes(Chunk chunk, Kind kind) {
        flush(chunk);
        int[] boxes = chunk.getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER_ARRAY);
        List<int[]> found = new ArrayList<>();
        if (boxes != null) {
            for (int i = 0; i + 6 < boxes.length; i += 7) {
                if (boxes[i] == kind.ordinal()) {
                    found.add(java.util.Arrays.copyOfRange(boxes, i + 1, i + 7));
                }
            }
        }
        return found;
    }

    /** True when the location is inside a recorded piece of that kind. */
    public static boolean inside(Location location, Kind kind) {
        if (location.getWorld() == null || !location.isChunkLoaded()) {
            return false;
        }
        flush(location.getChunk());
        int[] boxes = location.getChunk().getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER_ARRAY);
        if (boxes == null) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        for (int i = 0; i + 6 < boxes.length; i += 7) {
            if (boxes[i] == kind.ordinal() && x >= boxes[i + 1] && y >= boxes[i + 2] && z >= boxes[i + 3]
                && x <= boxes[i + 4] && y <= boxes[i + 5] && z <= boxes[i + 6]) {
                return true;
            }
        }
        return false;
    }
}
