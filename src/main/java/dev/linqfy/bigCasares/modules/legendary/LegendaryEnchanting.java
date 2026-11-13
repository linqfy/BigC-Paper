package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.EnchantmentView;

import java.util.Objects;
import java.util.IdentityHashMap;
import java.util.Map;

/** Native rules run on disposable copies in unopened menus; every obtainable result keeps its neutral carrier. */
final class LegendaryEnchanting implements Listener, AutoCloseable {
    private record Price(Player player, org.bukkit.inventory.view.AnvilView view, ItemStack first, ItemStack second,
                         ItemStack result, String name) { }
    private final LegendaryVanillaBridge vanilla;
    private final BukkitRuntimeRegistrations registrations;
    private final Map<PrepareAnvilEvent, Price> prices = new IdentityHashMap<>();
    private boolean preparingMirror;

    LegendaryEnchanting(LegendaryVanillaBridge vanilla, BukkitRuntimeRegistrations registrations) {
        this.vanilla = vanilla;
        this.registrations = registrations;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void offers(PrepareItemEnchantEvent event) {
        var weapon = LegendaryItemIdentity.weapon(event.getItem()).orElse(null);
        if (preparingMirror || weapon == null) return;
        preparingMirror = true;
        EnchantmentView mirror = null;
        try {
            mirror = enchantingMirror(event.getEnchanter(), event.getEnchantBlock().getLocation(),
                    event.getView().getEnchantmentSeed(), event.getItem(), weapon);
            var offers = mirror.getOffers();
            System.arraycopy(offers, 0, event.getOffers(), 0, offers.length);
        } finally {
            clearMirror(mirror);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void enchant(EnchantItemEvent event) {
        var weapon = LegendaryItemIdentity.weapon(event.getItem()).orElse(null);
        if (preparingMirror || weapon == null || !(event.getView() instanceof EnchantmentView actual)) return;
        preparingMirror = true;
        EnchantmentView mirror = null;
        try {
            mirror = enchantingMirror(event.getEnchanter(), event.getEnchantBlock().getLocation(),
                    actual.getEnchantmentSeed(), event.getItem(), weapon);
            event.getEnchantsToAdd().clear();
            event.getEnchantsToAdd().putAll(vanilla.roll(mirror,
                    LegendaryItemCarrier.familyCopy(event.getItem(), weapon), event.whichButton(), event.getExpLevelCost()));
            ItemStack item = event.getItem().clone();
            LegendaryItemCarrier.upgrade(item, weapon);
            event.setItem(item);
        } finally {
            clearMirror(mirror);
        }
    }

    private EnchantmentView enchantingMirror(Player player, org.bukkit.Location location, int seed,
                                             ItemStack item, LegendaryWeapon weapon) {
        var view = MenuType.ENCHANTMENT.builder().location(location).checkReachable(false).build(player);
        view.setEnchantmentSeed(seed);
        view.getTopInventory().setItem(0, LegendaryItemCarrier.familyCopy(item, weapon));
        return view;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void anvil(PrepareAnvilEvent event) {
        if (preparingMirror || !(event.getView().getPlayer() instanceof Player player)) return;
        ItemStack first = event.getInventory().getItem(0), second = event.getInventory().getItem(1);
        var weapon = LegendaryItemIdentity.weapon(first).orElse(null);
        var other = LegendaryItemIdentity.weapon(second).orElse(null);
        if (weapon == null) {
            if (other != null) event.setResult(null);
            return;
        }
        if (second != null && !compatibleAddition(weapon, other, second.getType().name())) {
            event.setResult(null);
            return;
        }
        preparingMirror = true;
        var actual = event.getView();
        org.bukkit.inventory.view.AnvilView mirror = null;
        try {
            mirror = MenuType.ANVIL.builder().location(player.getLocation()).checkReachable(false).build(player);
            mirror.setMaximumRepairCost(actual.getMaximumRepairCost());
            mirror.bypassEnchantmentLevelRestriction(actual.bypassesEnchantmentLevelRestriction());
            mirror.getTopInventory().setItem(0, LegendaryItemCarrier.familyCopy(first, weapon));
            mirror.getTopInventory().setItem(1, second == null ? null : other == null ? second.clone()
                    : LegendaryItemCarrier.familyCopy(second, other));
            vanilla.rename(mirror, actual.getRenameText());
            ItemStack result = mirror.getTopInventory().getItem(2);
            if (result != null) {
                result = result.clone();
                LegendaryItemCarrier.upgrade(result, weapon);
            }
            event.setResult(result);
            int cost = Math.max(0, mirror.getRepairCost()), materialCost = mirror.getRepairItemCountCost();
            actual.setRepairCost(cost);
            actual.setRepairItemCountCost(materialCost);
            // Keep the final plugin-adjusted price tied to this exact transaction.
            // A later native recalculation may invalidate a custom result's price.
            prices.put(event, new Price(player, actual, first.clone(), second == null ? null : second.clone(),
                    result == null ? null : result.clone(), actual.getRenameText()));
        } finally {
            clearMirror(mirror);
        }
    }

    static boolean compatibleAddition(LegendaryWeapon first, LegendaryWeapon second, String material) {
        return second != null ? second == first : !material.equals(first.material);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void pricePrepared(PrepareAnvilEvent event) {
        Price price = prices.remove(event);
        if (price == null || !Objects.equals(event.getResult(), price.result())) return;
        int cost = price.view().getRepairCost(), materialCost = price.view().getRepairItemCountCost();
        if (cost < 0) return;
        registrations.scheduleImmediate("legendary-anvil-price", () -> {
            var view = price.view();
            if (price.player().isOnline() && price.player().getOpenInventory() == view && view.getRepairCost() < 0
                    && Objects.equals(view.getTopInventory().getItem(0), price.first())
                    && Objects.equals(view.getTopInventory().getItem(1), price.second())
                    && Objects.equals(view.getTopInventory().getItem(2), price.result())
                    && Objects.equals(view.getRenameText(), price.name())) {
                view.setRepairCost(cost);
                view.setRepairItemCountCost(materialCost);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void grindstone(PrepareGrindstoneEvent event) {
        ItemStack first = event.getInventory().getItem(0), second = event.getInventory().getItem(1);
        var firstWeapon = LegendaryItemIdentity.weapon(first).orElse(null);
        var secondWeapon = LegendaryItemIdentity.weapon(second).orElse(null);
        if (firstWeapon == null && secondWeapon == null) return;
        boolean both = first != null && !first.getType().isAir() && second != null && !second.getType().isAir();
        var expected = firstWeapon != null ? firstWeapon : secondWeapon;
        if ((both && (firstWeapon == null || firstWeapon != secondWeapon))
                || (event.getResult() != null && LegendaryItemIdentity.weapon(event.getResult()).orElse(null) != expected)) {
            event.setResult(null);
        }
    }

    private void clearMirror(org.bukkit.inventory.InventoryView mirror) {
        try {
            if (mirror != null) mirror.getTopInventory().clear();
        } finally {
            preparingMirror = false;
        }
    }

    @Override public void close() { prices.clear(); }
}
