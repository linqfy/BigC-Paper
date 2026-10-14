package dev.linqfy.bigCasares.modules.cosmicniv;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicNivCustomItemTest {

    private static CosmicNivItemIdentity identity(String material, String catalogId, String itemModel,
                                                  Map<String, String> data) {
        return new CosmicNivItemIdentity(material, catalogId, itemModel, data, Map.of());
    }

    private static CosmicNivSettings withCustomItems(CosmicNivCustomItem... extra) {
        CosmicNivSettings settings = CosmicNivSettingsLoaderTest.bundledSettings();
        List<CosmicNivCustomItem> custom = new ArrayList<>(settings.customItems());
        custom.addAll(List.of(extra));
        return new CosmicNivSettings(settings.maximum(), settings.decimals(), settings.armorWeight(),
            settings.weaponWeight(), settings.toolWeight(), settings.search(), settings.curve(),
            settings.zeroReference(), settings.maxReference(), settings.armorValues(), settings.weaponFamilies(),
            settings.toolFamilies(), settings.enchantmentValues(), custom, settings.memory());
    }

    @Test
    void theSahurBatIsRecognizedByItsCatalogIdAndKeepsItsEnchantments() {
        CosmicNivService service = new CosmicNivService(CosmicNivSettingsLoaderTest.bundledSettings());

        CosmicNivItem bat = service.identify(new CosmicNivItemIdentity("iron_sword", "sahurs_bat", null, Map.of(),
            Map.of("minecraft:sharpness", 2)));
        CosmicNivItem plain = service.identify(CosmicNivItemIdentity.of("iron_sword"));

        assertEquals("custom:sahurs_bat", bat.key());
        assertEquals(2, bat.enchantments().get("minecraft:sharpness"));
        assertEquals(11.0, service.itemPower(bat), 1e-9);
        assertEquals("minecraft:iron_sword", plain.key());
        assertEquals(6.0, service.itemPower(plain), 1e-9);
    }

    @Test
    void everyConditionThatIsSetMustHold() {
        CosmicNivCustomMatch match = new CosmicNivCustomMatch("diamond_pickaxe", null, "otro:pico",
            "otro:tipo", "magico");

        assertTrue(match.matches(identity("diamond_pickaxe", null, "otro:pico", Map.of("otro:tipo", "magico"))));
        assertFalse(match.matches(identity("iron_pickaxe", null, "otro:pico", Map.of("otro:tipo", "magico"))));
        assertFalse(match.matches(identity("diamond_pickaxe", null, null, Map.of("otro:tipo", "magico"))));
        assertFalse(match.matches(identity("diamond_pickaxe", null, "otro:pico", Map.of("otro:tipo", "comun"))));
        assertFalse(match.matches(identity("diamond_pickaxe", null, "otro:pico", Map.of())));
    }

    @Test
    void aPersistentDataKeyWithoutValueOnlyNeedsTheKey() {
        CosmicNivCustomMatch match = new CosmicNivCustomMatch(null, null, null, "otro:marca", null);

        assertTrue(match.matches(identity("stick", null, null, Map.of("otro:marca", "lo que sea"))));
        assertFalse(match.matches(identity("stick", null, null, Map.of())));
    }

    @Test
    void catalogIdsAndItemModelsMatchExactly() {
        CosmicNivCustomMatch byCatalog = new CosmicNivCustomMatch(null, "sahurs_bat", null, null, null);
        CosmicNivCustomMatch byModel = new CosmicNivCustomMatch(null, null, "BigCasares:item/sahurs_bat", null, null);

        assertTrue(byCatalog.matches(identity("iron_sword", "sahurs_bat", null, Map.of())));
        assertFalse(byCatalog.matches(identity("iron_sword", "copper_apple", null, Map.of())));
        assertTrue(byModel.matches(identity("iron_sword", null, "bigcasares:item/sahurs_bat", Map.of())));
        assertFalse(byModel.matches(identity("iron_sword", null, null, Map.of())));
    }

    @Test
    void theFirstMatchingCustomItemWins() {
        CosmicNivService service = new CosmicNivService(withCustomItems(
            new CosmicNivCustomItem("first", new CosmicNivCustomMatch(null, null, "otro:espada", null, null),
                CosmicNivItemCategory.WEAPON, "sword", 10.0),
            new CosmicNivCustomItem("second", new CosmicNivCustomMatch("iron_sword", null, "otro:espada", null, null),
                CosmicNivItemCategory.WEAPON, "sword", 20.0)));

        assertEquals("custom:first", service.identify(identity("iron_sword", null, "otro:espada", Map.of())).key());
    }

    @Test
    void customArmorCountsOnlyInArmorSlots() {
        CosmicNivService service = new CosmicNivService(withCustomItems(
            new CosmicNivCustomItem("dragon_helmet", new CosmicNivCustomMatch(null, null, "otro:casco", null, null),
                CosmicNivItemCategory.ARMOR, null, 9.0)));
        CosmicNivItem helmet = CosmicNivItem.of("custom:dragon_helmet");

        CosmicNivResult worn = service.evaluate(CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, helmet).build());
        CosmicNivResult held = service.evaluate(CosmicNivLoadout.builder().held(helmet).build());

        assertEquals(9.0, worn.armorPower(), 1e-9);
        assertFalse(held.hasWeapon());
        assertEquals(0.0, held.value());
    }

    @Test
    void customToolsCountAsTools() {
        CosmicNivService service = new CosmicNivService(withCustomItems(
            new CosmicNivCustomItem("magic_pick", new CosmicNivCustomMatch(null, null, null, "otro:tipo", "magico"),
                CosmicNivItemCategory.TOOL, "pickaxe", 6.0)));

        CosmicNivResult result = service.evaluate(CosmicNivLoadout.builder()
            .held(CosmicNivItem.of("custom:magic_pick")).build());

        assertEquals("pickaxe", result.tool().family());
        assertEquals(Set.of("otro:tipo"), service.persistentDataKeys());
    }

    @Test
    void invalidCustomDefinitionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCustomMatch(null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new CosmicNivCustomMatch(null, null, null, null, "x"));
        CosmicNivCustomMatch match = new CosmicNivCustomMatch(null, "x", null, null, null);
        assertThrows(IllegalArgumentException.class,
            () -> new CosmicNivCustomItem("Mal Nombre", match, CosmicNivItemCategory.ARMOR, null, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new CosmicNivCustomItem("con.punto", match, CosmicNivItemCategory.ARMOR, null, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new CosmicNivCustomItem("ok", match, CosmicNivItemCategory.WEAPON, null, 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new CosmicNivCustomItem("ok", match, CosmicNivItemCategory.ARMOR, "sword", 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> new CosmicNivCustomItem("ok", match, CosmicNivItemCategory.ARMOR, null, Double.NaN));
    }
}
