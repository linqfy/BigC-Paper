package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftStandInDiscsTest {

    private static final Map<NamespacedId, ContentItem> ITEMS = SiftItems.items().stream()
        .collect(Collectors.toMap(ContentItem::id, item -> item));

    @Test
    void theDiscsSitOnVanillaDiscsAndKeepTheModsNameModelAndTrackLine() throws IOException {
        String lang = Files.readString(Path.of("resourcepack/java/assets/the_sift/lang/en_us.json"));
        ContentItem rift = ITEMS.get(SiftItems.MUSIC_DISC_RIFT);
        ContentItem wellspring = ITEMS.get(SiftItems.MUSIC_DISC_WELLSPRING);
        assertEquals("MUSIC_DISC_5", rift.material());
        assertEquals("MUSIC_DISC_OTHERSIDE", wellspring.material());
        for (ContentItem disc : List.of(rift, wellspring)) {
            assertEquals(disc.id(), disc.itemModel());
            assertEquals(1, disc.maxStackSize());
            assertEquals("item.the_sift." + disc.id().path(), disc.nameKey());
            assertEquals("Music Disc", disc.fallbackName());
            assertTrue(lang.contains("\"" + disc.nameKey() + "\""), disc.nameKey());
            assertTrue(Files.isRegularFile(Path.of("resourcepack/java/assets/the_sift/items/" + disc.id().path() + ".json")));
            assertNotNull(disc.customizer(), "hides the vanilla song line");
        }
        assertEquals(List.of("The Sift - Rift"), rift.lore());
        assertEquals(List.of("The Sift - Wellspring"), wellspring.lore());
    }

    @Test
    void theSingerCanGiveTheStandInDisc() {
        assertTrue(ITEMS.containsKey(NamespacedId.parse(SingerBarter.MUSIC_DISC_WELLSPRING)));
        Set<String> available = ITEMS.keySet().stream().map(NamespacedId::asString).collect(Collectors.toSet());
        // 69..71 of the table's 200 is the disc; no outcome is rolled again when the content item exists.
        SingerBarter.Reward reward = SingerBarter.rollAvailable(new FixedRoll(70, 0), id ->
            !id.startsWith("the_sift:") || available.contains(id));
        assertEquals(SingerBarter.MUSIC_DISC_WELLSPRING, reward.item());
        assertEquals(1, reward.count());
    }

    /** Answers the scripted {@code nextInt} values, then zero. */
    private static final class FixedRoll extends Random {
        private final int[] values;
        private int next;

        FixedRoll(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            return next < values.length ? values[next++] : 0;
        }
    }
}
