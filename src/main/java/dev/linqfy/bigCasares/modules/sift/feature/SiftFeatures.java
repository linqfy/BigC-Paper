package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.modules.sift.SiftData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
        features.put("the_sift:overgrown_willow_tree", new OvergrownWillowTreeFeature(templates));
        return new SiftFeatures(features);
    }

    public Optional<SiftFeature> find(String type) {
        return Optional.ofNullable(features.get(type));
    }

    public Map<String, SiftFeature> all() {
        return features;
    }
}
