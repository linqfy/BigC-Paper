package dev.linqfy.bigCasares.modules.tideframe;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Opens chest inventories with a {@link MenuTitle}: the plain legacy title, or the background glyph plus the title. */
public final class MenuTitles {
    /** Title colour over the dark band of the drawn backgrounds (the plain title's own colours are dark). */
    private static final TextColor TITLE_ON_BACKGROUND = TextColor.color(0xFFD966);

    private MenuTitles() {
    }

    public static Inventory create(InventoryHolder holder, int size, MenuTitle title) {
        return Bukkit.createInventory(holder, size, component(title));
    }

    static Component component(MenuTitle title) {
        if (!title.drawsBackground()) {
            return LegacyComponentSerializer.legacySection().deserialize(title.text());
        }
        String text = ChatColor.stripColor(title.text());
        return Component.text()
            .append(Component.text(title.background()).font(Key.key(MenuTitle.FONT)).color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false))
            .append(Component.text(text == null ? "" : text).color(TITLE_ON_BACKGROUND))
            .build();
    }
}
