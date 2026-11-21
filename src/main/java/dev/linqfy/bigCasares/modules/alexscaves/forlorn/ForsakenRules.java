package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import java.util.Set;

/** Numbers and rules of the Forsaken that need no world, taken from the mod's {@code ForsakenEntity} and attack goals. */
final class ForsakenRules {
    /** Mod {@code ForsakenEntity.LIGHT_THRESHOLD}. */
    static final int LIGHT_THRESHOLD = 4;
    /** Mod attributes: 250 health, 10 damage, 64 blocks of follow range, 0.6 knockback resistance. */
    static final double MAX_HEALTH = 250.0;
    static final double ATTACK_DAMAGE = 10.0;
    static final double FOLLOW_RANGE = 64.0;
    static final double WALK_SPEED = 0.25;
    static final double RUN_SPEED = 0.45;
    static final double WALK_STEP = 0.6;
    static final double RUN_STEP = 1.1;
    /** Mod tag {@code forsaken_ignores}: the module's mobs (bats are vanilla and checked by type). */
    static final Set<String> IGNORED_MOBS = Set.of("vesper", "corrodent", "underzealot", "forsaken", "watcher");

    private ForsakenRules() {
    }

    /** Mod {@code Animation.create(...)} lengths of the Forsaken. */
    enum Anim {
        SUMMON(50), PREPARE_JUMP(15), BITE(15), LEFT_SLASH(33), RIGHT_SLASH(33), GROUND_SMASH(30), SONIC_ATTACK(35),
        SONIC_BLAST(45), LEFT_PICKUP(48), RIGHT_PICKUP(48);

        final int duration;

        Anim(int duration) {
            this.duration = duration;
        }

        boolean pickup() {
            return this == LEFT_PICKUP || this == RIGHT_PICKUP;
        }
    }

    /** Mod {@code getSonicDamageAgainst}: 45 for what the tag {@code weak_to_forsaken_sonic_attack} lists (the warden), else 4. */
    static float sonicDamage(boolean weak) {
        return weak ? 45.0F : 4.0F;
    }

    /** Mod sonic blast: {@code ceil(sonic damage * 0.65)}. */
    static float blastDamage(float sonicDamage) {
        return (float) Math.ceil(sonicDamage * 0.65F);
    }

    /** Ticks the sonic wave takes to reach something {@code distance} blocks away ({@code ceil(distance * 0.2)}). */
    static int sonicDelay(double distance) {
        return (int) Math.ceil(distance * 0.2F);
    }

    /**
     * Mod melee choice when the target is within reach: under 0.25 a pickup (only for targets narrower than 2), under
     * 0.5 a slash, under 0.75 the ground smash, else the bite. The side is a coin flip.
     */
    static Anim melee(float roll, double targetWidth, boolean left) {
        if (roll < 0.25F && targetWidth < 2.0) {
            return left ? Anim.LEFT_PICKUP : Anim.RIGHT_PICKUP;
        } else if (roll < 0.5F) {
            return left ? Anim.LEFT_SLASH : Anim.RIGHT_SLASH;
        } else if (roll < 0.75F) {
            return Anim.GROUND_SMASH;
        }
        return Anim.BITE;
    }

    /** Mod: the sonic attack is an attack (not the blast) unless the target is within 10 and a 60% roll succeeds. */
    static boolean sonicBlast(double distance, float roll) {
        return !(distance > 10.0) && !(roll < 0.4F);
    }

    /** Probability that a per-tick chance happens at least once in {@code ticks} ticks. */
    static double perSpan(double perTick, int ticks) {
        return 1.0 - Math.pow(1.0 - perTick, ticks);
    }

    /** How many of the ticks {@code from .. from + span - 1} are inside {@code lo .. hi} (the mod runs every tick). */
    static int ticksIn(int from, int span, int lo, int hi) {
        return Math.max(0, Math.min(hi, from + span - 1) - Math.max(lo, from) + 1);
    }

    /** How many multiples of {@code period} are among the ticks {@code from .. from + span - 1} that are inside {@code lo .. hi}. */
    static int multiplesIn(int from, int span, int period, int lo, int hi) {
        int first = Math.max(lo, from);
        int last = Math.min(hi, from + span - 1);
        if (last < first) {
            return 0;
        }
        return Math.floorDiv(last, period) - Math.floorDiv(first - 1, period);
    }

    /** Mod {@code getHandPos}: where the hand holds the picked up mob, relative to the Forsaken, in its own frame (x to the side, z ahead). */
    static double[] handPos(int tick, boolean left) {
        double side = left ? 1.0 : -1.0;
        if (tick <= 10) {
            return new double[] {0.0, 0.0, 4.0};
        } else if (tick <= 15) {
            return new double[] {0.0, 1.0, 3.7F};
        } else if (tick <= 25) {
            return new double[] {side * 2.75F, 4.65F, 1.9F};
        }
        return new double[] {side * 1.2F, 3.15F, 2.4F};
    }

    /** The darkness timer after one tick: 30 in the dark below half health, counting down in the light, 0 above half health. */
    static int darknessAfter(int darkness, boolean belowHalfHealth, int light, int ticks) {
        if (!belowHalfHealth) {
            return 0;
        }
        if (light <= LIGHT_THRESHOLD) {
            return 30;
        }
        return Math.max(0, darkness - ticks);
    }

    /**
     * Mod {@code Level.getSkyDarken}: 0 at noon up to 11 at midnight, less dimmed in rain (5/16 each for rain and thunder).
     *
     * @param dayTime ticks of the day (0 to 23999)
     */
    static int skyDarken(long dayTime, boolean raining, boolean thundering) {
        double fraction = dayTime / 24000.0 - 0.25;
        fraction -= Math.floor(fraction);
        double shaped = 0.5 - Math.cos(fraction * Math.PI) / 2.0;
        double timeOfDay = (fraction * 2.0 + shaped) / 3.0;
        double weather = 1.0 - (raining ? 5.0 / 16.0 : 0.0);
        weather *= 1.0 - (thundering ? 5.0 / 16.0 : 0.0);
        double light = 0.5 + 2.0 * Math.max(-0.25, Math.min(0.25, Math.cos(timeOfDay * Math.PI * 2.0)));
        return (int) ((1.0 - light * weather) * 11.0);
    }

    /** Mod {@code getMaxLocalRawBrightness}: the block light, or the sky light less the sky darkening if that is more. */
    static int localBrightness(int blockLight, int skyLight, int skyDarken) {
        return Math.max(blockLight, Math.max(0, skyLight - skyDarken));
    }

    /** Mod: golems (iron, snow, copper) hit it for half. */
    static float golemDamage(float damage) {
        return damage * 0.5F;
    }
}
