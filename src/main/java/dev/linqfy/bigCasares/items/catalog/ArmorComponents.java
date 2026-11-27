package dev.linqfy.bigCasares.items.catalog;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Enchantable;
import io.papermc.paper.datacomponent.item.Repairable;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.set.RegistrySet;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Armor parts that only exist as data components: what repairs it in an anvil and its enchantment value. Kept apart so
 * that code that builds plain stacks does not load the data component API.
 */
final class ArmorComponents {
    private ArmorComponents() {
    }

    static void apply(ItemStack stack, ItemArmorDefinition armor) {
        if (armor.repairItem() == null) {
            stack.resetData(DataComponentTypes.REPAIRABLE);
        } else {
            Material repair = Material.matchMaterial(armor.repairItem());
            if (repair == null || !repair.isItem()) {
                throw new IllegalArgumentException("armor repair item is not available: " + armor.repairItem());
            }
            stack.setData(DataComponentTypes.REPAIRABLE, Repairable.repairable(RegistrySet.keySet(RegistryKey.ITEM,
                TypedKey.create(RegistryKey.ITEM, repair.getKey()))));
        }
        if (armor.enchantability() > 0) {
            stack.setData(DataComponentTypes.ENCHANTABLE, Enchantable.enchantable(armor.enchantability()));
        } else {
            stack.resetData(DataComponentTypes.ENCHANTABLE);
        }
    }
}
