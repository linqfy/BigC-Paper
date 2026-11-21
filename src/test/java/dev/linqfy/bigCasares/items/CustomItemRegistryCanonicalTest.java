package dev.linqfy.bigCasares.items;

import dev.linqfy.bigCasares.items.catalog.CustomItemCatalog;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogLoader;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomItemRegistryCanonicalTest {
    @Test void moduleItemIdentityDoesNotRequireACatalogEntryOrLegacyModelData() {
        var registry = new CustomItemRegistry();
        var item = new Handle("bloodlust");
        registry.register(item);
        ItemStack existing = canonical("bloodlust");
        assertEquals("bloodlust", registry.resolveItemId(existing).orElseThrow());
        assertSame(item, registry.findByItemStack(existing).orElseThrow());
        registry.unregister("bloodlust");
        assertTrue(registry.resolveItemId(existing).isEmpty());
    }

    @Test void catalogAndModuleItemsCoexistAndUnknownPersistentIdsStayUnknown() {
        var registry = new CustomItemRegistry();
        CustomItemCatalog catalog = new ItemCatalogLoader().load(Path.of("src/main/resources/content/items"));
        registry.installCatalog(catalog);
        registry.register(new Handle("bloodlust"));
        assertEquals("copper_apple", registry.resolveItemId(canonical("copper_apple")).orElseThrow());
        assertEquals("bloodlust", registry.resolveItemId(canonical("bloodlust")).orElseThrow());
        assertTrue(registry.resolveItemId(canonical("nonexistent")).isEmpty());
    }

    private static ItemStack canonical(String id) {
        var pdc = (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("get") && args[0].equals(new NamespacedKey("bigcasares", "item_id"))
                            && args[1] == PersistentDataType.STRING) return id;
                    return method.getReturnType() == boolean.class ? false : null;
                });
        var meta = (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(), new Class<?>[] {ItemMeta.class},
                (proxy, method, args) -> method.getName().equals("getPersistentDataContainer") ? pdc
                        : method.getReturnType() == boolean.class ? false : null);
        return new ItemStack() {
            @Override public boolean hasItemMeta() { return true; }
            @Override public ItemMeta getItemMeta() { return meta; }
        };
    }

    private record Handle(String getId) implements CustomItem {
        @Override public int getCustomModelData() { return 21000; }
        @Override public NamespacedKey getItemKey() { return new NamespacedKey("bigcasares", "item_id"); }
        @Override public ItemStack createItemStack(int amount) { throw new AssertionError("Identity lookup must not create items"); }
        @Override public void onConsume(Player player, ItemStack consumedItem) { }
    }
}
