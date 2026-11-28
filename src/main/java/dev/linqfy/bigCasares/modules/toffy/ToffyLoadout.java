package dev.linqfy.bigCasares.modules.toffy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything an entity could use, as candidates. {@code armor}: every piece that could go in each slot (worn or
 * carried); the model keeps the best one per slot. {@code weapons} and {@code tools}: the items the model picks the
 * best weapon and the best tool from; items that are neither are ignored.
 */
public record ToffyLoadout(
    Map<ToffyArmorSlot, List<ToffyItem>> armor,
    List<ToffyItem> weapons,
    List<ToffyItem> tools
) {
    private static final ToffyLoadout EMPTY = new ToffyLoadout(Map.of(), List.of(), List.of());

    public ToffyLoadout {
        Objects.requireNonNull(armor, "armor");
        EnumMap<ToffyArmorSlot, List<ToffyItem>> copy = new EnumMap<>(ToffyArmorSlot.class);
        armor.forEach((slot, items) -> {
            if (slot != null && items != null) {
                List<ToffyItem> pieces = items.stream().filter(Objects::nonNull).toList();
                if (!pieces.isEmpty()) {
                    copy.put(slot, pieces);
                }
            }
        });
        armor = Collections.unmodifiableMap(copy);
        weapons = Objects.requireNonNull(weapons, "weapons").stream().filter(Objects::nonNull).toList();
        tools = Objects.requireNonNull(tools, "tools").stream().filter(Objects::nonNull).toList();
    }

    public static ToffyLoadout empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EnumMap<ToffyArmorSlot, List<ToffyItem>> armor = new EnumMap<>(ToffyArmorSlot.class);
        private final List<ToffyItem> weapons = new ArrayList<>();
        private final List<ToffyItem> tools = new ArrayList<>();

        private Builder() {
        }

        /**
         * Adds a piece that could go in {@code slot}. Several pieces per slot are allowed; the best one counts.
         */
        public Builder armor(ToffyArmorSlot slot, ToffyItem item) {
            armor.computeIfAbsent(Objects.requireNonNull(slot, "slot"), ignored -> new ArrayList<>())
                .add(Objects.requireNonNull(item, "item"));
            return this;
        }

        /**
         * Adds an item that can count as a weapon or as a tool.
         */
        public Builder held(ToffyItem item) {
            weapon(item);
            tools.add(item);
            return this;
        }

        /**
         * Adds an item that can count only as a weapon.
         */
        public Builder weapon(ToffyItem item) {
            weapons.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        /**
         * Adds an item that can count only as a tool.
         */
        public Builder tool(ToffyItem item) {
            tools.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        public ToffyLoadout build() {
            return new ToffyLoadout(armor, weapons, tools);
        }
    }
}
