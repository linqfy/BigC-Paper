package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 1 (no server types): Enlil hovers out of reach throwing gusts and tornadoes, then comes down for a few
 * seconds; only while he is on the ground can he be hurt. One airborne stretch is:
 * <pre>
 *   40 gust warn, 60 gust, 120 tornado warn, 144 tornadoes, 200 gust warn, 220 gust,
 *   260 landing ring, 284 descent (20) -> grounded (100, vulnerable) -> ascent (20) -> airborne again
 * </pre>
 */
public final class EnlilSkyCycle {

    public enum State { AIRBORNE, DESCENDING, GROUNDED, ASCENDING }

    static final int FIRST_GUST_AT = 40;
    static final int TORNADO_WARN_AT = 120;
    static final int SECOND_GUST_AT = 200;
    static final int DESCEND_WARN_AT = 260;

    private State state = State.AIRBORNE;
    private int ticksInState;

    /** Advances one tick and returns what happens on it (in order). */
    public List<EnlilCue> advance() {
        ticksInState++;
        List<EnlilCue> cues = new ArrayList<>(2);
        switch (state) {
            case AIRBORNE -> airborne(cues);
            case DESCENDING -> {
                if (ticksInState >= EnlilTuning.MOVE_TICKS) {
                    enter(State.GROUNDED);
                    cues.add(EnlilCue.LANDED);
                }
            }
            case GROUNDED -> {
                if (ticksInState >= EnlilTuning.GROUNDED_TICKS) {
                    enter(State.ASCENDING);
                    cues.add(EnlilCue.ASCEND);
                }
            }
            case ASCENDING -> {
                if (ticksInState >= EnlilTuning.MOVE_TICKS) {
                    enter(State.AIRBORNE);
                    cues.add(EnlilCue.AIRBORNE);
                }
            }
        }
        return cues;
    }

    private void airborne(List<EnlilCue> cues) {
        int t = ticksInState;
        if (t == FIRST_GUST_AT || t == SECOND_GUST_AT) {
            cues.add(EnlilCue.GUST_WARN);
        } else if (t == FIRST_GUST_AT + EnlilTuning.GUST_WARN_TICKS || t == SECOND_GUST_AT + EnlilTuning.GUST_WARN_TICKS) {
            cues.add(EnlilCue.GUST);
        } else if (t == TORNADO_WARN_AT) {
            cues.add(EnlilCue.TORNADO_WARN);
        } else if (t == TORNADO_WARN_AT + EnlilTuning.TORNADO_WARN_TICKS) {
            cues.add(EnlilCue.TORNADO);
        } else if (t == DESCEND_WARN_AT) {
            cues.add(EnlilCue.DESCEND_WARN);
        } else if (t >= DESCEND_WARN_AT + EnlilTuning.DESCEND_WARN_TICKS) {
            enter(State.DESCENDING);
            cues.add(EnlilCue.DESCEND);
        }
    }

    private void enter(State next) {
        state = next;
        ticksInState = 0;
    }

    public State state() {
        return state;
    }

    /** {@code true} from the landing warning until the descent starts (he stops drifting, the ring shows). */
    public boolean landingWarned() {
        return state == State.AIRBORNE && ticksInState >= DESCEND_WARN_AT;
    }

    /** Only on the ground: the wind no longer shields him. */
    public boolean vulnerable() {
        return state == State.GROUNDED;
    }

    /** Ticks left in the grounded window (0 outside it). */
    public int groundedTicksLeft() {
        return state == State.GROUNDED ? Math.max(0, EnlilTuning.GROUNDED_TICKS - ticksInState) : 0;
    }

    /**
     * Height above the walking level where the boss should be now: hovering, interpolated while he moves, zero on
     * the ground.
     */
    public double height() {
        double progress = Math.min(1.0, ticksInState / (double) EnlilTuning.MOVE_TICKS);
        return switch (state) {
            case AIRBORNE -> EnlilTuning.HOVER_HEIGHT;
            case DESCENDING -> EnlilTuning.HOVER_HEIGHT * (1.0 - progress);
            case GROUNDED -> 0.0;
            case ASCENDING -> EnlilTuning.HOVER_HEIGHT * progress;
        };
    }
}
