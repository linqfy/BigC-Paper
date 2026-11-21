package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IblisRulesTest {

    private static final double MAX = 1000.0;

    @Test
    void smokeTakesMeleeButNotArrowsNorFire() {
        IblisRules rules = new IblisRules();
        assertEquals(1, rules.phase());
        IblisRules.Decision melee = rules.admit(IblisRules.HitKind.MELEE, 8.0, 900.0, MAX, false);
        assertEquals(IblisRules.Verdict.ADMITTED, melee.verdict());
        assertEquals(8.0, melee.damage());
        assertEquals(IblisRules.Verdict.THROUGH_SMOKE,
            rules.admit(IblisRules.HitKind.PROJECTILE, 8.0, 900.0, MAX, false).verdict());
        IblisRules.Decision fire = rules.admit(IblisRules.HitKind.FIRE, 3.0, 900.0, MAX, false);
        assertEquals(IblisRules.Verdict.FIRE_IMMUNE, fire.verdict());
        assertEquals(0.0, fire.damage());
        assertEquals(IblisRules.Verdict.ENVIRONMENT,
            rules.admit(IblisRules.HitKind.ENVIRONMENT, 4.0, 900.0, MAX, false).verdict(), "no suffocation damage");
        assertEquals(5.0, rules.admit(IblisRules.HitKind.OTHER, 5.0, 900.0, MAX, false).damage());
    }

    @Test
    void theHitThatCrossesHalfHealthIsCutSoSolomonAlwaysAppears() {
        IblisRules rules = new IblisRules();
        IblisRules.Decision big = rules.admit(IblisRules.HitKind.MELEE, 50.0, 510.0, MAX, false);
        assertEquals(10.0, big.damage(), 1.0e-9);
        assertNull(rules.update(1, 0.501), "not yet");
        assertEquals(IblisRules.Stage.SOLOMON, rules.update(2, 0.5 + 1.0e-12), "floating rest still counts");
        assertEquals(2, rules.phase());
    }

    @Test
    void solomonSceneIsSafeAndThenTheVesselsStart() {
        IblisRules rules = new IblisRules();
        rules.update(10, 0.4);
        assertEquals(IblisRules.Verdict.SCENE, rules.admit(IblisRules.HitKind.MELEE, 8.0, 400.0, MAX, true).verdict());
        assertNull(rules.update(10 + IblisRules.SOLOMON_TICKS - 1, 0.4));
        assertEquals(IblisRules.Stage.VESSELS, rules.update(10 + IblisRules.SOLOMON_TICKS, 0.4));
        assertEquals(2, rules.phase());
    }

    @Test
    void inPhaseTwoOnlyATrappedIblisBleeds() {
        IblisRules rules = new IblisRules();
        rules.update(1, 0.5);
        rules.update(1 + IblisRules.SOLOMON_TICKS, 0.5);
        IblisRules.Decision free = rules.admit(IblisRules.HitKind.MELEE, 8.0, 400.0, MAX, false);
        assertEquals(IblisRules.Verdict.UNTOUCHABLE, free.verdict());
        assertEquals(0.0, free.damage());
        IblisRules.Decision trapped = rules.admit(IblisRules.HitKind.MELEE, 8.0, 400.0, MAX, true);
        assertEquals(IblisRules.Verdict.ADMITTED, trapped.verdict());
        assertEquals(8.0 * IblisRules.TRAPPED_MULTIPLIER, trapped.damage(), 1.0e-9);
        assertEquals(IblisRules.Verdict.ADMITTED,
            rules.admit(IblisRules.HitKind.PROJECTILE, 5.0, 400.0, MAX, true).verdict(), "arrows work in the jar");
        assertEquals(0.0, rules.admit(IblisRules.HitKind.MELEE, Double.NaN, 400.0, MAX, true).damage());
    }
}
