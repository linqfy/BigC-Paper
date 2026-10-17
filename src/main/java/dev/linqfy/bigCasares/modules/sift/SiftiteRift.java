package dev.linqfy.bigCasares.modules.sift;

/**
 * The timeline and placement of the mini rift ({@code MiniRiftEntity}) that releases the recovered items.
 * The rift opens for {@value #OPEN_TICKS} ticks; from then on, every {@value #EMIT_INTERVAL} ticks of its
 * age it releases the next item; when the last item has gone it closes for {@value #CLOSE_TICKS} ticks and
 * disappears. Its renderer draws a glowing cube of 0.8 blocks scaled by {@code smoothstep(open)}.
 * Pure state, tested without a server.
 *
 * <p>The mod's {@code SiftiteRecoveryManager.spawnMiniRift} puts the rift 0.75 blocks above the player's
 * feet on the first of eight points around the player (1.55 blocks away, every 45 degrees from the +X
 * direction) where that block and the one above it are air, and at the first point when none is.</p>
 *
 * <p>Differences: the rift has no entity or model here; {@link SiftiteRecovery} shows it as particles of the
 * same size and timing.</p>
 */
final class SiftiteRift {

    static final int OPEN_TICKS = 12;
    static final int EMIT_INTERVAL = 5;
    static final int CLOSE_TICKS = 12;
    static final int CANDIDATES = 8;
    static final double DISTANCE = 1.55;
    static final double HEIGHT = 0.75;
    /** Half the edge of the mod's rift cube. */
    static final double HALF_SIZE = 0.4;

    private int remaining;
    private int age;
    private boolean closing;
    private int closingTicks;
    private boolean finished;

    SiftiteRift(int items) {
        this.remaining = Math.max(0, items);
    }

    /** Offset from the player's feet of the {@code index}-th candidate position, as {x, y, z}. */
    static double[] candidate(int index) {
        double angle = index * Math.PI / 4.0;
        return new double[] {Math.cos(angle) * DISTANCE, HEIGHT, Math.sin(angle) * DISTANCE};
    }

    /** Where the rift goes when no candidate is free. */
    static double[] fallback() {
        return new double[] {DISTANCE, HEIGHT, 0.0};
    }

    /** Advances one tick. Returns true when an item is released on this tick. */
    boolean tick() {
        age++;
        if (closing) {
            closingTicks++;
            if (closingTicks > CLOSE_TICKS) {
                finished = true;
            }
            return false;
        }
        boolean release = false;
        if (age >= OPEN_TICKS && age % EMIT_INTERVAL == 0 && remaining > 0) {
            remaining--;
            release = true;
        }
        if (remaining == 0 && age >= OPEN_TICKS) {
            closing = true;
            closingTicks = 0;
        }
        return release;
    }

    boolean isClosing() {
        return closing;
    }

    /** True once the closing animation is over. */
    boolean isFinished() {
        return finished;
    }

    int remaining() {
        return remaining;
    }

    int age() {
        return age;
    }

    /** Linear open fraction, 0 to 1. */
    float openScale() {
        if (closing) {
            return Math.max(0.0F, 1.0F - closingTicks / (float) CLOSE_TICKS);
        }
        return Math.min(1.0F, age / (float) OPEN_TICKS);
    }

    /** The cube's scale as the mod's renderer applies it: smoothstep of the open fraction. */
    float size() {
        float open = openScale();
        return open * open * (3.0F - 2.0F * open);
    }
}
