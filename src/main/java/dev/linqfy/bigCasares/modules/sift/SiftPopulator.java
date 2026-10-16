package dev.linqfy.bigCasares.modules.sift;

import org.bukkit.inventory.ItemStack;
import java.util.Optional;
import java.util.function.BiFunction;

import dev.linqfy.bigCasares.modules.sift.feature.PlacementInterpreter;
import dev.linqfy.bigCasares.modules.sift.feature.SiftFeature;
import dev.linqfy.bigCasares.modules.sift.feature.SiftFeatureRunner;
import dev.linqfy.bigCasares.modules.sift.feature.SiftFeatures;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Runs the mod's Java-only features during Paper's native decoration step.
 *
 * <p>Paper calls world populators for every newly generated chunk with a
 * {@link LimitedRegion} that allows writes in the chunk plus a buffer, on
 * world-generation threads. Each placed feature gets its own random derived
 * from the decoration seed and its id, so results do not depend on thread
 * scheduling or on which other features are enabled.</p>
 */
final class SiftPopulator extends BlockPopulator {

    private record Entry(SiftWorldgen.CustomPlacement placement, SiftFeature feature, long salt, Set<String> biomes) {
    }

    private final List<Entry> entries = new ArrayList<>();

    private final BiFunction<String, Integer, Optional<ItemStack>> items;

    SiftPopulator(List<SiftWorldgen.CustomPlacement> placements, SiftFeatures features,
                  BiFunction<String, Integer, Optional<ItemStack>> items, Logger logger) {
        this.items = items;
        Set<String> missing = new LinkedHashSet<>();
        for (SiftWorldgen.CustomPlacement placement : placements) {
            var feature = features.find(placement.featureType());
            if (feature.isEmpty()) {
                missing.add(placement.featureType());
                continue;
            }
            Set<String> biomes = new LinkedHashSet<>();
            placement.biomes().forEach(id -> biomes.add(id.asString()));
            entries.add(new Entry(placement, feature.get(), placement.placedId().hashCode() * 0x9E3779B97F4A7C15L, biomes));
        }
        if (!missing.isEmpty()) {
            logger.info("[TheSift] Feature types not ported to the server yet (skipped): " + missing);
        }
    }

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limitedRegion) {
        var region = SiftFeatureRunner.region(org.bukkit.Bukkit.getServer(), limitedRegion,
            worldInfo.getMinHeight(), worldInfo.getMaxHeight(), worldInfo.getSeed(), items);
        long base = random.nextLong();
        for (Entry entry : entries) {
            Random featureRandom = new Random(base ^ entry.salt());
            for (PlacementInterpreter.Position position : PlacementInterpreter.positions(
                entry.placement().placement(), region, featureRandom, chunkX, chunkZ, entry.biomes())) {
                entry.feature().place(region, featureRandom, position.x(), position.y(), position.z());
            }
        }
    }
}
