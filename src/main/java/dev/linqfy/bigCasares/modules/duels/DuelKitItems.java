package dev.linqfy.bigCasares.modules.duels;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.logging.Logger;

/** Turns kit specs into items and equips players. */
final class DuelKitItems {

    private final Logger logger;

    DuelKitItems(Logger logger) {
        this.logger = logger;
    }

    void equip(Player player, DuelKit kit) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        player.setItemOnCursor(null);
        for (int i = 0; i < kit.items().size(); i++) {
            ItemStack item = build(kit.items().get(i));
            if (item != null) {
                inventory.setItem(i, item);
            }
        }
        inventory.setHelmet(build(kit.armor().get(0)));
        inventory.setChestplate(build(kit.armor().get(1)));
        inventory.setLeggings(build(kit.armor().get(2)));
        inventory.setBoots(build(kit.armor().get(3)));
        inventory.setItemInOffHand(build(kit.offhand()));
        player.updateInventory();
    }

    ItemStack build(DuelItemSpec spec) {
        if (spec == null) {
            return null;
        }
        Material material = Material.matchMaterial(spec.material());
        if (material == null || !material.isItem() || material.isAir()) {
            logger.warning("[Duelos] Material desconocido en un kit: " + spec.material());
            return null;
        }
        ItemStack item = new ItemStack(material, spec.amount());
        for (Map.Entry<String, Integer> entry : spec.enchantments().entrySet()) {
            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(entry.getKey()));
            if (enchantment == null) {
                logger.warning("[Duelos] Encantamiento desconocido en un kit: " + entry.getKey());
                continue;
            }
            item.addUnsafeEnchantment(enchantment, entry.getValue());
        }
        return item;
    }

    Material icon(DuelKit kit) {
        Material material = Material.matchMaterial(kit.icon());
        return material == null || !material.isItem() || material.isAir() ? Material.CHEST : material;
    }
}
