package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyCustomItemTest {

    private static ToffyItemIdentity identity(String material, String catalogId, String itemModel,
                                                  Map<String, String> data) {
        return new ToffyItemIdentity(material, catalogId, itemModel, data, Map.of());
    }

    private static ToffySettings withCustomItems(ToffyCustomItem... extra) {
        ToffySettings settings = ToffySettingsLoaderTest.bundledSettings();
        List<ToffyCustomItem> custom = new ArrayList<>(settings.customItems());
        custom.addAll(List.of(extra));
        return new ToffySettings(settings.maximum(), settings.decimals(), settings.armorWeight(),
            settings.weaponWeight(), settings.toolWeight(), settings.search(), settings.curve(),
            settings.zeroReference(), settings.maxReference(), settings.armorValues(), settings.weaponFamilies(),
            settings.toolFamilies(), settings.enchantmentValues(), custom, settings.memory());
    }

    @Test
    void theSahurBatIsRecognizedByItsCatalogIdAndKeepsItsEnchantments() {
        ToffyService service = new ToffyService(ToffySettingsLoaderTest.bundledSettings());

        ToffyItem bat = service.identify(new ToffyItemIdentity("iron_sword", "sahurs_bat", null, Map.of(),
            Map.of("minecraft:sharpness", 2)));
        ToffyItem plain = service.identify(ToffyItemIdentity.of("iron_sword"));

        assertEquals("custom:sahurs_bat", bat.key());
        assertEquals(2, bat.enchantments().get("minecraft:sharpness"));
        assertEquals(11.0, service.itemPower(bat), 1e-9);
        assertEquals("minecraft:iron_sword", plain.key());
        assertEquals(6.0, service.itemPower(plain), 1e-9);
    }

    @Test
    void everyConditionThatIsSetMustHold() {
        ToffyCustomMatch match = new ToffyCustomMatch("diamond_pickaxe", null, "otro:pico",
            "otro:tipo", "magico");

        assertTrue(match.matches(identity("diamond_pickaxe", null, "otro:pico", Map.of("otro:tipo", "magico"))));
        assertFalse(match.matches(identity("iron_pickaxe", null, "otro:pico", Map.of("otro:tipo", "magico"))));
        assertFalse(match.matches(identity("diamond_pickaxe", null, null, Map.of("otro:tipo", "magico"))));
        assertFalse(match.matches(identity("diamond_pickaxe", null, "otro:pico", Map.of("otro:tipo", "comun"))));
        assertFalse(match.matches(identity("diamond_pickaxe", null, "otro:pico", Map.of())));
    }

    @Test
    void aPersistentDataKeyWithoutValueOnlyNeedsTheKey() {
        ToffyCustomMatch match = new ToffyCustomMatch(null, null, null, "otro:marca", null);

        assertTrue(match.matches(identity("stick", null, null, Map.of("otro:marca", "lo que sea"))));
        assertFalse(match.matches(identity("stick", null, null, Map.of())));
    }

    @Test
    void catalogIdsAndItemModelsMatchExactly() {
        ToffyCustomMatch byCatalog = new ToffyCustomMatch(null, "sahurs_bat", null, null, null);
        ToffyCustomMatch byModel = new ToffyCustomMatch(null, null, "BigCasares:item/sahurs_bat", null, null);

        assertTrue(byCatalog.matches(identity("iron_sword", "sahurs_bat", null, Map.of())));
        assertFalse(byCatalog.matches(identity("iron_sword", "copper_apple", null, Map.of())));
        assertTrue(byModel.matches(identity("iron_sword", null, "bigcasares:item/sahurs_bat", Map.of())));
        assertFalse(byModel.matches(identity("iron_sword", null, null, Map.of())));
    }

    @Test
    void theFirstMatchingCustomItemWins() {
        ToffyService service = new ToffyService(withCustomItems(
            new ToffyCustomItem("first", new ToffyCustomMatch(null, null, "otro:espada", null, null),
                ToffyItemCategory.WEAPON, "sword", 10.0),
            new ToffyCustomItem("second", new ToffyCustomMatch("iron_sword", null, "otro:espada", null, null),
                ToffyItemCategory.WEAPON, "sword", 20.0)));

        assertEquals("custom:first", service.identify(identity("iron_sword", null, "otro:espada", Map.of())).key());
    }

    @Test
    void customArmorCountsOnlyInArmorSlots() {
        ToffyService service = new ToffyService(withCustomItems(
            new ToffyCustomItem("dragon_helmet", new ToffyCustomMatch(null, null, "otro:casco", null, null),
                ToffyItemCategory.ARMOR, null, 9.0)));
        ToffyItem helmet = ToffyItem.of("custom:dragon_helmet");

        ToffyResult worn = service.evaluate(ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, helmet).build());
        ToffyResult held = service.evaluate(ToffyLoadout.builder().held(helmet).build());

        assertEquals(9.0, worn.armorPower(), 1e-9);
        assertFalse(held.hasWeapon());
        assertEquals(0.0, held.value());
    }

    @Test
    void customToolsCountAsTools() {
        ToffyService service = new ToffyService(withCustomItems(
            new ToffyCustomItem("magic_pick", new ToffyCustomMatch(null, null, null, "otro:tipo", "magico"),
                ToffyItemCategory.TOOL, "pickaxe", 6.0)));

        ToffyResult result = service.evaluate(ToffyLoadout.builder()
            .held(ToffyItem.of("custom:magic_pick")).build());

        assertEquals("pickaxe", result.tool().family());
        assertEquals(Set.of("otro:tipo"), service.persistentDataKeys());
    }

    @Test
    void invalidCustomDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ToffyCustomMatch(null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ToffyCustomMatch(null, null, null, null, "x"));
        ToffyCustomMatch match = new ToffyCustomMatch(null, "x", null, null, null);
        assertThrows(IllegalArgumentException.class,
            () -> new ToffyCustomItem("Mal Nombre", match, ToffyItemCategory.ARMOR, null, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new ToffyCustomItem("con.punto", match, ToffyItemCategory.ARMOR, null, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new ToffyCustomItem("ok", match, ToffyItemCategory.WEAPON, null, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new ToffyCustomItem("ok", match, ToffyItemCategory.ARMOR, "sword", 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new ToffyCustomItem("ok", match, ToffyItemCategory.ARMOR, null, Double.NaN));
    }
}
