package dev.linqfy.bigCasares.modules.mutango;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El doble salto de cada jugador (sin Bukkit). El salto gastado solo se recarga al aterrizar: sacarse las botas en el
 * aire, volver a ponerselas, desconectarse o recargar el servidor no lo renuevan. Por eso el estado no se borra mientras el
 * jugador esta en el aire, y quien lo usa lo guarda tambien en el jugador (spentInStorage) para retomarlo.
 */
final class MutangoJumpLedger {

    private final Map<UUID, MutangoBootsJump> jumps = new HashMap<>();

    /** El salto de ese jugador; si todavia no se lo conoce en esta corrida, arranca con lo que tenia guardado. */
    MutangoBootsJump of(UUID player, boolean spentInStorage) {
        return jumps.computeIfAbsent(player, id -> new MutangoBootsJump(spentInStorage));
    }

    /**
     * Un tick de ese jugador. Si esta en el piso, se recarga (con botas) o se olvida (sin ellas) y devuelve true: ya no
     * queda nada gastado que guardar. En el aire no toca nada y devuelve false.
     */
    boolean tick(UUID player, boolean wearingBoots, boolean onGround) {
        if (!onGround) {
            return false;
        }
        if (wearingBoots) {
            MutangoBootsJump jump = jumps.get(player);
            if (jump != null) {
                jump.land();
            }
        } else {
            jumps.remove(player);
        }
        return true;
    }

    /** Se apaga el modulo: se suelta toda la memoria (lo gastado queda en cada jugador). */
    void clear() {
        jumps.clear();
    }

    /** Se desconecto: se suelta la memoria (el estado gastado queda en el jugador y se retoma al volver). */
    void forget(UUID player) {
        jumps.remove(player);
    }
}
