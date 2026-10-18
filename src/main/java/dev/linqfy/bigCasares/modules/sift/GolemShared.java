package dev.linqfy.bigCasares.modules.sift;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What every Echo Golem of a world shares, the mod's static fields: which golem has claimed which Soul Block,
 * how often a Soul Block has been given up on, and the budget of new path searches per tick.
 */
final class GolemShared {

    /** A Soul Block that golems failed to reach: how often, when to try again and when to forget it all. */
    record SoulFailure(int failures, long retryAfter, long forgetAfter) {
    }

    private final Map<Long, UUID> reservations = new HashMap<>();
    private final Map<Long, SoulFailure> failures = new HashMap<>();
    private final PathBudget budget = new PathBudget();

    Map<Long, UUID> reservations() {
        return reservations;
    }

    Map<Long, SoulFailure> failures() {
        return failures;
    }

    PathBudget budget() {
        return budget;
    }

    /** {@code clearTransientState}. */
    void clear() {
        reservations.clear();
        failures.clear();
        budget.clear();
    }
}
