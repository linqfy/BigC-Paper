package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import org.bukkit.util.Vector;

/**
 * Rules of the Brainiac's waste drum, after the mod's {@code BrainiacEntity}: it drinks one below half health
 * (animation tick 60 heals and buffs it) and throws it below three quarters while a target is in sight within
 * 20 blocks (animation tick 15 releases it). Pure logic, so the timings and the throw can be checked.
 */
final class BrainiacBarrel {
    static final int DRINK_TICKS = 75;
    static final int DRINK_EFFECT_TICK = 60;
    static final int THROW_TICKS = 30;
    static final int THROW_RELEASE_TICK = 15;
    static final int BITE_TICKS = 25;
    static final int SMASH_TICKS = 20;
    /** Bite while picking up a drum: the drum goes between ticks 10 and 15. */
    static final int PICKUP_FIRST_TICK = 10;
    static final int PICKUP_LAST_TICK = 15;
    static final double DRINK_HEAL = 10.0;
    static final int DRINK_EFFECT_DURATION = 400;
    static final double THROW_RANGE = 20.0;

    private BrainiacBarrel() {
    }

    /** One roll of 1 in {@code odds} per tick, over {@code ticks} ticks. */
    static double chance(int odds, int ticks) {
        return 1.0 - Math.pow((odds - 1.0) / odds, Math.max(1, ticks));
    }

    static boolean wantsToDrink(double health, double maxHealth, boolean hasBarrel, double roll, int ticks) {
        return hasBarrel && health < maxHealth * 0.5 && roll < chance(20, ticks);
    }

    static boolean wantsToThrow(double health, double maxHealth, double distance, boolean hasBarrel, double roll, int ticks) {
        return hasBarrel && health < maxHealth * 0.75 && distance < THROW_RANGE && roll < chance(30, ticks);
    }

    /** Where the drum leaves the hands: (0.65, -0.3, 0.9) turned by the head, from the eyes. */
    static Vector releaseOffset(double pitchDegrees, double headYawDegrees) {
        double pitch = -Math.toRadians(pitchDegrees);
        double yaw = -Math.toRadians(headYawDegrees);
        double y = -0.3 * Math.cos(pitch) + 0.9 * Math.sin(pitch);
        double z = 0.9 * Math.cos(pitch) + 0.3 * Math.sin(pitch);
        double x = 0.65;
        return new Vector(x * Math.cos(yaw) + z * Math.sin(yaw), y, z * Math.cos(yaw) - x * Math.sin(yaw));
    }

    /** Throw velocity: the aim (flattened to 0.35 horizontally, 0.4 up) as a direction, 0.2 per block of distance. */
    static Vector throwVelocity(Vector release, Vector targetEyes, double distance) {
        Vector aim = targetEyes.clone().subtract(release);
        aim.setX(aim.getX() * 0.35).setY(0.4).setZ(aim.getZ() * 0.35);
        return aim.length() == 0 ? new Vector() : aim.normalize().multiply(distance * 0.2);
    }
}
