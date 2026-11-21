package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntFunction;

/**
 * Two-block plants (curly fern): the bottom half is the {@code default} variant, the top half {@link #UPPER}. The
 * plant only goes in with room for both halves; removing either half (break, explosion) removes the other one
 * without drops, so the plant drops once, from the half that was removed.
 */
public final class TwoBlockPlants {
    public static final String UPPER = "upper";

    private TwoBlockPlants() {
    }

    public static boolean isTall(ModBlockRegistry registry, String blockId) {
        return registry.variants(blockId).contains(UPPER) && registry.variants(blockId).contains(BlockVariants.DEFAULT);
    }

    /** Whether a bottom half placed at {@code y} has room for its top half. */
    public static boolean roomForTop(int y, int maxHeight, boolean aboveEmpty) {
        return y + 1 < maxHeight && aboveEmpty;
    }

    /**
     * The height of the other half of the plant at {@code y}, when that block is still the plant's other half.
     *
     * @param at module block at a height of the same column
     */
    public static OptionalInt otherHalf(ModBlockRegistry registry, ModBlockRegistry.Placed placed, int y,
                                        IntFunction<Optional<ModBlockRegistry.Placed>> at) {
        if (!isTall(registry, placed.blockId())) {
            return OptionalInt.empty();
        }
        boolean top = UPPER.equals(placed.variant());
        int other = top ? y - 1 : y + 1;
        String want = top ? BlockVariants.DEFAULT : UPPER;
        return at.apply(other)
            .filter(found -> found.blockId().equals(placed.blockId()) && found.variant().equals(want))
            .map(found -> OptionalInt.of(other))
            .orElse(OptionalInt.empty());
    }
}
