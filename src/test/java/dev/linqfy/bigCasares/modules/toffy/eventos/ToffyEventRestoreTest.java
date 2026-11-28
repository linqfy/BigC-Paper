package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyEventRestoreTest {
    @Test
    void failedCheckpointRestoresThePriorStateAndQuarantines() {
        AtomicReference<String> inventory = new AtomicReference<>("event kit");
        AtomicBoolean quarantined = new AtomicBoolean();
        boolean restored = ToffyEventRestore.attempt(() -> {
            inventory.set("saved inventory");
            throw new IOException("failed save");
        }, () -> inventory.set("event kit"), failure -> quarantined.set(true));
        assertFalse(restored);
        assertEquals("event kit", inventory.get());
        assertTrue(quarantined.get());
    }

    @Test
    void failedRollbackStillQuarantinesAndKeepsTheOriginalError() {
        AtomicReference<Exception> error = new AtomicReference<>();
        boolean restored = ToffyEventRestore.attempt(() -> { throw new IOException("save"); },
            () -> { throw new IllegalStateException("rollback"); }, error::set);
        assertFalse(restored);
        assertEquals("save", error.get().getMessage());
        assertEquals(1, error.get().getSuppressed().length);
    }

    @Test
    void successfulCheckpointDoesNotRollbackOrQuarantine() {
        AtomicReference<String> inventory = new AtomicReference<>("event kit");
        AtomicBoolean quarantined = new AtomicBoolean();
        assertTrue(ToffyEventRestore.attempt(() -> inventory.set("saved inventory"),
            () -> inventory.set("event kit"), failure -> quarantined.set(true)));
        assertEquals("saved inventory", inventory.get());
        assertFalse(quarantined.get());
    }
}
