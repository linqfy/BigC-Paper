package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;

import java.util.Set;

/** Pure rules of {@link PrimordialTaming}, kept apart so tests need no server classes. */
final class TamingRules {
    /** Module items the taming code reacts to, checked against the catalog by a test. */
    static final Set<String> TAMING_ITEMS = Set.of("ac_trilocaris_tail", "ac_cooked_trilocaris_tail", "ac_dinosaur_nugget",
        "ac_serene_salad", "ac_primitive_club");

    /** PDC keys (plugin namespace) shared by taming and breeding. */
    static final String OWNER_KEY = "alexscaves_owner";
    static final String SITTING_KEY = "alexscaves_sitting";
    static final String BABY_UNTIL_KEY = "alexscaves_baby_until";
    static final String BREED_COOLDOWN_KEY = "alexscaves_breed_cooldown";

    /** Mod: babies grow up after 24000 ticks; parents wait 6000 ticks before breeding again. */
    static final long BABY_TICKS = 24000L;
    static final long BREED_COOLDOWN_TICKS = 6000L;
    static final double BABY_SCALE = 0.5;

    /**
     * Deadline as read from storage: values saved against another clock (the old day-time based one) can lie far
     * beyond what any timer here may reach, so they are pulled back to {@code now + max}.
     */
    static long clampDeadline(long stored, long now, long max) {
        return Math.min(stored, now + max);
    }

    private TamingRules() {
    }

    /**
     * Breeding food of each dinosaur ({@code isFood} in the mod). {@code item} is the module item id or,
     * for vanilla items, the material name. Tremorsaurus and Vallumraptor only breed when tamed.
     */
    static boolean breedingFood(ModMobType type, String item, boolean tamed) {
        if (type == PrimordialMobs.SUBTERRANODON) {
            return item.equals("COD") || item.equals("SALMON");
        }
        if (type == PrimordialMobs.VALLUMRAPTOR) {
            return tamed && item.equals("ac_dinosaur_nugget");
        }
        if (type == PrimordialMobs.TREMORSAURUS) {
            return tamed && (item.equals("ac_dinosaur_chop") || item.equals("ac_cooked_dinosaur_chop"));
        }
        if (type.id().equals(Tremorzilla.ID)) {
            // Mod TremorzillaEntity.isFood.
            return item.equals("ac_nuclear_bomb");
        }
        if (type == PrimordialMobs.GROTTOCERATOPS || type == PrimordialMobs.RELICHEIRUS || type == Atlatitan.ATLATITAN) {
            return item.equals("ac_tree_star");
        }
        return false;
    }

    /** Whether a held item is a mob's breeding food (a tree star feeds the Atlatitan; it is never tamed). */
    static boolean isBreedingFood(ModMobType type, String item) {
        return breedingFood(type, item, false) || breedingFood(type, item, true);
    }

    /** Subterranodons lay 1-3 eggs ({@code MultipleDinosaurEggsBlock}); the others one. */
    static int eggsLaid(ModMobType type, int roll3) {
        return type == PrimordialMobs.SUBTERRANODON ? 1 + roll3 : 1;
    }

    /** Hatched babies of these are tamed to the nearest player ({@code tamesFromHatching}). */
    static boolean tamesFromHatching(ModMobType type) {
        return type == PrimordialMobs.SUBTERRANODON || type == PrimordialMobs.VALLUMRAPTOR
            || type == PrimordialMobs.TREMORSAURUS;
    }

    /**
     * Chance per second that an egg advances one of its 3 hatch stages. Mod: 1 in 20 per random
     * tick (1 in 10 on fern thatch); a block gets a random tick with chance 3/4096 per game tick
     * at the default {@code randomTickSpeed}, so about 22 minutes per stage.
     */
    static double hatchStageChancePerSecond(boolean onFernThatch) {
        double perRandomTick = onFernThatch ? 1.0 / 10.0 : 1.0 / 20.0;
        return 20.0 * (3.0 / 4096.0) * perRandomTick;
    }

    static final int HATCH_STAGES = 3;

    /** Mod {@code TremorsaurusEntity.onFeedMixture}: fails while (attempts <= 3 or coin) and attempts <= 8. */
    static boolean tremorsaurusTames(int attempts, boolean coin) {
        return !((attempts <= 3 || coin) && attempts <= 8);
    }

    static boolean rideable(ModMobType type) {
        return type == PrimordialMobs.TREMORSAURUS || type == PrimordialMobs.SUBTERRANODON;
    }

    /**
     * Mount velocity from the rider's input: forward/back and strafe follow the rider's yaw; flying
     * mounts also climb with the rider's pitch, rise with jump and sink with sprint (sneak dismounts).
     *
     * @return {vx, vy, vz}
     */
    static double[] mountVelocity(float yaw, float pitch, boolean forward, boolean back, boolean left, boolean right,
                                  boolean jump, boolean descend, double speed, boolean flying, double currentVy,
                                  boolean onGround) {
        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double strafeX = Math.cos(yawRad);
        double strafeZ = Math.sin(yawRad);
        double f = (forward ? 1 : 0) - (back ? 1 : 0);
        double s = (left ? 1 : 0) - (right ? 1 : 0);
        double length = Math.hypot(f, s);
        if (length > 1) {
            f /= length;
            s /= length;
        }
        double vx = (forwardX * f + strafeX * s) * speed;
        double vz = (forwardZ * f + strafeZ * s) * speed;
        double vy;
        if (flying) {
            vy = forward ? -Math.sin(Math.toRadians(pitch)) * speed : 0;
            if (jump) {
                vy += speed * 0.6;
            } else if (descend) {
                vy -= speed * 0.6;
            }
        } else {
            vy = jump && onGround ? 0.55 : currentVy;
        }
        return new double[] {vx, vy, vz};
    }
}
