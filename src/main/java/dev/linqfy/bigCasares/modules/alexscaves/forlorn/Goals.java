package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import java.util.List;

/**
 * A small stand-in for vanilla's goal selector: the mod's mobs are a list of goals by priority, and the first one that
 * can start (or keeps running) drives the mob; a goal of higher priority can take over a running one.
 */
final class Goals {
    /** One goal of a mob. */
    interface Goal {
        /** Whether it may start now (checked every behaviour tick while it is not running). */
        boolean canUse();

        /** Whether it keeps running. */
        default boolean canContinue() {
            return canUse();
        }

        default void start() {
        }

        void tick();

        default void stop() {
        }
    }

    private final List<Goal> goals;
    private int active = -1;

    Goals(List<Goal> goals) {
        this.goals = List.copyOf(goals);
    }

    /** Runs one behaviour tick: the highest priority goal that can act does. */
    void tick() {
        for (int i = 0; i < goals.size(); i++) {
            Goal goal = goals.get(i);
            if (active >= 0 && i > active) {
                break;
            }
            boolean runs = i == active ? goal.canContinue() : goal.canUse();
            if (!runs) {
                if (i == active) {
                    goal.stop();
                    active = -1;
                }
                continue;
            }
            if (i != active) {
                if (active >= 0) {
                    goals.get(active).stop();
                }
                active = i;
                goal.start();
            }
            goal.tick();
            return;
        }
    }

    /** Index of the running goal, or -1. */
    int active() {
        return active;
    }

    /** Stops whatever runs (death, unload, riding). */
    void reset() {
        if (active >= 0) {
            goals.get(active).stop();
            active = -1;
        }
    }
}
