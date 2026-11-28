package dev.linqfy.bigCasares.modules.caichet;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** El Reloj de los Sueños (lo suelta la gallina) y la bebida Despertar (4 relojes en cuadrado). */
final class DreamItems {

    static final NamespacedKey ITEM_KEY = new NamespacedKey("caichet", "dream_item");
    static final NamespacedKey RECIPE_KEY = new NamespacedKey("caichet", "despertar");

    private DreamItems() {
    }

    static ItemStack clock() {
        return named(Material.CLOCK, "reloj_suenos", "Reloj de los Sueños", NamedTextColor.LIGHT_PURPLE,
                "Juntá cuatro en la mesa de crafteo");
    }

    /** Se bebe y te lleva al hotel. Va sobre papel: no es un totem, no te salva de morir. */
    static ItemStack wakeDrink() {
        ItemStack item = named(Material.PAPER, "despertar", "Despertar", NamedTextColor.GOLD,
                "Bebelo para despertar");
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        item.setData(DataComponentTypes.CONSUMABLE, HotelLoot.drink(HotelLoot.WAKE_DRINK_MS / 1000f));
        return item;
    }

    static boolean isWakeDrink(ItemStack item) {
        return item != null && item.hasItemMeta() && "despertar".equals(
                item.getItemMeta().getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.STRING));
    }

    /**
     * 2x2 como la mesa de crafteo: entra en cualquier esquina de la mesa (y en el inventario). Solo sirven los
     * relojes de la gallina, no los relojes normales.
     */
    static ShapedRecipe recipe() {
        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, wakeDrink());
        recipe.shape("CC", "CC");
        recipe.setIngredient('C', new RecipeChoice.ExactChoice(clock()));
        return recipe;
    }

    private static ItemStack named(Material material, String id, String name, NamedTextColor color, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.STRING, id);
        // textura propia del resource pack (assets/caichet/items/<id>.json)
        meta.setItemModel(new NamespacedKey("caichet", id));
        item.setItemMeta(meta);
        return item;
    }
}
