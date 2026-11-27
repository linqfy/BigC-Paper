package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

/** Numbers and rules of the Forlorn Hollows items that need no world, taken from the mod. */
final class ForlornItemRules {
    /** Mod config {@code darkness_cloak_charge_time}. */
    static final int CLOAK_CHARGE_TICKS = 1000;
    /** Mod config {@code darkness_cloak_fly_time}. */
    static final int CLOAK_FLIGHT_TICKS = 200;
    /** Mod {@code DarkenedAppleItem}: eating it while Darkness Incarnate runs adds this much. */
    static final int APPLE_EXTENSION_TICKS = 600;

    private ForlornItemRules() {
    }

    // --- Dreadbow --------------------------------------------------------------------------------------------

    /** Mod {@code DreadbowItem.getMaxLoadTime}: 5 with Relentless Darkness, else 40 less 8 per Dark Nock level. */
    static int maxLoadTime(boolean relentless, int darkNock) {
        return relentless ? 5 : 40 - 8 * darkNock;
    }

    /** Mod {@code getPowerForTime}: the vanilla bow curve over the bow's own load time. */
    static float power(int ticks, int maxLoadTime) {
        float f = (float) ticks / maxLoadTime;
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(1.0F, f);
    }

    /** Mod {@code releaseUsing}: a shot under this power fires nothing. */
    static boolean firesAt(float power) {
        return power > 0.1F;
    }

    /** Mod: 30 dark arrows (8 of anything else) at full power, scaled by the power and rounded up. */
    static int volleySize(boolean darkArrows, float power) {
        return (int) Math.ceil((darkArrows ? 30 : 8) * power);
    }

    /** Mod: how far the volley's target may be: 128 blocks at full power, then 42 if nothing was in reach. */
    static double volleyReach(float power, boolean fallback) {
        return (fallback ? 42.0 : 128.0) * power;
    }

    /** Mod: dark arrows hit for 3, or 2 with Precise Volley. */
    static float shadowArrowDamage(boolean precise) {
        return precise ? 2.0F : 3.0F;
    }

    /** Mod {@code DarkArrowEntity.onHitEntity}: a perfect shot doubles the hit. */
    static float darkArrowHit(float shadowDamage, boolean perfect) {
        return perfect ? shadowDamage * 2.0F : shadowDamage;
    }

    /**
     * Mod: the spread of a volley arrow in degrees. Precise Volley has none; otherwise 20 + up to 10 (5 + up to 10 for
     * ordinary arrows), and one arrow in four is almost straight ({@code straight} is the 0..1 roll that decides).
     */
    static float volleySpread(boolean precise, boolean darkArrows, float roll, float straightRoll, float straightSpread) {
        if (precise) {
            return 0.0F;
        }
        float spread = (darkArrows ? 20.0F : 5.0F) + roll * 10.0F;
        return straightRoll < 0.25F ? straightSpread : spread;
    }

    /** Mod: how many ticks a perfect shot window lasts: 4, plus 3 per Twilight Perfection level above the first. */
    static int perfectWindow(int twilightPerfection) {
        return 4 + (twilightPerfection - 1) * 3;
    }

    /** Release inside the window that opens when the bow is fully loaded. */
    static boolean perfectShot(int twilightPerfection, int drawTicks, int maxLoadTime) {
        return twilightPerfection > 0 && drawTicks >= maxLoadTime && drawTicks < maxLoadTime + perfectWindow(twilightPerfection);
    }

    /** Mod {@code onUseTick} (Relentless Darkness): a burst every third tick of use. */
    static boolean relentlessBurst(int usedTicks) {
        return usedTicks > 0 && usedTicks % 3 == 0;
    }

    /** Mod: a relentless burst fires 1 or 2 dark arrows, or one of anything else. */
    static int relentlessBurstSize(boolean darkArrows, int roll) {
        return darkArrows ? 1 + roll : 1;
    }

    // --- Totem of Possession ---------------------------------------------------------------------------------

    /** Mod: the pull of the totem ramps up over its first 15 ticks of use. */
    static float totemRamp(int usedTicks) {
        return usedTicks < 15 ? usedTicks / 15.0F : 1.0F;
    }

    /** Mod: how far the totem reaches for a destination: 32 blocks at full ramp. */
    static float totemReach(float ramp) {
        return 32.0F * ramp;
    }

    /** Mod: 1.25 plus 0.35 per Rapid Possession level. */
    static float totemSpeed(int rapidPossession) {
        return 1.25F + 0.35F * rapidPossession;
    }

    /** Mod: one point of durability every second tick of use. */
    static boolean totemWears(int usedTicks) {
        return usedTicks % 2 == 0;
    }

    /** Mod {@code isEntityLookingAt}: within {@code degree} (stretched by 10 % per block) of the line of sight. */
    static boolean looksAt(double dot, double distance, double degree) {
        double stretched = degree * (1.0 + distance * 0.1);
        return dot > 1.0 - stretched / distance;
    }

    /** Mod: Detonating Death blows up with 2 plus the whole width of the possessed mob. */
    static float totemExplosion(double width) {
        return 2.0F + (float) Math.floor(width);
    }

    // --- Desolate Dagger -------------------------------------------------------------------------------------

    /** Mod {@code DesolateDaggerItem.hurtEnemy}: 1 dagger, plus 1 per Double Stab level. */
    static int daggerCount(int doubleStab) {
        return 1 + doubleStab;
    }

    /** Mod: the blades orbit for 20 ticks (40 with Impending Stab) plus up to 9. */
    static int orbitTicks(int impendingStab, int roll) {
        return (impendingStab > 0 ? 40 : 20) + roll;
    }

    /** Mod {@code DesolateDaggerEntity}: the stab is 2, plus 2 per Impending Stab level. */
    static float stabDamage(int impendingStab) {
        return 2.0F + impendingStab * 2.0F;
    }

    /** Mod: Sated Blade adds 0.1 saturation per level to a player under 5. */
    static float satedSaturation(float saturation, int satedBlade) {
        return satedBlade > 0 && saturation < 5.0F ? saturation + satedBlade * 0.1F : saturation;
    }

    // --- Cloak of Darkness -----------------------------------------------------------------------------------

    /** Mod {@code getMeterProgress}. */
    static float meterProgress(int charge) {
        return (float) charge / CLOAK_CHARGE_TICKS;
    }

    /** Mod {@code onArmorTick}: it charges one tick at a time up to the full time. */
    static int charged(int charge, int ticks) {
        return Math.min(CLOAK_CHARGE_TICKS, charge + ticks);
    }
}
