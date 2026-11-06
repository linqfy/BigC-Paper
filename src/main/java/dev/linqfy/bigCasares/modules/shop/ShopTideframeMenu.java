package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.modules.tideframe.TideframeClick;
import dev.linqfy.bigCasares.modules.tideframe.TideframeScreenHandler;
import dev.linqfy.bigCasares.modules.tideframe.TideframeUi;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The shop on a Tideframe screen for Java players: categories, then a category's products, a page at a time, with the
 * same clicks as the inventory menu (left buys, right sells). Tideframe only draws: every purchase and sale is one call
 * to {@link ShopService} through {@link Actions}, per click, for the product resolved from what this player was shown
 * (the row's id, looked up again in the current catalog). The screen's buttons carry no Tideframe action of their own,
 * and a click handled here is cancelled in Tideframe, so nothing is charged or given twice.
 */
final class ShopTideframeMenu implements TideframeScreenHandler {
    static final String SCREEN = "bigc_tienda";
    static final String LIST = "bigc_tienda";
    /** Rows the screen's list shows: one page. More rows are never sent, so Tideframe's row is the page's index. */
    static final int ROWS = 6;
    /**
     * After the rows change (opened, a category, a page, back), clicks on them wait this long plus the player's whole
     * ping. A click sent before the player could see the new rows (the second click of a double click on a category, a
     * click already on its way) must not buy what that row shows now. Tideframe waits the same way for its opening
     * animation.
     */
    static final long SETTLE_NANOS = 500_000_000L;
    /** Lots a click buys or sells, in the order the "cantidad" button goes through them. */
    static final List<Integer> QUANTITIES = List.of(1, 4, 16);
    /** The most lots one operation moves (ShopService refuses more). */
    static final int MAX_LOTS = 64;

    /** What the shop service does for the screen (ShopService and the economy, or fakes in tests). */
    interface Actions {
        /** {@code lots} times the entry, as one operation (one charge). */
        ShopTransactionResult buy(Player player, ShopEntry entry, int lots);

        /** {@code lots} times the entry, as one operation (all or nothing, one payment). */
        ShopTransactionResult sell(Player player, ShopEntry entry, int lots);

        String balance(Player player);

        /** The player's balance as a number, to show how much a row is short of. */
        double funds(Player player);

        /** Items of the entry the player carries, for "sell all". */
        int owned(Player player, ShopEntry entry);

        /** The player's favourites as a category of the current catalog (see ShopFavorites), or empty. */
        Optional<ShopCategory> favorites(Player player);

        /** Marks or unmarks a favourite; true when it is one now. */
        boolean toggleFavorite(Player player, String categoryId, String entryId);

        /** The purchase price of one lot now (offers, happy hour): what the screen shows and the shop charges. */
        default double buyPrice(ShopEntry entry) {
            return entry.buyPrice();
        }

        /** "OFERTA -20%" while the entry is discounted, else "". */
        default String priceTag(ShopEntry entry) {
            return "";
        }

        String format(double amount);
    }

    /**
     * What one player sees: the categories ({@code categoryId} null) or a category, one page, with each row's id, the
     * lots a click moves, and when ({@link System#nanoTime()}) those rows and prices were first sent.
     */
    record View(String categoryId, int page, List<String> rowIds, int lots, long shownAt) {
        View {
            rowIds = List.copyOf(rowIds);
        }
    }

    private final Supplier<ShopCatalog> catalog;
    private final Actions actions;
    private final Supplier<TideframeUi> ui;
    private final LongSupplier clock;
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    private AutoCloseable registration = () -> { };

    ShopTideframeMenu(Supplier<ShopCatalog> catalog, Actions actions, Supplier<TideframeUi> ui) {
        this(catalog, actions, ui, System::nanoTime);
    }

    ShopTideframeMenu(Supplier<ShopCatalog> catalog, Actions actions, Supplier<TideframeUi> ui, LongSupplier clock) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The categories; false when Tideframe cannot show the screen now (the caller opens the inventory menu). */
    boolean openCategories(Player player, Runnable fallback) {
        return open(player, null, fallback);
    }

    /** One category (a shop NPC); false when Tideframe cannot show the screen now. */
    boolean openCategory(Player player, ShopCategory category, Runnable fallback) {
        return open(player, category.id(), fallback);
    }

    private boolean open(Player player, String categoryId, Runnable fallback) {
        TideframeUi tideframe = ui.get();
        if (!tideframe.canShow(player, SCREEN)) {
            return false;
        }
        registration = tideframe.handle(SCREEN, this);
        UUID playerId = player.getUniqueId();
        tideframe.open(player, SCREEN, () -> show(tideframe, player, categoryId, 0, 1, ""), opened -> {
            if (!opened) {
                views.remove(playerId);
                if (player.isOnline()) {
                    fallback.run();
                }
            }
        });
        return true;
    }

    @Override
    public boolean click(Player player, TideframeClick click) {
        View view = views.get(player.getUniqueId());
        if (view == null) {
            return false;
        }
        TideframeUi tideframe = ui.get();
        switch (click.elementId()) {
            case "fila" -> pick(tideframe, player, view, click);
            case "volver" -> {
                if (view.categoryId() == null) {
                    close(tideframe, player);
                } else {
                    show(tideframe, player, null, 0, view.lots(), "");
                }
            }
            case "anterior" -> show(tideframe, player, view.categoryId(), view.page() - 1, view.lots(), "");
            case "siguiente" -> show(tideframe, player, view.categoryId(), view.page() + 1, view.lots(), "");
            case "cantidad" -> show(tideframe, player, view.categoryId(), view.page(), next(view.lots()), "");
            // The X ("cerrar") keeps Tideframe's own close action; closed() hears it.
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void closed(Player player) {
        views.remove(player.getUniqueId());
    }

    /** The shop reloads or BigCasares goes away: forget every view and stop receiving clicks. */
    void close() {
        views.clear();
        try {
            registration.close();
        } catch (Exception ignored) {
            // The registration only removes a map entry.
        }
    }

    /**
     * A row: the id it showed, resolved again in the current catalog. Outside the page, gone, or clicked before the
     * player could see it, nothing is done.
     */
    private void pick(TideframeUi tideframe, Player player, View view, TideframeClick click) {
        if (click.row() < 0 || click.row() >= view.rowIds().size() || !settled(player, view)) {
            return;
        }
        String id = view.rowIds().get(click.row());
        ShopCatalog current = catalog.get();
        if (view.categoryId() == null) {
            if (ShopFavorites.CATEGORY.equals(id) || current.category(id).isPresent()) {
                show(tideframe, player, id, 0, view.lots(), "");
            } else {
                show(tideframe, player, null, 0, view.lots(), "Esa categoría ya no está.");
            }
            return;
        }
        // A favourites row says where its product lives ("category/entry"); any other row belongs to its category.
        boolean favorites = ShopFavorites.CATEGORY.equals(view.categoryId());
        String key = favorites ? id : ShopFavorites.key(view.categoryId(), id);
        Optional<ShopEntry> entry = favorites ? ShopFavorites.resolve(current, id) : current.entry(view.categoryId(), id);
        if (entry.isEmpty()) {
            show(tideframe, player, view.categoryId(), view.page(), view.lots(), "Ese producto ya no está.");
            return;
        }
        if (!click.rightClick() && click.shiftClick()) {
            // Shift + left click marks or unmarks a favourite; it buys nothing.
            int slash = key.indexOf('/');
            boolean now = actions.toggleFavorite(player, key.substring(0, slash), key.substring(slash + 1));
            show(tideframe, player, view.categoryId(), view.page(), view.lots(),
                (now ? "★ " + name(entry.get()) + " está en tus favoritos." : name(entry.get()) + " ya no está en tus favoritos."));
            return;
        }
        // One click, one operation (of the lots shown), by the same service and rules as the inventory menu.
        boolean sell = click.rightClick();
        int lots = view.lots();
        if (sell && click.shiftClick()) {
            // Shift + right click sells every whole lot carried (up to 64), still as one operation.
            lots = Math.min(MAX_LOTS, actions.owned(player, entry.get()) / Math.max(1, entry.get().amount()));
            if (lots == 0) {
                show(tideframe, player, view.categoryId(), view.page(), view.lots(), "No tenés " + name(entry.get()) + " para vender.");
                return;
            }
        }
        ShopTransactionResult result = sell ? actions.sell(player, entry.get(), lots) : actions.buy(player, entry.get(), lots);
        player.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
        String notice = !result.success() ? result.message() : (sell ? "Vendiste " : "Compraste ") + name(entry.get())
            + (lots > 1 ? " (x" + lots + ")" : "") + " por "
            + plain(actions.format((sell ? entry.get().sellPrice() : actions.buyPrice(entry.get())) * lots)) + ".";
        show(tideframe, player, view.categoryId(), view.page(), view.lots(), notice);
    }

    /** The next quantity after {@code lots}, back to the first after the last. */
    static int next(int lots) {
        int at = QUANTITIES.indexOf(lots);
        return QUANTITIES.get((at + 1) % QUANTITIES.size());
    }

    private boolean settled(Player player, View view) {
        // No cap: on a slow link the click that was already on its way arrives later still.
        long ping = Math.max(0, player.getPing()) * 1_000_000L;
        return clock.getAsLong() - view.shownAt() >= SETTLE_NANOS + ping;
    }

    private static String name(ShopEntry entry) {
        return entry.label();
    }

    private void close(TideframeUi tideframe, Player player) {
        views.remove(player.getUniqueId());
        tideframe.close(player, SCREEN);
    }

    /** Sends the page's rows and texts, and remembers exactly what the player is shown. */
    private void show(TideframeUi tideframe, Player player, String categoryId, int page, int lots, String notice) {
        ShopCatalog current = catalog.get();
        Optional<ShopCategory> favorites = actions.favorites(player);
        Optional<ShopCategory> category = categoryId == null ? Optional.empty()
            : ShopFavorites.CATEGORY.equals(categoryId) ? favorites : current.category(categoryId);
        if (categoryId != null && category.isEmpty()) {
            show(tideframe, player, null, 0, lots, ShopFavorites.CATEGORY.equals(categoryId)
                ? "Ya no tenés favoritos: Shift + clic izquierdo en un producto lo agrega." : "Esa categoría ya no está.");
            return;
        }
        // The categories, with the player's favourites first while there is any.
        List<ShopCategory> categories = new ArrayList<>();
        favorites.ifPresent(categories::add);
        categories.addAll(current.categories());
        int total = category.map(c -> c.entries().size()).orElse(categories.size());
        int pages = Math.max(1, (total + ROWS - 1) / ROWS);
        int shown = Math.max(0, Math.min(page, pages - 1));
        List<Map<String, String>> rows = category.isPresent()
            ? entryRows(category.get(), shown, lots, actions.funds(player)) : categoryRows(categories, shown);
        List<String> ids = rows.stream().map(row -> row.get("id")).toList();
        String shownCategory = category.map(ShopCategory::id).orElse(null);
        // The same rows and prices again (a purchase's notice) are already in front of the player: they do not wait
        // again. A new quantity changes every price shown, so it waits like new rows.
        View before = views.get(player.getUniqueId());
        long shownAt = before != null && Objects.equals(before.categoryId(), shownCategory) && before.rowIds().equals(ids)
            && before.lots() == lots ? before.shownAt() : clock.getAsLong();
        views.put(player.getUniqueId(), new View(shownCategory, shown, ids, lots, shownAt));
        tideframe.setVariable(player, "bigc_tienda_cantidad", "Cant. x" + lots);
        tideframe.setList(player, LIST, rows);
        tideframe.setVariable(player, "bigc_tienda_titulo", category.map(c -> plain(c.name())).orElse("Categorías"));
        tideframe.setVariable(player, "bigc_tienda_ayuda", category.isPresent()
            ? "Izq. comprar · Der. vender · Shift+Der. todo · Shift+Izq. ★" : "Tocá una para ver sus productos");
        tideframe.setVariable(player, "bigc_tienda_saldo", plain(actions.balance(player)));
        tideframe.setVariable(player, "bigc_tienda_pagina", pages > 1 ? (shown + 1) + " de " + pages : "");
        // The screen shows the arrows only with more than one page, and "Volver" only inside a category.
        tideframe.setVariable(player, "bigc_tienda_paginas", String.valueOf(pages));
        tideframe.setVariable(player, "bigc_tienda_productos", category.isPresent() ? "1" : "0");
        tideframe.setVariable(player, "bigc_tienda_aviso", notice == null ? "" : plain(notice));
    }

    private List<Map<String, String>> categoryRows(List<ShopCategory> categories, int page) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = page * ROWS; i < Math.min(categories.size(), (page + 1) * ROWS); i++) {
            ShopCategory category = categories.get(i);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", category.id());
            row.put("nombre", plain(category.name()));
            int count = category.entries().size();
            row.put("precio", count == 1 ? "1 producto" : count + " productos");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, String>> entryRows(ShopCategory category, int page, int lots, double funds) {
        List<Map<String, String>> rows = new ArrayList<>();
        List<ShopEntry> entries = category.entries();
        for (int i = page * ROWS; i < Math.min(entries.size(), (page + 1) * ROWS); i++) {
            ShopEntry entry = entries.get(i);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", entry.id());
            int items = entry.amount() * lots;
            String tag = actions.priceTag(entry);
            row.put("nombre", (items > 1 ? name(entry) + " x" + items : name(entry)) + (tag.isEmpty() ? "" : " · " + tag));
            // Buy / sell of the quantity chosen, as the inventory menu's lore; "—" when it cannot be sold. A purchase
            // the balance does not cover shows what is missing instead of its price.
            double price = actions.buyPrice(entry) * lots;
            String buy = funds < price ? "falta " + plain(actions.format(price - funds)) : plain(actions.format(price));
            row.put("precio", buy + " / " + (entry.sellPrice() > 0 ? plain(actions.format(entry.sellPrice() * lots)) : "—"));
            rows.add(row);
        }
        return rows;
    }

    /** Text without Minecraft colour codes (Tideframe draws its own colours). */
    static String plain(String text) {
        return text == null ? "" : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text)).strip();
    }
}
