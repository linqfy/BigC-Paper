package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * How the visible Iblis of phase 2 moves, without server types: he rests, aims a dash (the path burns on the floor
 * for {@link #AIM_TICKS}), crosses the room at {@link #SPEED} blocks per tick leaving a trail of fire, and rests
 * again. Most dashes run straight through a vessel (and {@link #OVERSHOOT} blocks past it), so the players can see
 * where he goes and arm that vessel in time; the rest go through a player.
 */
public final class IblisDash {

    public static final int REST_TICKS = 40;
    public static final int AIM_TICKS = 24;
    public static final double SPEED = 0.7;
    public static final double OVERSHOOT = 3.0;
    /** Dash endpoints keep this far from the walls. */
    public static final double MARGIN = 2.0;
    /** Share of dashes aimed through a vessel when there are players to aim at. */
    public static final double VESSEL_BIAS = 0.7;

    public enum Phase { REST, AIM, DASH }

    /** A dash from {@code from} to {@code to}; {@code vessel} is the vessel it runs through, or -1. */
    public record Plan(IblisPoint from, IblisPoint to, int vessel) {

        public Plan {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }

        public double length() {
            return from.distance(to);
        }
    }

    private Phase phase = Phase.REST;
    private int since;
    private Plan plan;

    public IblisDash(int startTick) {
        this.since = startTick;
    }

    public Phase phase() {
        return phase;
    }

    public Plan plan() {
        return plan;
    }

    /** Back to resting from {@code tick} (after a trap or a teleport). */
    public void rest(int tick) {
        phase = Phase.REST;
        since = tick;
        plan = null;
    }

    /** Advances the cycle; returns the phase entered this tick, or {@code null}. */
    public Phase advance(int tick, Supplier<Plan> planner) {
        switch (phase) {
            case REST -> {
                if (tick - since >= REST_TICKS) {
                    plan = Objects.requireNonNull(planner.get(), "plan");
                    phase = Phase.AIM;
                    since = tick;
                    return Phase.AIM;
                }
            }
            case AIM -> {
                if (tick - since >= AIM_TICKS) {
                    phase = Phase.DASH;
                    since = tick;
                    return Phase.DASH;
                }
            }
            case DASH -> {
                if (progress(tick) >= 1.0) {
                    rest(tick);
                    return Phase.REST;
                }
            }
        }
        return null;
    }

    /** 0..1 along the dash. */
    public double progress(int tick) {
        if (phase != Phase.DASH || plan == null) {
            return 0.0;
        }
        double length = plan.length();
        return length < 1.0e-6 ? 1.0 : Math.min(1.0, (tick - since) * SPEED / length);
    }

    /** Where Iblis is during the dash. */
    public IblisPoint position(int tick) {
        return plan == null ? null : plan.from().toward(plan.to(), progress(tick));
    }

    /**
     * Picks the next dash: through a random vessel he is not standing on ({@link #VESSEL_BIAS} of the time when
     * there are players), else through a random player, else to the room centre. The end is cut where the line
     * leaves the room (minus {@link #MARGIN}), so a vessel on the line always stays on the path.
     */
    public static Plan plan(IblisPoint boss, List<IblisPoint> vessels, List<IblisPoint> players, IblisRoom room,
                            RandomGenerator random) {
        List<Integer> far = new ArrayList<>();
        for (int index = 0; index < vessels.size(); index++) {
            if (vessels.get(index).distance(boss) > IblisVessels.CAPTURE_RADIUS + 1.0) {
                far.add(index);
            }
        }
        IblisPoint through;
        int vessel = -1;
        if (!far.isEmpty() && (players.isEmpty() || random.nextDouble() < VESSEL_BIAS)) {
            vessel = far.get(random.nextInt(far.size()));
            through = vessels.get(vessel);
        } else if (!players.isEmpty()) {
            through = players.get(random.nextInt(players.size()));
        } else {
            through = new IblisPoint(room.centerX(), room.centerZ());
        }
        double dx = through.x() - boss.x();
        double dz = through.z() - boss.z();
        double length = Math.hypot(dx, dz);
        IblisPoint end = length < 1.0e-6 ? through
            : new IblisPoint(through.x() + dx / length * OVERSHOOT, through.z() + dz / length * OVERSHOOT);
        IblisPoint from = room.clamp(boss, MARGIN);
        return new Plan(from, clip(from, end, room), vessel);
    }

    /** The part of {@code from}-{@code to} inside the room minus {@link #MARGIN} ({@code from} must be inside). */
    static IblisPoint clip(IblisPoint from, IblisPoint to, IblisRoom room) {
        double minX = room.minX() + MARGIN;
        double maxX = room.maxX() + 1 - MARGIN;
        double minZ = room.minZ() + MARGIN;
        double maxZ = room.maxZ() + 1 - MARGIN;
        double t = 1.0;
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        if (dx > 1.0e-9) {
            t = Math.min(t, (maxX - from.x()) / dx);
        } else if (dx < -1.0e-9) {
            t = Math.min(t, (minX - from.x()) / dx);
        }
        if (dz > 1.0e-9) {
            t = Math.min(t, (maxZ - from.z()) / dz);
        } else if (dz < -1.0e-9) {
            t = Math.min(t, (minZ - from.z()) / dz);
        }
        return from.toward(to, Math.max(0.0, t));
    }
}
