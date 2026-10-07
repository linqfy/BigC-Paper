package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosBossPhaseTest {

    private static final List<DonPollosBossPhase> PHASES = List.of(
        phase(1, 1.0, DonPollosBossPhase.BABY_ZOMBIE),
        phase(2, 0.66, DonPollosBossPhase.KEEP_AWAY),
        phase(3, 0.33, DonPollosBossPhase.BABY_ZOMBIE));

    private static DonPollosBossPhase phase(int number, double startsAt, String ai) {
        return new DonPollosBossPhase(number, "FASE " + number, startsAt, ai,
            java.util.EnumSet.of(DonPollosBossPhase.Ability.LASER), "");
    }

    @Test
    void fullHealthIsPhaseOne() {
        assertEquals(1, DonPollosBossPhase.forHealth(PHASES, 1.0).number());
        assertEquals(1, DonPollosBossPhase.forHealth(PHASES, 0.8).number());
    }

    @Test
    void phasesChangeWhenHealthDropsBelowTheirStart() {
        assertEquals(2, DonPollosBossPhase.forHealth(PHASES, 0.66).number());
        assertEquals(2, DonPollosBossPhase.forHealth(PHASES, 0.4).number());
        assertEquals(3, DonPollosBossPhase.forHealth(PHASES, 0.1).number());
    }

    @Test
    void defaultsAreChaseThenKeepAwayThenLevitateAndSummon() {
        List<DonPollosBossPhase> defaults = DonPollosBossPhase.defaults();
        assertEquals(4, defaults.size());
        DonPollosBossPhase one = DonPollosBossPhase.forHealth(defaults, 1.0);
        assertEquals("FASE 1", one.title());
        assertEquals(DonPollosBossPhase.BABY_ZOMBIE, one.ai());
        assertEquals("Un video más mi gente?", one.message());

        DonPollosBossPhase two = DonPollosBossPhase.forHealth(defaults, 0.75);
        assertEquals("FASE 2", two.title());
        assertEquals(DonPollosBossPhase.KEEP_AWAY, two.ai());
        assertTrue(two.meteors() && two.chickens());
        assertEquals("No hay salsa para ti...", two.message());
        assertEquals(1, DonPollosBossPhase.forHealth(defaults, 0.76).number());

        DonPollosBossPhase three = DonPollosBossPhase.forHealth(defaults, 0.5);
        assertEquals("FASE 3", three.title());
        assertTrue(three.summons() && three.levitate());
        assertFalse(three.meteors() || three.chickens() || three.laser());
        assertEquals("No hay picante para ti...", three.message());
        assertEquals(2, DonPollosBossPhase.forHealth(defaults, 0.51).number());

        DonPollosBossPhase last = DonPollosBossPhase.forHealth(defaults, 0.25);
        assertEquals("FASE FINAL", last.title());
        assertEquals("Me haz hecho enojar, Papi Don Pollo esta enojado", last.message());
        for (DonPollosBossPhase.Ability ability : List.of(DonPollosBossPhase.Ability.GROW,
            DonPollosBossPhase.Ability.WARDEN_BEAM, DonPollosBossPhase.Ability.HOP, DonPollosBossPhase.Ability.MEGA_JUMP,
            DonPollosBossPhase.Ability.EYE_LASERS, DonPollosBossPhase.Ability.FINAL_COLONELS)) {
            assertTrue(last.has(ability), ability.name());
        }
        assertFalse(last.summons() || last.levitate());
        assertEquals(3, DonPollosBossPhase.forHealth(defaults, 0.26).number());
    }

    @Test
    void rejectsInvalidPhases() {
        assertThrows(IllegalArgumentException.class, () -> phase(0, 1, "baby-zombie"));
        assertTrue(new DonPollosBossPhase(1, "X", 1, "baby-zombie", null, null).abilities().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> phase(1, 1.5, "baby-zombie"));
        assertThrows(IllegalArgumentException.class, () -> phase(1, 1, "volar"));
        assertThrows(IllegalArgumentException.class, () -> DonPollosBossPhase.forHealth(List.of(), 1));
    }
}
