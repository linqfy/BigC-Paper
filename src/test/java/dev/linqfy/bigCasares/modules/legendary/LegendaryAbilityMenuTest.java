package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryAbilityMenuTest {
    @Test void allSixteenActivePowersAreAvailableWithoutChangingTheirUnlockLevels() {
        var names = new HashSet<String>();
        for (var weapon : LegendaryWeapon.available()) {
            var actions = LegendaryAbilityMenu.actions(weapon);
            assertEquals(weapon == LegendaryWeapon.WINDWEAVER || weapon == LegendaryWeapon.THUNDER_SPEAR ? 5 : 2, actions.size());
            for (var action : actions) {
                assertTrue(names.add(action.id()));
                assertEquals(weapon, action.weapon());
                assertEquals(action.elemental() == null ? LegendaryAltarAbilities.requiredLevel(weapon, action.secondary())
                        : action.elemental().level, action.level());
            }
        }
        assertEquals(16, names.size());
        assertEquals(3, LegendaryAbilityMenu.resolve(LegendaryWeapon.BLOODLUST, "blood-trail").level());
        assertEquals(5, LegendaryAbilityMenu.resolve(LegendaryWeapon.BLOODLUST, "blood-hook").level());
    }

    @Test void aFormOrCommandCannotInvokeAnotherWeaponsSkillOrARetiredWeapon() {
        assertThrows(IllegalArgumentException.class, () -> LegendaryAbilityMenu.resolve(LegendaryWeapon.BLOODLUST, "wrath-of-zeus"));
        assertThrows(IllegalArgumentException.class, () -> LegendaryAbilityMenu.resolve(LegendaryWeapon.VOID_KATANA, "shadow-leap"));
        assertEquals("shadow-leap", LegendaryAbilityMenu.resolve(LegendaryWeapon.SHADOW_BLADE, "SHADOW-LEAP").id());
    }
}
