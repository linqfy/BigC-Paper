package dev.linqfy.bigCasares.modules.toffy.eventos;

import java.io.IOException;
import java.util.function.Consumer;

/** Keeps a failed inventory handoff quarantined even if restoring its previous state also fails. */
final class ToffyEventRestore {
    @FunctionalInterface
    interface Checkpoint {
        void applyAndSave() throws IOException;
    }

    private ToffyEventRestore() {
    }

    static boolean attempt(Checkpoint checkpoint, Runnable rollback, Consumer<Exception> quarantine) {
        try {
            checkpoint.applyAndSave();
            return true;
        } catch (IOException | RuntimeException failure) {
            try {
                rollback.run();
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            quarantine.accept(failure);
            return false;
        }
    }
}
