package dev.linqfy.bigCasares.modules.alexscaves.core.effect;

/**
 * Irradiated rules from {@code IrradiatedEffect} / {@code HazmatArmorItem}:
 * every level blocks healing; from amplifier 1 the effect "procs" every
 * {@code 200 / amplifier} ticks for {@code 1 - 0.25 * hazmatPieces} damage with
 * probability {@code damage + 0.1}. Unprotected players also gain exhaustion.
 */
public final class RadiationFormula {
    public static final int MAX_HAZMAT_PIECES = 4;
    public static final float UNPROTECTED_EXHAUSTION = 0.4F;
    /** Amplifier at which the mod switches to the blue (gamma) visuals. */
    public static final int BLUE_LEVEL = 4;
    /** Duration given by radioactive items carried in the inventory. */
    public static final int CARRIED_ITEM_DURATION_TICKS = 1800;

    private RadiationFormula() {
    }

    /** True when the effect does something on this tick (vanilla duration-effect tick contract). */
    public static boolean procsOnTick(int elapsedTicks, int amplifier) {
        if (amplifier <= 0) {
            return false;
        }
        int interval = 200 / amplifier;
        if (interval > 1) {
            return elapsedTicks % interval == interval / 2;
        }
        return true;
    }

    public static double damage(int hazmatPieces) {
        return 1.0 - 0.25 * clampPieces(hazmatPieces);
    }

    public static double damageChance(int hazmatPieces) {
        return Math.min(1.0, damage(hazmatPieces) + 0.1);
    }

    public static boolean causesExhaustion(int hazmatPieces) {
        return clampPieces(hazmatPieces) == 0;
    }

    /**
     * Chance per tick of getting irradiated by radioactive stacks in the
     * inventory ({@code RadioactiveItem}): {@code count * chance * (1 - hazmat / 4)}.
     */
    public static double carriedItemChance(int stackCount, double itemChance, int hazmatPieces) {
        double chance = stackCount * itemChance * (1.0 - clampPieces(hazmatPieces) / 4.0);
        return Math.max(0.0, Math.min(1.0, chance));
    }

    /** Damage multiplier for acid, which also uses the 25 % per Hazmat piece rule. */
    public static double acidMultiplier(int hazmatPieces) {
        return damage(hazmatPieces);
    }

    private static int clampPieces(int pieces) {
        return Math.max(0, Math.min(MAX_HAZMAT_PIECES, pieces));
    }
}
