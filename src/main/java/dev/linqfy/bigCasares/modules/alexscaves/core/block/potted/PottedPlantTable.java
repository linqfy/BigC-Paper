package dev.linqfy.bigCasares.modules.alexscaves.core.block.potted;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The mod's {@code potted_*} blocks as vanilla flower pots: which module plant item goes into an empty pot and which
 * vanilla pot (and plant item) stands in for it. Materials are Bukkit enum names so this stays free of the server.
 * The saplings and pewen pines have no module item yet, so their rows are inert until the items exist.
 */
public final class PottedPlantTable {
    /** The vanilla look of one potted module plant. */
    public record Substitute(String plant, String potMaterial, String plantMaterial) {
    }

    private static final Map<String, Substitute> BY_ITEM = new LinkedHashMap<>();

    static {
        add("ancient_sapling", "POTTED_JUNGLE_SAPLING", "JUNGLE_SAPLING");
        add("curly_fern", "POTTED_FERN", "FERN");
        add("cycad", "POTTED_BAMBOO", "BAMBOO");
        add("fiddlehead", "POTTED_FERN", "FERN");
        add("flytrap", "POTTED_RED_TULIP", "RED_TULIP");
        add("pewen_pines", "POTTED_SPRUCE_SAPLING", "SPRUCE_SAPLING");
        add("pewen_sapling", "POTTED_SPRUCE_SAPLING", "SPRUCE_SAPLING");
        // Same stand-in as the generated structures use for the thornwood sapling.
        add("thornwood_branch", "POTTED_DEAD_BUSH", "DEAD_BUSH");
        add("thornwood_sapling", "POTTED_DEAD_BUSH", "DEAD_BUSH");
        add("underweed", "POTTED_WARPED_ROOTS", "WARPED_ROOTS");
    }

    private PottedPlantTable() {
    }

    private static void add(String plant, String pot, String item) {
        BY_ITEM.put("ac_" + plant, new Substitute(plant, pot, item));
    }

    /** The substitute for a module item id ({@code ac_curly_fern}), when it is a pottable plant. */
    public static Optional<Substitute> forItem(String itemId) {
        return Optional.ofNullable(itemId == null ? null : BY_ITEM.get(itemId));
    }

    /** The substitute for a stored plant name ({@code curly_fern}). */
    public static Optional<Substitute> forPlant(String plant) {
        return plant == null ? Optional.empty() : Optional.ofNullable(BY_ITEM.get("ac_" + plant));
    }

    public static String itemId(String plant) {
        return "ac_" + plant;
    }

    public static Map<String, Substitute> all() {
        return Map.copyOf(BY_ITEM);
    }
}
