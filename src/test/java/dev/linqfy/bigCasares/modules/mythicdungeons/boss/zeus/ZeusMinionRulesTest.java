package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZeusMinionRulesTest {

    @Test
    void shockParalysesOneSecondThenGivesABriefImmunity() {
        ZeusShockLedger ledger = new ZeusShockLedger();
        UUID player = UUID.randomUUID();
        assertTrue(ledger.shock(player, 100));
        assertTrue(ledger.paralysed(player));
        assertFalse(ledger.shock(player, 105), "no stacking while paralysed");
        assertTrue(ledger.release(100 + ZeusShockLedger.PARALYSIS_TICKS - 1).isEmpty());
        assertEquals(List.of(player), ledger.release(100 + ZeusShockLedger.PARALYSIS_TICKS));
        assertFalse(ledger.paralysed(player));
        int free = 100 + ZeusShockLedger.PARALYSIS_TICKS;
        assertFalse(ledger.shock(player, free + 1), "immune right after");
        assertTrue(ledger.shock(player, free + ZeusShockLedger.IMMUNITY_TICKS));
        assertEquals(Set.of(player), ledger.releaseAll());
        assertTrue(ledger.releaseAll().isEmpty());
        assertEquals(20, ZeusShockLedger.PARALYSIS_TICKS, "one second");
    }

    @Test
    void aParalysedPlayerWhoLeftTheRoomIsReleasedAtOnce() {
        ZeusShockLedger ledger = new ZeusShockLedger();
        UUID gone = UUID.randomUUID();
        UUID stays = UUID.randomUUID();
        assertTrue(ledger.shock(gone, 100));
        assertTrue(ledger.shock(stays, 100));
        assertEquals(List.of(gone), ledger.releaseAbsent(List.of(stays), 105));
        assertFalse(ledger.paralysed(gone));
        assertTrue(ledger.paralysed(stays));
        assertFalse(ledger.shock(gone, 106), "the usual immunity after a release");
        assertTrue(ledger.releaseAbsent(List.of(stays), 107).isEmpty());
        assertEquals(List.of(stays), ledger.release(100 + ZeusShockLedger.PARALYSIS_TICKS));
    }

    @Test
    void cloudsHaveHalfAZombieAndTopUpOneAtATime() {
        assertEquals(10.0, ZeusCloudRoster.CLOUD_HEALTH);
        assertEquals(10.0, ZeusCloudRoster.remaining(ZeusCloudRoster.BUFFER_HEALTH));
        assertEquals(4.0, ZeusCloudRoster.remaining(ZeusCloudRoster.BUFFER_HEALTH - 6.0));
        assertEquals(0.0, ZeusCloudRoster.remaining(ZeusCloudRoster.BUFFER_HEALTH - 25.0));

        assertEquals(3, ZeusCloudRoster.target(1));
        assertEquals(3, ZeusCloudRoster.target(0));
        assertEquals(6, ZeusCloudRoster.target(4));
        assertEquals(6, ZeusCloudRoster.target(20));

        ZeusCloudRoster roster = new ZeusCloudRoster(0);
        assertEquals(4, roster.toSpawn(0, 0, 2, true));
        assertEquals(0, roster.toSpawn(10, 4, 2, false));
        assertEquals(0, roster.toSpawn(20, 2, 2, false), "waits before refilling");
        assertEquals(1, roster.toSpawn(10 + ZeusCloudRoster.REFILL_TICKS, 2, 2, false));
        assertEquals(0, roster.toSpawn(11 + ZeusCloudRoster.REFILL_TICKS, 3, 2, false));
        assertEquals(1, roster.toSpawn(10 + 2 * ZeusCloudRoster.REFILL_TICKS, 3, 2, false));
    }

    @Test
    void gridMapsWorldPointsToTiles() {
        ZeusGrid grid = new ZeusGrid(10.5, -4.5, 3, 6);
        assertEquals(13, grid.side());
        assertEquals(new ZeusCell(0, 0), grid.cellAt(10.5, -4.5));
        assertEquals(new ZeusCell(0, 0), grid.cellAt(11.9, -3.1));
        assertEquals(new ZeusCell(1, -1), grid.cellAt(12.1, -6.1));
        assertEquals(16.5, grid.centerX(new ZeusCell(2, 0)));
        assertFalse(grid.contains(new ZeusCell(7, 0)));
        assertEquals(new ZeusCell(6, -6), grid.clamp(new ZeusCell(9, -40)));
        assertEquals(169, grid.cells().size());
        assertEquals(2, new ZeusCell(0, 0).chebyshev(new ZeusCell(-2, 1)));
    }
}
