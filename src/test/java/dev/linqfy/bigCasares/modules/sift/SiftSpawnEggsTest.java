package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.modules.sift.SiftSpawnEggs.Use;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftSpawnEggsTest {

    private static final Map<NamespacedId, ContentItem> ITEMS = SiftItems.items().stream()
        .collect(Collectors.toMap(ContentItem::id, item -> item));

    @Test
    void clickingABlockSpawnsOnItsFaceUnlessTheBlockHasItsOwnUse() {
        assertEquals(Use.SPAWN_AT_FACE, SiftSpawnEggs.decide(Action.RIGHT_CLICK_BLOCK, false, false, false));
        assertEquals(Use.SPAWN_AT_FACE, SiftSpawnEggs.decide(Action.RIGHT_CLICK_BLOCK, false, true, true), "sneaking skips the block's use");
        assertEquals(Use.LEAVE, SiftSpawnEggs.decide(Action.RIGHT_CLICK_BLOCK, false, true, false), "a chest still opens");
    }

    @Test
    void aSpawnerIsRefusedWhateverThePlayerDoes() {
        for (boolean sneaking : List.of(false, true)) {
            assertEquals(Use.SPAWNER, SiftSpawnEggs.decide(Action.RIGHT_CLICK_BLOCK, true, false, sneaking));
            assertEquals(Use.SPAWNER, SiftSpawnEggs.decide(Action.RIGHT_CLICK_BLOCK, true, true, sneaking));
        }
    }

    @Test
    void aimingAtTheAirLooksForWaterAndOtherClicksAreNotTheEggs() {
        assertEquals(Use.SPAWN_AT_FLUID, SiftSpawnEggs.decide(Action.RIGHT_CLICK_AIR, false, false, false));
        assertEquals(Use.LEAVE, SiftSpawnEggs.decide(Action.LEFT_CLICK_BLOCK, false, false, false));
        assertEquals(Use.LEAVE, SiftSpawnEggs.decide(Action.LEFT_CLICK_AIR, false, false, false));
        assertEquals(Use.LEAVE, SiftSpawnEggs.decide(Action.PHYSICAL, false, false, false));
    }

    @Test
    void theThreeEggsAreContentItemsOnTheEggOfASimilarMob() {
        assertEquals("WOLF_SPAWN_EGG", ITEMS.get(SiftItems.BLUB_SPAWN_EGG).material());
        assertEquals("SILVERFISH_SPAWN_EGG", ITEMS.get(SiftItems.SIFTER_SPAWN_EGG).material());
        assertEquals("SNIFFER_SPAWN_EGG", ITEMS.get(SiftItems.DARK_SNIFFER_SPAWN_EGG).material());
    }

    @Test
    void everyEggHasTheModsModelAndNameInTheResourcePack() throws IOException {
        String lang = Files.readString(Path.of("resourcepack/java/assets/the_sift/lang/en_us.json"));
        for (NamespacedId id : List.of(SiftItems.BLUB_SPAWN_EGG, SiftItems.SIFTER_SPAWN_EGG, SiftItems.DARK_SNIFFER_SPAWN_EGG)) {
            ContentItem egg = ITEMS.get(id);
            assertEquals(id, egg.itemModel());
            assertTrue(Files.isRegularFile(Path.of("resourcepack/java/assets/the_sift/items/" + id.path() + ".json")), id.toString());
            assertTrue(lang.contains("\"" + egg.nameKey() + "\""), egg.nameKey() + " is not in the pack's lang file");
        }
    }
}
