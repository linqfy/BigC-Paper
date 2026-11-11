package dev.linqfy.bigCasares.modules.humillacion;

import java.time.Duration;
import java.time.Instant;

/** Pure rules of the Humillación, kept free of Bukkit so they can be tested. */
final class HumillacionPolicy {
    private static final double SIDE_DISTANCE = 1.5;

    private final HumillacionSettings settings;

    HumillacionPolicy(HumillacionSettings settings) {
        this.settings = settings;
    }

    /** Damage left over after the hit has emptied the victim's health and absorption. */
    static double overkill(double finalDamage, double health, double absorption) {
        return finalDamage - (Math.max(0.0, health) + Math.max(0.0, absorption));
    }

    /**
     * @param lastHumiliation when this victim was last humiliated, or null. Inside the immunity window the
     *                        hit kills normally; otherwise a player left at one heart could never die.
     */
    boolean shouldHumiliate(
        double finalDamage,
        double health,
        double absorption,
        boolean attackerIsPlayer,
        Instant lastHumiliation,
        Instant now
    ) {
        if (settings.requirePlayerAttacker() && !attackerIsPlayer) return false;
        if (lastHumiliation != null
            && now.isBefore(lastHumiliation.plus(Duration.ofSeconds(settings.immunitySeconds())))) {
            return false;
        }
        return overkill(finalDamage, health, absorption) >= settings.overkillThreshold();
    }

    double healthAfter(double maxHealth) {
        return Math.min(maxHealth, settings.remainingHealth());
    }

    /** Horizontal offset that puts the tombstone at the victim's right-hand side, given Bukkit yaw. */
    static double[] besideOffset(float yaw) {
        double radians = Math.toRadians(yaw);
        return new double[] {-Math.cos(radians) * SIDE_DISTANCE, -Math.sin(radians) * SIDE_DISTANCE};
    }
}
