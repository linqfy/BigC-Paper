package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import dev.linqfy.bigCasares.modules.legendary.LegendaryWeapon;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeyserLegendaryItemsTest {
    @Test void disabledModulesDoNotRegisterWeaponsAndModelsKeepStableCrossPlatformIds() {
        assertTrue(GeyserLegendaryItems.definitions(new CustomItemRegistry()).isEmpty());
        var ids = new HashSet<String>();
        for (var weapon : LegendaryWeapon.available()) {
            var definition = GeyserLegendaryItems.definition(weapon);
            assertEquals("minecraft:paper", definition.javaBaseIdentifier());
            assertTrue(ids.add(definition.bedrockIdentifier()));
            assertEquals("bigcasares:" + weapon.id, definition.javaModelIdentifier());
            assertEquals(definition.javaModelIdentifier(), definition.bedrockIdentifier());
            assertEquals("bigcasares.legendary." + weapon.id, definition.icon());
        }
        assertEquals(5, ids.size());
    }
}
