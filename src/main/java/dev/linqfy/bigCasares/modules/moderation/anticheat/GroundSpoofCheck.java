package dev.linqfy.bigCasares.modules.moderation.anticheat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * NoFall: el dano de caida se calcula con el "estoy en el suelo" que manda el cliente. Si lo dice repetidas veces sin
 * nada debajo (ni bloque ni entidad plataforma), esta mintiendo para no recibir dano.
 */
public final class GroundSpoofCheck {
    private final Map<UUID, ViolationBuffer> buffers = new HashMap<>();

    /**
     * @param claimsGround el cliente dice estar en el suelo
     * @param supported    hay un bloque o una entidad que lo puede sostener
     */
    public Optional<Detection> sample(UUID playerId, boolean claimsGround, boolean supported, boolean exempt) {
        ViolationBuffer buffer = buffers.computeIfAbsent(playerId, ignored -> new ViolationBuffer(6, 0.5, 2));
        if (exempt) {
            buffer.reset();
            return Optional.empty();
        }
        if (!claimsGround || supported) {
            buffer.decay();
            return Optional.empty();
        }
        if (buffer.add(1)) {
            return Optional.of(new Detection("ground-spoof",
                "El cliente dice estar en el suelo repetidas veces sin nada debajo (NoFall)"));
        }
        return Optional.empty();
    }

    public void forget(UUID playerId) {
        buffers.remove(playerId);
    }

    public void clear() {
        buffers.clear();
    }
}
