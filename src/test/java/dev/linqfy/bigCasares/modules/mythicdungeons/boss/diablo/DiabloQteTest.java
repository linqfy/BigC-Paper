package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiabloQteTest {

    private static final DiabloQte.Settings SETTINGS = new DiabloQte.Settings(3, 3, 60, 5);

    /** Aprieta y suelta {@code key}: dos ticks; devuelve el resultado del flanco de subida. */
    private static DiabloQte.Outcome tap(DiabloQte qte, long[] tick, QteKey key) {
        DiabloQte.Outcome outcome = qte.tick(++tick[0], EnumSet.of(key));
        qte.tick(++tick[0], Set.of());
        return outcome;
    }

    @Test
    void threeDifferentPromptsCompleteTheQte() {
        DiabloQte qte = new DiabloQte(SETTINGS, false, new SplittableRandom(7));
        long[] tick = {0};
        qte.begin(tick[0]);
        qte.tick(++tick[0], Set.of());
        Set<QteKey> seen = new HashSet<>();
        DiabloQte.Outcome last = DiabloQte.Outcome.NONE;
        for (int round = 0; round < 3; round++) {
            QteKey key = qte.prompt();
            assertTrue(seen.add(key), "each prompt is a different key");
            assertEquals(DiabloQte.Outcome.PROGRESS, tap(qte, tick, key));
            assertEquals(DiabloQte.Outcome.PROGRESS, tap(qte, tick, key));
            last = tap(qte, tick, key);
            if (round < 2) {
                assertEquals(DiabloQte.Outcome.PROMPT_CLEARED, last);
            }
        }
        assertEquals(DiabloQte.Outcome.COMPLETED, last);
        assertTrue(qte.completed());
        assertEquals(DiabloQte.Outcome.NONE, qte.tick(++tick[0], EnumSet.of(QteKey.JUMP)));
    }

    @Test
    void holdingTheKeyCountsOnlyOnce() {
        DiabloQte qte = new DiabloQte(SETTINGS, false, new SplittableRandom(1));
        qte.begin(0);
        QteKey key = qte.prompt();
        qte.tick(1, Set.of());
        assertEquals(DiabloQte.Outcome.PROGRESS, qte.tick(2, EnumSet.of(key)));
        assertEquals(DiabloQte.Outcome.NONE, qte.tick(3, EnumSet.of(key)));
        assertEquals(DiabloQte.Outcome.NONE, qte.tick(4, EnumSet.of(key)));
        assertEquals(1, qte.count());
    }

    @Test
    void aKeyAlreadyDownWhenThePromptAppearsDoesNotCount() {
        DiabloQte qte = new DiabloQte(SETTINGS, false, new SplittableRandom(2));
        qte.begin(0);
        QteKey key = qte.prompt();
        assertEquals(DiabloQte.Outcome.NONE, qte.tick(1, EnumSet.of(key)));
        assertEquals(0, qte.count());
    }

    @Test
    void wrongKeyAfterTheGraceRestartsTheSamePrompt() {
        DiabloQte qte = new DiabloQte(SETTINGS, false, new SplittableRandom(3));
        qte.begin(0);
        QteKey key = qte.prompt();
        QteKey wrong = key == QteKey.JUMP ? QteKey.SNEAK : QteKey.JUMP;
        qte.tick(1, Set.of());
        qte.tick(2, EnumSet.of(key));
        qte.tick(3, Set.of());
        assertEquals(1, qte.count());
        assertEquals(DiabloQte.Outcome.NONE, qte.tick(4, EnumSet.of(wrong)), "inside the grace");
        qte.tick(5, Set.of());
        assertEquals(DiabloQte.Outcome.FAILED_WRONG, qte.tick(6, EnumSet.of(wrong)));
        assertEquals(0, qte.count());
        assertEquals(key, qte.prompt());
        assertEquals(60, qte.ticksLeft(6));
    }

    @Test
    void timeoutRestartsThePrompt() {
        DiabloQte qte = new DiabloQte(SETTINGS, false, new SplittableRandom(4));
        qte.begin(0);
        QteKey key = qte.prompt();
        qte.tick(1, Set.of());
        qte.tick(2, EnumSet.of(key));
        assertEquals(DiabloQte.Outcome.NONE, qte.tick(59, Set.of()));
        assertEquals(DiabloQte.Outcome.FAILED_TIMEOUT, qte.tick(60, Set.of()));
        assertEquals(0, qte.count());
        assertEquals(key, qte.prompt());
        assertFalse(qte.completed());
    }

    @Test
    void bedrockAlternativesAcceptJumpOrSneakAndIgnoreStrayDirections() {
        DiabloQte qte = new DiabloQte(new DiabloQte.Settings(1, 2, 60, 0), true, new SplittableRandom(5));
        qte.begin(0);
        QteKey key = qte.prompt();
        qte.tick(1, Set.of());
        QteKey accepted = key.alternative() == null ? key : key.alternative();
        assertEquals(DiabloQte.Outcome.PROGRESS, qte.tick(2, EnumSet.of(accepted)));
        qte.tick(3, Set.of());
        QteKey stray = EnumSet.of(QteKey.LEFT, QteKey.RIGHT, QteKey.FORWARD).stream()
            .filter(candidate -> candidate != key).findFirst().orElseThrow();
        assertEquals(DiabloQte.Outcome.NONE, qte.tick(4, EnumSet.of(stray)));
        qte.tick(5, Set.of());
        assertEquals(DiabloQte.Outcome.COMPLETED, qte.tick(6, EnumSet.of(accepted)));
    }

    @Test
    void mashingEveryKeyNeverCompletesAndFails() {
        for (boolean bedrock : new boolean[] {false, true}) {
            DiabloQte qte = new DiabloQte(SETTINGS, bedrock, new SplittableRandom(11));
            qte.begin(0);
            boolean failed = false;
            for (long tick = 1; tick <= 2000; tick++) {
                DiabloQte.Outcome outcome = qte.tick(tick, tick % 2 == 0 ? EnumSet.allOf(QteKey.class) : Set.of());
                assertNotEquals(DiabloQte.Outcome.COMPLETED, outcome);
                assertNotEquals(DiabloQte.Outcome.PROGRESS, outcome);
                failed |= outcome == DiabloQte.Outcome.FAILED_WRONG;
            }
            assertFalse(qte.completed());
            assertEquals(0, qte.cleared());
            assertTrue(failed, "extra keys after the grace are a wrong press");
        }
    }

    @Test
    void thePromptPlusAnotherKeyIsAWrongPress() {
        DiabloQte qte = new DiabloQte(SETTINGS, false, new SplittableRandom(3));
        qte.begin(0);
        QteKey key = qte.prompt();
        QteKey other = key == QteKey.JUMP ? QteKey.SNEAK : QteKey.JUMP;
        qte.tick(1, Set.of());
        assertEquals(DiabloQte.Outcome.NONE, qte.tick(2, EnumSet.of(key, other)), "inside the grace");
        assertEquals(0, qte.count());
        qte.tick(8, Set.of());
        assertEquals(DiabloQte.Outcome.FAILED_WRONG, qte.tick(9, EnumSet.of(key, other)));
        qte.tick(10, Set.of());
        assertEquals(DiabloQte.Outcome.PROGRESS, qte.tick(11, EnumSet.of(key)), "a clean press still counts");
    }

    @Test
    void javaPlayersDoNotGetAlternatives() {
        for (long seed = 0; seed < 40; seed++) {
            DiabloQte qte = new DiabloQte(SETTINGS, false, new SplittableRandom(seed));
            qte.begin(0);
            QteKey key = qte.prompt();
            if (key.alternative() == null) {
                continue;
            }
            qte.tick(1, Set.of());
            assertEquals(DiabloQte.Outcome.NONE, qte.tick(2, EnumSet.of(key.alternative())), "grace");
            qte.tick(3, Set.of());
            qte.tick(10, EnumSet.of(key.alternative()));
            assertEquals(0, qte.count());
            return;
        }
    }

    @Test
    void everyPromptDiffersFromThePreviousOnes() {
        for (long seed = 0; seed < 50; seed++) {
            DiabloQte qte = new DiabloQte(new DiabloQte.Settings(7, 1, 60, 0), false, new SplittableRandom(seed));
            qte.begin(0);
            Set<QteKey> seen = EnumSet.noneOf(QteKey.class);
            long tick = 0;
            qte.tick(++tick, Set.of());
            for (int round = 0; round < 7; round++) {
                QteKey key = qte.prompt();
                assertTrue(seen.add(key));
                qte.tick(++tick, EnumSet.of(key));
                qte.tick(++tick, Set.of());
            }
            assertTrue(qte.completed());
            assertEquals(EnumSet.allOf(QteKey.class), seen);
        }
    }

    @Test
    void alternativesMapDirectionsToJumpOrSneak() {
        for (QteKey key : QteKey.values()) {
            if (key == QteKey.JUMP || key == QteKey.SNEAK) {
                assertEquals(null, key.alternative());
            } else {
                assertTrue(key.alternative() == QteKey.JUMP || key.alternative() == QteKey.SNEAK);
                assertNotEquals(key, key.alternative());
            }
        }
    }
}
