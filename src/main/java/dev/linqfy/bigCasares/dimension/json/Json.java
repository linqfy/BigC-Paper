package dev.linqfy.bigCasares.dimension.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.linqfy.bigCasares.dimension.NamespacedId;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Small helpers for building deterministic datapack JSON.
 *
 * <p>Objects keep insertion order; callers insert keys in a fixed order so the
 * generated datapack is byte-for-byte reproducible for identical inputs.</p>
 */
public final class Json {

    private static final Gson PRETTY = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();

    private Json() {
    }

    public static ObjectBuilder object() {
        return new ObjectBuilder();
    }

    public static JsonArray array(Object... values) {
        JsonArray array = new JsonArray();
        for (Object value : values) {
            array.add(of(value));
        }
        return array;
    }

    public static JsonArray arrayOf(Collection<?> values) {
        JsonArray array = new JsonArray();
        for (Object value : values) {
            array.add(of(value));
        }
        return array;
    }

    /** Converts supported Java values into JSON; unsupported types fail fast. */
    public static JsonElement of(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof JsonElement element) {
            return element.deepCopy();
        }
        if (value instanceof JsonSerializable serializable) {
            return serializable.toJson();
        }
        if (value instanceof NamespacedId id) {
            return new JsonPrimitive(id.asString());
        }
        if (value instanceof String string) {
            return new JsonPrimitive(string);
        }
        if (value instanceof Boolean bool) {
            return new JsonPrimitive(bool);
        }
        if (value instanceof Number number) {
            return number(number);
        }
        if (value instanceof Enum<?> constant) {
            return new JsonPrimitive(constant.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (value instanceof Collection<?> collection) {
            return arrayOf(collection);
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                object.add(String.valueOf(entry.getKey()), of(entry.getValue()));
            }
            return object;
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    private static JsonPrimitive number(Number number) {
        if (number instanceof Double doubleValue) {
            requireFinite(doubleValue);
        } else if (number instanceof Float floatValue) {
            requireFinite(floatValue);
        }
        return new JsonPrimitive(number);
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("JSON numbers must be finite: " + value);
        }
    }

    /** Serializes with two-space indentation and a trailing newline. */
    public static String write(JsonElement element) {
        return PRETTY.toJson(Objects.requireNonNull(element, "element")) + "\n";
    }

    public static byte[] bytes(JsonElement element) {
        return write(element).getBytes(StandardCharsets.UTF_8);
    }

    /** Formats a 24-bit RGB colour the way vanilla data files do ({@code #rrggbb}). */
    public static String rgb(int rgb) {
        if (rgb < 0 || rgb > 0xFFFFFF) {
            throw new IllegalArgumentException("RGB colour out of range: " + rgb);
        }
        return String.format(java.util.Locale.ROOT, "#%06x", rgb);
    }

    /** Formats a 32-bit ARGB colour ({@code #aarrggbb}). */
    public static String argb(long argb) {
        if (argb < 0 || argb > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("ARGB colour out of range: " + argb);
        }
        return String.format(java.util.Locale.ROOT, "#%08x", argb);
    }

    public static final class ObjectBuilder {
        private final JsonObject object = new JsonObject();

        private ObjectBuilder() {
        }

        public ObjectBuilder put(String key, Object value) {
            Objects.requireNonNull(key, "key");
            if (object.has(key)) {
                throw new IllegalArgumentException("Duplicate JSON key: " + key);
            }
            object.add(key, of(value));
            return this;
        }

        /** Adds the key only when the value is non-null. */
        public ObjectBuilder putIfPresent(String key, Object value) {
            return value == null ? this : put(key, value);
        }

        public JsonObject build() {
            return object.deepCopy();
        }
    }
}
