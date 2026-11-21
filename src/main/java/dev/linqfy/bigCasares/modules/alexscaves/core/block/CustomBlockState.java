package dev.linqfy.bigCasares.modules.alexscaves.core.block;

/** One state of a {@link BlockBase} (see {@code alexscaves/blocks.yml}). */
public record CustomBlockState(BlockBase base, int index) {
    public CustomBlockState {
        if (base == null || index < 0 || index >= base.count()) {
            throw new IllegalArgumentException("State out of range: " + base + " " + index);
        }
    }

    public static CustomBlockState note(int index) {
        return new CustomBlockState(BlockBase.NOTE_BLOCK, index);
    }

    public boolean allocatable() {
        return base.allocatable(index);
    }

    public String blockDataString() {
        return base.blockDataString(index);
    }
}
