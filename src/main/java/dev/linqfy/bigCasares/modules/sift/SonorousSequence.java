package dev.linqfy.bigCasares.modules.sift;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The note buffer of one Sonorous console (port of the mod's {@code SonorousConsoles}):
 * the last eight notes struck on the console are compared with the opening
 * sequence {@code 1 3 7 6 5 2 4 8} and its reverse, which closes the portal.
 *
 * @param <P> note-block position type
 */
final class SonorousSequence<P> {

    static final int[] OPENING = {1, 3, 7, 6, 5, 2, 4, 8};
    static final int[] CLOSING = reverse(OPENING);

    enum Type { OPENING, CLOSING }

    record Note<P>(P position, int sound) {
        Note {
            if (sound < 1 || sound > 8) {
                throw new IllegalArgumentException("Sonorous sounds are numbered 1-8, got " + sound);
            }
        }
    }

    record Match<P>(Type type, List<Note<P>> notes) {
    }

    private final Deque<Note<P>> buffer = new ArrayDeque<>(OPENING.length + 1);

    /** Records a struck note; returns the completed sequence and clears the buffer on a match. */
    Optional<Match<P>> play(P position, int sound) {
        buffer.addLast(new Note<>(position, sound));
        while (buffer.size() > OPENING.length) {
            buffer.removeFirst();
        }
        Type type = matches(OPENING) ? Type.OPENING : matches(CLOSING) ? Type.CLOSING : null;
        if (type == null) {
            return Optional.empty();
        }
        List<Note<P>> notes = List.copyOf(new ArrayList<>(buffer));
        buffer.clear();
        return Optional.of(new Match<>(type, notes));
    }

    void reset() {
        buffer.clear();
    }

    private boolean matches(int[] target) {
        if (buffer.size() != target.length) {
            return false;
        }
        int index = 0;
        for (Note<P> note : buffer) {
            if (note.sound() != target[index++]) {
                return false;
            }
        }
        return true;
    }

    private static int[] reverse(int[] source) {
        int[] result = new int[source.length];
        for (int index = 0; index < source.length; index++) {
            result[index] = source[source.length - 1 - index];
        }
        return result;
    }
}
