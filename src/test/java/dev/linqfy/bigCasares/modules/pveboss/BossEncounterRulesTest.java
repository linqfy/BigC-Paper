package dev.linqfy.bigCasares.modules.pveboss;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BossEncounterRulesTest {

    private static final BossMusicTrack ONE = new BossMusicTrack("phase-1", "a", "minecraft:music_disc.5");
    private static final BossMusicTrack TWO = new BossMusicTrack("phase-2", "b", "minecraft:music_disc.5");

    @Test
    void keepsTheStableMarkerKeysOfTheOriginalBosses() {
        assertEquals("pve_boss_id", BossEncounterRules.markerKeyName("abyss-guardian", false));
        assertEquals("sahur_boss_id", BossEncounterRules.markerKeyName("tung-tung-sahur", false));
        assertEquals("sahur_boss_id", BossEncounterRules.markerKeyName("tung-tung-sahur", true));
    }

    @Test
    void derivesADistinctMarkerKeyForEveryScriptedBoss() {
        assertEquals("myth_zeus_boss_id", BossEncounterRules.markerKeyName("myth-zeus", true));
        assertEquals("myth_hades_2_boss_id", BossEncounterRules.markerKeyName(" Myth-Hades.2 ", true));
        assertEquals("odin_boss_id", BossEncounterRules.markerKeyName("odin", true));
    }

    @Test
    void rejectsScriptedIdsThatWouldReuseAReservedKeyOrAreBlank() {
        assertThrows(IllegalArgumentException.class, () -> BossEncounterRules.markerKeyName("pve", true));
        assertThrows(IllegalArgumentException.class, () -> BossEncounterRules.markerKeyName("sahur", true));
        assertThrows(IllegalArgumentException.class, () -> BossEncounterRules.markerKeyName("  ", true));
        assertThrows(NullPointerException.class, () -> BossEncounterRules.markerKeyName(null, true));
    }

    @Test
    void anOverriddenPhaseReplacesTheHealthPhaseInsideTheSupportedRange() {
        assertEquals(3, BossEncounterRules.resolvePhase(OptionalInt.empty(), 3));
        assertEquals(1, BossEncounterRules.resolvePhase(OptionalInt.of(1), 3));
        assertEquals(2, BossEncounterRules.resolvePhase(OptionalInt.of(2), 1));
        assertEquals(1, BossEncounterRules.resolvePhase(OptionalInt.of(0), 2));
        assertEquals(3, BossEncounterRules.resolvePhase(OptionalInt.of(9), 1));
    }

    @Test
    void onlyFinitePositiveDamageReachesTheHealthPool() {
        assertEquals(12.5, BossEncounterRules.admittedDamage(12.5));
        assertEquals(0.0, BossEncounterRules.admittedDamage(0.0));
        assertEquals(0.0, BossEncounterRules.admittedDamage(-4.0));
        assertEquals(0.0, BossEncounterRules.admittedDamage(Double.NaN));
        assertEquals(0.0, BossEncounterRules.admittedDamage(Double.POSITIVE_INFINITY));
    }

    @Test
    void anOverriddenPhaseWithoutItsOwnTrackFallsBackInsteadOfThrowing() {
        Map<Integer, BossMusicTrack> tracks = Map.of(1, ONE, 2, TWO);

        assertEquals(Optional.of(TWO), BossEncounterRules.musicFor(tracks, 2));
        assertEquals(Optional.of(TWO), BossEncounterRules.musicFor(tracks, 3));
        assertEquals(Optional.of(ONE), BossEncounterRules.musicFor(Map.of(1, ONE), 2));
        assertEquals(Optional.of(TWO), BossEncounterRules.musicFor(Map.of(2, TWO), 1));
        assertEquals(Optional.empty(), BossEncounterRules.musicFor(Map.of(), 1));
    }
}
