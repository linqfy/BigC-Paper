package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import java.util.Set;

/**
 * Acid rules of the mod's {@code AcidBlock.entityInside}, as pure logic. Each tick in acid: a chance of
 * {@code 1 - hazmat/4} to take {@code (1 - hazmat/4)} damage, only 0.01 when wearing damageable armor
 * that is not Hazmat (the armor takes the hit: 5 % per piece and tick to lose 1 durability instead),
 * plus 10 for mobs weak to acid.
 */
public final class AcidRules {
    /** Mod tag {@code resists_acid}: vanilla and module mobs that acid does not hurt. */
    static final Set<String> RESISTS = Set.of("minecraft:creeper", "alexscaves:nucleeper", "alexscaves:radgill",
        "alexscaves:brainiac", "alexscaves:raycat", "alexscaves:tremorzilla");
    /** Mod tag {@code weak_to_acid}. */
    static final Set<String> WEAK = Set.of("minecraft:iron_golem");
    static final double ARMOR_WEAR_CHANCE_PER_TICK = 0.05;

    private AcidRules() {
    }

    /** Chance per tick of taking damage. */
    public static double damageChance(int hazmatPieces) {
        return 1.0 - Math.max(0, Math.min(4, hazmatPieces)) / 4.0;
    }

    /** Damage of one hit. */
    public static double damage(int hazmatPieces, boolean nonHazmatArmor, boolean weak) {
        return damageChance(hazmatPieces) * (nonHazmatArmor ? 0.01 : 1.0) + (weak ? 10.0 : 0.0);
    }

    /** Chance over {@code ticks} ticks of an event with {@code perTick} chance each tick. */
    public static double overTicks(double perTick, int ticks) {
        return 1.0 - Math.pow(1.0 - Math.max(0.0, Math.min(1.0, perTick)), ticks);
    }
}
