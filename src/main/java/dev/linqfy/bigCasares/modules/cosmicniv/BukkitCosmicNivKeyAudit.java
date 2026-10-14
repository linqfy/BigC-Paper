package dev.linqfy.bigCasares.modules.cosmicniv;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BukkitCosmicNivKeyAudit {
    private BukkitCosmicNivKeyAudit() {
    }

    static List<String> unknownKeys(CosmicNivSettings settings) {
        Set<String> items = new LinkedHashSet<>(settings.armorValues().keySet());
        settings.weaponFamilies().values().forEach(members -> items.addAll(members.keySet()));
        settings.toolFamilies().values().forEach(members -> items.addAll(members.keySet()));
        Set<String> enchantments = new LinkedHashSet<>(settings.enchantmentValues().keySet());
        for (CosmicNivReference reference : List.of(settings.zeroReference(), settings.maxReference())) {
            reference.armor().values().forEach(item -> enchantments.addAll(item.enchantments().keySet()));
            for (Map<String, CosmicNivItem> held : List.of(reference.weapons(), reference.tools())) {
                held.values().forEach(item -> enchantments.addAll(item.enchantments().keySet()));
            }
        }
        for (CosmicNivCustomItem custom : settings.customItems()) {
            if (custom.match().material() != null) {
                items.add(custom.match().material());
            }
        }

        List<String> warnings = new ArrayList<>();
        for (String item : items) {
            if (item.startsWith(CosmicNivCustomItem.NAMESPACE + ":")) {
                continue;
            }
            Material material = Material.matchMaterial(item);
            if (material == null || !material.isItem()) {
                warnings.add(item + " is not an item on this server; it will never match");
            }
        }
        Registry<Enchantment> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (String enchantment : enchantments) {
            NamespacedKey key = NamespacedKey.fromString(enchantment);
            if (key == null || registry.get(key) == null) {
                warnings.add(enchantment + " is not an enchantment on this server; it will never match");
            }
        }
        return List.copyOf(warnings);
    }
}
