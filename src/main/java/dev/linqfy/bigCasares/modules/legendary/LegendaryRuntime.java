package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.BigCasares;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/** Inventory transaction guards and recovery of spear receipts from older versions. */
final class LegendaryRuntime implements Listener, Runnable {
    private final BigCasares plugin;
    private final LegendaryStateStore store;
    private final LegendarySpearEscrow escrow;
    private final BukkitRuntimeRegistrations registrations;
    private final NamespacedKey temporary = new NamespacedKey("bigcasares", "legendary_temporary");
    private final java.util.function.Predicate<UUID> eventTransaction;
    private int tick;
    private boolean stopped;

    LegendaryRuntime(
            BigCasares plugin,
            LegendaryStateStore store,
            BukkitRuntimeRegistrations registrations,
            java.util.function.Predicate<UUID> eventTransaction) {
        this.plugin = plugin;
        this.eventTransaction = eventTransaction;
        this.store = store;
        this.escrow = new LegendarySpearEscrow(store);
        this.registrations = registrations;
    }

    boolean blocked(Player player) {
        return crafting(player) || escrow.locked(player, player.getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (escrow.locked(event.getPlayer(), event.getItem()) || crafting(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void melee(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && blocked(player))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void falling(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && crafting(player))
            event.setCancelled(true);
    }

    @Override
    public void run() {
        if (stopped || ++tick % 20 != 0) return;
        for (Player player : plugin.getServer().getOnlinePlayers())
            if (!player.isDead()) recover(player);
    }

    @EventHandler
    public void drop(PlayerDropItemEvent event) {
        if (escrow.locked(event.getPlayer(), event.getItemDrop().getItemStack())
                || crafting(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void pendingConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (crafting(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void swap(PlayerSwapHandItemsEvent event) {
        if (escrow.locked(event.getPlayer(), event.getMainHandItem())
                || escrow.locked(event.getPlayer(), event.getOffHandItem())
                || crafting(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void inventory(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (crafting(player)
                || escrow.locked(player, event.getCurrentItem())
                || escrow.locked(player, event.getCursor())
                || (event.getHotbarButton() >= 0
                        && escrow.locked(
                                player, player.getInventory().getItem(event.getHotbarButton())))
                || (event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                        && escrow.locked(player, player.getInventory().getItemInOffHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (crafting(player)
                || escrow.locked(player, event.getOldCursor())
                || event.getNewItems().values().stream()
                        .anyMatch(item -> escrow.locked(player, item))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void death(PlayerDeathEvent event) {
        event.getDrops().removeIf(item -> escrow.locked(event.getEntity(), item));
        if (escrow.pending(event.getEntity().getUniqueId())
                && "on-join"
                        .equals(
                                plugin.getConfig()
                                        .getString("legendary-weapons.spear.death-return"))) {
            try {
                store.put("spear.defer." + event.getEntity().getUniqueId(), "true");
            } catch (IOException failure) {
                reportRecovery(failure);
            }
        }
        // Escrow remains until a safe respawn. The configurable policy selects
        // immediate respawn recovery or a later reconnect recovery, never a drop.
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        if (!"on-join"
                .equals(
                        plugin.getConfig()
                                .getString("legendary-weapons.spear.death-return", "on-respawn"))) {
            registrations.scheduleImmediate("spear-respawn", () -> recover(event.getPlayer()));
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        try {
            store.put("spear.defer." + event.getPlayer().getUniqueId(), null);
        } catch (IOException failure) {
            reportRecovery(failure);
            return;
        }
        registrations.scheduleImmediate(
                "legendary-spear-join-recovery", () -> recover(event.getPlayer()));
    }

    void cleanTemporary(Entity[] entities) {
        for (Entity entity : entities) {
            if (entity.getPersistentDataContainer().has(temporary, PersistentDataType.BYTE))
                entity.remove();
        }
    }

    void recover(Player player) {
        if (store.get("spear.defer." + player.getUniqueId()) != null) return;
        try {
            escrow.restore(player);
        } catch (IOException failure) {
            reportRecovery(failure);
        }
    }

    void shutdown() {
        stopped = true;
    }

    private boolean crafting(Player player) {
        return eventTransaction.test(player.getUniqueId())
                || store.get("craft." + player.getUniqueId()) != null;
    }

    private void reportRecovery(Exception failure) {
        plugin.getLogger()
                .log(
                        Level.SEVERE,
                        "La devolución sigue pendiente en el diario persistente.",
                        failure);
    }
}
