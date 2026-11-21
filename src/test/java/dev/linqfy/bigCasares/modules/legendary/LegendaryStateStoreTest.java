package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryStateStoreTest {
    @TempDir Path directory;

    @Test
    void fiveAvailableWeaponsKeepUniqueAbilitiesAndRecipes() {
        assertEquals(5, LegendaryWeapon.available().size());
        var abilities = new HashSet<String>();
        var altars = new HashSet<String>();
        for (var weapon : LegendaryWeapon.available()) {
            assertTrue(abilities.add(weapon.abilityId));
            assertTrue(altars.add(weapon.altarName));
            assertEquals(9, weapon.ingredients.values().stream().mapToInt(Integer::intValue).sum());
            assertTrue(weapon.canCraft(weapon.ingredients));
            assertFalse(weapon.canCraft(Map.of()));
            for (String material : weapon.ingredients.keySet()) {
                var missingOne = new java.util.HashMap<>(weapon.ingredients);
                missingOne.compute(material, (key, count) -> count - 1);
                assertFalse(weapon.canCraft(missingOne));
            }
        }
        assertEquals("NETHERITE_SPEAR", LegendaryWeapon.THUNDER_SPEAR.material);
    }

    @Test
    void cooldownSurvivesReconnectAndRestartWithoutSharingPlayersOrAbilities() throws Exception {
        Path file = directory.resolve("state.properties");
        var store = new LegendaryStateStore(file);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertTrue(store.beginCooldown(first, LegendaryWeapon.THUNDER_SPEAR, 1_000));
        assertFalse(store.beginCooldown(first, LegendaryWeapon.THUNDER_SPEAR, 1_001));
        assertEquals(40_000, store.remaining(first, LegendaryWeapon.THUNDER_SPEAR, 1_000));
        assertEquals(30_000, store.remaining(first, LegendaryWeapon.THUNDER_SPEAR, 11_000));
        var restored = new LegendaryStateStore(file);
        assertEquals(1, restored.remaining(first, LegendaryWeapon.THUNDER_SPEAR, 40_999));
        assertTrue(restored.beginCooldown(second, LegendaryWeapon.THUNDER_SPEAR, 40_999));
        assertTrue(restored.beginCooldown(first, LegendaryWeapon.BLOODLUST, 40_999));
        assertTrue(restored.beginCooldown(first, LegendaryWeapon.THUNDER_SPEAR, 41_000));
    }

    @Test
    void escrowSurvivesRestartAndOnlyAcknowledgedReturnDeletesOwnership() throws Exception {
        Path file = directory.resolve("state.properties");
        var store = new LegendaryStateStore(file);
        store.put("spear.owner", "serialized-original-with-transaction-id");
        var restored = new LegendaryStateStore(file);
        assertEquals("serialized-original-with-transaction-id", restored.get("spear.owner"));
        restored.put("spear.owner", null);
        assertNull(new LegendaryStateStore(file).get("spear.owner"));
    }
}
