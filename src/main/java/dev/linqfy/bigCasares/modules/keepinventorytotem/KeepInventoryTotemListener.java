package dev.linqfy.bigCasares.modules.keepinventorytotem;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Tótem de Conservación: while one sits anywhere in the inventory, death keeps the inventory (and,
 * by default, the levels) and the totem is spent. Only {@code max-carried} totems fit in one inventory
 * and a use starts a long per-player cooldown stored on the player, so it survives restarts.
 */
final class KeepInventoryTotemListener implements Listener {

    private static final String PREFIX = ChatColor.GOLD + "[Tótem] " + ChatColor.RESET;

    private final KeepInventoryTotemItem item;
    private final KeepInventoryTotemSettings settings;
    private final NamespacedKey lastUseKey;
    private final BukkitRuntimeRegistrations registrations;
    private final LongSupplier clock;
    private final Set<UUID> protectedDeaths = new HashSet<>();
    private final Set<UUID> pendingChecks = new HashSet<>();

    KeepInventoryTotemListener(
        KeepInventoryTotemItem item,
        KeepInventoryTotemSettings settings,
        NamespacedKey lastUseKey,
        BukkitRuntimeRegistrations registrations,
        LongSupplier clock
    ) {
        this.item = item;
        this.settings = settings;
        this.lastUseKey = lastUseKey;
        this.registrations = registrations;
        this.clock = clock;
    }

    /** True while the current death of this player is protected by a totem (read by the tombstone module). */
    boolean protectsDeath(UUID playerId) {
        return protectedDeaths.contains(playerId);
    }

    void shutdown() {
        protectedDeaths.clear();
        pendingChecks.clear();
    }

    // --- death -----------------------------------------------------------------------------------

    // NORMAL: before the Siftite capture (HIGH, skips kept inventories) and the tombstone (HIGHEST).
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            // Already kept (gamerule or an event such as Lifesteal): do not spend the totem.
            return;
        }
        Player player = event.getEntity();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        int slot = KeepInventoryTotemCarryLimit.firstSlot(contents, item::matches);
        if (slot < 0) {
            return;
        }
        PersistentDataContainer data = player.getPersistentDataContainer();
        long now = clock.getAsLong();
        long remaining = KeepInventoryTotemCooldown.remainingMillis(
            data.get(lastUseKey, PersistentDataType.LONG), now, settings.cooldownMillis());
        if (remaining > 0L) {
            player.sendMessage(PREFIX + ChatColor.RED + "Tu tótem todavía se está recargando ("
                + KeepInventoryTotemCooldown.format(remaining) + "). No te salvó esta vez.");
            return;
        }

        ItemStack totem = contents[slot];
        if (totem.getAmount() > 1) {
            totem.setAmount(totem.getAmount() - 1);
            inventory.setItem(slot, totem);
        } else {
            inventory.setItem(slot, null);
        }
        data.set(lastUseKey, PersistentDataType.LONG, now);

        event.setKeepInventory(true);
        event.getDrops().clear();
        if (settings.keepExperience()) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
        protectedDeaths.add(player.getUniqueId());
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0),
            60, 0.4, 0.8, 0.4, 0.3);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 0.8f);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Tu Tótem de Conservación se rompió y guardó tu inventario.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathResolved(PlayerDeathEvent event) {
        protectedDeaths.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Long lastUse = player.getPersistentDataContainer().get(lastUseKey, PersistentDataType.LONG);
        long remaining = KeepInventoryTotemCooldown.remainingMillis(lastUse, clock.getAsLong(),
            settings.cooldownMillis());
        if (remaining > 0L && lastUse != null && clock.getAsLong() - lastUse < 60_000L) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Próximo tótem disponible en "
                + KeepInventoryTotemCooldown.format(remaining) + ".");
        }
    }

    // --- carry limit -----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !item.matches(event.getItem().getItemStack())) {
            return;
        }
        if (carried(player) >= settings.maxCarried()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result != null && item.matches(result)) {
            if (event.getView().getPlayer() instanceof Player player && carried(player) >= settings.maxCarried()) {
                event.getInventory().setResult(null);
            }
            return;
        }
        // No other recipe names the totem: never let it be spent as a plain vanilla ingredient.
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient != null && item.matches(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        scheduleCheck(event.getWhoClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        scheduleCheck(event.getWhoClicked());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        scheduleCheck(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleCheck(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pendingChecks.remove(event.getPlayer().getUniqueId());
        protectedDeaths.remove(event.getPlayer().getUniqueId());
    }

    private void scheduleCheck(HumanEntity entity) {
        if (!(entity instanceof Player player) || !pendingChecks.add(player.getUniqueId())) {
            return;
        }
        registrations.scheduleImmediate("keep-inventory-totem-carry-check", () -> {
            pendingChecks.remove(player.getUniqueId());
            if (player.isOnline() && !player.isDead()) {
                enforceLimit(player);
            }
        });
    }

    private void enforceLimit(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        List<int[]> excess = KeepInventoryTotemCarryLimit.excess(
            contents, item::matches, ItemStack::getAmount, settings.maxCarried());
        if (excess.isEmpty()) {
            return;
        }
        for (int[] removal : excess) {
            int slot = removal[0];
            int amount = removal[1];
            ItemStack stack = contents[slot];
            ItemStack dropped = stack.clone();
            dropped.setAmount(amount);
            if (stack.getAmount() > amount) {
                stack.setAmount(stack.getAmount() - amount);
                inventory.setItem(slot, stack);
            } else {
                inventory.setItem(slot, null);
            }
            player.getWorld().dropItemNaturally(player.getLocation(), dropped);
        }
        player.sendMessage(PREFIX + ChatColor.RED + "Solo podés llevar " + settings.maxCarried()
            + " Tótem de Conservación encima. Tiraste el resto.");
    }

    private int carried(Player player) {
        int total = KeepInventoryTotemCarryLimit.count(
            player.getInventory().getContents(), item::matches, ItemStack::getAmount);
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && item.matches(cursor)) {
            total += cursor.getAmount();
        }
        return total;
    }
}
