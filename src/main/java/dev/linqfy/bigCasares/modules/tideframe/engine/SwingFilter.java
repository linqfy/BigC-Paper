package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Tells a left click's arm swing from the swing the client adds after a right click it takes as done (interacting with
 * an entity, using an item). Both arrive as the same swing packet; the echo comes right after the right click.
 */
final class SwingFilter {
    /** A swing this soon after a right click is its echo. Far below a deliberate right-then-left click. */
    static final long WINDOW_NANOS = 300_000_000L;
    private final LongSupplier clock;
    private volatile long rightClickAt = Long.MIN_VALUE;

    SwingFilter(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

    /** A right click arrived (any thread). */
    void rightClick() { rightClickAt = clock.getAsLong(); }

    /**
     * True when a swing now is a left click; false when it echoes the last right click. One swing packet reaches
     * Tideframe twice (the server's left-click-air event and the animation event), so an echo is not used up.
     */
    boolean isClick() {
        long last = rightClickAt;
        return last == Long.MIN_VALUE || clock.getAsLong() - last >= WINDOW_NANOS;
    }
}
