package dev.linqfy.bigCasares.modules.moderation.anticheat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Romper y colocar bloques. El servidor vanilla ya rechaza romper antes del 70% del tiempo y fuera del alcance de bloques,
 * asi que aca se mide lo que deja pasar: muchos bloques rotos en el mismo tick (nuker) y colocar contra una cara que el
 * jugador no puede ver (scaffold / airplace).
 */
public final class BlockInteractionCheck {
    /** Un instamine legitimo rompe un bloque por tick; con lag pueden llegar un par juntos. */
    static final int MAX_BREAKS_PER_TICK = 3;
    static final long STRIKE_WINDOW_TICKS = 200;
    /** Margen para la cara: la posicion que conoce el servidor va un tick atrasada respecto del clic. */
    static final double FACE_MARGIN = 0.6;

    private final Map<UUID, State> states = new HashMap<>();

    public Optional<Detection> breakBlock(UUID playerId, long tick) {
        State state = states.computeIfAbsent(playerId, ignored -> new State());
        if (state.breakTick != tick) {
            state.breakTick = tick;
            state.breaksThisTick = 0;
        }
        state.breaksThisTick++;
        if (state.breaksThisTick != MAX_BREAKS_PER_TICK + 1) {
            return Optional.empty();
        }
        while (!state.breakStrikes.isEmpty() && state.breakStrikes.peekFirst() < tick - STRIKE_WINDOW_TICKS) {
            state.breakStrikes.removeFirst();
        }
        state.breakStrikes.addLast(tick);
        if (state.breakStrikes.size() >= 2) {
            state.breakStrikes.clear();
            return Optional.of(new Detection("block-break-rate",
                "Mas de " + MAX_BREAKS_PER_TICK + " bloques rotos en un mismo tick, dos veces en 10 s"));
        }
        return Optional.empty();
    }

    /**
     * @param faceX la normal de la cara clickeada (-1, 0 o 1 en un solo eje)
     */
    public Optional<Detection> place(
        UUID playerId,
        double eyeX, double eyeY, double eyeZ,
        int blockX, int blockY, int blockZ,
        int faceX, int faceY, int faceZ
    ) {
        State state = states.computeIfAbsent(playerId, ignored -> new State());
        if (faceVisible(eyeX, eyeY, eyeZ, blockX, blockY, blockZ, faceX, faceY, faceZ)) {
            state.place.decay();
            return Optional.empty();
        }
        if (state.place.add(1)) {
            return Optional.of(new Detection("block-place-face", String.format(Locale.ROOT,
                "Bloques colocados contra caras que no se ven desde los ojos (cara %d,%d,%d)", faceX, faceY, faceZ)));
        }
        return Optional.empty();
    }

    /** Los ojos tienen que estar del lado de afuera del plano de esa cara (con margen). */
    static boolean faceVisible(
        double eyeX, double eyeY, double eyeZ,
        int blockX, int blockY, int blockZ,
        int faceX, int faceY, int faceZ
    ) {
        if (faceX > 0) {
            return eyeX >= blockX + 1 - FACE_MARGIN;
        }
        if (faceX < 0) {
            return eyeX <= blockX + FACE_MARGIN;
        }
        if (faceY > 0) {
            return eyeY >= blockY + 1 - FACE_MARGIN;
        }
        if (faceY < 0) {
            return eyeY <= blockY + FACE_MARGIN;
        }
        if (faceZ > 0) {
            return eyeZ >= blockZ + 1 - FACE_MARGIN;
        }
        if (faceZ < 0) {
            return eyeZ <= blockZ + FACE_MARGIN;
        }
        return true;
    }

    public void forget(UUID playerId) {
        states.remove(playerId);
    }

    public void clear() {
        states.clear();
    }

    private static final class State {
        private final ViolationBuffer place = new ViolationBuffer(3, 0.25, 1);
        private final Deque<Long> breakStrikes = new ArrayDeque<>();
        private long breakTick = Long.MIN_VALUE;
        private int breaksThisTick;
    }
}
