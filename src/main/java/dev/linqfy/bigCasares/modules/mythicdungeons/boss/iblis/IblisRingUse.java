package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Using Solomon's ring, without server types. Encounters cannot listen to clicks, so the use is polled: a player who
 * holds the ring (either hand) and is sneaking (Shift on Java, the sneak button on Bedrock touch or a controller)
 * next to an idle vessel arms it. Each player then waits {@link #COOLDOWN_TICKS} before arming another one, so a
 * group must split up to cover the vessels Iblis is heading for.
 */
public final class IblisRingUse {

    public static final int COOLDOWN_TICKS = 60;

    public enum Outcome {
        /** Not holding the ring or not sneaking: nothing to do. */
        NONE,
        ARMED,
        /** Sneaking with the ring, but no vessel in reach. */
        NO_VESSEL,
        /** The vessel in reach is already armed, sealed or cooling down. */
        VESSEL_BUSY,
        /** This player used the ring too recently. */
        RECHARGING
    }

    public record Result(Outcome outcome, int vessel) {
        static Result of(Outcome outcome) {
            return new Result(outcome, -1);
        }
    }

    private final Map<UUID, Integer> lastUse = new HashMap<>();

    public Result attempt(UUID player, boolean holdingRing, boolean sneaking, IblisPoint at, IblisVessels vessels,
                          int tick) {
        Objects.requireNonNull(player, "player");
        if (!holdingRing || !sneaking) {
            return Result.of(Outcome.NONE);
        }
        Integer last = lastUse.get(player);
        OptionalInt idle = vessels.nearestIdle(at);
        if (idle.isEmpty()) {
            return Result.of(vessels.nearest(at).isPresent() ? Outcome.VESSEL_BUSY : Outcome.NO_VESSEL);
        }
        if (last != null && tick - last < COOLDOWN_TICKS) {
            return Result.of(Outcome.RECHARGING);
        }
        if (!vessels.arm(idle.getAsInt(), tick)) {
            return Result.of(Outcome.VESSEL_BUSY);
        }
        lastUse.put(player, tick);
        return new Result(Outcome.ARMED, idle.getAsInt());
    }

    /** Ticks until this player may arm again (0 when ready). */
    public int recharge(UUID player, int tick) {
        Integer last = lastUse.get(player);
        return last == null ? 0 : Math.max(0, COOLDOWN_TICKS - (tick - last));
    }

    public void clear() {
        lastUse.clear();
    }
}
