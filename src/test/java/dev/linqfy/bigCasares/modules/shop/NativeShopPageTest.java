package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NativeShopPageTest {
    private static ShopEntry apple(String id, String name, double buyPrice) {
        return new ShopEntry(id, 0, Material.APPLE, null, 16, buyPrice, 4, name, List.of());
    }

    private static ShopCategory category(String id, List<ShopEntry> entries) {
        return new ShopCategory(id, id, Material.APPLE, 0, entries);
    }

    @Test
    void allSevenTabsAndBothRowsAreReachableAndAnEmptyCategoryHasOnePage() {
        var apples = IntStream.range(0, 17).mapToObj(i -> apple("fruit_" + i, "Manzana " + i, 10)).toList();
        var cats = IntStream.range(0, 7).mapToObj(i -> category("cat_" + i, i == 6 ? apples : List.of())).toList();
        var catalog = new ShopCatalog(cats);
        var first = NativeShopPage.select(catalog, 6, 0, "");
        assertEquals(16, first.offers().size());
        assertEquals(2, first.pageCount());
        assertSame(apples.get(16), NativeShopPage.select(catalog, 6, 1, "").offers().getFirst().entry());
        assertEquals(1, NativeShopPage.select(catalog, 0, 0, "").pageCount());
    }

    @Test
    void searchKeepsTheCategoryOfEachMatchEvenWhenProductIdsRepeat() {
        var a = apple("fruit", "Jugo de limón", 20);
        var b = apple("fruit", "&aLimón entero", 30);
        var catalog = new ShopCatalog(List.of(category("bebidas", List.of(a)), category("frutas", List.of(b))));
        var results = NativeShopPage.select(catalog, 0, 0, "LIMON").offers();
        assertEquals(List.of("frutas", "bebidas"), results.stream().map(NativeShopPage.Offer::categoryId).toList());
        assertSame(b, results.getFirst().resolve(catalog, false));
        var changed = new ShopCatalog(List.of(category("frutas", List.of(apple("fruit", "Limón nuevo", 40)))));
        assertNull(results.getFirst().resolve(changed, false));
        assertEquals(List.of(), NativeShopPage.select(catalog, 0, 0, "xxxxxxxx").offers());
    }

    @Test
    void aDisabledTradeDirectionCannotReachTheEconomy() {
        for (double invalid : new double[] {0, -2, Double.NaN, Double.POSITIVE_INFINITY}) {
            ShopEntry item = apple("fruit", "Manzana", invalid);
            var catalog = new ShopCatalog(List.of(category("frutas", List.of(item))));
            var offer = NativeShopPage.select(catalog, 0, 0, "").offers().getFirst();
            assertNull(offer.resolve(catalog, false));
            assertSame(item, offer.resolve(catalog, true));
        }
    }
}
