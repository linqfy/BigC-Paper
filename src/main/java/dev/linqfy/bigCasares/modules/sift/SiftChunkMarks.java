package dev.linqfy.bigCasares.modules.sift;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.stream.LongStream;

/**
 * Block positions remembered in their chunk's persistent data, for stand-in blocks
 * that are a mod block only at some positions (a mangrove propagule that is a
 * willow sapling, a water cauldron that holds Ichor).
 */
final class SiftChunkMarks {

    private final NamespacedKey key;

    SiftChunkMarks(NamespacedKey key) {
        this.key = key;
    }

    boolean isMarked(Block block) {
        long[] packed = block.getChunk().getPersistentDataContainer().get(key, PersistentDataType.LONG_ARRAY);
        return packed != null && contains(packed, pack(block.getX(), block.getY(), block.getZ()));
    }

    void mark(Block block, boolean marked) {
        PersistentDataContainer chunk = block.getChunk().getPersistentDataContainer();
        long[] packed = chunk.getOrDefault(key, PersistentDataType.LONG_ARRAY, new long[0]);
        long position = pack(block.getX(), block.getY(), block.getZ());
        long[] updated = marked ? with(packed, position) : without(packed, position);
        if (updated.length == 0) {
            chunk.remove(key);
        } else if (updated != packed) {
            chunk.set(key, PersistentDataType.LONG_ARRAY, updated);
        }
    }

    /** Block position in one long, as vanilla's {@code BlockPos.asLong}. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static long[] with(long[] packed, long position) {
        return contains(packed, position) ? packed : LongStream.concat(Arrays.stream(packed), LongStream.of(position)).toArray();
    }

    static long[] without(long[] packed, long position) {
        return contains(packed, position) ? Arrays.stream(packed).filter(value -> value != position).toArray() : packed;
    }

    static boolean contains(long[] packed, long position) {
        return Arrays.stream(packed).anyMatch(value -> value == position);
    }
}
