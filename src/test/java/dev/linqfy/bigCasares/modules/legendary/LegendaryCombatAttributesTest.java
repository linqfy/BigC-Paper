package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegendaryCombatAttributesTest {
    @Test
    void oldAndNewWeaponsHaveTheSameStatsAndNoVanillaBaseModifierKeys() {
        for (var weapon : LegendaryWeapon.available()) {
            var plan = LegendaryCombatAttributes.plan(weapon, null, null);
            assertFalse(plan.damage().getFirst().getKey().getNamespace().equals("minecraft"));
            assertEquals("bigcasares", plan.speed().getFirst().getKey().getNamespace());
            assertEquals(EquipmentSlotGroup.MAINHAND, plan.damage().getFirst().getSlotGroup());
            assertEquals(plan, LegendaryCombatAttributes.plan(weapon, plan.damage(), plan.speed()));
        }
        var wind = LegendaryCombatAttributes.plan(LegendaryWeapon.WINDWEAVER, null, null);
        assertEquals(6.0, 1.0 + wind.damage().getFirst().getAmount());
        assertEquals(0.6, 4.0 + wind.speed().getFirst().getAmount(), 0.000001);
        var spear = LegendaryCombatAttributes.plan(LegendaryWeapon.THUNDER_SPEAR, null, null);
        assertEquals(5.0, 1.0 + spear.damage().getFirst().getAmount());
        assertEquals((double) (1.0F / 1.15F), 4.0 + spear.speed().getFirst().getAmount());
    }

    @Test
    void migrationReplacesOnlyBaseStatsAndPreservesForeignModifiersWithoutStackingOnReload() {
        var foreign = modifier("other:upgrade", 2.0, EquipmentSlotGroup.OFFHAND);
        var vanilla = modifier("minecraft:base_attack_damage", -20.0, EquipmentSlotGroup.MAINHAND);
        var plan = LegendaryCombatAttributes.plan(LegendaryWeapon.EARTHBREAKER, List.of(vanilla, foreign), null);
        assertEquals(2, plan.damage().size());
        assertSame(foreign, plan.damage().getFirst());
        assertEquals(9.0, plan.damage().getLast().getAmount());
        assertEquals("bigcasares:earthbreaker/attack_damage", plan.damage().getLast().getKey().toString());
        assertEquals(plan, LegendaryCombatAttributes.plan(LegendaryWeapon.EARTHBREAKER, plan.damage(), plan.speed()));
        assertThrows(UnsupportedOperationException.class, () -> plan.damage().clear());
    }

    private static AttributeModifier modifier(String key, double amount, EquipmentSlotGroup slot) {
        return new AttributeModifier(NamespacedKey.fromString(key), amount, AttributeModifier.Operation.ADD_NUMBER, slot);
    }
}
