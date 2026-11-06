package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryRetiredWeaponsTest {
    @TempDir Path directory;

    @Test
    void removedItemsCannotBeUsedOrOfferedAlongsideTheFiveRemainingWeapons() {
        assertEquals(Set.of("bloodlust", "shadow_blade", "windweaver", "earthbreaker", "thunder_spear"),
            LegendaryWeapon.available().stream().map(weapon -> weapon.id).collect(Collectors.toSet()));
        for (var weapon : List.of(LegendaryWeapon.VOID_KATANA, LegendaryWeapon.HYPERION, LegendaryWeapon.FROST_SCYTHE)) {
            assertTrue(LegendaryWeapon.find(weapon.id).isEmpty());
            assertFalse(weapon.canCraft(weapon.ingredients));
            assertEquals(LegendaryControls.Decision.BLOCK_SPECIAL, LegendaryControls.decide(weapon, true, true, false));
        }
    }

    @ParameterizedTest
    @EnumSource(value = LegendaryWeapon.class, names = {"VOID_KATANA", "HYPERION", "FROST_SCYTHE"})
    void anOldPendingClaimCanBeConfirmedWithoutMakingTheRetiredPrizeAvailableAgain(LegendaryWeapon weapon) throws Exception {
        var file = directory.resolve("state.properties");
        var store = new LegendaryStateStore(file);
        UUID player = UUID.randomUUID(), nonce = UUID.randomUUID();
        var session = new LegendaryCraftingSession(UUID.randomUUID(), weapon, UUID.randomUUID(),
            "End", 0, 64, 0, 1000, 1_201_000, LegendarySearchAreas.create(0, 0, new Random(1)),
            LegendaryCraftingSession.Stage.CLAIMED, 4,
            List.of(new LegendaryCraftingSession.Pending(player, nonce, "claimed-inventory", LegendaryCraftingSession.TransactionKind.CLAIM)));
        store.put("pvp." + weapon.id, session.encode());
        var ledger = new LegendaryCraftingLedger(store, new Random(1));
        assertTrue(ledger.pending(player));
        assertTrue(ledger.all().isEmpty());
        ledger.confirm(weapon, nonce);
        var restart = new LegendaryCraftingLedger(new LegendaryStateStore(file), new Random(1));
        assertFalse(restart.pending(player));
        assertTrue(restart.advance(2_000_000).isEmpty());
        assertEquals(LegendaryCraftingSession.Stage.CLAIMED, restart.history().getFirst().stage());
    }

    @Test
    void retiredRecipesCannotReserveAnEventOrConsumeItsUniqueSlot() throws Exception {
        var store = new LegendaryStateStore(directory.resolve("state.properties"));
        var ledger = new LegendaryCraftingLedger(store, new Random(1));
        for (var weapon : List.of(LegendaryWeapon.VOID_KATANA, LegendaryWeapon.HYPERION, LegendaryWeapon.FROST_SCYTHE)) {
            assertThrows(IllegalArgumentException.class, () -> ledger.start(weapon, UUID.randomUUID(), "Overworld", 0, 64, 0,
                1000, new LegendaryCraftingSession.Pending(UUID.randomUUID(), UUID.randomUUID(), "snapshot",
                    LegendaryCraftingSession.TransactionKind.START)));
        }
        assertTrue(ledger.history().isEmpty());
        assertTrue(store.snapshot().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = LegendaryWeapon.class, names = {"VOID_KATANA", "HYPERION", "FROST_SCYTHE"})
    void legacyRecordsStayRecoverableWithoutAdvancingProtectingTablesOrOfferingClaims(LegendaryWeapon weapon) throws Exception {
        var file = directory.resolve("state.properties");
        var store = new LegendaryStateStore(file);
        UUID world = UUID.randomUUID(), player = UUID.randomUUID(), nonce = UUID.randomUUID();
        var retired = new LegendaryCraftingSession(UUID.randomUUID(), weapon, world, "Overworld", 0, 64, 0,
            1000, 1_201_000, LegendarySearchAreas.create(0, 0, new Random(1)), LegendaryCraftingSession.Stage.CRAFTING, 0,
            List.of(new LegendaryCraftingSession.Pending(player, nonce, "original-inventory", LegendaryCraftingSession.TransactionKind.START)));
        store.put("pvp." + weapon.id, retired.encode());
        var ledger = new LegendaryCraftingLedger(new LegendaryStateStore(file), new Random(1));
        assertTrue(ledger.all().isEmpty());
        assertEquals(1, ledger.history().size());
        assertTrue(ledger.pending(player));
        assertFalse(ledger.protectedCell(world, 0, 64, 0));
        assertTrue(ledger.advance(2_000_000).isEmpty());
        assertEquals(retired.encode(), new LegendaryStateStore(file).get("pvp." + weapon.id));
        assertThrows(IllegalArgumentException.class, () -> ledger.claim(weapon,
            new LegendaryCraftingSession.Pending(UUID.randomUUID(), UUID.randomUUID(), "unused", LegendaryCraftingSession.TransactionKind.CLAIM)));
        ledger.start(LegendaryWeapon.BLOODLUST, world, "Overworld", 0, 64, 0, 2_000_000,
            new LegendaryCraftingSession.Pending(UUID.randomUUID(), UUID.randomUUID(), "new-inventory", LegendaryCraftingSession.TransactionKind.START));
        ledger.confirm(weapon, nonce);
        var restart = new LegendaryCraftingLedger(new LegendaryStateStore(file), new Random(1));
        assertFalse(restart.pending(player));
        assertEquals(List.of(LegendaryWeapon.BLOODLUST), restart.all().stream().map(LegendaryCraftingSession::weapon).toList());
        assertTrue(restart.find(weapon).orElseThrow().transactions().isEmpty());
    }
}
