package dev.linqfy.bigCasares.modules.sift.feature;

/**
 * Receives the position of every abandoned portal ruin the generator raises: the mod's
 * {@code SiftLandmarkTracker.record(ABANDONED_MAIN_PORTAL, ...)} call in {@code AbandonedMainPortalFeature},
 * which {@code /locate} reads. Called on world-generation threads, once per ruin, after its blocks are written, so
 * implementations must be thread-safe. Recording has no influence on generation.
 */
@FunctionalInterface
public interface AbandonedPortalLandmarks {

    AbandonedPortalLandmarks NONE = (x, y, z) -> {
    };

    /**
     * @param x the ruin's centre column
     * @param y the lowest terrain block of the ruin's template (the mod's {@code baseY})
     * @param z the ruin's centre column
     */
    void abandonedPortal(int x, int y, int z);
}
