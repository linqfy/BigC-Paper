package dev.linqfy.bigCasares.modules.mythicdungeons;

import java.util.random.RandomGenerator;

/**
 * The notes of the Mandinga guitar (no server types): a minor pentatonic scale over the two octaves of a note block,
 * as note-block pitches. Each shot picks a note different from the previous one, mostly a step up or down the scale
 * so a burst of shots sounds like a melody.
 */
public final class MandingaGuitarScale {

    /** Semitones above F#3 (the note-block range is 0..24). */
    private static final int[] SEMITONES = {0, 3, 5, 7, 10, 12, 15, 17, 19, 22, 24};

    private MandingaGuitarScale() {
    }

    public static int size() {
        return SEMITONES.length;
    }

    /** Note-block pitch of a scale degree: {@code 2^((semitone - 12) / 12)}, between 0.5 and 2.0. */
    public static float pitch(int degree) {
        int semitone = SEMITONES[Math.floorMod(degree, SEMITONES.length)];
        return (float) Math.pow(2.0, (semitone - 12) / 12.0);
    }

    /**
     * The next degree after {@code previous} ({@code -1} for the first shot): a step of one or two degrees up or
     * down, bouncing off the ends of the scale; never the same degree.
     */
    public static int next(int previous, RandomGenerator random) {
        if (previous < 0 || previous >= SEMITONES.length) {
            return random.nextInt(SEMITONES.length);
        }
        int step = 1 + random.nextInt(2);
        int direction = random.nextBoolean() ? 1 : -1;
        int next = previous + step * direction;
        if (next < 0 || next >= SEMITONES.length) {
            next = previous - step * direction;
        }
        return next;
    }
}
