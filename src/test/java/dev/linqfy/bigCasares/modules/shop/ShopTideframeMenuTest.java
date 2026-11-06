package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.testsupport.FakePlayer;
import dev.linqfy.bigCasares.testsupport.FakeTideframeUi;
import dev.linqfy.bigCasares.modules.tideframe.TideframeTestConnection;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopTideframeMenuTest {
    private static final String SCREEN = ShopTideframeMenu.SCREEN, LIST = ShopTideframeMenu.LIST;

    private static ShopEntry entry(String id, String name, double buy, double sell) {
        return new ShopEntry(id, 0, Material.STONE, null, 1, buy, sell, name, List.of());
    }

    private static ShopCatalog catalog(List<ShopEntry> tools) {
        return new ShopCatalog(List.of(
            new ShopCategory("bloques", "&6Bloques", Material.STONE, 10, List.of(entry("piedra", "Piedra", 4, 1))),
            new ShopCategory("herramientas", "Herramientas", Material.IRON_PICKAXE, 12, tools)));
    }

    /** The shop service as the screen sees it: each call recorded, every operation succeeding. */
    private static final class Service implements ShopTideframeMenu.Actions {
        final List<String> calls = new ArrayList<>();

        @Override
        public ShopTransactionResult buy(Player player, ShopEntry entry, int lots) {
            calls.add("buy " + entry.id() + (lots > 1 ? " x" + lots : ""));
            return ShopTransactionResult.success("Compraste " + entry.id() + " por $" + (int) (entry.buyPrice() * lots) + ".", null);
        }

        @Override
        public ShopTransactionResult sell(Player player, ShopEntry entry, int lots) {
            calls.add("sell " + entry.id() + (lots > 1 ? " x" + lots : ""));
            return ShopTransactionResult.failure("No tenes suficientes items para vender.");
        }

        double funds = 1000;

        @Override
        public String balance(Player player) {
            return "$1,000";
        }

        @Override
        public double funds(Player player) {
            return funds;
        }

        int owned;

        @Override
        public int owned(Player player, ShopEntry entry) {
            return owned;
        }

        ShopFavorites favorites;
        ShopCatalog catalog;

        @Override
        public java.util.Optional<ShopCategory> favorites(Player player) {
            return favorites == null ? java.util.Optional.empty() : favorites.category(player, catalog);
        }

        @Override
        public boolean toggleFavorite(Player player, String categoryId, String entryId) {
            calls.add("fav " + categoryId + "/" + entryId);
            return favorites.toggle(player, categoryId, entryId);
        }

        @Override
        public String format(double amount) {
            return "$" + (int) amount;
        }
    }

    private final FakeTideframeUi ui = new FakeTideframeUi();
    private final Service service = new Service();
    private final FakePlayer juan = new FakePlayer("juan");
    private final AtomicReference<ShopCatalog> current = new AtomicReference<>(catalog(List.of(
        entry("pico", "Pico de hierro", 120, 30), entry("gancho", "Gancho", 300, 0))));
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);
    private final ShopTideframeMenu menu = new ShopTideframeMenu(current::get, service, () -> ui, clock::get);

    private List<String> names() {
        return ui.list(juan.player, LIST).stream().map(row -> row.get("nombre")).toList();
    }

    /** Time for the player to see the rows just sent. */
    private void look() {
        clock.addAndGet(2_000_000_000L);
    }

    @Test
    void oneClickIsOnePurchaseOfTheProductItsRowShowed() {
        assertTrue(menu.openCategories(juan.player, () -> { }));
        assertEquals(List.of("Bloques", "Herramientas"), names(), "category names without colour codes");
        look();
        assertTrue(ui.click(juan.player, SCREEN, "fila", 1, false));
        assertEquals(List.of("Pico de hierro", "Gancho"), names());
        assertEquals("$300 / —", ui.list(juan.player, LIST).get(1).get("precio"), "cannot be sold: a dash");

        look();
        assertTrue(ui.click(juan.player, SCREEN, "fila", 1, false));
        assertEquals(List.of("buy gancho"), service.calls, "one click, one purchase, of the row's product");
        assertEquals("Compraste Gancho por $300.", ui.variable(juan.player, "bigc_tienda_aviso"), "one lot: no quantity in the notice");
        assertEquals(List.of("§aCompraste gancho por $300."), juan.messages, "the chat says what the service says, as in the inventory menu");

        assertTrue(ui.click(juan.player, SCREEN, "fila", 0, true));
        assertEquals(List.of("buy gancho", "sell pico"), service.calls, "right click sells, with no wait: the rows did not change");
        assertEquals("No tenes suficientes items para vender.", ui.variable(juan.player, "bigc_tienda_aviso"));
    }

    @Test
    void aRowClickedBeforeThePlayerCouldSeeTheNewRowsBuysNothing() {
        juan.ping = 120;
        menu.openCategories(juan.player, () -> { });
        look();
        assertTrue(ui.click(juan.player, SCREEN, "fila", 1, false));
        assertTrue(ui.click(juan.player, SCREEN, "fila", 1, false), "the second click of a double click on Herramientas");
        clock.addAndGet(ShopTideframeMenu.SETTLE_NANOS + 100_000_000L);
        assertTrue(ui.click(juan.player, SCREEN, "fila", 1, false), "still sooner than the wait plus a 120 ms ping");
        assertEquals(List.of(), service.calls, "the Gancho now on that row was not bought");
        assertEquals(List.of("Pico de hierro", "Gancho"), names());

        clock.addAndGet(30_000_000L);
        assertTrue(ui.click(juan.player, SCREEN, "fila", 1, false));
        assertTrue(ui.click(juan.player, SCREEN, "fila", 1, false));
        assertEquals(List.of("buy gancho", "buy gancho"), service.calls, "once seen, each click buys, as in the inventory menu");
    }

    @Test
    void theWaitCoversTheWholePingEvenOnAVerySlowLink() {
        juan.ping = 3000;
        menu.openCategory(juan.player, current.get().category("herramientas").orElseThrow(), () -> { });
        clock.addAndGet(ShopTideframeMenu.SETTLE_NANOS + 2_900_000_000L);
        assertTrue(ui.click(juan.player, SCREEN, "fila", 1, false), "a click sent before the rows arrived, 3 s away");
        assertEquals(List.of(), service.calls, "buys nothing");
        clock.addAndGet(200_000_000L);
        ui.click(juan.player, SCREEN, "fila", 1, false);
        assertEquals(List.of("buy gancho"), service.calls);
    }

    @Test
    void rowsPastThePageAndClicksWithoutAViewDoNothing() {
        assertFalse(ui.click(juan.player, SCREEN, "fila", 0, false), "not opened by this menu: not handled");
        menu.openCategory(juan.player, current.get().category("herramientas").orElseThrow(), () -> { });
        look();
        assertTrue(ui.click(juan.player, SCREEN, "fila", 2, false), "an empty row is still this screen's");
        assertTrue(ui.click(juan.player, SCREEN, "fila", -1, false));
        assertEquals(List.of(), service.calls);
        assertFalse(ui.click(juan.player, SCREEN, "cerrar", -1, false), "the X keeps Tideframe's own close action");
        assertFalse(ui.click(juan.player, SCREEN, "otro", -1, false));
    }

    @Test
    void aProductTakenOutOfTheCatalogIsNotSoldFromAScreenOpenedBefore() {
        menu.openCategory(juan.player, current.get().category("herramientas").orElseThrow(), () -> { });
        current.set(catalog(List.of(entry("pico", "Pico de hierro", 120, 30))));
        look();
        assertTrue(ui.click(juan.player, SCREEN, "fila", 1, false));
        assertEquals(List.of(), service.calls, "the row's id is looked up again: gone, nothing is bought");
        assertEquals("Ese producto ya no está.", ui.variable(juan.player, "bigc_tienda_aviso"));
        assertEquals(List.of("Pico de hierro"), names(), "the screen shows the catalog as it is now");
    }

    @Test
    void aListNeverHoldsMoreRowsThanTheScreenShows() {
        List<ShopEntry> many = new ArrayList<>();
        for (int i = 0; i < 13; i++) many.add(entry("item" + i, "Item " + i, 10 + i, 1));
        current.set(catalog(many));
        menu.openCategory(juan.player, current.get().category("herramientas").orElseThrow(), () -> { });
        assertEquals(ShopTideframeMenu.ROWS, names().size());
        assertEquals("1 de 3", ui.variable(juan.player, "bigc_tienda_pagina"));
        assertEquals("3", ui.variable(juan.player, "bigc_tienda_paginas"));
        ui.click(juan.player, SCREEN, "siguiente", -1, false);
        look();
        ui.click(juan.player, SCREEN, "fila", 0, false);
        assertEquals(List.of("buy item6"), service.calls, "row 0 of the second page is the seventh product");
        ui.click(juan.player, SCREEN, "siguiente", -1, false);
        ui.click(juan.player, SCREEN, "siguiente", -1, false);
        assertEquals(List.of("Item 12"), names(), "the last page, and no further");
        ui.click(juan.player, SCREEN, "anterior", -1, false);
        assertEquals("2 de 3", ui.variable(juan.player, "bigc_tienda_pagina"));
    }

    @Test
    void volverGoesBackToTheCategoriesAndShowsOnlyWhereItDoesSomething() {
        menu.openCategory(juan.player, current.get().category("herramientas").orElseThrow(), () -> { });
        assertEquals("1", ui.variable(juan.player, "bigc_tienda_productos"));
        ui.click(juan.player, SCREEN, "volver", -1, false);
        assertEquals(List.of("Bloques", "Herramientas"), names());
        assertEquals("0", ui.variable(juan.player, "bigc_tienda_productos"), "the screen hides Volver on the categories");
        assertEquals("1", ui.variable(juan.player, "bigc_tienda_paginas"), "and the arrows with a single page");
    }

    @Test
    void withoutTideframeOrWhenItCannotOpenTheInventoryMenuIsUsed() {
        ui.showable = false;
        AtomicInteger fallbacks = new AtomicInteger();
        assertFalse(menu.openCategories(juan.player, fallbacks::incrementAndGet), "cannot show: the caller opens the inventory");
        assertEquals(Map.of(), ui.lists);
        ui.showable = true;
        ui.opens = false;
        assertTrue(menu.openCategories(juan.player, fallbacks::incrementAndGet));
        assertEquals(1, fallbacks.get(), "the pack was refused or timed out: the inventory menu opens instead");
        assertFalse(ui.click(juan.player, SCREEN, "fila", 0, false), "and the screen that never opened keeps no view");
    }

    @Test
    void cantidadCyclesTheLotsAndOneClickMovesThemAsOneOperation() {
        menu.openCategory(juan.player, current.get().category("herramientas").orElseThrow(), () -> { });
        assertEquals("Cant. x1", ui.variable(juan.player, "bigc_tienda_cantidad"));
        look();
        assertTrue(ui.click(juan.player, SCREEN, "cantidad", -1, false));
        assertEquals("Cant. x4", ui.variable(juan.player, "bigc_tienda_cantidad"));
        assertEquals("Pico de hierro x4", names().get(0));
        assertEquals("$480 / $120", ui.list(juan.player, LIST).get(0).get("precio"), "the prices of four lots");

        assertTrue(ui.click(juan.player, SCREEN, "fila", 0, false), "right after the prices changed");
        assertEquals(List.of(), service.calls, "waits like new rows: the old price was on screen a moment ago");
        look();
        ui.click(juan.player, SCREEN, "fila", 0, false);
        ui.click(juan.player, SCREEN, "fila", 0, true);
        assertEquals(List.of("buy pico x4", "sell pico x4"), service.calls, "one call each, for four lots");
        assertEquals("No tenes suficientes items para vender.", ui.variable(juan.player, "bigc_tienda_aviso"));

        ui.click(juan.player, SCREEN, "cantidad", -1, false);
        assertEquals("Cant. x16", ui.variable(juan.player, "bigc_tienda_cantidad"));
        ui.click(juan.player, SCREEN, "cantidad", -1, false);
        assertEquals("Cant. x1", ui.variable(juan.player, "bigc_tienda_cantidad"), "and back to one");
    }

    @Test
    void shiftRightClickSellsEveryWholeLotCarriedAsOneOperation() {
        current.set(new ShopCatalog(List.of(new ShopCategory("bloques", "Bloques", Material.STONE, 10,
            List.of(new ShopEntry("piedra", 0, Material.STONE, null, 32, 64, 4, "Piedra", List.of()))))));
        menu.openCategory(juan.player, current.get().category("bloques").orElseThrow(), () -> { });
        look();
        service.owned = 20;
        assertTrue(ui.click(juan.player, SCREEN, "fila", 0, true, true));
        assertEquals(List.of(), service.calls, "20 stones are not one lot of 32: nothing to sell");
        assertEquals("No tenés Piedra para vender.", ui.variable(juan.player, "bigc_tienda_aviso"));

        service.owned = 100;
        ui.click(juan.player, SCREEN, "fila", 0, true, true);
        assertEquals(List.of("sell piedra x3"), service.calls, "three whole lots, one call");
        assertEquals("Cant. x1", ui.variable(juan.player, "bigc_tienda_cantidad"), "the chosen quantity stays");

        service.owned = 32 * 200;
        ui.click(juan.player, SCREEN, "fila", 0, true, true);
        assertEquals("sell piedra x64", service.calls.getLast(), "never more lots than one operation allows");
    }

    @Test
    void shiftLeftClickMarksFavouritesThatOpenAsTheFirstCategory() {
        service.favorites = new ShopFavorites(new dev.linqfy.bigCasares.testsupport.MapPlayerStrings());
        service.catalog = current.get();
        menu.openCategory(juan.player, current.get().category("herramientas").orElseThrow(), () -> { });
        look();
        assertTrue(ui.click(juan.player, SCREEN, "fila", 1, false, true));
        assertEquals(List.of("fav herramientas/gancho"), service.calls, "shift + left click buys nothing");
        assertEquals("★ Gancho está en tus favoritos.", ui.variable(juan.player, "bigc_tienda_aviso"));

        ui.click(juan.player, SCREEN, "volver", -1, false);
        assertEquals(List.of("★ Favoritos", "Bloques", "Herramientas"), names(), "favourites first");
        look();
        ui.click(juan.player, SCREEN, "fila", 0, false);
        assertEquals(List.of("Gancho"), names());
        look();
        ui.click(juan.player, SCREEN, "fila", 0, false);
        assertEquals("buy gancho", service.calls.getLast(), "a favourite buys the catalog's own product");

        ui.click(juan.player, SCREEN, "fila", 0, false, true);
        assertEquals("fav herramientas/gancho", service.calls.getLast(), "and unmarks it from the favourites list too");
        assertEquals("Ya no tenés favoritos: Shift + clic izquierdo en un producto lo agrega.",
            ui.variable(juan.player, "bigc_tienda_aviso"));
        assertEquals(List.of("Bloques", "Herramientas"), names(), "the empty favourites leave the categories");
    }

    @Test
    void aPurchaseTheBalanceDoesNotCoverShowsWhatIsMissing() {
        service.funds = 200;
        menu.openCategory(juan.player, current.get().category("herramientas").orElseThrow(), () -> { });
        assertEquals("$120 / $30", ui.list(juan.player, LIST).get(0).get("precio"), "covered: its price");
        assertEquals("falta $100 / —", ui.list(juan.player, LIST).get(1).get("precio"));
        ui.click(juan.player, SCREEN, "cantidad", -1, false);
        assertEquals("falta $280 / $120", ui.list(juan.player, LIST).get(0).get("precio"), "four lots of $120 against $200");
    }

    @Test
    void theQuantityStaysAcrossPagesAndCategories() {
        menu.openCategory(juan.player, current.get().category("herramientas").orElseThrow(), () -> { });
        ui.click(juan.player, SCREEN, "cantidad", -1, false);
        ui.click(juan.player, SCREEN, "volver", -1, false);
        look();
        ui.click(juan.player, SCREEN, "fila", 0, false);
        assertEquals("Piedra x4", names().get(0));
        assertEquals("Cant. x4", ui.variable(juan.player, "bigc_tienda_cantidad"));
    }

    @Test
    void theBundledScreenMatchesWhatThisMenuSendsAndHandles() throws Exception {
        menu.openCategory(juan.player, current.get().category("herramientas").orElseThrow(), () -> { });
        var contract = dev.linqfy.bigCasares.testsupport.TideframeScreenContract.load(SCREEN);
        contract.buttons(java.util.Set.of("fila", "volver", "anterior", "siguiente", "cantidad"), java.util.Set.of());
        contract.lists(LIST, ShopTideframeMenu.ROWS, java.util.Set.of("fila", "nombre", "precio"));
        contract.texts(ui.variables.get(juan.id).keySet());
    }

    @Test
    void closingTheMenuForgetsWhatThePlayerWasShown() {
        menu.openCategories(juan.player, () -> { });
        ui.closedByPlayer(juan.player, SCREEN);
        assertFalse(ui.click(juan.player, SCREEN, "fila", 0, false));
        menu.openCategories(juan.player, () -> { });
        menu.close();
        assertFalse(ui.handlers.containsKey(SCREEN), "a reloaded shop stops receiving clicks");
    }

    @Test
    void aShopRequestedByAnOnCloseCommandKeepsItsRowsAndAcceptsPurchases() {
        TideframeTestConnection connection = new TideframeTestConnection();
        ShopTideframeMenu connected = new ShopTideframeMenu(current::get, service, connection::ui, clock::get);
        AtomicInteger fallbacks = new AtomicInteger();
        connected.openCategories(juan.player, fallbacks::incrementAndGet);
        connection.tick();
        connection.tick();
        connection.closeWithCommand(juan.player, SCREEN, () -> connected.openCategory(juan.player,
            current.get().category("herramientas").orElseThrow(), fallbacks::incrementAndGet));
        connection.tick();
        connection.tick();
        look();
        assertTrue(connection.click(juan.player, SCREEN, "fila", 1), "the replacement still owns its view");
        assertEquals(List.of("buy gancho"), service.calls);
        assertEquals(List.of("pico", "gancho"), connection.list(juan.player, LIST).stream().map(row -> row.get("id")).toList());
        assertEquals(0, fallbacks.get());
    }

    @Test
    void anOldSameIdShopDoesNotMakeARejectedReplacementCountAsOpened() {
        TideframeTestConnection connection = new TideframeTestConnection();
        ShopTideframeMenu connected = new ShopTideframeMenu(current::get, service, connection::ui, clock::get);
        AtomicInteger fallbacks = new AtomicInteger();
        connected.openCategories(juan.player, fallbacks::incrementAndGet);
        connection.tick();
        connection.tick();
        connected.openCategories(juan.player, fallbacks::incrementAndGet);
        connection.ownPack = false;
        connection.tick();
        connection.tick();
        assertEquals(0, fallbacks.get(), "no inventory may open under the old screen's camera lock");
        assertTrue(connection.ui().menuOpen(juan.player));
        connection.closeWithCommand(juan.player, SCREEN, () -> { });
        connection.tick();
        assertEquals(1, fallbacks.get(), "after the old screen closes the rejected request falls back once");
    }

    @Test
    void aNewNavigationCancelsTheFallbackWaitingForAnOldScreenToClose() {
        TideframeTestConnection connection = new TideframeTestConnection();
        ShopTideframeMenu connected = new ShopTideframeMenu(current::get, service, connection::ui, clock::get);
        AtomicInteger fallbacks = new AtomicInteger();
        connected.openCategories(juan.player, fallbacks::incrementAndGet);
        connection.tick();
        connection.tick();
        connected.openCategories(juan.player, fallbacks::incrementAndGet);
        connection.ownPack = false;
        connection.tick();
        connection.tick();
        connection.ownPack = true;
        connected.openCategories(juan.player, fallbacks::incrementAndGet);
        connection.tick();
        connection.tick();
        connection.closeWithCommand(juan.player, SCREEN, () -> { });
        connection.tick();
        assertEquals(0, fallbacks.get(), "the superseded failure never opens its old inventory");
    }
}
