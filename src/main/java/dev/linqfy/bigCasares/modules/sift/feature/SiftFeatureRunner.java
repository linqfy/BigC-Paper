package dev.linqfy.bigCasares.modules.sift.feature;

import org.bukkit.Server;
import org.bukkit.generator.LimitedRegion;

/** Factory exposing the package-private Bukkit adapter to the populator. */
public final class SiftFeatureRunner {

    private SiftFeatureRunner() {
    }

    public static WorldRegion region(Server server, LimitedRegion region, int minY, int maxY) {
        return new LimitedRegionAdapter(server, region, minY, maxY);
    }
}
