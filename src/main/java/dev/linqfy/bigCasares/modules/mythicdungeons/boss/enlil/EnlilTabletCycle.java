package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Phase 2 (no server types). After the rage (the flood starts) three Tablets of Destiny float around Enlil and he
 * takes no damage while any of them stands. Each tablet breaks after a number of hits. When the last one breaks he
 * falls into the water and is vulnerable for {@link EnlilTuning#FALLEN_TICKS}; then he rises and, once up, the
 * tablets re-form with full hits and the cycle repeats until he dies. While shielded he keeps casting: a gust every
 * 9 s (first at 5 s) and tornadoes every 15 s (first at 10 s).
 */
public final class EnlilTabletCycle {

    public enum State { RAGING, SHIELDED, FALLING, FALLEN, RISING }

    public enum Hit { IGNORED, CRACKED, BROKEN, ALL_BROKEN }

    static final int GUST_FIRST = 100;
    static final int GUST_EVERY = 180;
    static final int TORNADO_FIRST = 200;
    static final int TORNADO_EVERY = 300;

    private final int hitsPerTablet;
    private final int[] hitsLeft;
    private State state = State.RAGING;
    private int ticksInState;
    private int cycles;

    public EnlilTabletCycle(int tablets, int hitsPerTablet) {
        if (tablets < 1 || hitsPerTablet < 1) {
            throw new IllegalArgumentException("tablets and hits must be positive");
        }
        this.hitsPerTablet = hitsPerTablet;
        this.hitsLeft = new int[tablets];
    }

    /** Advances one tick and returns what happens on it. */
    public List<EnlilCue> advance() {
        ticksInState++;
        List<EnlilCue> cues = new ArrayList<>(2);
        switch (state) {
            case RAGING -> {
                if (ticksInState >= EnlilTuning.RAGE_TICKS) {
                    form(cues);
                }
            }
            case SHIELDED -> shielded(cues);
            case FALLING -> {
                if (ticksInState >= EnlilTuning.FALL_TICKS) {
                    enter(State.FALLEN);
                    cues.add(EnlilCue.FALLEN);
                }
            }
            case FALLEN -> {
                if (ticksInState >= EnlilTuning.FALLEN_TICKS) {
                    enter(State.RISING);
                    cues.add(EnlilCue.RISE);
                }
            }
            case RISING -> {
                if (ticksInState >= EnlilTuning.RISE_TICKS) {
                    form(cues);
                }
            }
        }
        return cues;
    }

    private void shielded(List<EnlilCue> cues) {
        int t = ticksInState;
        if (fires(t, GUST_FIRST, GUST_EVERY)) {
            cues.add(EnlilCue.GUST_WARN);
        } else if (fires(t, GUST_FIRST + EnlilTuning.GUST_WARN_TICKS, GUST_EVERY)) {
            cues.add(EnlilCue.GUST);
        }
        if (fires(t, TORNADO_FIRST, TORNADO_EVERY)) {
            cues.add(EnlilCue.TORNADO_WARN);
        } else if (fires(t, TORNADO_FIRST + EnlilTuning.TORNADO_WARN_TICKS, TORNADO_EVERY)) {
            cues.add(EnlilCue.TORNADO);
        }
    }

    private static boolean fires(int t, int first, int every) {
        return t >= first && (t - first) % every == 0;
    }

    private void form(List<EnlilCue> cues) {
        Arrays.fill(hitsLeft, hitsPerTablet);
        cycles++;
        enter(State.SHIELDED);
        cues.add(EnlilCue.TABLETS_FORM);
    }

    /** A hit on tablet {@code index}; only counts while he is shielded and that tablet still stands. */
    public Hit hit(int index) {
        if (state != State.SHIELDED || index < 0 || index >= hitsLeft.length || hitsLeft[index] <= 0) {
            return Hit.IGNORED;
        }
        hitsLeft[index]--;
        if (hitsLeft[index] > 0) {
            return Hit.CRACKED;
        }
        if (standing() > 0) {
            return Hit.BROKEN;
        }
        enter(State.FALLING);
        return Hit.ALL_BROKEN;
    }

    private void enter(State next) {
        state = next;
        ticksInState = 0;
    }

    public State state() {
        return state;
    }

    public boolean vulnerable() {
        return state == State.FALLEN;
    }

    public int hitsLeft(int index) {
        return hitsLeft[index];
    }

    public boolean standing(int index) {
        return state == State.SHIELDED && hitsLeft[index] > 0;
    }

    public int standing() {
        int count = 0;
        for (int left : hitsLeft) {
            if (left > 0) {
                count++;
            }
        }
        return state == State.SHIELDED ? count : 0;
    }

    /** How many times the tablets have formed (1 the first time); used to rotate their positions. */
    public int cycles() {
        return cycles;
    }

    public int fallenTicksLeft() {
        return state == State.FALLEN ? Math.max(0, EnlilTuning.FALLEN_TICKS - ticksInState) : 0;
    }

    /** Height above the walking level where the boss should be now. */
    public double height() {
        double progress;
        return switch (state) {
            case RAGING, SHIELDED -> EnlilTuning.SHIELD_HEIGHT;
            case FALLING -> {
                progress = Math.min(1.0, ticksInState / (double) EnlilTuning.FALL_TICKS);
                yield EnlilTuning.SHIELD_HEIGHT * (1.0 - progress * progress);
            }
            case FALLEN -> 0.0;
            case RISING -> {
                progress = Math.min(1.0, ticksInState / (double) EnlilTuning.RISE_TICKS);
                yield EnlilTuning.SHIELD_HEIGHT * progress;
            }
        };
    }
}
