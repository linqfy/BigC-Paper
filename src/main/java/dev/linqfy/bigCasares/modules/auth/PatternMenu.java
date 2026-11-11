package dev.linqfy.bigCasares.modules.auth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.Consumer;

/**
 * Chest-based pattern lock: nine coloured wool "dots" clicked in order, like an Android unlock pattern. Works with
 * the mouse alone, so nothing has to be typed in chat.
 */
final class PatternMenu implements InventoryHolder {

    static final int[] DOT_SLOTS = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final Material[] DOTS = {
        Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL,
        Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
        Material.PINK_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL
    };
    private static final String[] DOT_NAMES = {
        "Blanco", "Naranja", "Magenta", "Celeste", "Amarillo", "Lima", "Rosa", "Cian", "Violeta"
    };
    static final int CLEAR_SLOT = 18;
    static final int CONFIRM_SLOT = 26;
    private static final int INFO_SLOT = 9;
    private static final int MAX_LENGTH = 16;

    private final Inventory inventory;
    private final Consumer<String> onConfirm;
    private final StringBuilder sequence = new StringBuilder();

    PatternMenu(String title, Consumer<String> onConfirm) {
        this.onConfirm = onConfirm;
        this.inventory = Bukkit.createInventory(this, 27, Component.text(title, NamedTextColor.DARK_PURPLE));
        for (int index = 0; index < DOT_SLOTS.length; index++) {
            inventory.setItem(DOT_SLOTS[index], named(DOTS[index], DOT_NAMES[index], NamedTextColor.WHITE,
                List.of(Component.text("Punto " + (index + 1), NamedTextColor.GRAY))));
        }
        inventory.setItem(CLEAR_SLOT, named(Material.BARRIER, "Borrar", NamedTextColor.RED, List.of()));
        inventory.setItem(CONFIRM_SLOT, named(Material.EMERALD_BLOCK, "Confirmar", NamedTextColor.GREEN, List.of()));
        refreshInfo();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Handles a click on the top inventory. */
    void click(int slot) {
        for (int index = 0; index < DOT_SLOTS.length; index++) {
            if (DOT_SLOTS[index] == slot && sequence.length() < MAX_LENGTH) {
                sequence.append((char) ('1' + index));
                refreshInfo();
                return;
            }
        }
        if (slot == CLEAR_SLOT) {
            sequence.setLength(0);
            refreshInfo();
        } else if (slot == CONFIRM_SLOT) {
            String entered = sequence.toString();
            sequence.setLength(0);
            refreshInfo();
            onConfirm.accept(entered);
        }
    }

    /** Canonical secret for hashing: a fixed prefix keeps patterns distinct from passwords. */
    static String secretOf(String sequence) {
        return "pattern:" + sequence;
    }

    private void refreshInfo() {
        String dots = "●".repeat(sequence.length());
        inventory.setItem(INFO_SLOT, named(Material.ENDER_EYE, "Patrón: " + (dots.isEmpty() ? "(vacío)" : dots),
            NamedTextColor.LIGHT_PURPLE, List.of(
                Component.text("Tocá los puntos en orden.", NamedTextColor.GRAY),
                Component.text("Podés repetir puntos.", NamedTextColor.GRAY))));
    }

    private static ItemStack named(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }
}
