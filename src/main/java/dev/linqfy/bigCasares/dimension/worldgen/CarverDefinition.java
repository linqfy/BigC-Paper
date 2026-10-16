package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;

import java.util.Objects;

/** A configured carver ({@code worldgen/configured_carver}): vanilla {@code cave} or {@code canyon}. */
public record CarverDefinition(NamespacedId id, String type, JsonObject config) {

    public CarverDefinition {
        Objects.requireNonNull(id, "id");
        type = NamespacedId.parse(Objects.requireNonNull(type, "type")).asString();
        if (!type.equals("minecraft:cave") && !type.equals("minecraft:canyon") && !type.equals("minecraft:nether_cave")) {
            throw new IllegalArgumentException("Unsupported carver type: " + type);
        }
        config = Objects.requireNonNull(config, "config").deepCopy();
    }

    @Override
    public JsonObject config() {
        return config.deepCopy();
    }

    public JsonObject toJson() {
        return Json.object().put("type", type).put("config", config).build();
    }
}
