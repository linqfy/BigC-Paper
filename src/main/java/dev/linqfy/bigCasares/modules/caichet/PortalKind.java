package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Los dos bloques del plugin. Son bloques de concreto vanilla (negro y celeste) marcados: el item lleva una marca
 * en su PersistentDataContainer y el bloque colocado se anota en el chunk (ver {@link PortalRegistry}).
 */
public enum PortalKind {
    /** Bloque negro: al tocarlo entras a la dimension. */
    DIMENSION('D', "dimension", Material.BLACK_CONCRETE, "Bloque de Dimensión", NamedTextColor.DARK_GRAY,
            "Toca el bloque para viajar al desierto"),
    /** Bloque celeste: al tocarlo vuelves al mundo. */
    MUNDO('M', "mundo", Material.LIGHT_BLUE_CONCRETE, "Bloque de Mundo", NamedTextColor.AQUA,
            "Toca el bloque para volver");

    static final NamespacedKey ITEM_KEY = new NamespacedKey("caichet", "portal_item");

    public final char code;
    public final String id;
    public final Material material;
    private final String name;
    private final NamedTextColor color;
    private final String lore;

    PortalKind(char code, String id, Material material, String name, NamedTextColor color, String lore) {
        this.code = code;
        this.id = id;
        this.material = material;
        this.name = name;
        this.color = color;
        this.lore = lore;
    }

    public ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    /** El tipo de bloque que representa este item, o null si es un item cualquiera. */
    public static PortalKind ofItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING);
        return byId(id);
    }

    public static PortalKind byId(String id) {
        for (PortalKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }

    static PortalKind byCode(char code) {
        for (PortalKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        return null;
    }
}
