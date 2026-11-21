package dev.linqfy.bigCasares.modules.alexscaves;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;

import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Everything the biome packages contribute; built once per enable. */
final class AlexsCavesContent {
    private final ModMobCatalog mobs = new ModMobCatalog();
    private final List<ChunkFeature> features = new ArrayList<>();
    private final Set<String> featureIds = new HashSet<>();
    private final List<Function<ModMobService, Listener>> listeners = new ArrayList<>();

    ModMobCatalog mobs() {
        return mobs;
    }

    void addFeature(ChunkFeature feature) {
        if (!featureIds.add(feature.id())) {
            throw new IllegalStateException("Feature de Alex's Caves duplicada: " + feature.id());
        }
        features.add(feature);
    }

    List<ChunkFeature> features() {
        return List.copyOf(features);
    }

    void addListener(Function<ModMobService, Listener> listener) {
        listeners.add(listener);
    }

    List<Function<ModMobService, Listener>> listeners() {
        return List.copyOf(listeners);
    }
}
