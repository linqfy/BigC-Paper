package dev.linqfy.bigCasares.modules.mythicdungeons;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The server-side cooldown of the Mandinga guitar (no server types), on top of the item cooldown the client draws:
 * one note per {@link #TICKS} per player, whatever the client does. Also remembers the last note of each player so
 * consecutive shots sound different.
 */
public final class MandingaGuitarCooldown {

    /** One second. */
    public static final int TICKS = 20;

    private final Map<UUID, Long> lastShot = new HashMap<>();
    private final Map<UUID, Integer> lastNote = new HashMap<>();

    /** Fires if the cooldown is over at {@code tick}, recording the shot. */
    public boolean tryFire(UUID player, long tick) {
        Long last = lastShot.get(player);
        if (last != null && tick - last < TICKS && tick >= last) {
            return false;
        }
        lastShot.put(player, tick);
        return true;
    }

    public int lastNote(UUID player) {
        return lastNote.getOrDefault(player, -1);
    }

    public void note(UUID player, int degree) {
        lastNote.put(player, degree);
    }

    /** Forgets a player (on quit). */
    public void forget(UUID player) {
        lastShot.remove(player);
        lastNote.remove(player);
    }
}
