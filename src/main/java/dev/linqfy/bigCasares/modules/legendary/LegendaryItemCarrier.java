package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Changes the real item type while retaining complete prototype components, not just metadata overrides. */
final class LegendaryItemCarrier implements Listener, AutoCloseable {
    private final BukkitRuntimeRegistrations registrations;
    private final Set<UUID> queued = new HashSet<>();

    LegendaryItemCarrier(BukkitRuntimeRegistrations registrations) {
        this.registrations = registrations;
    }

    static boolean upgrade(ItemStack stack, LegendaryWeapon weapon) {
        Material carrier = Material.valueOf(weapon.itemMaterial());
        if (stack.getType() == carrier) return false;
        ItemStack source = stack.clone();
        stack.setType(carrier);
        // Includes native MAX_DAMAGE, ENCHANTABLE, REPAIRABLE, TOOL and spear use/attack components.
        stack.copyDataFrom(source, type -> true);
        var meta = stack.getItemMeta();
        LegendaryCombatAttributes.initialize(meta, weapon);
        meta.displayName(weapon.coloredName(meta.displayName()));
        stack.setItemMeta(meta);
        return true;
    }

    static ItemStack familyCopy(ItemStack stack, LegendaryWeapon weapon) {
        ItemStack copy = stack.clone();
        copy.setType(Material.valueOf(weapon.material));
        copy.copyDataFrom(stack, type -> true);
        return copy;
    }

    static void inventory(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            var weapon = LegendaryItemIdentity.weapon(item).orElse(null);
            if (weapon != null && upgrade(item, weapon)) inventory.setItem(slot, item);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void join(PlayerJoinEvent event) { inventory(event.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void held(PlayerItemHeldEvent event) {
        var inventory = event.getPlayer().getInventory();
        ItemStack item = inventory.getItem(event.getNewSlot());
        var weapon = LegendaryItemIdentity.weapon(item).orElse(null);
        if (weapon != null && upgrade(item, weapon)) inventory.setItem(event.getNewSlot(), item);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ItemStack item = event.getItem().getItemStack();
        var weapon = LegendaryItemIdentity.weapon(item).orElse(null);
        if (weapon != null && upgrade(item, weapon)) event.getItem().setItemStack(item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) afterTransaction(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) afterTransaction(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepareCraft(PrepareItemCraftEvent event) {
        if (containsWeapon(event.getInventory().getMatrix())) event.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (containsWeapon(event.getInventory().getMatrix())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void automatedCraft(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter
                && containsWeapon(crafter.getInventory().getContents())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectResult(InventoryClickEvent event) {
        var inventory = event.getView().getTopInventory();
        String type = inventory.getType().name();
        if (event.getRawSlot() != 2 || !(type.equals("ANVIL") || type.equals("MERCHANT")
                || type.equals("CARTOGRAPHY"))) return;
        var first = LegendaryItemIdentity.weapon(inventory.getItem(0)).orElse(null);
        var second = LegendaryItemIdentity.weapon(inventory.getItem(1)).orElse(null);
        if (protectedResult(type, first, second)) event.setCancelled(true);
    }

    static boolean protectedResult(String type, LegendaryWeapon first, LegendaryWeapon second) {
        if (type.equals("MERCHANT") || type.equals("CARTOGRAPHY"))
            return first != null || second != null;
        return type.equals("ANVIL") && second != null && first != second;
    }

    private static boolean containsWeapon(ItemStack[] items) {
        for (ItemStack item : items) if (LegendaryItemIdentity.isLegendary(item)) return true;
        return false;
    }

    private void afterTransaction(Player player) {
        if (!queued.add(player.getUniqueId())) return;
        registrations.scheduleImmediate("legendary-carrier-inventory", () -> {
            queued.remove(player.getUniqueId());
            if (player.isOnline()) inventory(player);
        });
    }

    @Override public void close() { queued.clear(); }
}
