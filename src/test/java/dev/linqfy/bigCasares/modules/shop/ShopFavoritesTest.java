package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.testsupport.FakePlayer;
import dev.linqfy.bigCasares.testsupport.MapPlayerStrings;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopFavoritesTest {
    private static final ShopCatalog CATALOG = new ShopCatalog(List.of(
        new ShopCategory("blocks", "Bloques", Material.STONE, 10, List.of(
            new ShopEntry("stone", 10, Material.STONE, null, 64, 64, 4, "&fPiedra", List.of()))),
        new ShopCategory("food", "Comida", Material.BREAD, 12, List.of(
            new ShopEntry("bread", 10, Material.BREAD, null, 16, 40, 2, "&6Pan", List.of())))));

    private final MapPlayerStrings store = new MapPlayerStrings();
    private final ShopFavorites favorites = new ShopFavorites(store);
    private final FakePlayer juan = new FakePlayer("juan");

    @Test
    void toggleAddsAndRemovesAndTheyStayWithThePlayer() {
        assertEquals(Optional.empty(), favorites.category(juan.player, CATALOG), "no favourites: no category");
        assertTrue(favorites.toggle(juan.player, "food", "bread"));
        assertTrue(favorites.toggle(juan.player, "blocks", "stone"));
        assertEquals("food/bread\nblocks/stone", store.values.get(juan.id + "/" + ShopFavorites.KEY));

        ShopCategory category = favorites.category(juan.player, CATALOG).orElseThrow();
        assertEquals(ShopFavorites.CATEGORY, category.id());
        assertEquals(List.of("food/bread", "blocks/stone"), category.entries().stream().map(ShopEntry::id).toList(),
            "in the order they were added, each saying where it comes from");
        assertEquals(40, category.entries().getFirst().buyPrice(), "the catalog's own prices");

        assertFalse(favorites.toggle(juan.player, "food", "bread"));
        assertEquals(Set.of("blocks/stone"), favorites.keys(juan.player));
    }

    @Test
    void productsGoneFromTheCatalogAreSkippedButKept() {
        favorites.toggle(juan.player, "food", "cake");
        favorites.toggle(juan.player, "blocks", "stone");
        assertEquals(List.of("blocks/stone"),
            favorites.category(juan.player, CATALOG).orElseThrow().entries().stream().map(ShopEntry::id).toList());
        assertEquals(Set.of("food/cake", "blocks/stone"), favorites.keys(juan.player), "the cake comes back if the shop sells it again");
    }

    @Test
    void unreadableLinesAreIgnoredAndTheListHasALimit() {
        store.set(juan.player, ShopFavorites.KEY, "food/bread\n../etc\nnada\n\nblocks/stone\n");
        assertEquals(Set.of("food/bread", "blocks/stone"), favorites.keys(juan.player));
        for (int i = 0; i < ShopFavorites.MAX + 5; i++) favorites.toggle(juan.player, "c", "e" + i);
        Set<String> keys = favorites.keys(juan.player);
        assertEquals(ShopFavorites.MAX, keys.size());
        assertTrue(keys.contains("c/e" + (ShopFavorites.MAX + 4)), "the newest stays, the oldest leave");
        assertFalse(keys.contains("food/bread"));
    }
}
