package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class LegendaryInventoryTransactionTest {
    static class Inventory implements LegendaryInventoryTransaction.Inventory {
        String marker; String contents = "before"; int replacements; boolean fails;
        public String marker() { return marker; }
        public void replace(String encoded) { contents = encoded; replacements++; }
        public void mark(String nonce) { marker = nonce; }
        public void saveAndVerify(String nonce) throws IOException { if (fails) throw new IOException("save failed"); }
    }
    private static LegendaryCraftingSession.Pending pending() {
        return new LegendaryCraftingSession.Pending(UUID.randomUUID(), UUID.randomUUID(), "after",
            LegendaryCraftingSession.TransactionKind.CLAIM);
    }
    @Test void unappliedDeliveryIsRestoredBeforeConfirmation() throws Exception {
        var inventory = new Inventory(); var tx = pending();
        LegendaryInventoryTransaction.finish(tx, inventory, () -> {
            assertEquals("after", inventory.contents); assertEquals(tx.nonce().toString(), inventory.marker);
        });
        assertEquals(1, inventory.replacements);
    }
    @Test void failedVerifiedSaveNeverAcknowledgesAndRetryDoesNotRepeatDelivery() throws Exception {
        var inventory = new Inventory(); var tx = pending(); inventory.fails = true;
        assertThrows(IOException.class, () -> LegendaryInventoryTransaction.finish(tx, inventory, () -> fail("must not acknowledge")));
        inventory.fails = false;
        LegendaryInventoryTransaction.finish(tx, inventory, () -> { });
        assertEquals(1, inventory.replacements);
    }
    @Test void crashAfterSavedInventoryBeforeAcknowledgementDoesNotOverwriteLaterContents() throws Exception {
        var inventory = new Inventory(); var tx = pending();
        assertThrows(IOException.class, () -> LegendaryInventoryTransaction.finish(tx, inventory, () -> { throw new IOException("journal failed"); }));
        inventory.contents = "already delivered inventory";
        LegendaryInventoryTransaction.finish(tx, inventory, () -> { });
        assertEquals("already delivered inventory", inventory.contents);
        assertEquals(1, inventory.replacements);
    }
    @Test void crashBeforePlayerSaveReappliesSnapshotWithDifferentLoadedMarker() throws Exception {
        var inventory = new Inventory(); var tx = pending(); inventory.marker = "old persisted nonce";
        LegendaryInventoryTransaction.finish(tx, inventory, () -> { });
        assertEquals("after", inventory.contents); assertEquals(1, inventory.replacements);
    }
}
