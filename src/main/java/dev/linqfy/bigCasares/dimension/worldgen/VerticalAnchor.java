package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonElement;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.json.JsonSerializable;

/** Vanilla vertical anchor: absolute Y, offset above the bottom, or offset below the top. */
public record VerticalAnchor(Kind kind, int value) implements JsonSerializable {

    public enum Kind {
        ABSOLUTE("absolute"), ABOVE_BOTTOM("above_bottom"), BELOW_TOP("below_top");

        private final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    public VerticalAnchor {
        java.util.Objects.requireNonNull(kind, "kind");
    }

    public static VerticalAnchor absolute(int y) {
        return new VerticalAnchor(Kind.ABSOLUTE, y);
    }

    public static VerticalAnchor aboveBottom(int offset) {
        return new VerticalAnchor(Kind.ABOVE_BOTTOM, offset);
    }

    public static VerticalAnchor belowTop(int offset) {
        return new VerticalAnchor(Kind.BELOW_TOP, offset);
    }

    /** Resolves the anchor for a dimension spanning {@code [minY, minY + height)}. */
    public int resolve(int minY, int height) {
        return switch (kind) {
            case ABSOLUTE -> value;
            case ABOVE_BOTTOM -> minY + value;
            case BELOW_TOP -> minY + height - 1 - value;
        };
    }

    @Override
    public JsonElement toJson() {
        return Json.object().put(kind.key, value).build();
    }
}
