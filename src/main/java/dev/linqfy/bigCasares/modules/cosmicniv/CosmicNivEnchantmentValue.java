package dev.linqfy.bigCasares.modules.cosmicniv;

public record CosmicNivEnchantmentValue(double pointsPerLevel, int maxLevel) {
    public static final int HIGHEST_LEVEL = 255;

    public CosmicNivEnchantmentValue {
        if (!Double.isFinite(pointsPerLevel)) {
            throw new IllegalArgumentException("points-per-level must be a finite number");
        }
        if (maxLevel < 1 || maxLevel > HIGHEST_LEVEL) {
            throw new IllegalArgumentException("max-level must be between 1 and " + HIGHEST_LEVEL);
        }
    }

    public double points(int level) {
        return pointsPerLevel * Math.clamp(level, 0, maxLevel);
    }
}
