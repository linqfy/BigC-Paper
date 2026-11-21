package dev.linqfy.bigCasares.modules.moderation.anticheat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ventanas de gracia por jugador medidas en ticks del servidor (no en reloj: si el servidor se traba, la gracia no se
 * consume sola). Cualquier impulso que no viene del cliente (setVelocity de un modulo, knockback, explosion, teleport,
 * piston...) abre una ventana; mientras dure, los checks de movimiento no miden a ese jugador.
 */
public final class ExemptionLedger {
    private final Map<UUID, Grant> grants = new HashMap<>();

    public void grant(UUID playerId, long untilTick, String reason) {
        grants.merge(playerId, new Grant(untilTick, reason),
            (current, next) -> next.untilTick() > current.untilTick() ? next : current);
    }

    public boolean isExempt(UUID playerId, long tick) {
        return active(playerId, tick).isPresent();
    }

    public Optional<String> active(UUID playerId, long tick) {
        Grant grant = grants.get(playerId);
        if (grant == null) {
            return Optional.empty();
        }
        if (tick > grant.untilTick()) {
            grants.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(grant.reason());
    }

    public void forget(UUID playerId) {
        grants.remove(playerId);
    }

    public void clear() {
        grants.clear();
    }

    private record Grant(long untilTick, String reason) {
    }
}
