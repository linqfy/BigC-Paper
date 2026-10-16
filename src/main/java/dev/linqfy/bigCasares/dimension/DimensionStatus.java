package dev.linqfy.bigCasares.dimension;

import java.util.Objects;

/**
 * Lifecycle state of a registered dimension.
 *
 * <ul>
 *   <li>{@code PENDING_RESTART}: its datapack was installed or changed during this
 *   server run; Minecraft only loads dimensions, biomes and dimension types at
 *   startup, so the world appears after the next restart.</li>
 *   <li>{@code ACTIVE}: the world is loaded and runtime hooks are running.
 *   {@code restartRecommended} is set when the definition changed since startup.</li>
 *   <li>{@code FAILED}: activation threw; see {@code message}.</li>
 * </ul>
 */
public record DimensionStatus(State state, String message, boolean restartRecommended) {

    public enum State {
        REGISTERED, PENDING_RESTART, ACTIVE, FAILED, UNREGISTERED
    }

    public DimensionStatus {
        Objects.requireNonNull(state, "state");
        message = message == null ? "" : message;
    }

    public static DimensionStatus of(State state, String message) {
        return new DimensionStatus(state, message, false);
    }

    public boolean isActive() {
        return state == State.ACTIVE;
    }
}
