package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;

import java.util.List;
import java.util.Objects;

/**
 * A placed feature ({@code worldgen/placed_feature}): a configured feature plus
 * placement modifiers. Placement is evaluated per chunk with a chunk-and-feature
 * derived random, which keeps decoration deterministic under parallel generation.
 */
public record PlacedFeatureDefinition(NamespacedId id, FeatureDefinition feature, List<JsonObject> placement) {

    public PlacedFeatureDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(feature, "feature");
        placement = placement.stream().map(JsonObject::deepCopy).toList();
    }

    @Override
    public List<JsonObject> placement() {
        return placement.stream().map(JsonObject::deepCopy).toList();
    }

    public JsonObject toJson() {
        JsonArray modifiers = new JsonArray();
        placement.forEach(modifier -> modifiers.add(modifier.deepCopy()));
        return Json.object().put("feature", feature.id()).put("placement", modifiers).build();
    }
}
