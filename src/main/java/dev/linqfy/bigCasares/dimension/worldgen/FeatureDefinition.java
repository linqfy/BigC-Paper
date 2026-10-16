package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;

import java.util.List;
import java.util.Objects;

/**
 * A configured feature ({@code worldgen/configured_feature}).
 *
 * <p>The config is vanilla JSON for the given feature {@code type}; helpers in
 * {@link Features} build the common ones. {@code dependencies} lists placed
 * features referenced by id from the config (e.g. selector features) so the
 * compiler emits them too.</p>
 */
public record FeatureDefinition(
    NamespacedId id,
    String type,
    JsonObject config,
    List<PlacedFeatureDefinition> dependencies
) {

    public FeatureDefinition {
        Objects.requireNonNull(id, "id");
        type = NamespacedId.parse(Objects.requireNonNull(type, "type")).asString();
        config = Objects.requireNonNull(config, "config").deepCopy();
        dependencies = List.copyOf(dependencies);
    }

    public FeatureDefinition(NamespacedId id, String type, JsonObject config) {
        this(id, type, config, List.of());
    }

    @Override
    public JsonObject config() {
        return config.deepCopy();
    }

    public JsonObject toJson() {
        return Json.object().put("type", type).put("config", config).build();
    }

    /** Places this feature with the given modifiers under a new id. */
    public PlacedFeatureDefinition placed(NamespacedId placedId, List<JsonObject> placement) {
        return new PlacedFeatureDefinition(placedId, this, placement);
    }
}
