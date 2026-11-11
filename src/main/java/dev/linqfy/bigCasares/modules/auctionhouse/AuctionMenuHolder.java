package dev.linqfy.bigCasares.modules.auctionhouse;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Marks auction house inventories and remembers which listing sits in each slot. */
final class AuctionMenuHolder implements InventoryHolder {

    enum View { BROWSE, MINE, CONFIRM }

    private final View view;
    private final int page;
    private final Map<Integer, Long> listingSlots = new HashMap<>();
    private Inventory inventory;

    AuctionMenuHolder(View view, int page) {
        this.view = view;
        this.page = page;
    }

    View view() {
        return view;
    }

    int page() {
        return page;
    }

    void bind(int slot, long listingId) {
        listingSlots.put(slot, listingId);
    }

    Long listingAt(int slot) {
        return listingSlots.get(slot);
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
