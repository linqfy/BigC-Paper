package dev.linqfy.bigCasares.modules.alexscaves.integration;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossFightTrackerTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();
    private final Map<UUID, Double> table = new LinkedHashMap<>();

    @Test
    void ranksTopThreeByDamage() {
        BossFightTracker.record(table, a, 10);
        BossFightTracker.record(table, b, 30);
        BossFightTracker.record(table, c, 20);
        BossFightTracker.record(table, d, 5);
        BossFightTracker.record(table, a, 15);
        List<BossFightTracker.Standing> standings = BossFightTracker.standings(table, 0);
        assertEquals(List.of(b, a, c), standings.stream().map(BossFightTracker.Standing::playerId).toList());
        assertEquals(List.of(1, 2, 3), standings.stream().map(BossFightTracker.Standing::placement).toList());
        assertEquals(25, standings.get(1).damage());
    }

    @Test
    void firstToHitWinsTies() {
        BossFightTracker.record(table, b, 10);
        BossFightTracker.record(table, a, 10);
        assertEquals(b, BossFightTracker.standings(table, 0).get(0).playerId());
    }

    @Test
    void minimumDamageKeepsTapsOffThePodium() {
        BossFightTracker.record(table, a, 100);
        BossFightTracker.record(table, b, 1);
        List<BossFightTracker.Standing> standings = BossFightTracker.standings(table, 5);
        assertEquals(1, standings.size());
        assertEquals(a, standings.get(0).playerId());
    }

    @Test
    void ignoresNonPositiveNonFiniteAndSelfKillDamage() {
        BossFightTracker.record(table, a, 0);
        BossFightTracker.record(table, a, -4);
        BossFightTracker.record(table, a, Double.NaN);
        BossFightTracker.record(table, a, Double.POSITIVE_INFINITY);
        BossFightTracker.record(table, a, 1.0E6);
        assertTrue(table.isEmpty());
    }

    @Test
    void playersAreBounded() {
        for (int i = 0; i < BossFightTracker.MAX_PLAYERS + 10; i++) {
            BossFightTracker.record(table, UUID.randomUUID(), 1);
        }
        assertEquals(BossFightTracker.MAX_PLAYERS, table.size());
        BossFightTracker.record(table, UUID.randomUUID(), 99);
        assertEquals(BossFightTracker.MAX_PLAYERS, table.size());
    }

    @Test
    void tableSurvivesEncodeDecodeAndSkipsGarbage() {
        BossFightTracker.record(table, a, 12.5);
        BossFightTracker.record(table, b, 3);
        Map<UUID, Double> decoded = BossFightTracker.decode(BossFightTracker.encode(table));
        assertEquals(table, decoded);
        assertEquals(List.of(a, b), List.copyOf(decoded.keySet()));
        assertTrue(BossFightTracker.decode(null).isEmpty());
        assertTrue(BossFightTracker.decode("junk;=5;x=y;" + a + "=NaN;" + b + "=-1").isEmpty());
        assertEquals(1, BossFightTracker.decode("junk;" + a + "=7").size());
    }

    @Test
    void onlyABossCancelledHitThatStartedTheDeathAnimationIsAFinalBlow() {
        // The boss cancels the lethal blow and drops to 1 health.
        assertTrue(BossFightTracker.enteredDying(true, 40, 1.0, 50));
        // Another plugin cancelled it (health untouched), or the hit was not lethal, or it was not cancelled.
        assertFalse(BossFightTracker.enteredDying(true, 40, 40, 50));
        assertFalse(BossFightTracker.enteredDying(true, 40, 1.0, 10));
        assertFalse(BossFightTracker.enteredDying(false, 40, 1.0, 50));
        // Already dying (1 health): later taps never count.
        assertFalse(BossFightTracker.enteredDying(true, 1.0, 1.0, 5));
    }

    @Test
    void onlyModBossesCountAndTamedTremorzillaDoesNot() {
        assertTrue(BossIntegrationRules.counts("luxtructosaurus", false));
        assertTrue(BossIntegrationRules.counts("tremorzilla", false));
        assertFalse(BossIntegrationRules.counts("tremorzilla", true));
        assertTrue(BossIntegrationRules.counts("forsaken", true));
        assertFalse(BossIntegrationRules.counts("teletor", false));
    }
}
