package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.modules.sift.SiftData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Java ports of the mod's custom feature types, keyed by mod feature type
 * ({@code the_sift:overgrown_willow_tree}). Types without a port are reported
 * once by the populator and simply not generated.
 */
public final class SiftFeatures {

    private final Map<String, SiftFeature> features;

    private SiftFeatures(Map<String, SiftFeature> features) {
        this.features = Collections.unmodifiableMap(features);
    }

    public static SiftFeatures create(SiftData data) {
        SiftTemplates templates = new SiftTemplates(data);
        Map<String, SiftFeature> features = new LinkedHashMap<>();
        OvergrownWillowTreeFeature willow = new OvergrownWillowTreeFeature(templates);
        features.put("the_sift:overgrown_willow_tree", willow);
        features.put("the_sift:bare_monolith", new SiftMonolithFeature(false));
        features.put("the_sift:lush_monolith", new SiftMonolithFeature(true));
        features.put("the_sift:dry_sculk_spikes", new SiftDrySpikeFeature());
        features.put("the_sift:cliff_arch", new SiftArchFeature());
        features.put("the_sift:soul_canyon", new SoulCanyonFeature());
        features.put("the_sift:covered_growth_cleanup", new CoveredGrowthCleanupFeature());
        features.put("the_sift:sift_flower_patch", new SiftFlowerPatchFeature());
        features.put("the_sift:siftslate_plant_patch", new SiftslatePlantPatchFeature());
        features.put("the_sift:sniffer_cave_overgrown", SnifferCaveFeature.overgrown());
        features.put("the_sift:sniffer_cave_wastes", SnifferCaveFeature.wastes());
        features.put("the_sift:sniffer_plant_patch", new SnifferPlantPatchFeature());
        features.put("the_sift:surface_sculk_region", new SiftSurfaceSculkRegionFeature());
        features.put("the_sift:abandoned_main_portal", new AbandonedMainPortalFeature(templates));
        features.put("the_sift:ichor_lake", new IchorLakeFeature(willow));
        features.put("the_sift:ichor_cave_spring", new SiftIchorCaveSpringFeature());
        features.put("the_sift:ichor_snow", new SiftIchorSnowFeature());
        features.put("the_sift:lava_floor", new SiftLavaFloorFeature());
        return new SiftFeatures(features);
    }

    /**
     * Grows an Overgrown Willow from a sapling whose position is {@code x, y, z} (the mod's
     * {@code OvergrownWillowTreeFeature.growFromSapling}); false when it does not fit there.
     */
    public boolean growWillow(WorldRegion region, Random random, int x, int y, int z) {
        return ((OvergrownWillowTreeFeature) features.get("the_sift:overgrown_willow_tree")).growFromSapling(region, random, x, y, z);
    }

    public Optional<SiftFeature> find(String type) {
        return Optional.ofNullable(features.get(type));
    }

    public Map<String, SiftFeature> all() {
        return features;
    }
}
