package dev.linqfy.bigCasares.modules.piranhas;

/** Pure piranha rules, kept apart from Bukkit so they are unit-testable. */
final class PiranhaPolicy {

    /** Below this share of max health the swimmer "bleeds" and piranhas swim faster. */
    static final double FRENZY_HEALTH = 0.5;
    static final double FRENZY_SPEED_MULTIPLIER = 1.4;
    static final double BITE_DISTANCE = 1.4;

    private final PiranhaSettings settings;

    PiranhaPolicy(PiranhaSettings settings) {
        this.settings = settings;
    }

    boolean shouldSpawn(String biomeKey, int nearbyPiranhas, double roll) {
        return biomeKey != null
            && settings.biomes().contains(PiranhaSettings.normalizeBiome(biomeKey))
            && nearbyPiranhas < settings.maxNearby()
            && roll < settings.spawnChance();
    }

    /** School size for a roll uniform in [0, 1), between the configured min and max inclusive. */
    int schoolSize(double roll) {
        int span = settings.schoolMax() - settings.schoolMin() + 1;
        return settings.schoolMin() + Math.min(span - 1, (int) Math.floor(roll * span));
    }

    double huntSpeed(double health, double maxHealth) {
        boolean bleeding = maxHealth > 0.0 && health / maxHealth <= FRENZY_HEALTH;
        return bleeding ? settings.speed() * FRENZY_SPEED_MULTIPLIER : settings.speed();
    }

    boolean canBite(double distance, long nowTick, long lastBiteTick) {
        return distance <= BITE_DISTANCE && nowTick - lastBiteTick >= settings.biteCooldownTicks();
    }
}
