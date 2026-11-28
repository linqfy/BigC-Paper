package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.modules.missions.MissionHudView;
import dev.linqfy.bigCasares.modules.tideframe.NativeMenus;
import dev.linqfy.bigCasares.modules.tideframe.Panel;
import dev.linqfy.bigCasares.modules.tideframe.PixelFont;
import dev.linqfy.bigCasares.modules.tideframe.PixelImage;
import dev.linqfy.bigCasares.modules.tideframe.ScreenArt;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

final class NativeShop {
    private final BigCasares plugin;
    private final NativeMenus menus;
    private final ShopCatalog catalog;
    private final ShopService service;
    private final ShopEconomyGateway economy;
    private final BiConsumer<Player, ShopCategory> classic;
    private boolean active = true;

    NativeShop(BigCasares plugin, NativeMenus menus, ShopCatalog catalog, ShopService service,
               ShopEconomyGateway economy, BiConsumer<Player, ShopCategory> classic) {
        this.plugin = plugin;
        this.menus = menus;
        this.catalog = catalog;
        this.service = service;
        this.economy = economy;
        this.classic = classic;
    }

    boolean open(Player player, ShopCategory selected) {
        int size = catalog.categories().size();
        int index = selected == null ? 0 : catalog.categories().indexOf(selected);
        if (!active || index < 0 || size == 0 || size > ScreenArt.MAX_TABS || !menus.available(player)) return false;
        return show(player, NativeShopPage.select(catalog, index, 0, ""));
    }

    void close() {
        active = false;
        menus.closeGroup("shop");
    }

    private boolean current(Player player, Panel panel) {
        return active && player.isOnline() && !player.isDead() && menus.current(player, panel) && menus.available(player);
    }

    private void navigate(Player player, Panel source, int category, int page, String query) {
        if (!current(player, source)) return;
        if (!show(player, NativeShopPage.select(catalog, category, page, query))) {
            menus.close(player);
            classic.accept(player, catalog.categories().get(category));
        }
    }

    private boolean show(Player player, NativeShopPage view) {
        List<ShopCategory> categories = catalog.categories();
        boolean searching = !view.query().isEmpty();
        Panel panel = new Panel(true, searching ? -1 : view.category(), categories.size());
        PixelFont font = PixelFont.get();
        String name = NativeShopPage.plain(categories.get(view.category()).name());
        String title = searching ? "Resultados" : "Tienda - " + name;
        if (!searching && font.width(PixelFont.sanitize(title)) > 103) title = name;
        panel.image(ScreenArt.header(title, ScreenArt.WHITE, 104), 21, 41);
        String balance = font.fit("$ " + ScreenArt.amount(economy.balance(player)), 36);
        panel.text(balance, 223 - font.width(balance), 43, ScreenArt.GOLD);

        for (int k = 0; k < categories.size(); k++) {
            int index = k, x = ScreenArt.tabX(k);
            item(panel, categories.get(k).icon(), x + 4, 116);
            panel.button(x, 115, 24, 20, right -> { if (!right) navigate(player, panel, index, 0, ""); });
            panel.highlight(x, 115, 24, 20, ScreenArt.tabHover(), x, 115);
        }
        panel.item("writable_book", ScreenArt.NAV_X + 4, 116);
        panel.highlight(ScreenArt.NAV_X, 115, 24, 20, ScreenArt.tabHover(), ScreenArt.NAV_X, 115);
        panel.button(ScreenArt.NAV_X, 115, 24, 20, right -> {
            if (right || !current(player, panel)) return;
            var missions = plugin.getMissionModule();
            if (missions == null || !missions.isEnabled()) {
                player.sendActionBar(Component.text("Las misiones no están disponibles.", NamedTextColor.GRAY));
                return;
            }
            missions.openHud(player, MissionHudView.DAILY_LIST);
        });

        int fieldWidth = view.pageCount() > 1 ? 34 : 60;
        PixelImage field = new PixelImage(fieldWidth, 11).fill(0, 0, fieldWidth, 11, PixelImage.rgb(0))
            .outline(0, 0, fieldWidth, 11, PixelImage.rgb(0x7d7d79));
        font.draw(field, font.fit(searching ? view.query() : "Buscar...", fieldWidth - 6), 3, 3,
            searching ? ScreenArt.WHITE : 0xFF808080, false);
        panel.image(field, 126, 41);
        panel.hover(126, 41, fieldWidth, 11, new PixelImage(fieldWidth, 11).outline(0, 0, fieldWidth, 11, ScreenArt.WHITE), 126, 41);
        panel.button(126, 41, fieldWidth, 11, right -> { if (!right && current(player, panel)) search(player, panel, view); });
        if (view.pageCount() > 1) {
            pageButton(player, panel, view, true);
            pageButton(player, panel, view, false);
        }
        for (int i = 0; i < view.offers().size(); i++) product(player, panel, view, view.offers().get(i), i);
        if (view.offers().isEmpty()) {
            String message = searching ? "Sin resultados" : "Sin productos en esta categoría";
            PixelImage empty = ScreenArt.tooltip(List.of(message), List.of(ScreenArt.GRAY));
            panel.image(empty, 128 - empty.width() / 2, 73);
        }
        return menus.show(player, panel);
    }

    private void pageButton(Player player, Panel panel, NativeShopPage view, boolean previous) {
        int x = previous ? 162 : 175;
        int target = view.number() + (previous ? -1 : 1);
        boolean enabled = target >= 0 && target < view.pageCount();
        PixelImage button = new PixelImage(11, 11).button(0, 0, 11, 11);
        ScreenArt.arrow(button, previous ? 3 : 4, 2, previous, enabled ? ScreenArt.WHITE : 0xFF555555);
        panel.image(button, x, 41);
        if (enabled) {
            panel.hover(x, 41, 11, 11, new PixelImage(11, 11).outline(0, 0, 11, 11, ScreenArt.WHITE), x, 41);
            panel.button(x, 41, 11, 11, right -> { if (!right) navigate(player, panel, view.category(), target, view.query()); });
        }
    }

    private void product(Player player, Panel panel, NativeShopPage view, NativeShopPage.Offer offer, int index) {
        ShopEntry entry = offer.entry();
        int x = 24 + 27 * (index % 8), y = 52 + 30 * (index / 8);
        Material material = entry.material();
        if (material == null) {
            material = plugin.getCustomItemRegistry().findById(entry.customItemId())
                .map(item -> item.createItemStack(1).getType()).orElse(null);
        }
        item(panel, material, x + 1, y + 1);
        PixelFont font = PixelFont.get();
        if (entry.amount() > 1) {
            String quantity = font.fit(Integer.toString(entry.amount()), 16);
            panel.text(quantity, x + 17 - font.width(quantity), y + 9, ScreenArt.WHITE);
        }
        boolean buy = material != null && entry.amount() > 0 && NativeShopPage.tradable(entry.buyPrice());
        boolean sell = material != null && entry.amount() > 0 && NativeShopPage.tradable(entry.sellPrice());
        String amount = ScreenArt.amount(buy ? entry.buyPrice() : sell ? entry.sellPrice() : 0);
        String price = buy || sell ? font.width("$" + amount) <= 25 ? "$" + amount : font.fit(amount, 25) : "-";
        panel.text(price, x + 9 - (font.width(price) + 1) / 2, y + 20, buy ? ScreenArt.GOLD : sell ? ScreenArt.GREEN : ScreenArt.GRAY);

        PixelImage tip = ScreenArt.tooltip(List.of(font.fit(offer.name() + (entry.amount() > 1 ? " x" + entry.amount() : ""), 195),
            buy ? "Clic: comprar $ " + exactPrice(entry.buyPrice()) : "No se compra",
            sell ? "Clic der.: vender $ " + exactPrice(entry.sellPrice()) : "No se vende"),
            List.of(ScreenArt.WHITE, buy ? ScreenArt.GOLD : ScreenArt.GRAY, sell ? ScreenArt.GREEN : ScreenArt.GRAY));
        int tx = index % 8 < 4 ? x + 20 : x - 2 - tip.width(), ty = y + 1;
        if (tx < 14 || tx + tip.width() > 242) {
            tx = Math.clamp(x + 9 - tip.width() / 2, 14, 242 - tip.width());
            ty = index < 8 ? y + 19 : y - tip.height() - 1;
        }
        panel.highlight(x, y, 18, 18, ScreenArt.slotHover(), x, y);
        panel.hover(x, y, 18, 18, tip, tx, ty);
        panel.button(x, y, 18, 18, right -> {
            if (!current(player, panel)) return;
            ShopEntry current = offer.resolve(catalog, right);
            if (current == null) {
                player.sendActionBar(Component.text("Esta operación no está disponible.", NamedTextColor.RED));
                return;
            }
            ShopTransactionResult result = service.trade(player, current, right);
            navigate(player, panel, view.category(), view.number(), view.query());
            player.sendActionBar(Component.text(result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
        });
    }

    private static void item(Panel panel, Material material, int x, int y) {
        String id = material == null ? "barrier" : material.name().toLowerCase(Locale.ROOT);
        panel.item(Panel.knowsItem(id) ? id : "barrier", x, y);
    }

    private static String exactPrice(double price) {
        var decimal = BigDecimal.valueOf(price).stripTrailingZeros();
        return decimal.precision() - decimal.scale() <= 12 && decimal.scale() <= 6 ? decimal.toPlainString() : decimal.toString();
    }

    private void search(Player player, Panel panel, NativeShopPage view) {
        var options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();
        var submit = DialogAction.customClick((response, audience) -> {
            if (audience != player || !current(player, panel)) return;
            String query = response.getText("query");
            if (query != null && query.length() <= 32) navigate(player, panel, view.category(), 0, query);
        }, options);
        player.showDialog(Dialog.create(factory -> factory.empty()
            .base(DialogBase.builder(Component.text("Buscar en la tienda")).canCloseWithEscape(true).pause(false)
                .inputs(List.of(DialogInput.text("query", Component.text("¿Qué buscás?")).initial(view.query()).maxLength(32).build())).build())
            .type(DialogType.confirmation(ActionButton.create(Component.text("Buscar"), null, 100, submit),
                ActionButton.create(Component.text("Cancelar"), null, 100, null)))));
    }
}
