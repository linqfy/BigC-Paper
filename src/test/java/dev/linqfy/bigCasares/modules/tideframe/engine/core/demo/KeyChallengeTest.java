package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.BarTrails;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyChallengeTest {
    private static void countdown(KeyChallenge game) {
        assertEquals("3", game.values().get("key"));
        while (game.phase() == KeyChallenge.Phase.COUNTDOWN) { assertEquals(KeyChallenge.Outcome.IGNORED, game.press(KeyChallenge.Key.W)); game.tick(); }
        assertEquals(KeyChallenge.Phase.PROMPT, game.phase());
    }
    private static void settle(KeyChallenge game) { while (game.phase() == KeyChallenge.Phase.GAP) game.tick(); }

    @Test void perfectPlayDefeatsTheBossWithCombos() {
        var game = new KeyChallenge(7);
        countdown(game);
        int presses = 0;
        while (!game.finished()) {
            var key = game.current();
            assertNotNull(key);
            String shown = game.values().get(key.label.length() == 1 ? "key" : "key_word");
            assertEquals(key.label, shown, "the prompt shows the expected key");
            assertEquals(KeyChallenge.Outcome.HIT, game.press(key));
            presses++;
            settle(game);
        }
        assertEquals(KeyChallenge.Phase.WON, game.phase());
        assertEquals(KeyChallenge.WIN_SCREEN, game.frameId());
        assertEquals(0, game.bossHp()); assertEquals(KeyChallenge.PLAYER_MAX, game.playerHp());
        assertTrue(presses >= 7 && presses <= 12, "presses=" + presses);
        assertEquals(String.valueOf(presses), game.values().get("best_combo"));
        assertTrue(game.score() > 0);
    }

    @Test void wrongKeysAndTimeoutsCostHeartsUntilDefeat() {
        var game = new KeyChallenge(3);
        countdown(game);
        var wrong = game.current() == KeyChallenge.Key.W ? KeyChallenge.Key.A : KeyChallenge.Key.W;
        assertEquals(KeyChallenge.Outcome.IGNORED, game.press(wrong), "one stray key is forgiven");
        assertEquals(KeyChallenge.Outcome.MISS, game.press(wrong));
        assertEquals("WRONG KEY!", game.values().get("feedback"));
        assertEquals(KeyChallenge.PLAYER_MAX - 1, game.playerHp());
        assertEquals(KeyChallenge.SCREEN, game.frameId(), "no keycap between prompts");
        settle(game);
        // Do nothing: every prompt times out.
        int guard = 0;
        while (!game.finished() && guard++ < 10_000) game.tick();
        assertEquals(KeyChallenge.Phase.LOST, game.phase());
        assertEquals(KeyChallenge.LOSE_SCREEN, game.frameId());
        assertEquals("0.0000", game.values().get("bar:hp"));
    }

    @Test void timerBarDrainsAndPromptsNeverRepeatBackToBack() {
        var game = new KeyChallenge(11);
        countdown(game);
        double before = Double.parseDouble(game.values().get("bar:timer"));
        game.tick();
        assertTrue(Double.parseDouble(game.values().get("bar:timer")) < before);
        KeyChallenge.Key last = null;
        for (int i = 0; i < 40 && !game.finished(); i++) {
            assertNotEquals(last, game.current());
            last = game.current();
            game.press(last); settle(game);
        }
    }

    @Test void keyPromptsHaveNoBossHealthBarAndTimersHaveNoTrail() {
        var settings = new BossFightSettings(java.util.List.of(KeyChallenge.Key.values()), 5, 40, 14, 30, 6, 2.0, Map.of("boss-bar", "#ff0000"),
                Map.of("boss-bar-width", 1.0), Map.of("boss-bar", true, "boss-health", true), Map.of(), Map.of());
        for (var screen : BossFightScreens.build(settings).values())
            for (var element : screen.elements()) {
                assertFalse(element.text().contains("{bar:boss") || element.text().contains("{boss_hp}"), screen.id() + " shows boss health in " + element.id());
            }
        var screen = RuntimeDemoCatalog.screens().get(KeyChallenge.KEY_SCREEN);
        assertTrue(screen.elements().stream().noneMatch(e -> e.id().equals("boss_bar") || e.id().equals("boss_hp")));
        var trails = new BarTrails();
        trails.apply(screen, Map.of("timer", "1.0000"));
        assertEquals("0.5000", trails.apply(screen, Map.of("timer", "0.5000")).get("timer"), "timers have no trail");
    }

    @Test void keycapShapeFollowsThePromptAndARealBossDeathWins() {
        var game = new KeyChallenge(9);
        assertEquals(KeyChallenge.KEY_SCREEN, game.frameId(), "countdown digits use the square cap");
        countdown(game);
        assertEquals(game.current().label.length() == 1 ? KeyChallenge.KEY_SCREEN : KeyChallenge.WIDE_SCREEN, game.frameId());
        game.syncBoss(.5);
        assertEquals(50, game.bossHp());
        game.syncBoss(0);
        while (!game.finished()) game.tick();
        assertEquals(KeyChallenge.Phase.WON, game.phase());
    }
}
