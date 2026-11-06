package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopBedrockCategoryItemsTest {
    private static final ShopCategory FOOD = new ShopCategory("food", "&cComida", Material.BREAD, 13, List.of(
        new ShopEntry("bread", 10, Material.BREAD, null, 16, 40.0, 2.0, "&6Pan", List.of("&7Pack")),
        new ShopEntry("nuke", 11, null, "nuke_shot", 1, 500.0, 0.0, "Nuke", List.of())));

    private static List<ShopViewItem> items(int lots, boolean selling, double funds, int breadCarried) {
        return ShopModule.bedrockCategoryItems(FOOD, lots, selling ? ShopModule.BedrockMode.SELL : ShopModule.BedrockMode.BUY, funds,
            entry -> entry.id().equals("bread") ? breadCarried : 0, amount -> "$" + (int) amount, entry -> false, ShopEntry::buyPrice, entry -> "");
    }

    private static final int FIRST = ShopModule.HEADER_BUTTONS;

    @Test
    void backQuantityAndModeComeFirstThenTheProductsInOrder() {
        List<ShopViewItem> items = items(1, false, 1000, 0);
        assertEquals(List.of(ShopModule.BACK_BUTTON, ShopModule.QUANTITY_BUTTON, ShopModule.MODE_BUTTON),
            items.subList(0, FIRST).stream().map(ShopViewItem::id).toList());
        assertEquals("§7« Volver a las categorías", items.get(0).name());
        assertEquals("§bCantidad: x1 §7(tocá para cambiar)", items.get(1).name());
        assertEquals("§aModo: comprar §7(tocá para cambiar)", items.get(2).name());
        assertEquals(List.of("bread", "nuke"), items.subList(FIRST, FIRST + 2).stream().map(ShopViewItem::id).toList());
        assertEquals("&6Pan §7x16", items.get(FIRST).name());
        assertEquals("$40", items.get(FIRST).price());
        assertEquals("textures/items/bread", items.get(FIRST).iconPath());
        assertNull(items.get(FIRST + 1).iconPath(), "a custom item has no vanilla icon");
    }

    @Test
    void buyingFourLotsShowsTheirCountPriceAndWhatIsMissing() {
        List<ShopViewItem> items = items(4, false, 200, 0);
        assertEquals("&6Pan §7x64", items.get(FIRST).name());
        assertEquals("$160", items.get(FIRST).price());
        assertTrue(items.get(FIRST).available());
        assertEquals("$2000 (faltan $1800)", items.get(FIRST + 1).price());
        assertFalse(items.get(FIRST + 1).available());
    }

    @Test
    void sellingShowsTheSalePriceAndWhatThePlayerCarries() {
        List<ShopViewItem> items = items(2, true, 0, 20);
        assertEquals("§6Modo: vender §7(tocá para cambiar)", items.get(2).name());
        assertEquals("$4 (tenés 20)", items.get(FIRST).price(), "two lots are 32 breads; 20 are not enough");
        assertFalse(items.get(FIRST).available());
        assertEquals("no se compra acá", items.get(FIRST + 1).price(), "the nuke has no sale price");
        assertFalse(items.get(FIRST + 1).available());

        assertTrue(items(2, true, 0, 40).get(FIRST).available());
        assertEquals("$4", items(2, true, 0, 40).get(FIRST).price());
    }

    @Test
    void favouriteModeMarksEachProductAndFavouritesListFirst() {
        List<ShopViewItem> items = ShopModule.bedrockCategoryItems(FOOD, 1, ShopModule.BedrockMode.FAVORITE, 0,
            entry -> 0, amount -> "$", entry -> entry.id().equals("bread"), ShopEntry::buyPrice, entry -> "");
        assertEquals("§eModo: favoritos §7(tocá para cambiar)", items.get(2).name());
        assertEquals(List.of("★ en tus favoritos", "☆ tocá para agregar"),
            items.subList(FIRST, FIRST + 2).stream().map(ShopViewItem::price).toList());
        assertEquals(ShopModule.BedrockMode.BUY, ShopModule.BedrockMode.FAVORITE.next(), "the mode button cycles back to buying");

        ShopCategory favorites = new ShopCategory(ShopFavorites.CATEGORY, ShopFavorites.NAME, Material.NETHER_STAR, 0,
            List.of(FOOD.entries().getFirst()));
        List<ShopViewItem> buttons = ShopModule.bedrockCategoryButtons(new ShopCatalog(List.of(FOOD)), java.util.Optional.of(favorites));
        assertEquals(List.of(ShopFavorites.CATEGORY, "food"), buttons.stream().map(ShopViewItem::id).toList());
        assertEquals("food/bread", ShopModule.favoriteKey("food", "bread"));
        assertEquals("food/bread", ShopModule.favoriteKey(ShopFavorites.CATEGORY, "food/bread"), "a favourites row already is a key");
    }

    @Test
    void categoriesShowHowManyProductsTheyHave() {
        ShopCatalog catalog = new ShopCatalog(List.of(FOOD,
            new ShopCategory("blocks", "Bloques", Material.STONE, 10, List.of(FOOD.entries().getFirst()))));
        List<ShopViewItem> buttons = ShopModule.bedrockCategoryButtons(catalog, java.util.Optional.empty());
        assertEquals(List.of("2 productos", "1 producto"), buttons.stream().map(ShopViewItem::price).toList());
        assertEquals("textures/blocks/stone", buttons.get(1).iconPath());
    }
}
