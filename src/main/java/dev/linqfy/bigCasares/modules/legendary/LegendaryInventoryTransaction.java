package dev.linqfy.bigCasares.modules.legendary;

import java.io.IOException;

/** Recovery never overwrites an already applied, marked inventory. */
final class LegendaryInventoryTransaction {
    interface Inventory {
        String marker();
        void replace(String encoded);
        void mark(String nonce);
        void saveAndVerify(String nonce) throws IOException;
    }
    @FunctionalInterface interface Confirmation { void confirm() throws IOException; }
    private LegendaryInventoryTransaction() { }

    static void finish(LegendaryCraftingSession.Pending transaction, Inventory inventory, Confirmation confirmation) throws IOException {
        String nonce = transaction.nonce().toString();
        if (!nonce.equals(inventory.marker())) {
            inventory.replace(transaction.inventory());
            inventory.mark(nonce);
        }
        inventory.saveAndVerify(nonce);
        confirmation.confirm();
    }
}
