package dev.linqfy.bigCasares.modules.duels;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelsTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void exposesStableModuleId() {
        assertEquals("duels", new DuelsModule(null).getId());
    }

    @Test
    void arenaSizesFollowTheRequestedChunks() {
        assertEquals(64, DuelArenaSize.GRANDE.side());
        assertEquals(37, DuelArenaSize.MEDIANA.side());
        assertEquals(24, DuelArenaSize.CHICA.side());
        assertEquals(Optional.of(DuelArenaSize.CHICA), DuelArenaSize.parse("Chica"));
        assertEquals(Optional.of(DuelArenaSize.MEDIANA), DuelArenaSize.parse("media"));
        assertEquals(Optional.of(DuelArenaSize.GRANDE), DuelArenaSize.parse("large"));
        assertEquals(Optional.empty(), DuelArenaSize.parse("gigante"));
    }

    @Test
    void arenasNeverOverlap() {
        double gap = DuelArenaWorld.centerX(1) - DuelArenaWorld.centerX(0);
        assertTrue(gap > DuelArenaSize.GRANDE.side() + 32);
    }

    @Test
    void parsesItemSpecs() {
        DuelItemSpec sword = DuelItemSpec.parse("diamond_sword sharpness:2 unbreaking:1");
        assertEquals("DIAMOND_SWORD", sword.material());
        assertEquals(1, sword.amount());
        assertEquals(Map.of("sharpness", 2, "unbreaking", 1), sword.enchantments());

        DuelItemSpec apples = DuelItemSpec.parse("GOLDEN_APPLE*6");
        assertEquals(6, apples.amount());
        assertTrue(apples.enchantments().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> DuelItemSpec.parse(""));
        assertThrows(IllegalArgumentException.class, () -> DuelItemSpec.parse("ARROW*0"));
        assertThrows(IllegalArgumentException.class, () -> DuelItemSpec.parse("ARROW*x"));
        assertThrows(IllegalArgumentException.class, () -> DuelItemSpec.parse("BOW power"));
        assertThrows(IllegalArgumentException.class, () -> DuelItemSpec.parse("BOW power:0"));
    }

    @Test
    void defaultConfigKitsAllLoad() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        List<String> warnings = new ArrayList<>();
        DuelSettings settings = DuelSettings.load(config, warnings::add);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertTrue(settings.kits().size() >= 3, "the draft needs kits to ban and pick");
        assertEquals("bigcasares_duels", settings.worldName());
        assertEquals(DuelArenaSize.MEDIANA, settings.defaultArena());
        DuelKit diamante = settings.kits().get("diamante");
        assertEquals("DIAMOND_HELMET", diamante.armor().getFirst().material());
        assertEquals("SHIELD", diamante.offhand().material());
        assertTrue(config.getBoolean("modules.duels.enabled"));
    }

    @Test
    void brokenKitsAreSkippedWithAWarning() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("duels.kits.ok.items", List.of("STONE_SWORD"));
        config.set("duels.kits.bad.items", List.of("BOW power:x"));
        List<String> warnings = new ArrayList<>();
        DuelSettings settings = DuelSettings.load(config, warnings::add);
        assertEquals(List.of("ok"), settings.kitIds());
        assertEquals(1, warnings.size());
        assertNull(settings.kits().get("ok").armor().getFirst());
    }

    @Test
    void draftBansThenPicks() {
        DuelKitDraft draft = new DuelKitDraft(List.of("a", "b", "c", "d"), A, B, () -> 0.9);
        assertEquals(DuelKitDraft.Phase.BAN, draft.phase());
        assertFalse(draft.pick(A, "a"), "no picks while banning");
        assertTrue(draft.ban(A, "a"));
        assertFalse(draft.ban(A, "b"), "one ban each");
        assertTrue(draft.ban(B, "b"));
        assertEquals(DuelKitDraft.Phase.PICK, draft.phase());
        assertEquals(List.of("c", "d"), draft.available());
        assertFalse(draft.pick(A, "a"), "banned kits cannot be picked");
        assertTrue(draft.pick(A, "c"));
        assertTrue(draft.pick(B, "d"));
        assertEquals(DuelKitDraft.Phase.DONE, draft.phase());
        assertEquals("d", draft.result(), "different picks are settled by the coin flip");
    }

    @Test
    void equalPicksWinWithoutACoinFlip() {
        DuelKitDraft draft = new DuelKitDraft(List.of("a", "b", "c"), A, B, () -> 0.9);
        draft.ban(A, "a");
        draft.ban(B, "a");
        draft.pick(A, "c");
        draft.pick(B, "c");
        assertEquals("c", draft.result());
    }

    @Test
    void bansLeavingOneKitEndTheDraft() {
        DuelKitDraft draft = new DuelKitDraft(List.of("a", "b", "c"), A, B, () -> 0.0);
        draft.ban(A, "a");
        draft.ban(B, "b");
        assertEquals(DuelKitDraft.Phase.DONE, draft.phase());
        assertEquals("c", draft.result());
    }

    @Test
    void bansThatRemoveEveryKitBringThemBack() {
        DuelKitDraft draft = new DuelKitDraft(List.of("a", "b"), A, B, () -> 0.0);
        draft.ban(A, "a");
        draft.ban(B, "b");
        assertEquals(DuelKitDraft.Phase.PICK, draft.phase());
        assertEquals(List.of("a", "b"), draft.available());
    }

    @Test
    void singleKitNeedsNoDraftAndOutsidersCannotVote() {
        assertEquals("solo", new DuelKitDraft(List.of("solo"), A, B, () -> 0.0).result());
        DuelKitDraft draft = new DuelKitDraft(List.of("a", "b", "c"), A, B, () -> 0.0);
        assertFalse(draft.ban(UUID.randomUUID(), "a"));
    }

    @Test
    void timeoutsFillOnlyTheCurrentPhase() {
        DuelKitDraft draft = new DuelKitDraft(List.of("a", "b", "c", "d"), A, B, () -> 0.0);
        draft.ban(A, "b");
        draft.autoComplete();
        assertEquals(DuelKitDraft.Phase.PICK, draft.phase());
        assertEquals("a", draft.banOf(B));
        draft.autoComplete();
        assertEquals(DuelKitDraft.Phase.DONE, draft.phase());
        assertEquals("c", draft.result());
    }

    @Test
    void escrowTakesBothStakesAndPaysTheWinner() {
        FakeBank bank = new FakeBank(Map.of(A, 100L, B, 100L));
        DuelEscrow escrow = new DuelEscrow(bank);
        assertTrue(escrow.collect(A, B, 40L));
        assertEquals(60L, bank.balance(A));
        assertEquals(60L, bank.balance(B));
        escrow.payWinner(A, 40L);
        assertEquals(140L, bank.balance(A));
        assertEquals(60L, bank.balance(B));
    }

    @Test
    void escrowGivesTheFirstStakeBackWhenTheSecondFails() {
        FakeBank bank = new FakeBank(Map.of(A, 100L, B, 10L));
        DuelEscrow escrow = new DuelEscrow(bank);
        assertFalse(escrow.collect(A, B, 40L));
        assertEquals(100L, bank.balance(A));
        assertEquals(10L, bank.balance(B));
        assertFalse(escrow.canAfford(B, 40L));
        assertTrue(escrow.canAfford(B, 0L));
    }

    @Test
    void escrowWithoutAnEconomyOnlyAllowsFreeDuels() {
        DuelEscrow escrow = new DuelEscrow(null);
        assertFalse(escrow.available());
        assertTrue(escrow.collect(A, B, 0L));
        assertFalse(escrow.collect(A, B, 5L));
    }

    @Test
    void placedBlockKeysAreDistinct() {
        assertNotEquals(DuelSession.blockKey(1, 64, 2), DuelSession.blockKey(2, 64, 1));
        assertNotEquals(DuelSession.blockKey(-1, 64, 0), DuelSession.blockKey(1, 64, 0));
        DuelSession session = new DuelSession(A, B, DuelArenaSize.CHICA, 0L, "a", null);
        session.placed(4096, 65, -3);
        assertTrue(session.wasPlaced(4096, 65, -3));
        session.broken(4096, 65, -3);
        assertFalse(session.wasPlaced(4096, 65, -3));
        assertEquals(B, session.opponent(A));
    }

    private static final class FakeBank implements DuelBank {
        private final Map<UUID, Long> balances;

        FakeBank(Map<UUID, Long> balances) {
            this.balances = new HashMap<>(balances);
        }

        @Override
        public long balance(UUID player) {
            return balances.getOrDefault(player, 0L);
        }

        @Override
        public boolean withdraw(UUID player, long amount) {
            if (balance(player) < amount) {
                return false;
            }
            balances.put(player, balance(player) - amount);
            return true;
        }

        @Override
        public boolean deposit(UUID player, long amount) {
            balances.put(player, balance(player) + amount);
            return true;
        }
    }
}
