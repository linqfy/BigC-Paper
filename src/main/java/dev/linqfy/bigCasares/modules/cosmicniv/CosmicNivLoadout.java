package dev.linqfy.bigCasares.modules.cosmicniv;

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
public record CosmicNivLoadout(
    Map<CosmicNivArmorSlot, List<CosmicNivItem>> armor,
    List<CosmicNivItem> weapons,
    List<CosmicNivItem> tools
) {
    private static final CosmicNivLoadout EMPTY = new CosmicNivLoadout(Map.of(), List.of(), List.of());

    public CosmicNivLoadout {
        Objects.requireNonNull(armor, "armor");
        EnumMap<CosmicNivArmorSlot, List<CosmicNivItem>> copy = new EnumMap<>(CosmicNivArmorSlot.class);
        armor.forEach((slot, items) -> {
            if (slot != null && items != null) {
                List<CosmicNivItem> pieces = items.stream().filter(Objects::nonNull).toList();
                if (!pieces.isEmpty()) {
                    copy.put(slot, pieces);
                }
            }
        });
        armor = Collections.unmodifiableMap(copy);
        weapons = Objects.requireNonNull(weapons, "weapons").stream().filter(Objects::nonNull).toList();
        tools = Objects.requireNonNull(tools, "tools").stream().filter(Objects::nonNull).toList();
    }

    public static CosmicNivLoadout empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final EnumMap<CosmicNivArmorSlot, List<CosmicNivItem>> armor = new EnumMap<>(CosmicNivArmorSlot.class);
        private final List<CosmicNivItem> weapons = new ArrayList<>();
        private final List<CosmicNivItem> tools = new ArrayList<>();

        private Builder() {
        }

        /**
         * Adds a piece that could go in {@code slot}. Several pieces per slot are allowed; the best one counts.
         */
        public Builder armor(CosmicNivArmorSlot slot, CosmicNivItem item) {
            armor.computeIfAbsent(Objects.requireNonNull(slot, "slot"), ignored -> new ArrayList<>())
                .add(Objects.requireNonNull(item, "item"));
            return this;
        }

        /**
         * Adds an item that can count as a weapon or as a tool.
         */
        public Builder held(CosmicNivItem item) {
            weapon(item);
            tools.add(item);
            return this;
        }

        /**
         * Adds an item that can count only as a weapon.
         */
        public Builder weapon(CosmicNivItem item) {
            weapons.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        /**
         * Adds an item that can count only as a tool.
         */
        public Builder tool(CosmicNivItem item) {
            tools.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        public CosmicNivLoadout build() {
            return new CosmicNivLoadout(armor, weapons, tools);
        }
    }
}
