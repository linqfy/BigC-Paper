package dev.linqfy.bigCasares.dimension.environment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.worldgen.EnvironmentAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One keyframed environment attribute inside a vanilla {@code timeline}.
 * The client interpolates between keyframes using the dimension's world clock,
 * so a visual cycle costs no packets after the initial clock sync.
 */
public record TimelineTrack(String attribute, String modifier, JsonElement ease, List<Keyframe> keyframes) {

    /**
     * Modifiers observed in Minecraft 26.2's own timelines. A missing modifier
     * overrides the underlying value; others are rejected to avoid emitting a
     * datapack the server cannot load.
     */
    private static final Set<String> MODIFIERS = Set.of("multiply", "maximum", "or", "and");

    public record Keyframe(int ticks, JsonElement value) {
        public Keyframe {
            if (ticks < 0) {
                throw new IllegalArgumentException("Keyframe ticks must be non-negative");
            }
            Objects.requireNonNull(value, "value");
            value = value.deepCopy();
        }
    }

    public TimelineTrack {
        EnvironmentAttributes.attributeId(Objects.requireNonNull(attribute, "attribute"));
        if (modifier != null && !MODIFIERS.contains(modifier)) {
            throw new IllegalArgumentException("Unknown timeline modifier: " + modifier);
        }
        ease = ease == null ? null : ease.deepCopy();
        keyframes = List.copyOf(keyframes);
        if (keyframes.isEmpty()) {
            throw new IllegalArgumentException("Track " + attribute + " needs keyframes");
        }
        for (int index = 1; index < keyframes.size(); index++) {
            if (keyframes.get(index).ticks() < keyframes.get(index - 1).ticks()) {
                throw new IllegalArgumentException("Keyframes of " + attribute + " must be ordered by ticks");
            }
        }
    }

    public static Builder of(String attribute) {
        return new Builder(attribute);
    }

    public JsonObject toJson() {
        JsonArray frames = new JsonArray();
        for (Keyframe keyframe : keyframes) {
            frames.add(Json.object().put("ticks", keyframe.ticks()).put("value", keyframe.value()).build());
        }
        Json.ObjectBuilder builder = Json.object();
        if (ease != null) {
            builder.put("ease", ease);
        }
        builder.put("keyframes", frames);
        if (modifier != null) {
            builder.put("modifier", modifier);
        }
        return builder.build();
    }

    public static final class Builder {
        private final String attribute;
        private String modifier;
        private JsonElement ease;
        private final List<Keyframe> keyframes = new ArrayList<>();

        private Builder(String attribute) {
            this.attribute = attribute;
        }

        public Builder modifier(String modifier) { this.modifier = modifier; return this; }

        /** Smooth cubic-bezier easing between keyframes. */
        public Builder smooth() {
            this.ease = Json.object().put("cubic_bezier", Json.array(0.4, 0.0, 0.6, 1.0)).build();
            return this;
        }

        public Builder constant() {
            this.ease = Json.of("constant");
            return this;
        }

        public Builder at(int ticks, Object value) {
            keyframes.add(new Keyframe(ticks, Json.of(value)));
            return this;
        }

        public TimelineTrack build() {
            return new TimelineTrack(attribute, modifier, ease, keyframes);
        }
    }
}
