package dev.linqfy.bigCasares.modules.duels;

/** Incremental traversal of the largest arena footprint. */
final class DuelArenaWork {
    @FunctionalInterface interface BlockAction { void apply(int x, int y, int z); }
    private static final int WIDTH = 69;
    private static final int HEIGHT = 17;
    private static final int TOTAL = WIDTH * WIDTH * HEIGHT;
    private int cursor;

    boolean done() { return cursor == TOTAL; }

    void step(int budget, BlockAction action) {
        int end = Math.min(TOTAL, cursor + budget);
        while (cursor < end) {
            int index = cursor++;
            int y = index % HEIGHT + 62;
            int z = index / HEIGHT % WIDTH - 34;
            int x = index / (HEIGHT * WIDTH) - 34;
            action.apply(x, y, z);
        }
    }
}
