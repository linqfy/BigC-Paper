package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.JsonSerializable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A vanilla block state as written in data files ({@code {"Name": ..., "Properties": {...}}}).
 *
 * <p>Properties are kept sorted so the emitted JSON and {@link #asString()} are stable.
 * Custom blocks are expressed with their backing vanilla state, which is why
 * worldgen can place them natively.</p>
 */
public record BlockStateSpec(NamespacedId block, SortedMap<String, String> properties) implements JsonSerializable {

    public BlockStateSpec {
        Objects.requireNonNull(block, "block");
        TreeMap<String, String> copy = new TreeMap<>();
        if (properties != null) {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                String key = Objects.requireNonNull(entry.getKey(), "property").trim();
                String value = Objects.requireNonNull(entry.getValue(), "value").trim();
                if (!key.matches("[a-z0-9_]+") || !value.matches("[a-z0-9_]+")) {
                    throw new IllegalArgumentException("Invalid block property " + key + "=" + value + " on " + block);
                }
                copy.put(key, value);
            }
        }
        properties = Collections.unmodifiableSortedMap(copy);
    }

    public static BlockStateSpec of(String block) {
        return new BlockStateSpec(NamespacedId.parse(block), null);
    }

    public static BlockStateSpec of(String block, Map<String, String> properties) {
        return new BlockStateSpec(NamespacedId.parse(block), new TreeMap<>(properties));
    }

    /** Parses command syntax: {@code minecraft:vine[east=true,up=false]}. */
    public static BlockStateSpec parse(String value) {
        String trimmed = Objects.requireNonNull(value, "value").trim();
        int bracket = trimmed.indexOf('[');
        if (bracket < 0) {
            return of(trimmed);
        }
        if (!trimmed.endsWith("]")) {
            throw new IllegalArgumentException("Invalid block state: " + value);
        }
        TreeMap<String, String> properties = new TreeMap<>();
        String body = trimmed.substring(bracket + 1, trimmed.length() - 1);
        if (!body.isBlank()) {
            for (String pair : body.split(",")) {
                int equals = pair.indexOf('=');
                if (equals <= 0) {
                    throw new IllegalArgumentException("Invalid block state property in " + value);
                }
                properties.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
        return new BlockStateSpec(NamespacedId.parse(trimmed.substring(0, bracket)), properties);
    }

    public BlockStateSpec with(String property, String value) {
        TreeMap<String, String> copy = new TreeMap<>(properties);
        copy.put(property, value);
        return new BlockStateSpec(block, copy);
    }

    /** Bukkit/command syntax, e.g. {@code minecraft:brown_mushroom_block[down=true,east=false]}. */
    public String asString() {
        if (properties.isEmpty()) {
            return block.asString();
        }
        StringBuilder builder = new StringBuilder(block.asString()).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.append(']').toString();
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("Name", block.asString());
        if (!properties.isEmpty()) {
            JsonObject props = new JsonObject();
            properties.forEach(props::addProperty);
            object.add("Properties", props);
        }
        return object;
    }

    @Override
    public String toString() {
        return asString();
    }
}
