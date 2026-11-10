package dev.linqfy.bigCasares.modules.forex;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Chest menu whose buttons are callbacks. Nothing can be taken out of it. */
final class ForexMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    ForexMenu(int rows, String title) {
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, text(title));
    }

    ForexMenu set(int slot, ItemStack item, Consumer<Player> action) {
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
        return this;
    }

    void fill() {
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    void open(Player player) {
        player.openInventory(inventory);
    }

    int size() {
        return inventory.getSize();
    }

    void dispatch(Player player, int slot) {
        Consumer<Player> action = actions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    static ItemStack button(Material material, String name, String... lore) {
        return button(material, name, List.of(lore));
    }

    static ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name));
        meta.lore(lore.stream().map(ForexMenu::text).toList());
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private static Component text(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }
}
