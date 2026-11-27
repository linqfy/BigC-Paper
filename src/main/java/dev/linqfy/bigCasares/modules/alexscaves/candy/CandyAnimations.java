package dev.linqfy.bigCasares.modules.alexscaves.candy;

import java.util.random.RandomGenerator;

/**
 * Pure rules behind the candy mobs' less common animations (the mod's {@code Animation} constants): which arm swings,
 * when a Gummy Bear fishes or eats, and which limbs a Gingerbread Man loses when hurt.
 */
final class CandyAnimations {
    /** Mod {@code ANIMATION_FISH} / {@code ANIMATION_BACKSCRATCH}: 35 and 90 ticks. */
    static final int FISH_TICKS = 35;
    static final int BACKSCRATCH_TICKS = 90;
    /** Mod {@code ANIMATION_EAT} of the Gummy Bear: 40 ticks. */
    static final int EAT_TICKS = 40;

    static final int LEFT_LEG = 1;
    static final int RIGHT_LEG = 2;
    static final int LEFT_ARM = 4;
    static final int RIGHT_ARM = 8;

    private CandyAnimations() {
    }

    /** Mod {@code getMainArm() == RIGHT ? SWING_RIGHT : SWING_LEFT} (Licowitch) and {@code getAnimationForHand(false)}. */
    static String mainHandSwing(boolean leftHanded) {
        return leftHanded ? "swing_left" : "swing_right";
    }

    /** Mod open-door goal: {@code random.nextBoolean() ? SWING_RIGHT : SWING_LEFT}. */
    static String randomSwing(RandomGenerator random) {
        return random.nextBoolean() ? "swing_right" : "swing_left";
    }

    /**
     * Mod {@code GummyBearMeleeGoal} against a Sweetish Fish: within 5 blocks the bear eats it when it is within 1,
     * fishes for it otherwise; farther away it only chases.
     */
    static String huntAction(double distance) {
        if (distance >= 5.0) {
            return null;
        }
        return distance <= 1.0 ? "eat" : "fish";
    }

    /** Mod: the bear jumps on tick 16 of the fishing animation. */
    static boolean fishJumps(int tickBefore, int tickNow) {
        return tickBefore < 16 && tickNow >= 16;
    }

    /** Mod: ticks 16 to 20 of the fishing animation pull the bear towards the fish. */
    static boolean fishLunges(int tick) {
        return tick > 15 && tick <= 20;
    }

    /** Mod: from tick 17 to 24 a fish within 1.5 blocks is caught. */
    static boolean fishCatches(int tick, double distance) {
        return tick > 16 && tick < 25 && distance < 1.5;
    }

    /** Mod {@code GummyBearEntity.isDigestiblePotion}: a potion with effects and none of them instant. */
    static boolean digestible(boolean potion, boolean instant, boolean hasEffects) {
        return potion && !instant && hasEffects;
    }

    /** Mod {@code GummyBearBackScratchGoal.canUse}: a grown, awake bear that is digesting. */
    static boolean scratches(boolean baby, boolean sleepy, boolean sleeping, boolean digesting) {
        return !baby && !sleepy && !sleeping && digesting;
    }

    /** Mod {@code hurt}: a hit of at least 1 may cost a leg (while it has both, or on its last breath) or an arm. */
    static int afterHit(int lost, double damage, boolean dying, RandomGenerator random) {
        if (damage < 1.0) {
            return lost;
        }
        boolean bothLegs = (lost & (LEFT_LEG | RIGHT_LEG)) == 0;
        if ((bothLegs || dying) && random.nextBoolean()) {
            return lost | (random.nextBoolean() ? LEFT_LEG : RIGHT_LEG);
        }
        if (random.nextInt(2) == 0) {
            return lost | (random.nextBoolean() ? LEFT_ARM : RIGHT_ARM);
        }
        return lost;
    }
}
