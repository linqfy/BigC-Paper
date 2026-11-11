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
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class TombstoneListener implements Listener {
    private final TombstoneRuntime runtime;
    private final BukkitRuntimeRegistrations registrations;
    private final Predicate<UUID> deathCaptureEligibility;
    /** Tombstones with a reconcile already queued; shift-click spam then costs one save per tick. */
    private final Set<UUID> pendingReconcile = new HashSet<>();

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
        List<ItemStack> items = snapshot(player);
        runtime.logDeathInventory(player.getName(), items);

        org.bukkit.Location loc = player.getLocation();
        player.sendMessage(org.bukkit.ChatColor.YELLOW + "Has muerto en las coordenadas: " + 
            org.bukkit.ChatColor.RED + "X: " + loc.getBlockX() + " Y: " + loc.getBlockY() + " Z: " + loc.getBlockZ());

        event.getDrops().clear();
        event.setKeepInventory(false);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(null);
        player.setItemOnCursor(null);
        runtime.create(player.getUniqueId(), player.getName(), player.getLocation(), items);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        UUID id = runtime.tombstoneId(event.getRightClicked());
        if (id == null) return;
        event.setCancelled(true);
        runtime.open(id, event.getPlayer());
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
            "tombstone-selection-open", () -> runtime.open(id, player), 1L
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
            runtime.reconcile(holder.tombstoneId());
        }
    }

    private void reconcileNextTick(org.bukkit.inventory.InventoryHolder holder) {
        if (holder instanceof TombstoneInventoryHolder tombstone && pendingReconcile.add(tombstone.tombstoneId())) {
            UUID id = tombstone.tombstoneId();
            registrations.scheduleDelayed("tombstone-inventory-reconcile", () -> {
                pendingReconcile.remove(id);
                runtime.reconcile(id);
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
}
