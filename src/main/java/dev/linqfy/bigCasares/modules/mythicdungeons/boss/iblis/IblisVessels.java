package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The sealed vessels of phase 2 and the trap windows, without server types.
 *
 * <p>A vessel is {@link State#IDLE} until a player with the ring arms it: it stays {@link State#ARMED} for
 * {@link #ARMED_TICKS}. If Iblis comes within {@link #CAPTURE_RADIUS} of an armed vessel he is sucked in: the vessel
 * is {@link State#SEALED} for {@link #TRAP_TICKS} (the only window in which he takes damage), then he breaks out and
 * the vessel cools down for {@link #CRACKED_TICKS}. An armed vessel that catches nothing cools down for
 * {@link #RECHARGE_TICKS}. Only one trap at a time.</p>
 */
public final class IblisVessels {

    public static final int ARMED_TICKS = 100;
    public static final int TRAP_TICKS = 140;
    /** Last ticks of a trap in which the jar shakes: a warning that he is about to break out. */
    public static final int BREAKOUT_WARNING = 30;
    public static final int RECHARGE_TICKS = 60;
    public static final int CRACKED_TICKS = 200;
    public static final double CAPTURE_RADIUS = 2.5;
    /** How close a player must stand to arm a vessel. */
    public static final double USE_RANGE = 3.0;

    public enum State { IDLE, ARMED, SEALED, COOLDOWN }

    public enum Change { EXPIRED, RECHARGED, BREAKOUT_WARNING, RELEASED }

    public record Event(int vessel, Change change) {
    }

    private final List<IblisPoint> points;
    private final State[] states;
    private final int[] until;
    private final boolean[] warned;

    public IblisVessels(List<IblisPoint> points) {
        this.points = List.copyOf(Objects.requireNonNull(points, "points"));
        if (this.points.isEmpty()) {
            throw new IllegalArgumentException("at least one vessel");
        }
        this.states = new State[this.points.size()];
        this.until = new int[this.points.size()];
        this.warned = new boolean[this.points.size()];
        Arrays.fill(states, State.IDLE);
    }

    public int size() {
        return points.size();
    }

    public IblisPoint point(int vessel) {
        return points.get(vessel);
    }

    public List<IblisPoint> points() {
        return points;
    }

    public State state(int vessel) {
        return states[vessel];
    }

    /** Ticks left in the current timed state (0 for idle). */
    public int remaining(int vessel, int tick) {
        return states[vessel] == State.IDLE ? 0 : Math.max(0, until[vessel] - tick);
    }

    /** The idle vessel nearest to (x, z) within {@link #USE_RANGE}, if any. */
    public OptionalInt nearestIdle(IblisPoint at) {
        int best = -1;
        double distance = USE_RANGE;
        for (int index = 0; index < points.size(); index++) {
            double d = points.get(index).distance(at);
            if (states[index] == State.IDLE && d <= distance) {
                best = index;
                distance = d;
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }

    /** The vessel nearest to (x, z) within {@link #USE_RANGE} whatever its state, if any. */
    public OptionalInt nearest(IblisPoint at) {
        int best = -1;
        double distance = USE_RANGE;
        for (int index = 0; index < points.size(); index++) {
            double d = points.get(index).distance(at);
            if (d <= distance) {
                best = index;
                distance = d;
            }
        }
        return best < 0 ? OptionalInt.empty() : OptionalInt.of(best);
    }

    /** Arms an idle vessel; {@code false} if it is not idle. */
    public boolean arm(int vessel, int tick) {
        if (states[vessel] != State.IDLE) {
            return false;
        }
        states[vessel] = State.ARMED;
        until[vessel] = tick + ARMED_TICKS;
        return true;
    }

    /** {@code true} while Iblis is sealed in a vessel (the damage window). */
    public boolean trapped() {
        return sealedVessel().isPresent();
    }

    public OptionalInt sealedVessel() {
        for (int index = 0; index < states.length; index++) {
            if (states[index] == State.SEALED) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * Iblis is at {@code boss}: the nearest armed vessel within {@link #CAPTURE_RADIUS} seals him. Returns that vessel,
     * or empty when he is already sealed or no armed vessel is close.
     */
    public OptionalInt capture(IblisPoint boss, int tick) {
        if (trapped()) {
            return OptionalInt.empty();
        }
        int best = -1;
        double distance = CAPTURE_RADIUS;
        for (int index = 0; index < points.size(); index++) {
            double d = points.get(index).distance(boss);
            if (states[index] == State.ARMED && d <= distance) {
                best = index;
                distance = d;
            }
        }
        if (best < 0) {
            return OptionalInt.empty();
        }
        states[best] = State.SEALED;
        until[best] = tick + TRAP_TICKS;
        warned[best] = false;
        return OptionalInt.of(best);
    }

    /** Runs the timers; returns what changed this tick. */
    public List<Event> advance(int tick) {
        List<Event> events = new ArrayList<>();
        for (int index = 0; index < states.length; index++) {
            switch (states[index]) {
                case ARMED -> {
                    if (tick >= until[index]) {
                        states[index] = State.COOLDOWN;
                        until[index] = tick + RECHARGE_TICKS;
                        events.add(new Event(index, Change.EXPIRED));
                    }
                }
                case SEALED -> {
                    if (tick >= until[index]) {
                        states[index] = State.COOLDOWN;
                        until[index] = tick + CRACKED_TICKS;
                        events.add(new Event(index, Change.RELEASED));
                    } else if (!warned[index] && until[index] - tick <= BREAKOUT_WARNING) {
                        warned[index] = true;
                        events.add(new Event(index, Change.BREAKOUT_WARNING));
                    }
                }
                case COOLDOWN -> {
                    if (tick >= until[index]) {
                        states[index] = State.IDLE;
                        events.add(new Event(index, Change.RECHARGED));
                    }
                }
                case IDLE -> {
                }
            }
        }
        return events;
    }

    /** Ends every timed state at once (fight over); returns the vessels that were sealed. */
    public List<Integer> reset() {
        List<Integer> sealed = new ArrayList<>();
        for (int index = 0; index < states.length; index++) {
            if (states[index] == State.SEALED) {
                sealed.add(index);
            }
            states[index] = State.IDLE;
            until[index] = 0;
        }
        return sealed;
    }
}
