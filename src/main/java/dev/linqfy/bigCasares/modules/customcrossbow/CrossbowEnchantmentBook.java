package dev.linqfy.bigCasares.modules.customcrossbow;

import dev.linqfy.bigCasares.items.CustomItem;
import dev.linqfy.bigCasares.items.ModelDataUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

final class CrossbowEnchantmentBook implements CustomItem {

    private final CrossbowEnchantment enchantment;

    CrossbowEnchantmentBook(CrossbowEnchantment enchantment) {
        this.enchantment = enchantment;
    }

    @Override
    public String getId() { return enchantment.bookId(); }

    @Override
    public int getCustomModelData() { return enchantment.modelData(); }

    @Override
    public NamespacedKey getItemKey() { return CrossbowEnchantmentData.BOOK; }

    @Override
    public ItemStack createItemStack(int amount) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, Math.clamp(amount, 1, 64));
        ItemMeta meta = book.getItemMeta();
        ModelDataUtil.writeCustomModelData(meta, getCustomModelData());
        meta.getPersistentDataContainer().set(getItemKey(), PersistentDataType.STRING, enchantment.name());
        meta.setDisplayName("§eLibro de " + enchantment.displayName());
        List<String> lore = new ArrayList<>(List.of(enchantment.loreLine()));
        lore.addAll(enchantment.description());
        lore.add("§8Aplicar a una ballesta en un yunque (4 niveles).");
        lore.add("§8Incompatible con Multidisparo y el otro encantamiento.");
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        book.setItemMeta(meta);
        return book;
    }

    @Override
    public boolean matchesLegacy(ItemStack item) {
        return new CrossbowEnchantmentData().book(item) == enchantment;
    }

    @Override
    public void onConsume(Player player, ItemStack consumedItem) { }
}
