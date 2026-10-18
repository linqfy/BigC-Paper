package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;
import dev.linqfy.bigCasares.modules.sift.SiftItems;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps the item ids of the mod's loot tables to what the server can hand out.
 *
 * <ol>
 *   <li>Vanilla ids stay as they are.</li>
 *   <li>{@code the_sift:*} ids that are content items of the port ({@link SiftItems}) keep their id.</li>
 *   <li>Mod plants that only exist as vanilla stand-ins ({@link SiftBlocks}) become the item of that stand-in,
 *       so the stack places the same block the world generator does.</li>
 *   <li>The remaining mod items are replaced or dropped as listed in {@link #REPLACEMENTS}; any other
 *       {@code the_sift:*} id without a content item is dropped.</li>
 * </ol>
 */
final class SiftLootItems {

    /** Mod plants whose block is a vanilla stand-in; the vanilla block id doubles as the item id. */
    private static final List<String> STAND_IN_PLANTS = List.of("overgrown_fronds", "overgrown_chard", "overgrown_stalks",
        "siftslate_stalks", "healthy_sculk_sprouts", "dry_healthy_sculk_sprouts", "overgrown_lotus", "sunburst_plant",
        "whisperbloom");

    /**
     * Mod items without a counterpart in the port, mapped to another id; an empty value means "drop".
     * None are left: the Siftite upgrade template and the stand-in music discs
     * ({@code the_sift:music_disc_rift}, a vanilla disc and song in the mod's clothes) are content items of the
     * port ({@link SiftItems}) and need no entry.
     */
    static final Map<String, String> REPLACEMENTS = Map.of();

    private static final Set<String> CONTENT = SiftItems.items().stream()
        .map(ContentItem::id)
        .map(id -> id.asString())
        .collect(Collectors.toUnmodifiableSet());

    private static final Map<String, String> PLANT_ITEMS = STAND_IN_PLANTS.stream()
        .collect(Collectors.toUnmodifiableMap(path -> "the_sift:" + path,
            path -> SiftBlocks.remapType(path).orElseThrow(() -> new IllegalStateException("No stand-in for " + path))));

    private SiftLootItems() {
    }

    /** The item id to hand out for a loot-table item id, or empty when the entry is dropped. */
    static Optional<String> resolve(String id) {
        if (!id.startsWith("the_sift:")) {
            return Optional.of(id);
        }
        if (CONTENT.contains(id)) {
            return Optional.of(id);
        }
        String plant = PLANT_ITEMS.get(id);
        if (plant != null) {
            return Optional.of(plant);
        }
        String replacement = REPLACEMENTS.get(id);
        return replacement == null || replacement.isEmpty() ? Optional.empty() : Optional.of(replacement);
    }

    static boolean isContentItem(String id) {
        return CONTENT.contains(id);
    }
}
