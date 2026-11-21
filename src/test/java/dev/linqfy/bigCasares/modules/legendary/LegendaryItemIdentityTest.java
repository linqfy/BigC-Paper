package dev.linqfy.bigCasares.modules.legendary;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryItemIdentityTest {
    @Test
    void eachWeaponHasItsOwnCategoryEvenOnOldStacksWithoutTheCategoryTag() {
        var categories = new HashSet<String>();
        for (var weapon : LegendaryWeapon.available()) {
            ItemStack item = item(weapon.id, null);
            assertEquals(weapon, LegendaryItemIdentity.weapon(item).orElseThrow());
            assertTrue(LegendaryItemIdentity.isLegendary(item));
            assertTrue(categories.add(LegendaryItemIdentity.category(item).orElseThrow()));
        }
        assertEquals("bigcasares:windweaver", LegendaryItemIdentity.category(item("windweaver", null)).orElseThrow());
    }

    @Test
    void aMaceNameModelOrCategoryAloneDoesNotExemptAnOrdinaryOrUnknownItem() {
        assertFalse(LegendaryItemIdentity.isLegendary(null));
        assertFalse(LegendaryItemIdentity.isLegendary(item(null, null)));
        assertFalse(LegendaryItemIdentity.isLegendary(item(null, "bigcasares:windweaver")));
        assertFalse(LegendaryItemIdentity.isLegendary(item("unknown", "bigcasares:windweaver")));
        for (String retired : new String[]{"frost_scythe", "hyperion", "void_katana"}) {
            assertFalse(LegendaryItemIdentity.isLegendary(item(retired, "bigcasares:" + retired)));
        }
    }

    @Test
    void persistentIdTakesPrecedenceOverAnIncorrectCategoryAndMaterial() {
        assertEquals("bigcasares:bloodlust",
                LegendaryItemIdentity.category(item("bloodlust", "minecraft:mace")).orElseThrow());
    }

    @Test
    void namesHaveTheRequestedColorsAndAreNotItalic() {
        assertEquals(NamedTextColor.RED, LegendaryWeapon.BLOODLUST.itemName().color());
        assertEquals(NamedTextColor.BLACK, LegendaryWeapon.SHADOW_BLADE.itemName().color());
        assertEquals(NamedTextColor.AQUA, LegendaryWeapon.WINDWEAVER.itemName().color());
        assertEquals(NamedTextColor.YELLOW, LegendaryWeapon.THUNDER_SPEAR.itemName().color());
        assertEquals(NamedTextColor.GOLD, LegendaryWeapon.EARTHBREAKER.itemName().color());
        for (var weapon : LegendaryWeapon.available()) {
            assertEquals(TextDecoration.State.FALSE, weapon.itemName().decoration(TextDecoration.ITALIC));
        }
        assertEquals(Component.text("Mi maza", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                LegendaryWeapon.WINDWEAVER.coloredName(Component.text("Mi maza")),
                "coloring must preserve anvil renames");
    }

    @Test
    void oldStacksAreStampedOnceWithoutChangingTheirPersistentIdentity() {
        var values = new HashMap<NamespacedKey, String>();
        values.put(LegendaryItemIdentity.ITEM_ID, "windweaver");
        var data = (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("get")) return values.get(args[0]);
                    if (method.getName().equals("set")) values.put((NamespacedKey) args[0], (String) args[2]);
                    return method.getReturnType() == boolean.class ? false : null;
                });
        var meta = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class<?>[]{ItemMeta.class},
                (proxy, method, args) -> method.getName().equals("getPersistentDataContainer") ? data : null);
        assertTrue(LegendaryItemIdentity.stamp(meta, LegendaryWeapon.WINDWEAVER));
        assertEquals("windweaver", values.get(LegendaryItemIdentity.ITEM_ID));
        assertEquals("legendary", values.get(LegendaryItemIdentity.TIER));
        assertEquals("bigcasares:windweaver", values.get(LegendaryItemIdentity.CATEGORY));
        assertFalse(LegendaryItemIdentity.stamp(meta, LegendaryWeapon.WINDWEAVER));
    }

    private static ItemStack item(String id, String category) {
        var data = (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("get") && args[1] == PersistentDataType.STRING) {
                        if (args[0].equals(LegendaryItemIdentity.ITEM_ID)) return id;
                        if (args[0].equals(LegendaryItemIdentity.CATEGORY)) return category;
                    }
                    return method.getReturnType() == boolean.class ? false : null;
                });
        var meta = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class<?>[]{ItemMeta.class},
                (proxy, method, args) -> method.getName().equals("getPersistentDataContainer") ? data
                        : method.getReturnType() == boolean.class ? false : null);
        return new ItemStack() {
            @Override public boolean hasItemMeta() { return true; }
            @Override public ItemMeta getItemMeta() { return meta; }
            @Override public Material getType() { return Material.MACE; }
        };
    }
}
