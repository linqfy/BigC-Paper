package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure rules of the Luxtructosaurus and the Atlatitan (mod {@code LuxtructosaurusEntity}, {@code LuxtructosaurusMeleeGoal},
 * {@code SauropodBaseEntity}, {@code AtlatitanMeleeGoal} and {@code TephraExplosion}), apart from the entities so tests
 * need no server. The mod runs these every tick; the module's behaviours run every 2 ticks, so timers take a step.
 */
final class SauropodRules {
    /** Mod {@code Animation.create(...)} lengths of the sauropod base. */
    enum Anim {
        NONE(0), SPEAK(15), ROAR(60), EPIC_DEATH(120), SUMMON(120), STOMP(50), SPEW_FLAMES(80), JUMP(45), LEFT_KICK(20),
        RIGHT_KICK(20), LEFT_WHIP(40), RIGHT_WHIP(40), EAT_LEAVES(100);

        final int duration;

        Anim(int duration) {
            this.duration = duration;
        }

        boolean whip() {
            return this == LEFT_WHIP || this == RIGHT_WHIP;
        }

        boolean kick() {
            return this == LEFT_KICK || this == RIGHT_KICK;
        }
    }

    /** Mod attributes of the Luxtructosaurus ({@code createAttributes}). */
    static final double LUX_HEALTH = 600.0;
    static final double LUX_ARMOR = 20.0;
    static final double LUX_ATTACK = 12.0;
    static final double LUX_SPEED = 0.325;
    static final double LUX_FOLLOW_RANGE = 256.0;
    /** Mod {@code createAttributes} of the Atlatitan. */
    static final double ATLATITAN_HEALTH = 400.0;
    static final double ATLATITAN_ATTACK = 8.0;
    static final double ATLATITAN_SPEED = 0.325;
    /** Mod {@code getProjectileDamageReduction}: projectiles keep 55 % (Luxtructosaurus) or 75 % (other sauropods). */
    static final float LUX_PROJECTILE = 0.55F;
    static final float SAUROPOD_PROJECTILE = 0.75F;
    /** Mod: ticks before a volcanic core can summon again. */
    static final int BOSS_SPAWN_COOLDOWN = 24000;

    private SauropodRules() {
    }

    /** Whether an animation tick is crossed in a step: the mod's {@code tick == at} evaluated every tick. */
    static boolean crossed(int previous, int now, int at) {
        return previous < at && now >= at;
    }

    /** Chance that something with a per-tick chance of {@code 1 / n} happens over {@code ticks} ticks. */
    static double perTick(int n, int ticks) {
        return n <= 1 ? 1.0 : 1.0 - Math.pow(1.0 - 1.0 / n, ticks);
    }

    /**
     * Mod {@code LuxtructosaurusEntity.hurt}: the damage the boss takes. Reduced to 35 % in the 10 ticks after a hit,
     * 65 % from dinosaurs (not a Tremorzilla), 50 % from golems, 25 % from wardens, then the projectile factor.
     */
    static float luxDamage(float amount, boolean recentlyHit, boolean dinosaur, boolean golem, boolean warden,
                           boolean projectile) {
        float damage = amount;
        if (recentlyHit) {
            damage *= 0.35F;
        }
        if (dinosaur) {
            damage = (float) (damage * 0.65);
        }
        if (golem) {
            damage *= 0.5F;
        }
        if (warden) {
            damage *= 0.25F;
        }
        if (projectile) {
            damage *= LUX_PROJECTILE;
        }
        return damage;
    }

    /** Mod {@code m_8107_}: the enrage wears off when {@code enragedFor} is over and health is at least a quarter. */
    static boolean calmsDown(int enragedFor, double health, double max) {
        return enragedFor <= 0 && health >= Math.ceil(max * 0.25F);
    }

    enum Attack { STOMP, KICK, WHIP }

    /**
     * Mod {@code LuxtructosaurusMeleeGoal}: near the target (within width sum + 4) it stomps (a third, on the ground),
     * kicks (another third, when within width sum + 1) or whips its tail.
     */
    static Attack luxAttack(float random, double distance, double attackDistance, boolean onGround) {
        if (random < 0.33 && onGround) {
            return Attack.STOMP;
        }
        if (random < 0.66 && distance < attackDistance + 1.0) {
            return Attack.KICK;
        }
        return Attack.WHIP;
    }

    /** Mod {@code AtlatitanMeleeGoal}: half the time a kick (when within width sum + 1), otherwise a tail whip. */
    static Attack atlatitanAttack(float random, double distance, double attackDistance) {
        return random < 0.5F && distance < attackDistance + 1.0 ? Attack.KICK : Attack.WHIP;
    }

    /** Mod: {@code nextInt(roarRandomChance)}, 90 when the target is far, 150 near, plus 200 while enraged. */
    static int roarChance(boolean far, boolean enraged) {
        int chance = far ? 90 : 150;
        return enraged ? chance + 200 : chance;
    }

    /** Mod: the roar cooldown, 100 to 299 ticks. */
    static int roarCooldown(Random random) {
        return 100 + random.nextInt(200);
    }

    /** Mod: how long a roar keeps it enraged, 500 to 699 ticks. */
    static int enragedFor(Random random) {
        return 500 + random.nextInt(200);
    }

    /** Mod: flames cooldown 200 to 499 ticks, only while enraged and far. */
    static int flamesCooldown(Random random) {
        return 200 + random.nextInt(300);
    }

    /** Mod {@code tickStompAttack}: the fissure ring width of the stomp tick whose remaining ticks are {@code post}. */
    static int fissureWidth(int post) {
        return 1 + Math.max(0, (int) Math.ceil((20 - post) * 2.3F));
    }

    /** Mod: ticks the stomp keeps working after its hit; fissures run 15, plain crushing 50. */
    static int postStopTicks(boolean fissures) {
        return fissures ? 15 : 50;
    }

    /** Mod {@code LuxtructosaurusMeleeGoal}: the next jump check in 20 to 59 ticks. */
    static int navigationCheck(Random random) {
        return 20 + random.nextInt(40);
    }

    /** Mod: cooldown after a jump, 100 to 299 ticks. */
    static int jumpCooldown(Random random) {
        return 100 + random.nextInt(200);
    }

    /**
     * Mod jump velocity ({@code m_8119_} for animation ticks 15 to 25): a fraction of the distance to the target along
     * the ground and a lift that fades over the first ten ticks.
     *
     * @return {vx, vy, vz}
     */
    static double[] jumpVelocity(double dx, double dz, double vx, double vz, int animationTick) {
        double hx = dx;
        double hz = dz;
        double length = Math.sqrt(hx * hx + hz * hz);
        if (length > 250.0) {
            double speed = Math.sqrt(vx * vx + vz * vz);
            hx = speed < 1.0E-6 ? 0 : vx / speed * 250.0;
            hz = speed < 1.0E-6 ? 0 : vz / speed * 250.0;
        }
        if (hx * hx + hz * hz > 1.0E-7) {
            hx = hx * 0.155F + vx * 0.2;
            hz = hz * 0.155F + vz * 0.2;
        }
        double horizontal = Math.sqrt(hx * hx + hz * hz);
        double lift = (10 - Math.min(animationTick - 10, 10)) * 0.2F + horizontal * 0.3;
        return new double[] {hx, lift, hz};
    }

    /**
     * Mod {@code burnWithBreath}: the (distance, width) steps of the flame along its direction, a block wider each step.
     *
     * @return pairs {distance, width}
     */
    static double[][] breathSteps(float maxDistance) {
        List<double[]> steps = new ArrayList<>();
        float burnWidth = 1.0F;
        for (float distance = 0.0F; distance < maxDistance; distance += burnWidth) {
            burnWidth++;
            steps.add(new double[] {distance, burnWidth});
        }
        return steps.toArray(new double[0][]);
    }

    /** Mod {@code TephraExplosion.explode}: damage to an entity at fraction {@code d12} of the reach, seen {@code seen}. */
    static int tephraDamage(double d12, double seen, float radius) {
        double d10 = (1.0 - d12) * seen;
        float f2 = radius * 2.0F;
        return (int) ((d10 * d10 + d10) / 2.0 * 9.0 * f2 + 1.0);
    }

    /** Mod {@code TephraEntity.explode}: radius of the explosion of a tephra of a maximum scale. */
    static float tephraRadius(float maxScale) {
        return 1.0F + maxScale;
    }

    /** Mod {@code TephraEntity.tick}: the scale eases to its maximum by a tenth of the gap. */
    static float tephraScale(float scale, float max) {
        return scale + (max - scale) * 0.1F;
    }

    /** Mod {@code VolcanicCoreBlockEntity}: next eruption in 120 to 239 ticks. */
    static int eruptionCooldown(Random random) {
        return 120 + random.nextInt(120);
    }

    /** Mod {@code AtlatitanEntity}: heals 2 every 100 ticks. */
    static boolean atlatitanHeals(long age, double health, double max) {
        return age % 100 == 0 && health < max;
    }

    /**
     * Mod {@code TephraExplosion.explode}: the lattice of 16x16x16 surface rays (flattened to half in y) that each
     * lose {@code (resistance + 0.3) * 0.3} plus 0.225 per step from a power of {@code radius * (0.7 .. 1.3)}, and the
     * positions they cross. {@code probe} says whether a position stops the ray (unmovable or outside the world) and the
     * explosion resistance of what is there.
     */
    interface Probe {
        /** NaN stops the ray (unloaded, or unmovable); -0.3 for a fluid (it costs nothing); else the explosion resistance. */
        float resistance(int x, int y, int z);
    }

    static java.util.Set<Long> tephraBlocks(double ox, double oy, double oz, float radius, Random random, Probe probe) {
        java.util.Set<Long> blown = new java.util.HashSet<>();
        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                for (int l = 0; l < 16; l++) {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                        double d0 = j / 15.0F * 2.0F - 1.0F;
                        double d1 = (k / 15.0F * 2.0F - 1.0F) * 0.5;
                        double d2 = l / 15.0F * 2.0F - 1.0F;
                        double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                        d0 /= d3;
                        d1 /= d3;
                        d2 /= d3;
                        float power = radius * (0.7F + random.nextFloat() * 0.6F);
                        double x = ox;
                        double y = oy;
                        double z = oz;
                        while (power > 0.0F) {
                            int bx = (int) Math.floor(x);
                            int by = (int) Math.floor(y);
                            int bz = (int) Math.floor(z);
                            float resistance = probe.resistance(bx, by, bz);
                            if (Float.isNaN(resistance)) {
                                break;
                            }
                            power -= (resistance + 0.3F) * 0.3F;
                            blown.add(pack(bx, by, bz));
                            x += d0 * 0.3F;
                            y += d1 * 0.3F;
                            z += d2 * 0.3F;
                            power -= 0.22500001F;
                        }
                    }
                }
            }
        }
        return blown;
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    static int unpackZ(long packed) {
        return (int) ((packed << 26) >> 38);
    }

    static int unpackY(long packed) {
        return (int) ((packed << 52) >> 52);
    }

    // --- Atlatitan: young, trees, mount ---

    /** Mod {@code AtlatitanEntity.getScale}: a young Atlatitan is 15 % of the adult. */
    static final double ATLATITAN_BABY_SCALE = 0.15;
    /** Mod {@code onFeedMixture}: a serene salad makes it rideable for 12000 ticks. */
    static final int RIDEABLE_TICKS = 12000;
    /** Mod {@code tickRidden}/{@code getRiddenInput}: stomp meter gain a tick while ridden, and the stomp animation tick that lands. */
    static final float METER_STEP = 0.0025F;
    static final int STOMP_HIT_TICK = 30;
    /** Mod {@code AtlatitanNibbleTreesGoal(this, 30)}: search range; vertical range 16. */
    static final int NIBBLE_RANGE = 30;
    static final int NIBBLE_HEIGHT = 16;
    /** Mod {@code TemptGoal}: players within this many blocks holding a tree star draw it. */
    static final double TEMPT_RANGE = 10.0;

    /** Mod {@code highEnough}: how far above the ground a nibbled block is. Adults want 4 to 19, the young 2 or less. */
    static boolean nibbleHighEnough(int height, boolean baby) {
        return baby ? height <= 2 : height > 3 && height < 20;
    }

    /** Mod {@code MoveToBlockGoal.nextStartTick}: the next search for a tree in 200 to 399 ticks. */
    static int nibbleCooldown(Random random) {
        return 200 + random.nextInt(200);
    }

    /** Mod {@code getStandAtTreePos}: how far back from the tree, along the side it faces, the body stands. */
    static int standDistance(double scale) {
        return (int) Math.floor(13.0F * scale);
    }

    /** Nearest of the four directions the mod's {@code Direction.fromYRot} gives, as a yaw (0 south, 90 west, 180 north, 270 east). */
    static float cardinalYaw(float yaw) {
        float wrapped = yaw - 360.0F * (float) Math.floor(yaw / 360.0F);
        return (Math.round(wrapped / 90.0F) % 4) * 90.0F;
    }

    /** Mod {@code onKeyPacket}: the stomp needs a full meter and no animation running. */
    static boolean canStomp(float meter, boolean animating) {
        return meter >= 1.0F && !animating;
    }

    /** Mod: the meter fills 0.0025 a tick while it is ridden and empties when it is not. */
    static float meterAfter(float meter, boolean ridden, int ticks) {
        return ridden ? Math.min(meter + METER_STEP * ticks, 1.0F) : 0.0F;
    }

    /** Mod {@code getRiddenInput}: strafe 0.35, forward 0.8 and half of that backwards. */
    static double[] riddenInput(double forward, double strafe) {
        double f = forward < 0 ? 0.5 : 1.0;
        return new double[] {strafe * 0.35, forward * 0.8 * f};
    }
}
