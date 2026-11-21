package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

/**
 * Mod {@code DeepOneReaction}: how the Deep Ones treat a player, from the player's reputation with them. The distances
 * are the range the reaction goal keeps (a Deep One further out, or closer, drops it).
 */
public enum DeepOneReaction {
    STALKING(0.0, 80.0),
    AGGRESSIVE(0.0, 40.0),
    NEUTRAL(10.0, 25.0),
    HELPFUL(8.0, 30.0);

    /** Mod {@code ACWorldData.setDeepOneReputation} clamps to this range. */
    public static final int MIN_REPUTATION = -100;
    public static final int MAX_REPUTATION = 100;

    private final double minDistance;
    private final double maxDistance;

    DeepOneReaction(double minDistance, double maxDistance) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
    }

    public double minDistance() {
        return minDistance;
    }

    public double maxDistance() {
        return maxDistance;
    }

    /** Up to -10 aggressive, up to 10 stalking, up to 30 neutral and above that helpful. */
    public static DeepOneReaction fromReputation(int reputation) {
        if (reputation <= -10) {
            return AGGRESSIVE;
        }
        if (reputation <= 10) {
            return STALKING;
        }
        return reputation <= 30 ? NEUTRAL : HELPFUL;
    }

    public static int clamp(int reputation) {
        return Math.max(MIN_REPUTATION, Math.min(MAX_REPUTATION, reputation));
    }

    /**
     * Mod {@code validPlayer}: a stalker ignores a player more than 15 blocks above it, and (apart from aggressive and
     * helpful ones) a Deep One in the water ignores a player on dry land.
     */
    public boolean validPlayer(double deepOneY, boolean deepOneInWater, double playerY, boolean playerInWater) {
        if (this == STALKING && playerY > deepOneY + 15.0) {
            return false;
        }
        return this == AGGRESSIVE || this == HELPFUL || playerInWater || !deepOneInWater;
    }
}
