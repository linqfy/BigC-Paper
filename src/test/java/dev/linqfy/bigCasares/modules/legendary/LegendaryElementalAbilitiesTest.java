package dev.linqfy.bigCasares.modules.legendary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

class LegendaryElementalAbilitiesTest {
    @TempDir Path directory;

    @Test
    void spearRightClickPreservesNativeChargeAndOtherSkillsConsumeItemUse() {
        var event = new org.bukkit.event.player.PlayerInteractEvent(null,
                org.bukkit.event.block.Action.RIGHT_CLICK_AIR, null, null, org.bukkit.block.BlockFace.SELF,
                org.bukkit.inventory.EquipmentSlot.HAND);
        assertTrue(LegendaryElementalSkill.claimInput(event, LegendaryElementalSkill.THUNDER_MARK));
        assertEquals(org.bukkit.event.Event.Result.DEFAULT, event.useItemInHand());
        assertEquals(org.bukkit.event.Event.Result.DENY, event.useInteractedBlock());
        assertTrue(LegendaryElementalSkill.claimInput(event, LegendaryElementalSkill.SKYBREAKER));
        assertEquals(org.bukkit.event.Event.Result.DENY, event.useItemInHand());
    }

    @Test
    void explicitItemDenialFromAnotherPluginCannotActivateAnAirSkill() {
        var event = new org.bukkit.event.player.PlayerInteractEvent(null,
                org.bukkit.event.block.Action.RIGHT_CLICK_AIR, null, null, org.bukkit.block.BlockFace.SELF,
                org.bukkit.inventory.EquipmentSlot.HAND);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        assertFalse(LegendaryElementalSkill.claimInput(event, LegendaryElementalSkill.THUNDER_MARK));
        assertEquals(org.bukkit.event.Event.Result.DENY, event.useItemInHand());
    }

    @Test
    void gesturesKeepLowerLevelAbilitiesAvailableAndNativeMeleeUnassigned() {
        var thunder = LegendaryWeapon.THUNDER_SPEAR;
        var wind = LegendaryWeapon.WINDWEAVER;
        assertNull(LegendaryElementalSkill.click(thunder, 5, false, false, true, 0));
        assertEquals(
                LegendaryElementalSkill.LIGHTNING_DASH,
                LegendaryElementalSkill.click(thunder, 5, true, false, true, 0));
        assertEquals(
                LegendaryElementalSkill.THUNDERSTORM,
                LegendaryElementalSkill.click(thunder, 5, true, false, true, -70));
        assertEquals(
                LegendaryElementalSkill.LIGHTNING_DASH,
                LegendaryElementalSkill.click(thunder, 3, true, false, true, -70));
        assertEquals(
                LegendaryElementalSkill.THUNDER_MARK,
                LegendaryElementalSkill.click(thunder, 5, false, true, true, 0));
        assertEquals(
                LegendaryElementalSkill.CHAIN_LIGHTNING,
                LegendaryElementalSkill.click(thunder, 5, true, true, true, 0));
        assertEquals(
                LegendaryElementalSkill.WIND_PRISON,
                LegendaryElementalSkill.click(wind, 5, true, false, false, 70));
        assertNull(LegendaryElementalSkill.click(wind, 3, true, false, false, 70));
        assertEquals(
                LegendaryElementalSkill.WIND_DASH,
                LegendaryElementalSkill.click(wind, 5, true, false, true, 0));
        assertEquals(
                LegendaryElementalSkill.SKYBREAKER,
                LegendaryElementalSkill.click(wind, 5, false, true, true, 0));
        assertEquals(
                LegendaryElementalSkill.TORNADO,
                LegendaryElementalSkill.click(wind, 5, true, true, true, 0));
    }

    @Test
    void cooldownsAreIndependentPersistentAndLegacyCooldownStillApplies() throws Exception {
        var player = UUID.randomUUID();
        var file = directory.resolve("state.properties");
        var store = new LegendaryStateStore(file);
        assertTrue(
                store.beginAbilityCooldown(
                        player, LegendaryElementalSkill.LIGHTNING_DASH, 1000, 20000));
        assertTrue(
                store.beginAbilityCooldown(
                        player, LegendaryElementalSkill.THUNDER_MARK, 1000, 40000));
        assertFalse(
                store.beginAbilityCooldown(
                        player, LegendaryElementalSkill.LIGHTNING_DASH, 2000, 20000));
        assertEquals(
                19000,
                new LegendaryStateStore(file)
                        .abilityRemaining(player, LegendaryElementalSkill.LIGHTNING_DASH, 2000));
        assertEquals(
                0, store.abilityRemaining(player, LegendaryElementalSkill.CHAIN_LIGHTNING, 2000));
        store.resetCooldowns(player);
        assertEquals(
                0, store.abilityRemaining(player, LegendaryElementalSkill.LIGHTNING_DASH, 2000));
        store.beginCooldown(player, LegendaryWeapon.THUNDER_SPEAR, 3000);
        assertEquals(
                39000,
                store.abilityRemaining(player, LegendaryElementalSkill.CHAIN_LIGHTNING, 4000));
    }

    @Test
    void chainHasFourUniqueHitsWithCompoundedFalloffAndTornadoBudgetCannotOverrun() {
        assertEquals(6, LegendaryElementalSkill.chainDamage(6, 0));
        assertEquals(4.5, LegendaryElementalSkill.chainDamage(6, 1));
        assertEquals(3.375, LegendaryElementalSkill.chainDamage(6, 2));
        assertEquals(2.53125, LegendaryElementalSkill.chainDamage(6, 3));
        var budget = new LegendaryHitBudget(6);
        var target = UUID.randomUUID();
        assertEquals(2, budget.reserve(target, 2));
        assertEquals(2, budget.reserve(target, 2));
        assertEquals(2, budget.reserve(target, 2));
        assertEquals(0, budget.reserve(target, 2));
        assertEquals(2, budget.reserve(UUID.randomUUID(), 2));
        var once = new LegendaryHitBudget(4);
        assertEquals(4, once.reserve(target, 4));
        assertEquals(0, once.reserve(target, 4));
    }

    @Test
    void configurationSnapshotsCannotBeChangedMidCastAndInvalidNumbersAreBounded() {
        var values = new java.util.HashMap<String, Object>();
        values.put("damage", 12.0);
        values.put("duration", Double.NaN);
        values.put("range", -7);
        values.put("cooldown", Double.POSITIVE_INFINITY);
        values.put("radius", 500);
        var snapshot =
                new LegendaryElementalSkill.Settings(LegendaryElementalSkill.WRATH_OF_ZEUS, values);
        values.put("damage", 40.0);
        assertEquals(12, snapshot.damage());
        assertEquals(30, snapshot.durationTicks());
        assertEquals(0, snapshot.range());
        assertEquals(90000, snapshot.cooldownMillis());
        assertEquals(24, snapshot.radius());
    }

    @Test
    void landingAndWallContactReleaseFallProtectionExactlyOnce() {
        var groundDash = new LegendaryFlightWindow();
        assertFalse(groundDash.update(1, true, false));
        assertFalse(groundDash.update(3, true, false));
        assertTrue(groundDash.update(8, true, true));
        assertFalse(groundDash.protectsFall());
        for (int tick = 9; tick < 400; tick++) assertFalse(groundDash.update(tick, true, true));
        var airDash = new LegendaryFlightWindow();
        assertFalse(airDash.update(1, false, false));
        assertTrue(airDash.protectsFall());
        assertTrue(airDash.update(7, true, false));
        assertFalse(airDash.protectsFall());
        var eventFirst = new LegendaryFlightWindow();
        eventFirst.land();
        assertFalse(eventFirst.update(8, true, true));
        assertFalse(eventFirst.protectsFall());
    }
}
