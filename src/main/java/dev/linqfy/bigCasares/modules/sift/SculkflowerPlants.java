package dev.linqfy.bigCasares.modules.sift;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * How the positions of placed Sculkflower crops and flowers are stored in the chunk that holds them.
 * Their blocks are vanilla stand-ins (see {@code SiftSculkflowers}), so the position is the only thing
 * that tells them from a torchflower crop or a warped fungus. A position is packed into one long:
 * the block's Y (offset by 32768) over its Z and X inside the chunk.
 */
final class SculkflowerPlants {

    private static final int Y_OFFSET = 32768;

    private SculkflowerPlants() {
    }

    static long pack(int localX, int y, int localZ) {
        if (localX < 0 || localX > 15 || localZ < 0 || localZ > 15 || y + Y_OFFSET < 0 || y + Y_OFFSET > 0xFFFF) {
            throw new IllegalArgumentException("Position outside a chunk column: " + localX + ", " + y + ", " + localZ);
        }
        return ((long) (y + Y_OFFSET) << 8) | ((long) localZ << 4) | localX;
    }

    static int localX(long packed) {
        return (int) (packed & 15);
    }

    static int localZ(long packed) {
        return (int) ((packed >> 4) & 15);
    }

    static int y(long packed) {
        return (int) (packed >> 8) - Y_OFFSET;
    }

    /** Sorted, so the stored value of an unchanged set does not change. */
    static long[] encode(Set<Long> positions) {
        return new TreeSet<>(positions).stream().mapToLong(Long::longValue).toArray();
    }

    static Set<Long> decode(long[] stored) {
        TreeSet<Long> positions = new TreeSet<>();
        if (stored != null) {
            Arrays.stream(stored).forEach(positions::add);
        }
        return positions;
    }
}
