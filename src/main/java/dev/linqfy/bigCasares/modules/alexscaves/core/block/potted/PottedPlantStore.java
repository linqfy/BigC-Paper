package dev.linqfy.bigCasares.modules.alexscaves.core.block.potted;

import java.util.Map;
import java.util.TreeMap;

/**
 * Which module plant sits in each vanilla flower pot of a chunk. Saved as one string in the chunk's persistent data:
 * {@code packed:plant;packed:plant}, where {@code packed} is {@link
 * dev.linqfy.bigCasares.modules.alexscaves.core.block.remap.WaterloggedIndex#pack the chunk-local position}. Pure
 * functions over the saved string so they can be tested without a server; unreadable entries are dropped.
 */
public final class PottedPlantStore {
    private PottedPlantStore() {
    }

    public static Map<Integer, String> decode(String saved) {
        Map<Integer, String> out = new TreeMap<>();
        if (saved == null || saved.isBlank()) {
            return out;
        }
        for (String entry : saved.split(";")) {
            int colon = entry.indexOf(':');
            if (colon <= 0 || colon == entry.length() - 1) {
                continue;
            }
            String plant = entry.substring(colon + 1);
            if (!plant.matches("[a-z0-9_]+")) {
                continue;
            }
            try {
                out.put(Integer.parseInt(entry.substring(0, colon)), plant);
            } catch (NumberFormatException ignored) {
                // Damaged entry: dropped.
            }
        }
        return out;
    }

    /** Sorted by position; {@code null} when empty (the key is then removed). */
    public static String encode(Map<Integer, String> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        new TreeMap<>(entries).forEach((packed, plant) -> {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(packed).append(':').append(plant);
        });
        return builder.toString();
    }

    public static String with(String saved, int packed, String plant) {
        Map<Integer, String> entries = decode(saved);
        entries.put(packed, plant);
        return encode(entries);
    }

    /** The saved string without that position ({@code null} when nothing is left). */
    public static String without(String saved, int packed) {
        Map<Integer, String> entries = decode(saved);
        entries.remove(packed);
        return encode(entries);
    }

    public static String plantAt(String saved, int packed) {
        return decode(saved).get(packed);
    }

    /**
     * Whether the position has a mark that its block no longer backs: the block is not the vanilla potted plant that
     * stands in for the marked module plant (the pot was emptied, pushed, replaced...).
     */
    public static boolean isStale(String saved, int packed, String blockType) {
        String plant = plantAt(saved, packed);
        if (plant == null) {
            return false;
        }
        return PottedPlantTable.forPlant(plant).map(sub -> !sub.potMaterial().equals(blockType)).orElse(true);
    }
}
