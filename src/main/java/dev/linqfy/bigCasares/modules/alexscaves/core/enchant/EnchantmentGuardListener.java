package dev.linqfy.bigCasares.modules.alexscaves.core.enchant;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.Map;
import java.util.Optional;

/**
 * The datapack enchantments target vanilla materials (the module items are a
 * shield underneath), so tables and anvils would offer them on any shield. Each
 * one is only allowed on its own module item, as in the mod.
 */
public final class EnchantmentGuardListener implements Listener {
    private final ModEnchantments enchantments;
    private final CustomItemRegistry items;

    public EnchantmentGuardListener(ModEnchantments enchantments, CustomItemRegistry items) {
        this.enchantments = enchantments;
        this.items = items;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemEnchantEvent event) {
        EnchantmentOffer[] offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) {
            if (offers[i] != null && !allowed(event.getItem(), offers[i].getEnchantment())) {
                offers[i] = null;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEnchant(EnchantItemEvent event) {
        event.getEnchantsToAdd().keySet().removeIf(enchantment -> !allowed(event.getItem(), enchantment));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.isEmpty()) {
            return;
        }
        if (result.getItemMeta() instanceof EnchantmentStorageMeta) {
            // Books may carry them; only applying one to the wrong item is blocked.
            return;
        }
        Map<Enchantment, Integer> present = result.getEnchantments();
        for (Enchantment enchantment : present.keySet()) {
            if (!allowed(result, enchantment)) {
                event.setResult(null);
                return;
            }
        }
    }

    private boolean allowed(ItemStack item, Enchantment enchantment) {
        Optional<ModEnchantment> module = enchantments.moduleEnchantment(enchantment);
        if (module.isEmpty() || isBook(item)) {
            // The module items are shields with no enchantable component, so the table never takes them:
            // books are the survival route, and the anvil check below keeps them off other items.
            return true;
        }
        return allowedOn(items.resolveItemId(item), module.get());
    }

    static boolean isBook(ItemStack item) {
        return item != null && (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK);
    }

    static boolean allowedOn(Optional<String> itemId, ModEnchantment enchantment) {
        return itemId.filter(enchantment.itemId()::equals).isPresent();
    }
}
