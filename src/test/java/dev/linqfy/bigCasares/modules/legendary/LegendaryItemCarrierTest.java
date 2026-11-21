package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryItemCarrierTest {
    @Test
    void aMaterialOnlyNerfCannotMatchAnyLegendaryCarrier() {
        var nerfed = Set.of("MACE", "NETHERITE_SPEAR", "NETHERITE_AXE", "NETHERITE_SWORD");
        for (var weapon : LegendaryWeapon.available()) {
            assertEquals("PAPER", weapon.itemMaterial());
            assertFalse(nerfed.contains(weapon.itemMaterial()));
        }
        assertEquals("MACE", LegendaryWeapon.WINDWEAVER.material);
        assertEquals("NETHERITE_SPEAR", LegendaryWeapon.THUNDER_SPEAR.material);
    }

    @Test
    void conversionCopiesCompleteComponentValuesRatherThanOnlyItemMeta() throws Exception {
        var carrier = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.modules.legendary.LegendaryItemCarrier");
        assertTrue(carrier.callsAnyOwner("copyDataFrom", Set.of("org/bukkit/inventory/ItemStack")),
                "prototype durability, weapon, tool and spear components must survive changing item type");
    }

    @Test
    void anvilsCombineOnlyMatchingLegendaryIdentities() {
        assertTrue(LegendaryEnchanting.compatibleAddition(LegendaryWeapon.BLOODLUST, LegendaryWeapon.BLOODLUST,
                "PAPER"));
        assertFalse(LegendaryEnchanting.compatibleAddition(LegendaryWeapon.BLOODLUST, LegendaryWeapon.SHADOW_BLADE,
                "PAPER"));
        assertFalse(LegendaryEnchanting.compatibleAddition(LegendaryWeapon.BLOODLUST, null, "NETHERITE_SWORD"));
        assertTrue(LegendaryEnchanting.compatibleAddition(LegendaryWeapon.WINDWEAVER, null, "ENCHANTED_BOOK"));
        assertTrue(LegendaryEnchanting.compatibleAddition(LegendaryWeapon.WINDWEAVER, null, "BREEZE_ROD"));
    }

    @Test
    void mapsAndTradesCannotConsumeWeaponsButAnvilsStillAllowBookAndSameWeaponOperations() {
        for (var type : new String[]{"MERCHANT", "CARTOGRAPHY"}) {
            assertTrue(LegendaryItemCarrier.protectedResult(type, LegendaryWeapon.BLOODLUST, null));
            assertTrue(LegendaryItemCarrier.protectedResult(type, null, LegendaryWeapon.WINDWEAVER));
            assertFalse(LegendaryItemCarrier.protectedResult(type, null, null));
        }
        assertFalse(LegendaryItemCarrier.protectedResult("ANVIL", LegendaryWeapon.WINDWEAVER, null));
        assertFalse(LegendaryItemCarrier.protectedResult("ANVIL", LegendaryWeapon.WINDWEAVER,
                LegendaryWeapon.WINDWEAVER));
        assertTrue(LegendaryItemCarrier.protectedResult("ANVIL", LegendaryWeapon.BLOODLUST,
                LegendaryWeapon.SHADOW_BLADE));
        assertFalse(LegendaryItemCarrier.protectedResult("GRINDSTONE", null, LegendaryWeapon.WINDWEAVER));
    }
}
