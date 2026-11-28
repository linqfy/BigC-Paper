package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.items.CustomItem;
import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.function.Function;

public final class ShopItemResolver {

    private final CustomItemRegistry registry;
    private final Function<ItemStack, Optional<String>> customIdLookup;

    public ShopItemResolver(CustomItemRegistry registry) {
        // resolveItemId also reports custom ids that are no longer registered, so such a stack is never
        // mistaken for the plain vanilla item it is built on.
        this(registry, registry::resolveItemId);
    }

    ShopItemResolver(CustomItemRegistry registry, Function<ItemStack, Optional<String>> customIdLookup) {
        this.registry = registry;
        this.customIdLookup = customIdLookup;
    }

    public ItemStack createStack(ShopEntry entry) {
        if (entry.material() != null) {
            return new ItemStack(entry.material(), entry.amount());
        }

        CustomItem item = registry.findById(entry.customItemId())
            .orElseThrow(() -> new IllegalArgumentException("Custom item desconocido: " + entry.customItemId()));
        return item.createItemStack(entry.amount());
    }

    /** A custom product can only be traded while its item is still registered. */
    boolean available(ShopEntry entry) {
        return entry.material() != null || registry.findById(entry.customItemId()).isPresent();
    }

    public boolean matches(ItemStack stack, ShopEntry entry) {
        if (stack == null || stack.getAmount() < 1) {
            return false;
        }
        String customId;
        try {
            customId = customIdLookup.apply(stack).orElse(null);
        } catch (RuntimeException unavailable) {
            // Unknown identity must not be treated as a confirmed vanilla item.
            return false;
        }
        if (entry.material() != null) {
            // A custom item built on this material is not the vanilla item the entry trades.
            return stack.getType() == entry.material() && customId == null;
        }
        // Custom entries only match registry items. Comparing base materials would let a
        // plain vanilla stack (for example a compass) sell at the custom item's price.
        return customId != null && customId.equalsIgnoreCase(entry.customItemId());
    }

}
