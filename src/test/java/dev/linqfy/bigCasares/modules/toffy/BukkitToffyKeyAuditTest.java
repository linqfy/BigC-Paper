package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitToffyKeyAuditTest {
    private static ToffyCustomItem armor(String id, ToffyCustomMatch match) {
        return new ToffyCustomItem(id, match, ToffyItemCategory.ARMOR, null, 1.0);
    }

    @Test
    void aCatalogIdThatIsNotInTheCatalogWarns() {
        List<ToffyCustomItem> items = List.of(
            armor("ghost", new ToffyCustomMatch(null, "fantasma", null, null, null)),
            armor("bat", new ToffyCustomMatch(null, "sahurs_bat", null, null, null)));

        List<String> warnings = BukkitToffyKeyAudit.customMatchWarnings(items, id -> id.equals("sahurs_bat"));

        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().startsWith("custom-items.ghost.match.bigcasares-item: fantasma"),
            warnings.getFirst());
    }

    @Test
    void validItemModelsAndPersistentDataKeysDoNotWarn() {
        List<ToffyCustomItem> items = List.of(armor("hat",
            new ToffyCustomMatch(null, null, "otroplugin:casco_dragon", "otroplugin:tipo", "magico")));

        assertTrue(BukkitToffyKeyAudit.customMatchWarnings(items, id -> false).isEmpty());
    }

    @Test
    void theCheckOnlyLooksAtMatchesThatNameACatalogId() {
        List<ToffyCustomItem> items = List.of(
            armor("model", new ToffyCustomMatch(null, null, "otroplugin:casco", null, null)));

        assertTrue(BukkitToffyKeyAudit.customMatchWarnings(items, id -> false).isEmpty());
    }
}
