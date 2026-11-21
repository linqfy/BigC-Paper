package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The fallback way to accept Mandinga's deal (no server types): hold sneak next to the pedestal for
 * {@link PachamamaTuning#DEAL_SNEAK_TICKS}. It exists because a click on the Interaction entity may not reach the
 * server from every client (Bedrock through Geyser); sneaking is seen from every client. Leaving or standing up
 * starts over.
 */
public final class PachamamaDealHold {

    private final Map<UUID, Integer> held = new HashMap<>();

    /**
     * One tick for the players holding sneak at the pedestal now.
     *
     * @return the player who completed the hold this tick, or {@code null}
     */
    public UUID tick(Set<UUID> holding) {
        held.keySet().retainAll(holding);
        UUID done = null;
        for (UUID player : holding) {
            int ticks = held.merge(player, 1, Integer::sum);
            if (ticks >= PachamamaTuning.DEAL_SNEAK_TICKS && done == null) {
                done = player;
            }
        }
        if (done != null) {
            held.remove(done);
        }
        return done;
    }

    /** Ticks held so far by a player (0 if not holding). */
    public int progress(UUID player) {
        return held.getOrDefault(player, 0);
    }

    /** Forgets a player (after a refusal, so the message is not repeated every tick). */
    public void reset(UUID player) {
        held.remove(player);
    }
}
