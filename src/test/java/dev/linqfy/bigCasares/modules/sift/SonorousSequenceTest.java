package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SonorousSequenceTest {

    private static Optional<SonorousSequence.Match<String>> playAll(SonorousSequence<String> sequence, int... sounds) {
        Optional<SonorousSequence.Match<String>> last = Optional.empty();
        for (int sound : sounds) {
            last = sequence.play("note" + sound, sound);
        }
        return last;
    }

    @Test
    void theOpeningSequenceOpens() {
        SonorousSequence<String> sequence = new SonorousSequence<>();
        var match = playAll(sequence, 1, 3, 7, 6, 5, 2, 4, 8).orElseThrow();
        assertEquals(SonorousSequence.Type.OPENING, match.type());
        assertEquals(8, match.notes().size());
        assertEquals("note1", match.notes().getFirst().position());
    }

    @Test
    void theReverseSequenceCloses() {
        assertEquals(SonorousSequence.Type.CLOSING,
            playAll(new SonorousSequence<>(), 8, 4, 2, 5, 6, 7, 3, 1).orElseThrow().type());
    }

    @Test
    void leadingWrongNotesAreForgottenButMistakesInsideRestart() {
        assertTrue(playAll(new SonorousSequence<>(), 2, 2, 1, 3, 7, 6, 5, 2, 4, 8).isPresent());
        assertTrue(playAll(new SonorousSequence<>(), 1, 3, 7, 6, 4, 2, 4, 8).isEmpty());
    }

    @Test
    void aMatchClearsTheBuffer() {
        SonorousSequence<String> sequence = new SonorousSequence<>();
        playAll(sequence, 1, 3, 7, 6, 5, 2, 4, 8);
        assertTrue(sequence.play("again", 8).isEmpty());
    }
}
