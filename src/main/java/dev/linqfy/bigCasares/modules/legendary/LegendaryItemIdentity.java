package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/** Identity for balance integrations; a vanilla material or a display name is never a legendary identity. */
public final class LegendaryItemIdentity {
    public static final NamespacedKey ITEM_ID = new NamespacedKey("bigcasares", "item_id");
    public static final NamespacedKey CATEGORY = new NamespacedKey("bigcasares", "weapon_category");
    public static final NamespacedKey TIER = new NamespacedKey("bigcasares", "item_tier");

    private LegendaryItemIdentity() { }

    public static Optional<LegendaryWeapon> weapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_ID, PersistentDataType.STRING);
        return id == null ? Optional.empty() : LegendaryWeapon.find(id);
    }

    public static boolean isLegendary(ItemStack item) {
        return weapon(item).isPresent();
    }

    /** Each weapon has its own balance category, including old stacks that predate the category tag. */
    public static Optional<String> category(ItemStack item) {
        return weapon(item).map(LegendaryItemIdentity::category);
    }

    static String category(LegendaryWeapon weapon) {
        return "bigcasares:" + weapon.id;
    }

    static boolean stamp(ItemMeta meta, LegendaryWeapon weapon) {
        String category = category(weapon);
        var data = meta.getPersistentDataContainer();
        if (category.equals(data.get(CATEGORY, PersistentDataType.STRING))
                && "legendary".equals(data.get(TIER, PersistentDataType.STRING))) return false;
        data.set(CATEGORY, PersistentDataType.STRING, category);
        data.set(TIER, PersistentDataType.STRING, "legendary");
        return true;
    }
}
