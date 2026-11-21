package dev.linqfy.bigCasares.modules.forex;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

final class ForexListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ForexMenu menu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getRawSlot() >= 0
                && event.getRawSlot() < menu.size()) {
                menu.dispatch(player, event.getRawSlot());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ForexMenu) {
            event.setCancelled(true);
        }
    }
}
