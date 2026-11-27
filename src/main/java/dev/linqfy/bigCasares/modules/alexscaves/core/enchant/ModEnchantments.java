package dev.linqfy.bigCasares.modules.alexscaves.core.enchant;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry handles of the module's data-driven enchantments. Before the
 * datapack is loaded (first start) every level reads as 0, so the items still
 * work with their base behaviour.
 */
public final class ModEnchantments {
    private final Map<ModEnchantment, Enchantment> loaded;

    private ModEnchantments(Map<ModEnchantment, Enchantment> loaded) {
        this.loaded = loaded;
    }

    public static ModEnchantments lookup() {
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        Map<ModEnchantment, Enchantment> found = new EnumMap<>(ModEnchantment.class);
        for (ModEnchantment enchantment : ModEnchantment.values()) {
            Enchantment value = registry.get(new NamespacedKey(ModEnchantment.NAMESPACE, enchantment.id()));
            if (value != null) {
                found.put(enchantment, value);
            }
        }
        return new ModEnchantments(found);
    }

    public int level(ItemStack item, ModEnchantment enchantment) {
        Enchantment value = loaded.get(enchantment);
        return value == null || item == null ? 0 : item.getEnchantmentLevel(value);
    }

    public Optional<ModEnchantment> moduleEnchantment(Enchantment enchantment) {
        NamespacedKey key = enchantment.getKey();
        return ModEnchantment.NAMESPACE.equals(key.getNamespace()) ? ModEnchantment.parse(key.getKey()) : Optional.empty();
    }

    public int loadedCount() {
        return loaded.size();
    }
}
