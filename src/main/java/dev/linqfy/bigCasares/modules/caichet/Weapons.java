package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;

/** El arma de un doble: una copia de la que mas pega en el inventario del jugador copiado. */
final class Weapons {

    private Weapons() {
    }

    /** Copia (con sus encantamientos) del item que mas pega del inventario, o null si no tiene nada mejor que el puño. */
    static ItemStack bestOf(Player player) {
        ItemStack best = null;
        double bestDamage = 1.0; // el puño
        for (ItemStack item : player.getInventory().getContents()) {
            double damage = damage(item);
            if (damage > bestDamage) {
                bestDamage = damage;
                best = item;
            }
        }
        if (best == null) {
            return null;
        }
        ItemStack copy = best.clone();
        copy.setAmount(1);
        return copy;
    }

    /** Daño de un golpe con este item (como lo calcula el juego para un jugador, sin crítico). */
    static double damage(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 1.0;
        }
        double damage = 1.0;
        ItemMeta meta = item.getItemMeta();
        Collection<AttributeModifier> modifiers = meta != null && meta.hasAttributeModifiers()
                ? meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE)
                : item.getType().getDefaultAttributeModifiers(EquipmentSlot.HAND).get(Attribute.ATTACK_DAMAGE);
        if (modifiers != null) {
            for (AttributeModifier modifier : modifiers) {
                if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                    damage += modifier.getAmount();
                }
            }
        }
        int sharpness = item.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpness > 0) {
            damage += 0.5 * sharpness + 0.5;
        }
        return damage;
    }
}
