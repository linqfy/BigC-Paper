package dev.linqfy.bigCasares.modules.customcrossbow;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class CrossbowEnchantmentData {

    static final NamespacedKey ENCHANTMENT = new NamespacedKey("bigcasares", "crossbow_enchantment");
    static final NamespacedKey BOOK = new NamespacedKey("bigcasares", "crossbow_enchantment_book");
    private static final NamespacedKey WEAPON = new NamespacedKey("bigcasares", "sniper_weapon_id");
    private static final NamespacedKey ROUNDS = new NamespacedKey("bigcasares", "crossbow_magazine_rounds");
    private static final NamespacedKey AMMO = new NamespacedKey("bigcasares", "crossbow_magazine_ammo");
    private static final List<String> OLD_SNIPER_LORE = List.of(
        "§7Apuntá con un catalejo en la otra mano.",
        "§7Clic derecho para disparar; levantate para cancelar.",
        "§7Soltá para gastar hasta 6 corazones y disparar."
    );

    CrossbowEnchantment enchantment(ItemStack item) {
        return read(item, Material.CROSSBOW, ENCHANTMENT);
    }

    CrossbowEnchantment book(ItemStack item) {
        return read(item, Material.ENCHANTED_BOOK, BOOK);
    }

    private CrossbowEnchantment read(ItemStack item, Material material, NamespacedKey key) {
        if (item == null || item.getType() != material || !item.hasItemMeta()) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return CrossbowEnchantment.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    void apply(ItemStack item, CrossbowEnchantment enchantment) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ENCHANTMENT, PersistentDataType.STRING, enchantment.name());
        meta.getPersistentDataContainer().set(WEAPON, PersistentDataType.STRING, UUID.randomUUID().toString());
        List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.of());
        removeLore(lore);
        lore.add(enchantment.loreLine());
        lore.addAll(enchantment.description());
        meta.setLore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
    }

    void remove(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(ENCHANTMENT);
        meta.getPersistentDataContainer().remove(WEAPON);
        meta.getPersistentDataContainer().remove(ROUNDS);
        meta.getPersistentDataContainer().remove(AMMO);
        List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.of());
        removeLore(lore);
        meta.setLore(lore.isEmpty() ? null : lore);
        meta.setEnchantmentGlintOverride(null);
        item.setItemMeta(meta);
    }

    private void removeLore(List<String> lore) {
        lore.removeAll(OLD_SNIPER_LORE);
        for (CrossbowEnchantment value : CrossbowEnchantment.values()) {
            lore.removeIf(line -> line.equals(value.loreLine()) || value.description().contains(line));
        }
    }

    void refreshSniperLore(ItemStack item) {
        if (enchantment(item) != CrossbowEnchantment.SNIPER) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.of());
        if (lore.stream().noneMatch(OLD_SNIPER_LORE::contains)) {
            return;
        }
        removeLore(lore);
        lore.add(CrossbowEnchantment.SNIPER.loreLine());
        lore.addAll(CrossbowEnchantment.SNIPER.description());
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    UUID weaponId(ItemStack item) {
        if (enchantment(item) != CrossbowEnchantment.SNIPER) {
            return null;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(WEAPON, PersistentDataType.STRING);
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    int rounds(ItemStack item) {
        if (enchantment(item) != CrossbowEnchantment.MULTICHARGE) {
            return 0;
        }
        return Math.clamp(item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(ROUNDS, PersistentDataType.INTEGER, 0), 0, CrossbowEnchantmentRules.MAX_SHOTS);
    }

    void loadMagazine(ItemStack crossbow, ItemStack ammo, int rounds) {
        ItemMeta meta = crossbow.getItemMeta();
        ItemStack single = ammo.clone();
        single.setAmount(1);
        meta.getPersistentDataContainer().set(AMMO, PersistentDataType.BYTE_ARRAY, single.serializeAsBytes());
        meta.getPersistentDataContainer().set(ROUNDS, PersistentDataType.INTEGER,
            Math.clamp(rounds, 1, CrossbowEnchantmentRules.MAX_SHOTS));
        crossbow.setItemMeta(meta);
    }

    void consumeRound(ItemStack crossbow) {
        int remaining = rounds(crossbow) - 1;
        ItemMeta meta = crossbow.getItemMeta();
        if (remaining <= 0) {
            meta.getPersistentDataContainer().remove(AMMO);
            meta.getPersistentDataContainer().remove(ROUNDS);
        } else {
            meta.getPersistentDataContainer().set(ROUNDS, PersistentDataType.INTEGER, remaining);
        }
        crossbow.setItemMeta(meta);
    }

    void prepareNextShot(ItemStack crossbow) {
        if (rounds(crossbow) <= 0 || !(crossbow.getItemMeta() instanceof CrossbowMeta meta)
            || meta.hasChargedProjectiles()) {
            return;
        }
        byte[] bytes = meta.getPersistentDataContainer().get(AMMO, PersistentDataType.BYTE_ARRAY);
        if (bytes != null) {
            meta.setChargedProjectiles(List.of(ItemStack.deserializeBytes(bytes)));
            crossbow.setItemMeta(meta);
        }
    }
}
