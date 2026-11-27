package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import java.util.Set;

/**
 * Pure rules of the Tremorzilla (mod {@code TremorzillaEntity} and {@code TremorzillaAttackGoal}): animation lengths and
 * hit ticks, the atomic charge, the spikes that light up before the beam, the beam's duration and the healing.
 * All timings are in game ticks, as in the mod.
 */
final class TremorzillaRules {
    static final double MAX_HEALTH = 500.0;
    static final double ARMOR = 10.0;
    static final double ATTACK_DAMAGE = 30.0;
    static final double MOVEMENT_SPEED = 0.3;
    static final double FOLLOW_RANGE = 128.0;
    static final double WIDTH = 4.5;
    static final double HEIGHT = 11.0;
    static final double STEP_HEIGHT = 1.6;
    /** Mod {@code getScale} of a baby. */
    static final double BABY_SCALE = 0.15;
    static final int MAX_CHARGE = 1000;
    static final int EXPERIENCE = 70;
    /** Mod {@code hurtEntitiesAround} amounts. */
    static final float MELEE_DAMAGE = 10.0F;
    static final float BEAM_DAMAGE = 20.0F;
    static final float WALK_STOMP_DAMAGE = 2.0F;
    /** Irradiated given by the beam: 6000 ticks, amplifier 2. */
    static final int BEAM_IRRADIATED_TICKS = 6000;
    static final int BEAM_IRRADIATED_AMPLIFIER = 2;
    /** Explosions do 35 % (mod {@code hurt} with {@code IS_EXPLOSION}). */
    static final double EXPLOSION_FACTOR = 0.35;
    /** Mod {@code KaijuMob}: the big ones it does not hurt with stomps and that are not flung. */
    static final Set<String> KAIJU = Set.of("tremorzilla", "gum_worm", "hullbreaker", "atlatitan", "luxtructosaurus");
    /** Mod tag {@code resists_tremorsaurus_roar}. */
    static final Set<String> RESISTS_ROAR = Set.of("tremorsaurus", "luxtructosaurus", "tremorzilla");

    /** Mod {@code Animation.create(...)} lengths. */
    enum Anim {
        NONE(0), SPEAK(20), ROAR_1(60), ROAR_2(60), RIGHT_SCRATCH(35), LEFT_SCRATCH(35), RIGHT_TAIL(40), LEFT_TAIL(40),
        RIGHT_STOMP(35), LEFT_STOMP(35), BITE(25), PREPARE_BREATH(20), CHEW(35);

        final int duration;

        Anim(int duration) {
            this.duration = duration;
        }

        boolean tail() {
            return this == RIGHT_TAIL || this == LEFT_TAIL;
        }

        boolean stomp() {
            return this == RIGHT_STOMP || this == LEFT_STOMP;
        }

        boolean scratch() {
            return this == RIGHT_SCRATCH || this == LEFT_SCRATCH;
        }

        boolean roar() {
            return this == ROAR_1 || this == ROAR_2;
        }

        boolean melee() {
            return tail() || stomp() || scratch() || this == BITE;
        }
    }

    private TremorzillaRules() {
    }

    /** Whether tick {@code mark} was reached in the last {@code ticks} ticks (the mod checks {@code tick == mark}). */
    static boolean crossed(int previous, int now, int mark) {
        return previous < mark && now >= mark;
    }

    /** Mod {@code calculateSpikesDownAmount}: spikes light one after another, each with a fast finish. */
    static float spikesDownAmount(float progress, float spikeCount) {
        float scaled = progress * spikeCount;
        float remains = scaled % 1.0F;
        return (float) Math.floor(scaled) + (float) Math.pow(remains, 5.0);
    }

    /** Mod charge sound: a beep each time a spike starts lighting, up to the fifth; "complete" for the last ones. */
    static boolean chargeBeep(float previousSpikes, float spikes) {
        float before = spikesDownAmount(previousSpikes, 6.0F);
        float after = spikesDownAmount(spikes, 6.0F);
        return Math.floor(before) != Math.floor(after) && after <= 5.0F;
    }

    /** Mod: the spikes are lit enough to breathe at 95 %. */
    static boolean spikesReady(float spikes) {
        return spikes >= 0.95F;
    }

    /**
     * Mod: how fast the beam's time runs out. Ridden it runs 1 a tick; otherwise 8 when the target is farther than 100
     * blocks, 3 with no target, 1 near its target.
     */
    static int beamTimeStep(boolean ridden, boolean hasTarget, double targetDistance) {
        if (ridden) {
            return 1;
        }
        if (!hasTarget) {
            return 3;
        }
        return targetDistance > 100.0 ? 8 : 1;
    }

    /** Mod {@code healEveryTick}: below 20 % health 5 every 10 ticks, below 50 % 3 every 20, else 2 every 100. */
    static int healPeriod(double healthFraction) {
        return healthFraction <= 0.2 ? 10 : healthFraction <= 0.5 ? 20 : 100;
    }

    static double healAmount(double healthFraction) {
        return healthFraction <= 0.2 ? 5.0 : healthFraction <= 0.5 ? 3.0 : 2.0;
    }

    /** Health given back by Irradiated, which it eats at once: 12 per level. */
    static double irradiatedHeal(int amplifier) {
        return 12.0 * (1 + amplifier);
    }

    /**
     * Mod {@code TremorzillaAttackGoal}: the melee attack picked from a roll in [0, 1). Under water and as a baby it
     * neither stomps nor whips its tail (it bites instead).
     */
    static Anim meleeChoice(float decision, boolean left, boolean inWater, boolean baby) {
        if (decision < 0.25F) {
            return left ? Anim.LEFT_SCRATCH : Anim.RIGHT_SCRATCH;
        }
        if (decision < 0.5F && !inWater && !baby) {
            return left ? Anim.LEFT_STOMP : Anim.RIGHT_STOMP;
        }
        if (decision < 0.75F && !inWater && !baby) {
            return left ? Anim.LEFT_TAIL : Anim.RIGHT_TAIL;
        }
        return Anim.BITE;
    }

    /** Mod {@code onKeyPacket} type 3 (the rider attacks): scratch, stomp (not under water) or bite, a third each. */
    static Anim riderChoice(float decision, boolean left, boolean inWater) {
        if (decision < 0.33F) {
            return left ? Anim.LEFT_SCRATCH : Anim.RIGHT_SCRATCH;
        }
        if (decision < 0.66F && !inWater) {
            return left ? Anim.LEFT_STOMP : Anim.RIGHT_STOMP;
        }
        return Anim.BITE;
    }

    /** Mod taming by waste drum: from the 4th drum eaten, 1 in 3. */
    static boolean tames(int attempts, int roll3) {
        return attempts >= 4 && roll3 == 0;
    }

    /** Mod roar cooldown: 300 to 699 ticks. */
    static int roarCooldown(int roll400) {
        return 300 + roll400;
    }

    /** Mod: the beam lasts 100 to 249 ticks when it decides by itself (200 when the rider fires it). */
    static int maxBeamTime(int roll150) {
        return 100 + roll150;
    }

    /** Mod {@code tickBeamTargeting}: the swing of the beam narrows over the first 75 % of its time. */
    static float beamAccuracy(int beamTime, int maxBeamTime) {
        float time = (float) beamTime / maxBeamTime;
        return 1.0F - Math.min(0.75F, time) / 0.75F;
    }

    /** Chance that something happening 1 in {@code oneIn} a tick happens over {@code ticks} ticks. */
    static double chanceOver(int oneIn, int ticks) {
        return 1.0 - Math.pow(1.0 - 1.0 / oneIn, ticks);
    }
}
