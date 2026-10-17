package dev.linqfy.bigCasares.modules.sift.feature;

import org.bukkit.inventory.ItemStack;
import java.util.Optional;
import java.util.function.BiFunction;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.generator.LimitedRegion;

/** Factory exposing the package-private Bukkit adapter to the populator and to play-time growth. */
public final class SiftFeatureRunner {

    private SiftFeatureRunner() {
    }

    public static WorldRegion region(Server server, LimitedRegion region, int minY, int maxY, long seed,
                                     BiFunction<String, Integer, Optional<ItemStack>> items) {
        return new LimitedRegionAdapter(server, region, minY, maxY, seed, items);
    }

    /** A loaded world, for features grown at play time; only loaded chunks are read or written. */
    public static WorldRegion region(Server server, World world, BiFunction<String, Integer, Optional<ItemStack>> items) {
        return new LimitedRegionAdapter(server, world, (x, y, z) -> world.isChunkLoaded(x >> 4, z >> 4),
            world.getMinHeight(), world.getMaxHeight(), world.getSeed(), items);
    }
}
