package dev.linqfy.bigCasares.modules.sift.feature;

import org.bukkit.inventory.ItemStack;
import java.util.Optional;
import java.util.function.BiFunction;

import org.bukkit.Server;
import org.bukkit.generator.LimitedRegion;

/** Factory exposing the package-private Bukkit adapter to the populator. */
public final class SiftFeatureRunner {

    private SiftFeatureRunner() {
    }

    public static WorldRegion region(Server server, LimitedRegion region, int minY, int maxY, long seed,
                                     BiFunction<String, Integer, Optional<ItemStack>> items) {
        return new LimitedRegionAdapter(server, region, minY, maxY, seed, items);
    }
}
