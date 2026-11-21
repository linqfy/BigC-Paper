package dev.linqfy.bigCasares.modules.auctionhouse;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.function.Consumer;

final class AuctionHouseListener implements Listener {

    private final AuctionHouseController controller;
    private final Consumer<Runnable> nextTick;

    AuctionHouseListener(AuctionHouseController controller, Consumer<Runnable> nextTick) {
        this.controller = controller;
        this.nextTick = nextTick;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AuctionMenuHolder holder)) {
            return;
        }
        // Nothing may be moved in or out of an auction menu, including shift-clicks from the player inventory.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Long listingId = holder.listingAt(slot);
        AuctionMenus menus = controller.menus();
        switch (holder.view()) {
            case BROWSE -> {
                if (listingId != null) {
                    controller.service().find(listingId).ifPresentOrElse(
                        listing -> {
                            if (listing.seller().equals(player.getUniqueId())) {
                                player.sendMessage("§7Es tu subasta. Retirala desde §f/ah mis§7.");
                            } else {
                                later(() -> menus.openConfirm(player, listing));
                            }
                        },
                        () -> {
                            player.sendMessage("§cEsa subasta ya no está disponible.");
                            later(() -> menus.openBrowse(player, holder.page()));
                        });
                } else if (slot == AuctionMenus.SLOT_PREVIOUS && holder.page() > 0) {
                    later(() -> menus.openBrowse(player, holder.page() - 1));
                } else if (slot == AuctionMenus.SLOT_NEXT) {
                    later(() -> menus.openBrowse(player, holder.page() + 1));
                } else if (slot == AuctionMenus.SLOT_MINE) {
                    later(() -> menus.openMine(player));
                } else if (slot == AuctionMenus.SLOT_CLAIMS) {
                    later(() -> {
                        controller.claim(player);
                        menus.openBrowse(player, holder.page());
                    });
                }
            }
            case MINE -> {
                if (listingId != null) {
                    later(() -> {
                        controller.cancel(player, listingId);
                        menus.openMine(player);
                    });
                } else if (slot == AuctionMenus.SLOT_PREVIOUS) {
                    later(() -> menus.openBrowse(player, 0));
                } else if (slot == AuctionMenus.SLOT_CLAIMS) {
                    later(() -> {
                        controller.claim(player);
                        menus.openMine(player);
                    });
                }
            }
            case CONFIRM -> {
                if (slot == AuctionMenus.SLOT_CONFIRM && listingId != null) {
                    later(() -> {
                        player.closeInventory();
                        controller.buy(player, listingId);
                    });
                } else if (slot == AuctionMenus.SLOT_CANCEL) {
                    later(() -> menus.openBrowse(player, 0));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AuctionMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        int waiting = controller.service().claims(event.getPlayer().getUniqueId()).size();
        if (waiting > 0) {
            event.getPlayer().sendMessage("§dTenés " + waiting
                + " item(s) de la casa de subastas para reclamar: §f/ah reclamar");
        }
    }

    private void later(Runnable action) {
        nextTick.accept(action);
    }
}
