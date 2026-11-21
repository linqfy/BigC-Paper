package dev.linqfy.bigCasares.modules.legendary;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-cast base damage cap, reserved before dispatching a potentially reentrant damage event. */
final class LegendaryHitBudget {
    private final double maximum;
    private final Map<UUID, Double> used = new HashMap<>();

    LegendaryHitBudget(double maximum) {
        this.maximum = Math.max(0, maximum);
    }

    double reserve(UUID target, double requested) {
        double previous = used.getOrDefault(target, 0.0);
        double amount = Math.max(0, Math.min(requested, maximum - previous));
        used.put(target, previous + amount);
        return amount;
    }
}
