package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EnmaFightRulesTest {

    @Test
    void enmaIsUntouchableWhileTheMirrorStands() {
        EnmaFightRules rules = new EnmaFightRules();
        for (int tick = 1; tick < 500; tick++) {
            assertNull(rules.update(tick, false));
        }
        assertEquals(EnmaFightRules.Stage.MIRROR, rules.stage());
        assertEquals(1, rules.phase());
        assertEquals(EnmaFightRules.Verdict.MIRROR_SHIELD, rules.admit(true));
    }

    @Test
    void breakingTheMirrorOpensTheJudgementAfterTheTransition() {
        EnmaFightRules rules = new EnmaFightRules();
        assertEquals(EnmaFightRules.Stage.SHATTER, rules.update(100, true));
        assertEquals(1, rules.phase());
        assertEquals(EnmaFightRules.Verdict.TRANSITION, rules.admit(true));
        assertNull(rules.update(100 + EnmaFightRules.SHATTER_TICKS - 1, true));
        assertEquals(EnmaFightRules.Stage.JUDGEMENT, rules.update(100 + EnmaFightRules.SHATTER_TICKS, true));
        assertEquals(2, rules.phase());
        assertEquals(EnmaFightRules.Verdict.ADMITTED, rules.admit(true));
        assertNull(rules.update(1000, true));
    }

    @Test
    void damageWithoutAnAttackerNeverCounts() {
        EnmaFightRules rules = new EnmaFightRules();
        rules.update(1, true);
        rules.update(1 + EnmaFightRules.SHATTER_TICKS, true);
        assertEquals(EnmaFightRules.Verdict.NO_ATTACKER, rules.admit(false));
    }
}
