package dev.linqfy.bigCasares.modules.toffy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * What one player is remembered to own: per memory slot, the item and the last time the player actually had it.
 * Durability is not part of an item, so it never makes the memory change.
 */
public record ToffyMemory(Map<ToffyMemorySlot, Remembered> slots) {
    private static final ToffyMemory EMPTY = new ToffyMemory(Map.of());

    public ToffyMemory {
        EnumMap<ToffyMemorySlot, Remembered> copy = new EnumMap<>(ToffyMemorySlot.class);
        Objects.requireNonNull(slots, "slots").forEach((slot, remembered) -> {
            if (slot != null && remembered != null) {
                copy.put(slot, remembered);
            }
        });
        slots = Collections.unmodifiableMap(copy);
    }

    public static ToffyMemory empty() {
        return EMPTY;
    }

    public Remembered get(ToffyMemorySlot slot) {
        return slots.get(slot);
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /**
     * {@code true} when both memories remember the same items, ignoring when they were last seen.
     */
    public boolean sameItems(ToffyMemory other) {
        if (other == null || !slots.keySet().equals(other.slots.keySet())) {
            return false;
        }
        for (Map.Entry<ToffyMemorySlot, Remembered> entry : slots.entrySet()) {
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
    public record Remembered(ToffyItem item, long lastSeenMillis) {
        public Remembered {
            Objects.requireNonNull(item, "item");
        }
    }
}
