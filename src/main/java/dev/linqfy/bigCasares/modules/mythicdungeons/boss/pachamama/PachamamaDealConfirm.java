package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The click way to accept Mandinga's deal (no server types): any right-click looking at the pedestal's Interaction
 * registers (eating, raising a shield, placing a block), so one click only arms the deal for that player and a second
 * click of the same player within {@link PachamamaTuning#DEAL_CONFIRM_TICKS} pays. Clicks repeated by a held use key
 * (closer than {@link PachamamaTuning#DEAL_REPEAT_GAP_TICKS}) keep it armed but never confirm.
 */
public final class PachamamaDealConfirm {

    /** What a click did. */
    public enum Result {
        /** First click (or the previous one expired): ask for a second one. */
        ARMED,
        /** A repeat of a held click: nothing new. */
        HELD,
        /** Second click within the window: pay. */
        CONFIRMED
    }

    private record Armed(long since, long last) {
    }

    private final Map<UUID, Armed> armed = new HashMap<>();

    /** A click of {@code player} on tick {@code tick}. */
    public Result click(UUID player, long tick) {
        Armed state = armed.get(player);
        if (state == null || tick - state.since() > PachamamaTuning.DEAL_CONFIRM_TICKS) {
            armed.put(player, new Armed(tick, tick));
            return Result.ARMED;
        }
        if (tick - state.last() <= PachamamaTuning.DEAL_REPEAT_GAP_TICKS) {
            armed.put(player, new Armed(state.since(), tick));
            return Result.HELD;
        }
        armed.remove(player);
        return Result.CONFIRMED;
    }

    /** Whether {@code player} has a click waiting for its confirmation on tick {@code tick}. */
    public boolean armed(UUID player, long tick) {
        Armed state = armed.get(player);
        return state != null && tick - state.since() <= PachamamaTuning.DEAL_CONFIRM_TICKS;
    }

    /** Forgets the expired clicks. */
    public void expire(long tick) {
        armed.values().removeIf(state -> tick - state.since() > PachamamaTuning.DEAL_CONFIRM_TICKS);
    }
}
