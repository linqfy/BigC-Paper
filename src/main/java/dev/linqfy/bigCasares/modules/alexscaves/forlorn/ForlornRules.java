package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import java.util.random.RandomGenerator;

/** Numbers and rules of the Forlorn Hollows mobs that need no world, taken from the mod. */
final class ForlornRules {
    private ForlornRules() {
    }

    /** Mod {@code Monster.isDarkEnoughToSpawn}: sky light above a random 0..31 stops it, then 0..7 for the total light. */
    static boolean darkEnough(int skyLight, int light, RandomGenerator random) {
        return skyLight <= random.nextInt(32) && light <= random.nextInt(8);
    }

    /** Mod {@code GloomothEntity.isValidLightLevel}: the same test as the monsters', in the same order. */
    static boolean gloomothLightLevel(int skyLight, int light, RandomGenerator random) {
        return darkEnough(skyLight, light, random);
    }

    /**
     * Mod {@code GloomothEntity.isWithinMothBallRange}: a moth ball scares it when the (integer) distance is below the
     * share of the range that its balls fill (one ball is a fifth).
     */
    static boolean mothBallRepels(int balls, int range, double distanceSquared) {
        int distance = (int) Math.sqrt(distanceSquared);
        return distance < balls / 5.0F * range;
    }

    /** Mod {@code GloomothEntity.isValidSacrifice}: within 3 blocks of the ground. */
    static boolean gloomothSacrificeValid(int distanceFromGround) {
        return distanceFromGround < 4;
    }

    /** Mod {@code VesperEntity.isValidSacrifice}: 2 blocks when it hangs, 8 when it flies. */
    static boolean vesperSacrificeValid(int distanceFromGround, boolean hanging) {
        return distanceFromGround < (hanging ? 3 : 9);
    }

    /** Mod {@code UnderzealoteEntity.isSurroundedByPrayers}: three of the pack pray. */
    static boolean surroundedByPrayers(int prayers) {
        return prayers >= 3;
    }

    /**
     * Mod {@code UnderzealotSacrificeGoal}: where a follower stands around the altar: on a circle of radius 2, evenly
     * spread by its place in the pack (the leader counts as 0).
     *
     * @return {x, z} offset from the altar
     */
    static double[] ringOffset(int packPosition, int packSize) {
        double fraction = packSize <= 1 ? 0.0 : packPosition / (packSize - 1.0);
        double angle = fraction * Math.PI * 2;
        return new double[] {2.0 * Math.cos(angle), 2.0 * Math.sin(angle)};
    }

    /**
     * Mod {@code VesperAttackGoal.orbitAroundPos}: where the vesper circles after a bite. The offset is relative to
     * where the bite landed ({@code x, y, z}); it spirals in from 8 blocks out and 7 up to 3 and 4.
     */
    static double[] vesperOrbit(int orbitTime, int maxOrbitTime) {
        float zoomIn = 1.0F - (float) orbitTime / maxOrbitTime;
        double circle = 3.0F + zoomIn * 5.0F;
        float angle = 3.0F * (float) Math.toRadians(orbitTime * 3.0F);
        return new double[] {circle * Math.sin(angle), 4.0F + zoomIn * 3.0F, circle * Math.cos(angle)};
    }

    /** Mod {@code CorrodentAttackGoal}: light above 7 (block light) scares it. */
    static boolean corrodentFearsLight(int blockLight) {
        return blockLight > 7;
    }

    /** Mod {@code CorrodentEntity.canDigBlock} apart from the tag: a solid block without fluid. */
    static boolean diggable(boolean occluding, boolean fluid, boolean undiggableTag) {
        return occluding && !fluid && !undiggableTag;
    }

    /** Fall damage of a corrodent: the vanilla damage minus 5 (mod {@code calculateFallDamage}). */
    static float corrodentFallDamage(float vanilla) {
        return Math.max(0.0F, vanilla - 5.0F);
    }

    /** Mod {@code MothDustItem.getPowerForTime}: how far a charge of {@code ticks} throws the dust (as a 0..1 share of 5 blocks). */
    static float mothDustPower(int ticks) {
        float f = ticks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(1.0F, f);
    }

    /**
     * Mod {@code WatcherEntity} possession, tick by tick. The strength starts at 1 and drains 0.05 a tick; after 140 ticks
     * it climbs 0.1 a tick (net +0.05) until it reaches 1 again, which ends the possession (160 ticks). Pressing jump
     * adds 0.07 once it is breakable, which cannot beat the drain, as in the mod.
     */
    static final class Possession {
        private float strength;
        private int timeout;
        private boolean started;

        /** @return true when the possession is over this tick */
        boolean step(double distanceToWatcher) {
            if (!started) {
                started = true;
                strength = 1.0F;
            }
            if (timeout++ > 140) {
                strength = Math.max(0.0F, strength + 0.1F);
            }
            if (distanceToWatcher < 1.0) {
                return true;
            }
            float before = strength;
            strength = Math.max(0.0F, before - 0.05F);
            return before >= 1.0F && timeout > 40;
        }

        float strength() {
            return strength;
        }

        int timeout() {
            return timeout;
        }

        /** Mod {@code onPossessionKeyPacket}: a key press that adds to the strength. */
        void press() {
            strength += 0.07F;
        }
    }
}
