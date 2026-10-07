package dev.linqfy.bigCasares.modules.donpollos;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

final class DonPollosItems {

    static final String NAMESPACE = "donpollos";

    private final NamespacedKey productKey;

    DonPollosItems(JavaPlugin plugin) {
        this.productKey = new NamespacedKey(plugin, "product");
    }

    ItemStack create(DonPollosProduct product, int amount) {
        ItemStack stack = new ItemStack(product == DonPollosProduct.POLLO_FRITO ? Material.COOKED_CHICKEN : Material.COOKIE);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(NAMESPACE, product.modelKey()));
        if (product.effect() != null) {
            PotionEffectType type = Registry.EFFECT.getOrThrow(NamespacedKey.minecraft(product.effect()));
            stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(1.0f)
                .animation(ItemUseAnimation.DRINK)
                .sound(Key.key("minecraft", "entity.generic.drink"))
                .hasConsumeParticles(false)
                .addEffect(ConsumeEffect.applyStatusEffects(
                    List.of(new PotionEffect(type, product.seconds() * 20, product.amplifier())), 1.0f))
                .build());
            stack.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(1).saturation(0.4f).canAlwaysEat(true).build());
        } else {
            stack.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(8).saturation(9.6f).build());
        }
        stack.editMeta(meta -> {
            meta.setDisplayName(product.displayName());
            meta.setLore(product.lore());
            meta.getPersistentDataContainer().set(productKey, PersistentDataType.STRING, product.id());
        });
        stack.setAmount(amount);
        return stack;
    }

    /** Que producto de la polleria es este item (vacio si no es ninguno). */
    java.util.Optional<DonPollosProduct> productOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return java.util.Optional.empty();
        }
        String id = stack.getItemMeta().getPersistentDataContainer().get(productKey, PersistentDataType.STRING);
        return id == null ? java.util.Optional.empty() : DonPollosProduct.fromId(id);
    }

    ItemStack menuIcon(DonPollosProduct product, int amount, String name, List<String> lore) {
        ItemStack stack = create(product, amount);
        stack.editMeta(meta -> {
            meta.setDisplayName(name);
            List<String> lines = new ArrayList<>(product.lore());
            lines.addAll(lore);
            meta.setLore(lines);
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }

    ItemStack bucketIcon(String name, List<String> lore) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key(NAMESPACE, "balde_pollo_frito"));
        stack.editMeta(meta -> {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }
}
