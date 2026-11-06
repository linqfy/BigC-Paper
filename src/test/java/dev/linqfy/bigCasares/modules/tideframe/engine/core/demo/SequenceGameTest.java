package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SequenceGameTest {
    @Test void onlyCorrectLetterAdvancesAndThreeFadeFramesFinishBeforeNextInput() {
        var game = new SequenceGame();
        assertEquals("sequence_demo", game.frameId());
        assertFalse(game.click("B"));
        assertFalse(game.click(null));
        assertEquals("sequence_demo", game.frameId());
        assertTrue(game.click("letter_a"));
        assertEquals("seq_f0_1", game.frameId());
        assertFalse(game.click("C"), "Input during fade must not skip the next letter");
        assertEquals("seq_f0_2", game.tick());
        assertEquals("seq_f0_3", game.tick());
        assertEquals("seq_s1", game.tick());
        assertFalse(game.finished());
        assertTrue(game.click("C"));
    }

    @Test void completeSequenceDisappearsInOrderAndResetRestoresInitialState() {
        var game = new SequenceGame();
        for (String letter : new String[]{"A", "C", "B", "D"}) {
            assertTrue(game.click(letter));
            game.tick(); game.tick(); game.tick();
        }
        assertTrue(game.finished());
        assertEquals("seq_s4", game.frameId());
        assertEquals("seq_s4", game.tick());
        assertFalse(game.click("A"));
        game.reset();
        assertFalse(game.finished());
        assertEquals("sequence_demo", game.frameId());
    }
}
