package dev.linqfy.bigCasares.modules.customcrossbow;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.concurrent.ThreadLocalRandom;

final class CrossbowEnchantmentListener implements Listener {

    private final CrossbowEnchantmentData data;

    CrossbowEnchantmentListener(CrossbowEnchantmentData data) {
        this.data = data;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        CrossbowEnchantment book = data.book(second);
        CrossbowEnchantment left = data.enchantment(first);
        CrossbowEnchantment right = data.enchantment(second);
        if (book == null && left == null && right == null) {
            return;
        }
        if (first == null || first.getType() != Material.CROSSBOW
            || data.rounds(first) > 0 || data.rounds(second) > 0 || loaded(second)) {
            event.setResult(null);
            return;
        }
        if (book != null) {
            if (loaded(first) || !CrossbowEnchantmentRules.canApply(left, book,
                first.containsEnchantment(Enchantment.MULTISHOT))) {
                event.setResult(null);
                return;
            }
            // A custom book can also carry stored vanilla enchantments after
            // book merging. Retain vanilla's valid upgrades and their cost.
            ItemStack result = event.getResult() == null ? first.clone() : event.getResult().clone();
            if (result.containsEnchantment(Enchantment.MULTISHOT)) {
                event.setResult(null);
                return;
            }
            data.apply(result, book);
            String rename = event.getView().getRenameText();
            if (rename != null) {
                ItemMeta meta = result.getItemMeta();
                meta.setDisplayName(rename.isBlank() ? null : rename);
                result.setItemMeta(meta);
            }
            event.getView().setRepairCost(Math.max(4, event.getView().getRepairCost()));
            event.getView().setRepairItemCountCost(1);
            event.setResult(result);
            return;
        }
        ItemStack result = event.getResult();
        if ((left != null && right != null && left != right)
            || (result != null && result.containsEnchantment(Enchantment.MULTISHOT))) {
            event.setResult(null);
        } else if (result != null) {
            // Vanilla copies the first input's PDC; also retain an enchantment
            // supplied by the sacrificed crossbow during an ordinary repair.
            data.apply(result, left != null ? left : right);
            event.setResult(result);
        }
    }

    private boolean loaded(ItemStack item) {
        return item != null && item.getItemMeta() instanceof CrossbowMeta meta && meta.hasChargedProjectiles();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantOffers(PrepareItemEnchantEvent event) {
        CrossbowEnchantment applied = data.enchantment(event.getItem());
        EnchantmentOffer[] offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) {
            if (offers[i] != null && CrossbowEnchantmentRules.blocksVanillaEnchantment(applied,
                offers[i].getEnchantment().getKey().getKey())) {
                offers[i] = null;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        CrossbowEnchantment applied = data.enchantment(event.getItem());
        if (applied == null) {
            return;
        }
        event.getEnchantsToAdd().keySet().removeIf(enchantment ->
            CrossbowEnchantmentRules.blocksVanillaEnchantment(applied, enchantment.getKey().getKey()));
        if (event.getEnchantsToAdd().isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        ItemStack first = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);
        if (data.enchantment(first) == null && data.enchantment(second) == null) {
            return;
        }
        if (data.rounds(first) > 0 || data.rounds(second) > 0) {
            event.setResult(null);
            return;
        }
        ItemStack result = event.getResult();
        if (result == null && (first == null || second == null)) {
            result = (first == null ? second : first).clone();
        }
        if (result != null) {
            data.remove(result);
            event.setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        if (event.isPlugin() || event.getLootTable() == null
            || !event.getLootTable().getKey().getNamespace().equals("minecraft")) {
            return;
        }
        CrossbowEnchantment enchantment = lootBook(event.getLootTable().getKey().getKey(),
            ThreadLocalRandom.current().nextDouble());
        if (enchantment != null) {
            event.getLoot().add(new CrossbowEnchantmentBook(enchantment).createItemStack(1));
        }
    }

    static CrossbowEnchantment lootBook(String table, double roll) {
        return switch (table) {
            case "chests/end_city_treasure", "chests/ancient_city" ->
                roll < 0.08 ? CrossbowEnchantment.SNIPER : null;
            case "chests/pillager_outpost", "chests/woodland_mansion", "chests/simple_dungeon" ->
                roll < 0.12 ? CrossbowEnchantment.MULTICHARGE : null;
            default -> null;
        };
    }
}
