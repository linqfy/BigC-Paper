package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.modules.sift.SiftStandIns.StandIn;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftStandInsTest {

    @Test
    void everyStandInIsAContentItemWithTheModsIconWhereThePackHasOne() {
        Map<NamespacedId, ContentItem> items = SiftItems.items().stream()
            .collect(Collectors.toMap(ContentItem::id, item -> item));
        for (StandIn standIn : SiftStandIns.ALL) {
            ContentItem item = items.get(NamespacedId.of(SiftBlocks.NS, standIn.path()));
            assertTrue(item != null, standIn.path());
            assertEquals(standIn.item().name(), item.material(), standIn.path());
            NamespacedId model = item.itemModel();
            if (model.namespace().equals(SiftBlocks.NS)) {
                assertTrue(Files.isRegularFile(Path.of("resourcepack/java/assets/the_sift/items/" + model.path() + ".json")),
                    standIn.path() + " has no item definition in the pack");
            }
        }
    }

    @Test
    void contentIdsStayUnique() {
        Set<NamespacedId> ids = new HashSet<>();
        for (ContentItem item : SiftItems.items()) {
            assertTrue(ids.add(item.id()), "duplicate " + item.id());
        }
    }

    @Test
    void worldGeneratedPlantsAreToldApartByTheirFloor() {
        assertEquals(Optional.of("siftslate_stalks"),
            SiftStandIns.worldgenPlant(Material.CRIMSON_ROOTS, true).map(StandIn::path));
        assertEquals(Optional.of("healthy_sculk_sprouts"),
            SiftStandIns.worldgenPlant(Material.CRIMSON_ROOTS, false).map(StandIn::path));
        assertEquals(Optional.of("overgrown_fronds"), SiftStandIns.worldgenPlant(Material.SHORT_GRASS, false).map(StandIn::path));
        assertEquals(Optional.empty(), SiftStandIns.worldgenPlant(Material.WARPED_SLAB, false), "only plants come from worldgen");
    }

    @Test
    void plantStandInsMatchTheWorldGeneratorsStandIns() {
        for (StandIn standIn : SiftStandIns.ALL) {
            if (standIn.siftWorldgen() && !standIn.path().equals("ichor_snow")) {
                String generated = SiftBlocks.remap(standIn.path(), Map.of()).orElseThrow().block().asString();
                // The placed block is the item's block or its plant segment (weeping vines plant).
                String item = "minecraft:" + standIn.item().name().toLowerCase(java.util.Locale.ROOT);
                assertTrue(generated.startsWith(item), standIn.path() + " generates " + generated + ", the item is " + item);
            }
        }
    }
}
