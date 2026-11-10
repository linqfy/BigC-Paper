package dev.linqfy.bigCasares.modules.customcrossbow;

final class CrossbowEnchantmentRules {

    static final int MAX_HEARTS = 6;
    static final int TICKS_PER_HEART = 30;
    static final int SNIPER_COOLDOWN_TICKS = 20;
    static final int MAX_SHOTS = 5;
    static final int SHOT_INTERVAL_TICKS = 8;

    private CrossbowEnchantmentRules() { }

    static int sniperHearts(long scopedTicks, double health) {
        if (scopedTicks < 0 || !Double.isFinite(health) || health < 4.0) {
            return 0;
        }
        return (int) Math.min(MAX_HEARTS,
            Math.min(scopedTicks / TICKS_PER_HEART, Math.floor((health - 2.0) / 2.0)));
    }

    static double sniperDamage(int hearts) {
        return Math.clamp(hearts, 0, MAX_HEARTS) * 2.0;
    }

    static int magazineSize(int extraAmmunition) {
        return 1 + Math.clamp(extraAmmunition, 0, MAX_SHOTS - 1);
    }

    static boolean canApply(CrossbowEnchantment existing, CrossbowEnchantment incoming, boolean multishot) {
        return incoming != null && existing == null && !multishot;
    }

    static boolean blocksVanillaEnchantment(CrossbowEnchantment applied, String vanillaKey) {
        return applied != null && "multishot".equals(vanillaKey);
    }
}
