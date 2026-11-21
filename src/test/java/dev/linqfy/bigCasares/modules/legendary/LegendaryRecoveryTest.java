package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegendaryRecoveryTest {
    @TempDir Path directory;

    @Test
    void deliveredSpearWinsOverReceiptAfterCrashAndNeverCreatesSecondCopy() {
        for (boolean reserved : List.of(false, true)) {
            for (boolean empty : List.of(false, true)) {
                assertEquals(LegendaryReturnPolicy.Decision.ACKNOWLEDGE_EXISTING,
                    LegendaryReturnPolicy.decide(true, reserved, empty));
            }
        }
    }

    @Test
    void fullInventoryKeepsEscrowUnlessItsReservedSlotIsStillAvailable() {
        assertEquals(LegendaryReturnPolicy.Decision.KEEP_PENDING, LegendaryReturnPolicy.decide(false, false, false));
        assertEquals(LegendaryReturnPolicy.Decision.REPLACE_RECEIPT, LegendaryReturnPolicy.decide(false, true, false));
        assertEquals(LegendaryReturnPolicy.Decision.USE_EMPTY_SLOT, LegendaryReturnPolicy.decide(false, false, true));
    }

    @Test
    void persistenceFailureDoesNotMutateLiveCooldownState() throws Exception {
        Path invalid = directory.resolve("directory");
        var store = new LegendaryStateStore(invalid);
        java.nio.file.Files.createDirectory(invalid);
        UUID player = UUID.randomUUID();
        assertThrows(java.io.IOException.class, () -> store.beginCooldown(player, LegendaryWeapon.BLOODLUST, 0));
        assertEquals(0, store.remaining(player, LegendaryWeapon.BLOODLUST, 0));
    }
}
