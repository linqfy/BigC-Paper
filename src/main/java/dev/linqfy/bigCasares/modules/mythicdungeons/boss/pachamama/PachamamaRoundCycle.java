package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import java.util.ArrayList;
import java.util.List;

/**
 * The timing of the painted-floor rounds, one call to {@link #advance} per tick (no server types):
 * rest, then {@link Cue#PAINT} (the cells change colour: the telegraph), {@link Cue#STRIKE} once the paint time is
 * over, a {@link Cue#PULSE} every {@link PachamamaTuning#PULSE_TICKS} while the effect lasts and {@link Cue#CLEAR}
 * when the floor goes back to normal. The phase is read at each paint, so phase 2 rounds are slower and lighter.
 */
public final class PachamamaRoundCycle {

    public enum State { RESTING, PAINTED, ACTIVE }

    public enum Cue { PAINT, STRIKE, PULSE, CLEAR }

    private State state = State.RESTING;
    private int left;
    private int ticksInState;
    private boolean phaseTwo;

    /** @param firstDelay ticks before the first paint */
    public PachamamaRoundCycle(int firstDelay) {
        this.left = Math.max(1, firstDelay);
    }

    public List<Cue> advance(boolean phaseTwoNow) {
        List<Cue> cues = new ArrayList<>(2);
        ticksInState++;
        if (state == State.ACTIVE && ticksInState % PachamamaTuning.PULSE_TICKS == 0 && left > 1) {
            cues.add(Cue.PULSE);
        }
        if (--left > 0) {
            return cues;
        }
        switch (state) {
            case RESTING -> {
                phaseTwo = phaseTwoNow;
                enter(State.PAINTED, PachamamaTuning.paintTicks(phaseTwo));
                cues.add(Cue.PAINT);
            }
            case PAINTED -> {
                enter(State.ACTIVE, PachamamaTuning.effectTicks(phaseTwo));
                cues.add(Cue.STRIKE);
            }
            case ACTIVE -> {
                enter(State.RESTING, PachamamaTuning.restTicks(phaseTwo));
                cues.add(Cue.CLEAR);
            }
        }
        return cues;
    }

    /** Drops the current round (the phase change): the caller restores the floor; next paint after {@code delay}. */
    public boolean reset(int delay) {
        boolean wasPainted = state != State.RESTING;
        enter(State.RESTING, Math.max(1, delay));
        return wasPainted;
    }

    private void enter(State next, int ticks) {
        state = next;
        left = ticks;
        ticksInState = 0;
    }

    public State state() {
        return state;
    }

    /** Ticks left in the current state. */
    public int left() {
        return left;
    }
}
