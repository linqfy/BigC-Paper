package dev.linqfy.bigCasares.modules.warp;

import dev.linqfy.bigCasares.BigCasares;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class WarpListener implements Listener {
    private final WarpModule module;

    public WarpListener(WarpModule module) {
        this.module = module;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WarpMenuHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        NamespacedKey key = new NamespacedKey(module.getPlugin(), "warp_id");
        if (item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
            String warpId = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (warpId != null) {
                Warp warp = module.getStorage().get(warpId);
                if (warp != null && warp.location().getWorld() == null) {
                    player.sendMessage("§cEl mundo de ese warp no esta cargado.");
                } else if (warp != null) {
                    player.closeInventory();
                    player.teleport(warp.location());
                    player.sendMessage("§aTeletransportado a " + warp.name() + ".");
                } else {
                    player.sendMessage("§cEse warp ya no existe.");
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WarpMenuHolder) {
            event.setCancelled(true);
        }
    }
}
