package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ZeusFightRulesTest {

    private static final double MAX = 1000.0;

    @Test
    void phaseOneTakesOnlyMeleeAndStopsAtHalfHealth() {
        ZeusFightRules rules = new ZeusFightRules();
        assertEquals(1, rules.phase());
        assertEquals(ZeusFightRules.Verdict.ADMITTED,
            rules.admit(ZeusFightRules.HitKind.MELEE, 8.0, 2.5, 900.0, MAX, false).verdict());
        assertEquals(8.0, rules.admit(ZeusFightRules.HitKind.MELEE, 8.0, 2.5, 900.0, MAX, false).damage());
        assertEquals(new ZeusFightRules.Decision(ZeusFightRules.Verdict.PROJECTILE_IMMUNE, 0.0),
            rules.admit(ZeusFightRules.HitKind.PROJECTILE, 9.0, 20.0, 900.0, MAX, false));
        assertEquals(ZeusFightRules.Verdict.NOT_MELEE,
            rules.admit(ZeusFightRules.HitKind.OTHER, 5.0, 1.0, 900.0, MAX, false).verdict());
        // un "golpe" de jugador que llega desde lejos no es cuerpo a cuerpo
        assertEquals(ZeusFightRules.Verdict.NOT_MELEE,
            rules.admit(ZeusFightRules.HitKind.MELEE, 5.0, 15.0, 900.0, MAX, false).verdict());
        // no pasa de la mitad en un golpe
        assertEquals(3.0, rules.admit(ZeusFightRules.HitKind.MELEE, 8.0, 2.0, 503.0, MAX, false).damage());
        assertEquals(ZeusFightRules.Verdict.SHIELDED,
            rules.admit(ZeusFightRules.HitKind.MELEE, 8.0, 2.0, 500.0, MAX, false).verdict());
    }

    @Test
    void halfHealthStartsAnInvulnerableTransitionThenTheSpearPhase() {
        ZeusFightRules rules = new ZeusFightRules();
        assertNull(rules.update(10, 0.6));
        assertEquals(ZeusFightRules.Stage.TRANSITION, rules.update(11, 0.5));
        assertEquals(2, rules.phase());
        assertEquals(ZeusFightRules.Verdict.SHIELDED,
            rules.admit(ZeusFightRules.HitKind.MELEE, 8.0, 2.0, 500.0, MAX, true).verdict());
        assertNull(rules.update(11 + ZeusFightRules.TRANSITION_TICKS - 1, 0.5));
        assertEquals(ZeusFightRules.Stage.SPEARS, rules.update(11 + ZeusFightRules.TRANSITION_TICKS, 0.5));
        assertNull(rules.update(500, 0.2));
        assertEquals(2, rules.phase());
    }

    @Test
    void phaseTwoOnlyHurtsWithASpearStuck() {
        ZeusFightRules rules = new ZeusFightRules();
        rules.update(0, 0.5);
        rules.update(ZeusFightRules.TRANSITION_TICKS, 0.5);
        assertEquals(ZeusFightRules.Verdict.SHIELDED,
            rules.admit(ZeusFightRules.HitKind.MELEE, 8.0, 2.0, 400.0, MAX, false).verdict());
        assertEquals(8.0, rules.admit(ZeusFightRules.HitKind.MELEE, 8.0, 2.0, 400.0, MAX, true).damage());
        assertEquals(ZeusFightRules.Verdict.PROJECTILE_IMMUNE,
            rules.admit(ZeusFightRules.HitKind.PROJECTILE, 8.0, 2.0, 400.0, MAX, true).verdict());
        assertEquals(0.0, rules.admit(ZeusFightRules.HitKind.MELEE, Double.NaN, 2.0, 400.0, MAX, true).damage());
    }
}
