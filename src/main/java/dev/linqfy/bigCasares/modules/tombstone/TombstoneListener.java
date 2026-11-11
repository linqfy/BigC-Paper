package dev.linqfy.bigCasares.modules.tombstone;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class TombstoneListener implements Listener {
    private final TombstoneRuntime runtime;
    private final BukkitRuntimeRegistrations registrations;
    private final Predicate<UUID> deathCaptureEligibility;
    private final Set<UUID> pendingReconciles = new HashSet<>();

    public TombstoneListener(
        TombstoneRuntime runtime,
        BukkitRuntimeRegistrations registrations,
        Predicate<UUID> deathCaptureEligibility
    ) {
        this.runtime = runtime;
        this.registrations = registrations;
        this.deathCaptureEligibility = deathCaptureEligibility;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!deathCaptureEligibility.test(player.getUniqueId())) {
            return;
        }
        List<ItemStack> layout = snapshot(player);
        // The drops are what this death really loses once every other listener ran (vanishing curse, items other
        // modules keep or destroy). With keepInventory nothing drops, so the inventory minus the kept items counts.
        List<ItemStack> items = event.getKeepInventory()
            ? TombstoneItemLayout.withoutKept(layout, event.getItemsToKeep(), ItemStack::isSimilar)
            : TombstoneItemLayout.claimDrops(layout, event.getDrops(), ItemStack::isSimilar);
        if (!TombstoneItemLayout.hasItems(items)) {
            return;
        }

        StringBuilder logMessage = new StringBuilder("Inventario de " + player.getName() + " antes de morir:\n");
        for (ItemStack item : items) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                logMessage.append("- ").append(item.getAmount()).append("x ").append(item.getType().name()).append("\n");
            }
        }
        org.bukkit.Bukkit.getLogger().info(logMessage.toString());

        org.bukkit.Location loc = player.getLocation();
        player.sendMessage(org.bukkit.ChatColor.YELLOW + "Has muerto en las coordenadas: " +
            org.bukkit.ChatColor.RED + "X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ());

        // Recorded first: if it cannot be saved, the drops and inventory stay untouched and fall as in vanilla.
        if (!runtime.create(player.getUniqueId(), player.getName(), loc, items)) {
            return;
        }
        event.getDrops().clear();
        event.setKeepInventory(false);
        takeStoredItems(player, layout, items);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (runtime.tombstoneId(event.getRightClicked()) == null) return;
        event.setCancelled(true);
        // The client sends one interaction per hand; opening twice closes and reopens the inventory mid-click.
        if (event.getHand() != EquipmentSlot.HAND) return;
        runtime.openFromEntity(event.getRightClicked(), event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        runtime.discardStrays(event.getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        reconcileNextTick(event.getView().getTopInventory().getHolder());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSelectionClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TombstoneSelectionHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        holder.tombstoneAt(event.getRawSlot()).ifPresent(id -> registrations.scheduleDelayed(
            "tombstone-selection-open", () -> {
                if (player.isOnline()) runtime.open(id, player);
            }, 1L
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        reconcileNextTick(event.getView().getTopInventory().getHolder());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSelectionDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TombstoneSelectionHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof TombstoneInventoryHolder holder) {
            runtime.reconcile(holder.tombstoneId(), event.getPlayer());
        }
    }

    private void reconcileNextTick(org.bukkit.inventory.InventoryHolder holder) {
        if (holder instanceof TombstoneInventoryHolder tombstone && pendingReconciles.add(tombstone.tombstoneId())) {
            // Several clicks in one tick share one save.
            registrations.scheduleDelayed("tombstone-inventory-reconcile", () -> {
                pendingReconciles.remove(tombstone.tombstoneId());
                runtime.reconcile(tombstone.tombstoneId(), null);
            }, 1L);
        }
    }

    private List<ItemStack> snapshot(Player player) {
        return TombstoneItemLayout.snapshot(
            player.getInventory().getStorageContents(),
            player.getInventory().getArmorContents(),
            player.getInventory().getItemInOffHand(),
            player.getItemOnCursor()
        );
    }

    /**
     * Takes out of the inventory exactly what went into the tombstone. Whatever is left is handled by vanilla:
     * items kept by other listeners stay, the rest is cleared because keepInventory is now off.
     */
    private static void takeStoredItems(Player player, List<ItemStack> layout, List<ItemStack> stored) {
        List<ItemStack> remaining = TombstoneItemLayout.remainder(layout, stored);
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < TombstoneItemLayout.CURSOR_SLOT; slot++) {
            if (layout.get(slot) != null) inventory.setItem(slot, remaining.get(slot));
        }
        if (layout.get(TombstoneItemLayout.CURSOR_SLOT) != null) {
            player.setItemOnCursor(remaining.get(TombstoneItemLayout.CURSOR_SLOT));
        }
    }
}
