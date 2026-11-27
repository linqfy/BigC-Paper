package dev.linqfy.bigCasares.modules.alexscaves.magnetic;

/** Pure formulas of the Magnetic Caves items, with the values of the mod. */
final class MagneticFormulas {
    private MagneticFormulas() {
    }

    /** Resistor Shield: first slam deals {@code 6 + 3 * heavy_slam}. */
    static double shieldSlamDamage(int heavySlamLevel) {
        return 6.0 + 3.0 * Math.max(0, heavySlamLevel);
    }

    /** Galena Gauntlet: progress per tick {@code digSpeed / hardness / 10} ({@code / 8} with Ferrous Haste). */
    static double miningProgress(double digSpeed, double hardness, boolean haste) {
        return hardness <= 0 ? 1.0 : digSpeed / hardness / (haste ? 8.0 : 10.0);
    }

    /** Tool tier speed of the launched tool (only iron and netherite are magnetic without Crystallization). */
    static double digSpeed(String material) {
        if (material.startsWith("NETHERITE_")) {
            return 9.0;
        }
        if (material.startsWith("DIAMOND_")) {
            return 8.0;
        }
        if (material.startsWith("IRON_")) {
            return 6.0;
        }
        return 1.0;
    }

    /** Seeking Arrow: searches within {@code min(10, 3 + age / 4)} blocks. */
    static double seekRadius(int ticksLived) {
        return Math.min(10.0, 3.0 + ticksLived / 4.0);
    }
}
