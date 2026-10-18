package dev.linqfy.bigCasares.modules.sift;

/**
 * The mod's per-tick limit on new path searches ({@code MAX_NEW_PATHS_PER_SERVER_TICK} and
 * {@code MAX_CRITICAL_EXIT_PATHS_PER_SERVER_TICK}, both one): Echo Golems search for long routes, and
 * several searches in one tick would stall the server. One budget is shared by every golem of a world.
 */
final class PathBudget {

    static final int MAX_NEW_PATHS_PER_TICK = 1;
    static final int MAX_CRITICAL_EXIT_PATHS_PER_TICK = 1;

    private long tick = Long.MIN_VALUE;
    private int used;
    private int criticalUsed;

    /** Takes one ordinary search for this tick; false when it is spent. */
    boolean tryAcquire(long now) {
        reset(now);
        if (used >= MAX_NEW_PATHS_PER_TICK) {
            return false;
        }
        used++;
        return true;
    }

    /** Takes one search of the separate budget for routes out of a canyon. */
    boolean tryAcquireCriticalExit(long now) {
        reset(now);
        if (criticalUsed >= MAX_CRITICAL_EXIT_PATHS_PER_TICK) {
            return false;
        }
        criticalUsed++;
        return true;
    }

    void clear() {
        tick = Long.MIN_VALUE;
        used = 0;
        criticalUsed = 0;
    }

    private void reset(long now) {
        if (tick == now) {
            return;
        }
        tick = now;
        used = 0;
        criticalUsed = 0;
    }
}
