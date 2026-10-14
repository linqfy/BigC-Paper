package dev.linqfy.bigCasares.modules.cosmicniv;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * What one player is remembered to own: per memory slot, the item and the last time the player actually had it.
 * Durability is not part of an item, so it never makes the memory change.
 */
public record CosmicNivMemory(Map<CosmicNivMemorySlot, Remembered> slots) {
    private static final CosmicNivMemory EMPTY = new CosmicNivMemory(Map.of());

    public CosmicNivMemory {
        EnumMap<CosmicNivMemorySlot, Remembered> copy = new EnumMap<>(CosmicNivMemorySlot.class);
        Objects.requireNonNull(slots, "slots").forEach((slot, remembered) -> {
            if (slot != null && remembered != null) {
                copy.put(slot, remembered);
            }
        });
        slots = Collections.unmodifiableMap(copy);
    }

    public static CosmicNivMemory empty() {
        return EMPTY;
    }

    public Remembered get(CosmicNivMemorySlot slot) {
        return slots.get(slot);
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /**
     * {@code true} when both memories remember the same items, ignoring when they were last seen.
     */
    public boolean sameItems(CosmicNivMemory other) {
        if (other == null || !slots.keySet().equals(other.slots.keySet())) {
            return false;
        }
        for (Map.Entry<CosmicNivMemorySlot, Remembered> entry : slots.entrySet()) {
            if (!entry.getValue().item().equals(other.slots.get(entry.getKey()).item())) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param item           the remembered item (key and enchantments)
     * @param lastSeenMillis epoch millis of the last check in which the player had this item or something better
     */
    public record Remembered(CosmicNivItem item, long lastSeenMillis) {
        public Remembered {
            Objects.requireNonNull(item, "item");
        }
    }
}
