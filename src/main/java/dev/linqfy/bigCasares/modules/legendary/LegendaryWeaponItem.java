package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.items.CustomItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

final class LegendaryWeaponItem implements CustomItem {
    private static final NamespacedKey ID = LegendaryItemIdentity.ITEM_ID;
    private final LegendaryWeapon weapon;

    LegendaryWeaponItem(LegendaryWeapon weapon) {
        this.weapon = weapon;
    }

    @Override public String getId() { return weapon.id; }

    // Registry slot only. The first CMD float is reserved for animation frames.
    @Override public int getCustomModelData() { return 21000 + weapon.ordinal(); }

    @Override public NamespacedKey getItemKey() { return ID; }

    @Override public ItemStack createItemStack(int amount) {
        if (amount != 1) throw new IllegalArgumentException("Las armas son únicas por espacio.");
        ItemStack item = new ItemStack(Material.valueOf(weapon.material), 1);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, weapon.id);
        meta.displayName(weapon.itemName());
        meta.lore(List.of(Component.text(weapon.abilityName),
                Component.text("/weapons info " + weapon.id + " · niveles 0-5 por bajas PvP")));
        meta.setMaxStackSize(1);
        meta.setItemModel(new NamespacedKey("bigcasares", weapon.id));
        LegendaryCombatAttributes.initialize(meta, weapon);
        if (weapon == LegendaryWeapon.THUNDER_SPEAR) meta.addEnchant(Enchantment.LUNGE, 3, true);
        item.setItemMeta(meta);
        LegendaryItemCarrier.upgrade(item, weapon);
        return item;
    }

    @Override public boolean matchesLegacy(ItemStack item) { return false; }

    @Override public boolean matches(ItemStack item) {
        return item != null && item.hasItemMeta() && weapon.id.equals(
                item.getItemMeta().getPersistentDataContainer().get(ID, PersistentDataType.STRING));
    }

    @Override public void onConsume(Player player, ItemStack consumedItem) { }
}
