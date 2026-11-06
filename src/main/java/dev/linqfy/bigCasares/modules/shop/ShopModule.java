package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.bounties.BountyEconomyGateway;
import dev.linqfy.bigCasares.platform.BedrockItemIcons;
import dev.linqfy.bigCasares.platform.FeedbackSounds;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import dev.linqfy.bigCasares.platform.ClientEntityPresentationRegistry;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ShopModule implements PluginModule {

    private final BigCasares plugin;

    private ShopCatalog catalog = new ShopCatalog(java.util.List.of());
    private ShopService service;
    private ShopEconomyGateway economyGateway;
    private ShopGuiController guiController;
    private ShopNpcFactory npcFactory;
    private ShopNpcListener npcListener;
    private final List<org.bukkit.entity.Entity> npcs = new ArrayList<>();
    private ClientPlatformGateway platformGateway = ignored -> ClientPlatform.JAVA;
    private BedrockFormSender bedrockFormSender;
    private java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> tideframe =
        () -> dev.linqfy.bigCasares.modules.tideframe.TideframeUi.NONE;
    private ShopTideframeMenu tideframeMenu;
    private java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> tideframeNavigation =
        () -> dev.linqfy.bigCasares.modules.tideframe.TideframeUi.NONE;
    private final ShopFormGeneration formGeneration = new ShopFormGeneration();
    private final ShopHistory history = new ShopHistory();
    private final ShopFavorites favorites;
    private volatile dev.linqfy.bigCasares.platform.HappyHour happyHour = dev.linqfy.bigCasares.platform.HappyHour.OFF;
    private boolean happyHourWasActive;
    private final dev.linqfy.bigCasares.platform.PlayerStrings playerStrings;
    /** The id of the Bedrock form's quantity button; never a catalog entry id (those come from shop.yml keys). */
    static final String QUANTITY_BUTTON = "#cantidad";
    /** The id of the Bedrock form's buy/sell switch. */
    static final String MODE_BUTTON = "#modo";
    /** The id of the Bedrock category form's way back to the categories. */
    static final String BACK_BUTTON = "#volver";
    /** Buttons before the products in a Bedrock category form (back, quantity and mode). */
    static final int HEADER_BUTTONS = 3;
    /** Each Bedrock player's chosen quantity of lots per tap (1 when not set). */
    private final Map<UUID, Integer> bedrockLots = new java.util.concurrent.ConcurrentHashMap<>();
    /** Each Bedrock player's mode in category forms (buy when not set). */
    private final Map<UUID, BedrockMode> bedrockModes = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean enabled;
    private BukkitRuntimeRegistrations registrations;
    private RuntimeRegistrationScope compatibilityScope;
    private long runtimeResourceSequence;

    public ShopModule(BigCasares plugin) {
        this.plugin = plugin;
        this.playerStrings = dev.linqfy.bigCasares.platform.PlayerStrings.of(plugin);
        this.favorites = new ShopFavorites(playerStrings);
    }

    @Override
    public String getId() {
        return "entity-shop-system";
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        this.compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        this.registrations = new BukkitRuntimeRegistrations(plugin, scope);
        scope.register("module-state", this::clearRuntimeState);
        // The history kept with each player comes back when they join (and for those online at a reload).
        registrations.registerListener("shop-history-restore", new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                restoreHistory(event.getPlayer());
            }
        });
        plugin.getServer().getOnlinePlayers().forEach(this::restoreHistory);
        // The happy hour says in chat when it starts and ends (checked every minute; nothing while it is off).
        registrations.scheduleRepeating("happy-hour-announcer", this::announceHappyHour, 1200L, 1200L);
        plugin.saveResource("shop.yml", false);
        reload();
    }

    @Override
    public void onDisable() {
        enabled = false;
        formGeneration.invalidate();
        if (guiController != null) {
            guiController.closeAll();
            org.bukkit.event.HandlerList.unregisterAll(guiController);
        }
        if (npcListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(npcListener);
            npcListener = null;
        }
        despawnNpcs();
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    public BigCasares plugin() {
        return plugin;
    }

    public void reload() {
        enabled = false;
        formGeneration.invalidate();
        Economy economy = BountyEconomyGateway.resolveOrThrow(plugin);
        File file = new File(plugin.getDataFolder(), "shop.yml");
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        this.catalog = new ShopCatalogLoader().load(configuration);
        this.economyGateway = new ShopEconomyGateway(plugin, economy);
        // Offers (shop.yml "ofertas") and the happy hour (config.yml "eventos.hora-feliz"), both off by default, and the
        // daily limits of shop.yml entries: one price and one count for every menu.
        ShopCatalog loaded = catalog;
        this.happyHour = dev.linqfy.bigCasares.platform.HappyHour.load(plugin.getConfig());
        ShopPricing pricing = new ShopPricing(loaded, ShopPricing.Offers.load(configuration), happyHour, java.time.ZonedDateTime::now);
        ShopDailyLimits limits = new ShopDailyLimits(playerStrings, java.time.LocalDate::now, loaded::keyOf);
        this.service = new ShopService(
            new ShopItemResolver(plugin.getCustomItemRegistry()),
            economyGateway,
            plugin::enforceInventoryLimits,
            // Every menu's purchases and sales: a sound for the player, and the done ones in their history.
            (player, result) -> {
                FeedbackSounds.result(player, result.success());
                if (result.success()) {
                    history.record(player.getUniqueId(), result.message());
                    playerStrings.set(player, ShopHistory.KEY, history.encode(player.getUniqueId()));
                }
            },
            pricing,
            limits
        );
        if (guiController != null) {
            guiController.closeAll();
            org.bukkit.event.HandlerList.unregisterAll(guiController);
        }
        this.guiController = new ShopGuiController(this, catalog, service);
        ShopGuiController ownedGuiController = guiController;
        registrations.registerListener(nextResourceId("shop-gui-listener"), ownedGuiController);
        registrations.ownCleanup(nextResourceId("shop-gui-windows"), ownedGuiController::closeAll);
        // Java players with Tideframe get the same shop on its screen: same catalog, same service, one call per click.
        if (tideframeMenu != null) {
            tideframeMenu.close();
        }
        ShopService ownedService = service;
        ShopEconomyGateway ownedEconomy = economyGateway;
        ShopCatalog ownedCatalog = catalog;
        ShopTideframeMenu menu = new ShopTideframeMenu(() -> ownedCatalog, new ShopTideframeMenu.Actions() {
            @Override public ShopTransactionResult buy(Player player, ShopEntry entry, int lots) { return ownedService.buy(player, entry, lots); }
            @Override public ShopTransactionResult sell(Player player, ShopEntry entry, int lots) { return ownedService.sell(player, entry, lots); }
            @Override public String balance(Player player) { return ownedEconomy.format(ownedEconomy.balance(player)); }
            @Override public double funds(Player player) { return ownedEconomy.balance(player); }
            @Override public int owned(Player player, ShopEntry entry) { return ownedService.owned(player, entry); }
            @Override public java.util.Optional<ShopCategory> favorites(Player player) { return favorites.category(player, ownedCatalog); }
            @Override public boolean toggleFavorite(Player player, String categoryId, String entryId) {
                return favorites.toggle(player, categoryId, entryId);
            }
            @Override public double buyPrice(ShopEntry entry) { return ownedService.buyPrice(entry); }
            @Override public String priceTag(ShopEntry entry) { return ownedService.priceTag(entry); }
            @Override public String format(double amount) { return ownedEconomy.format(amount); }
        }, () -> tideframe.get());
        this.tideframeMenu = menu;
        registrations.ownCleanup(nextResourceId("shop-tideframe"), menu::close);
        if (npcListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(npcListener);
        }
        despawnNpcs();
        this.npcFactory = new ShopNpcFactory(plugin);
        this.npcListener = new ShopNpcListener(npcFactory, this::openNpcShop);
        registrations.registerListener(nextResourceId("shop-npc-listener"), npcListener);
        spawnConfiguredNpcs(configuration);
        this.enabled = true;
        formGeneration.activateNext();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void openMainMenu(Player player) {
        if (!enabled || guiController == null) {
            player.sendMessage("§cEl shop no esta disponible.");
            return;
        }
        if (platformGateway.resolvePlatform(player.getUniqueId()) == ClientPlatform.BEDROCK
            && bedrockFormSender != null) {
            openBedrockCategories(player);
            return;
        }
        ShopTideframeMenu menu = tideframeMenu;
        if (menu != null && menu.openCategories(player, () -> openClassicCategories(player))) {
            return;
        }
        openClassicCategories(player);
    }

    public void configurePlatform(ClientPlatformGateway platformGateway, BedrockFormSender bedrockFormSender) {
        this.platformGateway = platformGateway == null ? ignored -> ClientPlatform.JAVA : platformGateway;
        this.bedrockFormSender = bedrockFormSender;
    }

    /** Where the Tideframe screens come from (the Tideframe module, or none). Bedrock keeps its forms either way. */
    public void configureTideframe(java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> tideframe) {
        this.tideframe = tideframe == null ? () -> dev.linqfy.bigCasares.modules.tideframe.TideframeUi.NONE : tideframe;
    }

    /** The inventory menu, when Tideframe could not open after all. */
    private void openClassicCategories(Player player) {
        if (enabled && guiController != null && player.isOnline()) {
            tideframeNavigation.get().openClassic(player, () -> guiController.openCategories(player));
        }
    }

    public void configureTideframeNavigation(java.util.function.Supplier<dev.linqfy.bigCasares.modules.tideframe.TideframeUi> navigation) {
        this.tideframeNavigation = navigation == null ? () -> dev.linqfy.bigCasares.modules.tideframe.TideframeUi.NONE : navigation;
    }

    private void openClassicCategory(Player player, ShopCategory category) {
        if (enabled && guiController != null && player.isOnline()) {
            tideframeNavigation.get().openClassic(player, () -> guiController.openCategory(player, category));
        }
    }

    private void openNpcShop(Player player, String shopId) {
        // With a Tideframe menu up the click is the menu's (Tideframe answers it as a right click): opening the shop
        // again would only queue the classic menu for when that screen closes.
        if (tideframeNavigation.get().menuOpen(player)) {
            return;
        }
        ShopCategory category = catalog.category(shopId).orElse(null);
        if (category == null) {
            openMainMenu(player);
            return;
        }
        if (platformGateway.resolvePlatform(player.getUniqueId()) == ClientPlatform.BEDROCK
            && bedrockFormSender != null) {
            openBedrockCategory(player, category);
        } else {
            ShopTideframeMenu menu = tideframeMenu;
            if (menu == null || !menu.openCategory(player, category, () -> openClassicCategory(player, category))) {
                openClassicCategory(player, category);
            }
        }
    }

    private void openBedrockCategories(Player player) {
        UUID playerId = player.getUniqueId();
        long generation = formGeneration.current();
        ShopView view = new ShopView(
            "categories",
            "§6§lTienda BigCasares",
            "§7Elegí una categoría",
            economyGateway.format(economyGateway.balance(player)),
            bedrockCategoryButtons(catalog, favorites.category(player, catalog))
        );
        BukkitRuntimeRegistrations activeRegistrations = registrations;
        boolean sent = bedrockFormSender.send(playerId, view,
            index -> activeRegistrations.guard(
                () -> handleBedrockCategoryResponse(playerId, generation, view, index)).run());
        if (!sent) {
            new ChatFallbackShopUi(plugin.getServer()).openShop(player.getUniqueId(), view);
        }
    }

    private void openBedrockCategory(Player player, ShopCategory category) {
        UUID playerId = player.getUniqueId();
        long generation = formGeneration.current();
        int lots = bedrockLots.getOrDefault(playerId, 1);
        BedrockMode mode = bedrockModes.getOrDefault(playerId, BedrockMode.BUY);
        double funds = economyGateway.balance(player);
        ShopService activeService = service;
        java.util.Set<String> favoriteKeys = favorites.keys(player);
        List<ShopViewItem> items = bedrockCategoryItems(category, lots, mode, funds,
            entry -> activeService == null ? 0 : activeService.owned(player, entry), economyGateway::format,
            entry -> favoriteKeys.contains(favoriteKey(category.id(), entry.id())),
            entry -> activeService == null ? entry.buyPrice() : activeService.buyPrice(entry),
            entry -> activeService == null ? "" : activeService.priceTag(entry));
        ShopView view = new ShopView(category.id(), category.name(), mode.help, economyGateway.format(funds), items);
        BukkitRuntimeRegistrations activeRegistrations = registrations;
        boolean sent = bedrockFormSender.send(playerId, view,
            index -> activeRegistrations.guard(
                () -> handleBedrockPurchaseResponse(playerId, generation, category.id(), view, lots, mode, index)).run());
        if (!sent) {
            new ChatFallbackShopUi(plugin.getServer()).openShop(player.getUniqueId(), new ShopView(view.id(), view.title(),
                view.description(), view.balance(), items.subList(HEADER_BUTTONS, items.size())));
        }
    }

    private void handleBedrockCategoryResponse(UUID playerId, long generation, ShopView view, int index) {
        Player player = resolveActiveFormPlayer(playerId, generation);
        if (player == null || index < 0 || index >= view.items().size()) {
            return;
        }
        String categoryId = view.items().get(index).id();
        bedrockCategory(player, categoryId).ifPresent(category -> openBedrockCategory(player, category));
    }

    /** A category by id, the player's favourites included. */
    private java.util.Optional<ShopCategory> bedrockCategory(Player player, String categoryId) {
        return ShopFavorites.CATEGORY.equals(categoryId) ? favorites.category(player, catalog) : catalog.category(categoryId);
    }

    /** The {@code category/entry} key of a row (a favourites row already is one). */
    static String favoriteKey(String categoryId, String entryId) {
        return ShopFavorites.CATEGORY.equals(categoryId) ? entryId : ShopFavorites.key(categoryId, entryId);
    }

    /**
     * The Bedrock categories form: the player's favourites first while there is any, then each category with its
     * icon and how many products it has, as the Java screen.
     */
    static List<ShopViewItem> bedrockCategoryButtons(ShopCatalog catalog, java.util.Optional<ShopCategory> favorites) {
        List<ShopCategory> categories = new ArrayList<>();
        favorites.ifPresent(categories::add);
        categories.addAll(catalog.categories());
        return categories.stream()
            .map(category -> new ShopViewItem(category.id(), category.name(), "",
                category.entries().size() == 1 ? "1 producto" : category.entries().size() + " productos", null, true,
                BedrockItemIcons.path(category.icon()).orElse(null)))
            .toList();
    }

    /** What a tap on a product does in the Bedrock category form; the mode button goes through them in order. */
    enum BedrockMode {
        BUY("§aModo: comprar §7(tocá para cambiar)", "§7Tocá un objeto para comprarlo"),
        SELL("§6Modo: vender §7(tocá para cambiar)", "§7Tocá un objeto para venderlo"),
        FAVORITE("§eModo: favoritos §7(tocá para cambiar)", "§7Tocá un objeto para agregarlo o quitarlo de tus favoritos");

        final String label;
        final String help;

        BedrockMode(String label, String help) {
            this.label = label;
            this.help = help;
        }

        BedrockMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * The Bedrock category form's buttons: back to the categories, the quantity a tap moves (as the Java screen's "Cant.", x1, x4, x16 in turn)
     * and whether it buys or sells, then each product with the name and price of that quantity and its vanilla Bedrock
     * icon. Buying, a row the balance does not cover is red with what is missing; selling, a row the player does not
     * carry enough of is red with how many they have, and one the shop does not buy back says so.
     */
    static List<ShopViewItem> bedrockCategoryItems(ShopCategory category, int lots, BedrockMode mode, double funds,
                                                   java.util.function.ToIntFunction<ShopEntry> owned,
                                                   java.util.function.DoubleFunction<String> money,
                                                   java.util.function.Predicate<ShopEntry> favorite,
                                                   java.util.function.ToDoubleFunction<ShopEntry> unitPrice,
                                                   java.util.function.Function<ShopEntry, String> tag) {
        List<ShopViewItem> items = new ArrayList<>();
        items.add(new ShopViewItem(BACK_BUTTON, "§7« Volver a las categorías", "", "", null, true));
        items.add(new ShopViewItem(QUANTITY_BUTTON, "§bCantidad: x" + lots + " §7(tocá para cambiar)", "", "", null, true));
        items.add(new ShopViewItem(MODE_BUTTON, mode.label, "", "", null, true));
        for (ShopEntry entry : category.entries()) {
            int count = entry.amount() * lots;
            String name = entry.displayName() == null ? entry.id() : entry.displayName();
            String price;
            boolean available;
            if (mode == BedrockMode.FAVORITE) {
                available = true;
                price = favorite.test(entry) ? "★ en tus favoritos" : "☆ tocá para agregar";
            } else if (mode == BedrockMode.BUY) {
                double cost = unitPrice.applyAsDouble(entry) * lots;
                available = funds >= cost;
                price = money.apply(cost) + (available ? "" : " (faltan " + money.apply(cost - funds) + ")");
            } else if (entry.sellPrice() <= 0) {
                available = false;
                price = "no se compra acá";
            } else {
                int carried = owned.applyAsInt(entry);
                available = carried >= count;
                price = money.apply(entry.sellPrice() * lots) + (available ? "" : " (tenés " + carried + ")");
            }
            String label = tag.apply(entry);
            items.add(new ShopViewItem(
                entry.id(),
                (count > 1 ? name + " §7x" + count : name) + (label.isEmpty() ? "" : " §6" + label),
                String.join("\n", entry.lore()),
                price,
                null,
                available,
                // A custom item has no vanilla icon on Bedrock: its row keeps the name and price only.
                entry.customItemId() == null ? BedrockItemIcons.path(entry.material()).orElse(null) : null
            ));
        }
        return items;
    }

    /**
     * A tap on the Bedrock category form: the quantity and mode buttons switch and show the form again; a product row
     * buys or sells the quantity that form showed, in the mode it showed, as one operation.
     */
    private void handleBedrockPurchaseResponse(UUID playerId, long generation, String categoryId, ShopView view, int lots,
                                               BedrockMode mode, int index) {
        Player player = resolveActiveFormPlayer(playerId, generation);
        if (player == null || index < 0 || index >= view.items().size()) {
            return;
        }
        String id = view.items().get(index).id();
        if (BACK_BUTTON.equals(id)) {
            openBedrockCategories(player);
            return;
        }
        if (QUANTITY_BUTTON.equals(id) || MODE_BUTTON.equals(id)) {
            if (QUANTITY_BUTTON.equals(id)) {
                bedrockLots.put(playerId, ShopTideframeMenu.next(lots));
            } else {
                bedrockModes.put(playerId, mode.next());
            }
            reopenBedrockCategory(player, categoryId);
            return;
        }
        String key = favoriteKey(categoryId, id);
        ShopFavorites.resolve(catalog, key).ifPresent(entry -> {
            if (mode == BedrockMode.FAVORITE) {
                boolean now = favorites.toggle(player, key.substring(0, key.indexOf('/')), key.substring(key.indexOf('/') + 1));
                player.sendMessage(now ? "§e★ Agregado a tus favoritos." : "§7Quitado de tus favoritos.");
            } else {
                ShopService activeService = service;
                if (activeService == null) {
                    return;
                }
                ShopTransactionResult result = mode == BedrockMode.SELL
                    ? activeService.sell(player, entry, lots) : activeService.buy(player, entry, lots);
                player.sendMessage((result.success() ? "§a" : "§c") + result.message());
            }
            // A Bedrock form closes on every tap: show the category again (new balance and stock), as the Java
            // screen stays open, so buying or selling more needs no trip back to the merchant.
            reopenBedrockCategory(player, categoryId);
        });
    }

    /** The category form again; the categories when it no longer exists (favourites all removed). */
    private void reopenBedrockCategory(Player player, String categoryId) {
        if (!player.isOnline()) {
            return;
        }
        bedrockCategory(player, categoryId).ifPresentOrElse(category -> openBedrockCategory(player, category),
            () -> openBedrockCategories(player));
    }

    /** The player's last purchases and sales, from every menu (kept with the player across restarts). */
    public ShopHistory history() {
        return history;
    }

    private void announceHappyHour() {
        dev.linqfy.bigCasares.platform.HappyHour current = happyHour;
        boolean active = current.active(java.time.ZonedDateTime.now());
        if (active == happyHourWasActive) {
            return;
        }
        happyHourWasActive = active;
        String message = active
            ? "§6§l¡Hora feliz! §eLas misiones pagan x" + trimmed(current.missionMultiplier()) + " y la tienda tiene "
                + Math.round(current.shopDiscount() * 100) + "% de descuento."
            : "§7Terminó la hora feliz.";
        plugin.getServer().getOnlinePlayers().forEach(player -> player.sendMessage(message));
    }

    private static String trimmed(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private void restoreHistory(Player player) {
        playerStrings.get(player, ShopHistory.KEY).ifPresent(encoded -> history.restore(player.getUniqueId(), encoded));
    }

    private Player resolveActiveFormPlayer(UUID playerId, long generation) {
        Player player = plugin.getServer().getPlayer(playerId);
        boolean online = player != null && player.isOnline();
        return enabled && formGeneration.accepts(generation, online) ? player : null;
    }

    private void spawnConfiguredNpcs(YamlConfiguration configuration) {
        // Purge existing NPCs in worlds to avoid duplicates
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (npcFactory.isShopNpc(entity)) {
                    entity.remove();
                }
            }
        }

        ConfigurationSection shops = configuration.getConfigurationSection("shops");
        if (shops == null) {
            return;
        }
        for (String shopId : shops.getKeys(false)) {
            ConfigurationSection npc = shops.getConfigurationSection(shopId + ".npc");
            if (npc == null || !npc.getBoolean("enabled", false)) {
                continue;
            }
            ConfigurationSection location = npc.getConfigurationSection("location");
            if (location == null) {
                plugin.getLogger().warning("Shop NPC " + shopId + " has no location.");
                continue;
            }
            org.bukkit.World world = Bukkit.getWorld(location.getString("world", "world"));
            if (world == null) {
                plugin.getLogger().warning("Shop NPC world is not loaded for " + shopId);
                continue;
            }
            ShopNpcDefinition definition = loadNpcDefinition(shopId, npc);
            Location spawn = new Location(
                world,
                location.getDouble("x"), location.getDouble("y"), location.getDouble("z"),
                (float) location.getDouble("yaw"), (float) location.getDouble("pitch")
            );
            try {
                java.util.List<org.bukkit.entity.Entity> spawnedEntities = npcFactory.spawn(spawn, definition);
                for (org.bukkit.entity.Entity e : spawnedEntities) {
                    npcs.add(e);
                    registrations.ownCleanup(nextResourceId("shop-npc"), () -> removeNpc(e));
                }
            } catch (IllegalArgumentException | IllegalStateException ex) {
                plugin.getLogger().warning("No se pudo crear el NPC de shop " + shopId + ": " + ex.getMessage());
            }
        }
    }

    private ShopNpcDefinition loadNpcDefinition(String shopId, ConfigurationSection npc) {
        ShopNpcType type = ShopNpcType.valueOf(npc.getString("type", "VILLAGER").toUpperCase(Locale.ROOT));
        ConfigurationSection skin = npc.getConfigurationSection("skin");
        ShopSkinSource skinSource = type == ShopNpcType.PLAYER_MODEL
            ? ShopSkinSource.valueOf(skin == null ? "PLAYER_NAME" : skin.getString("source", "PLAYER_NAME").toUpperCase(Locale.ROOT))
            : null;
        String skinValue = skin == null ? null : skin.getString("value", skin.getString("player-name"));
        Map<String, String> equipment = new LinkedHashMap<>();
        ConfigurationSection equipmentSection = npc.getConfigurationSection("equipment");
        if (equipmentSection != null) {
            equipmentSection.getKeys(false).forEach(key -> equipment.put(key, equipmentSection.getString(key, "AIR")));
        }
        return new ShopNpcDefinition(
            shopId,
            type,
            npc.getString("display-name", "§6§lMercader"),
            skinSource,
            skinValue,
            Pose.valueOf(npc.getString("pose", "STANDING").toUpperCase(Locale.ROOT)),
            npc.getBoolean("look-at-player", true),
            npc.getBoolean("immovable", true),
            equipment,
            npc.getString("profession", "none"),
            npc.getString("biome-type", "plains"),
            npc.getInt("level", 1),
            npc.getBoolean("baby", false)
        );
    }

    private void despawnNpcs() {
        npcs.forEach(this::removeNpc);
        npcs.clear();
    }

    private void removeNpc(org.bukkit.entity.Entity entity) {
        if (entity != null) {
            ClientEntityPresentationRegistry.unregister(entity.getUniqueId());
        }
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
    }

    private String nextResourceId(String prefix) {
        return prefix + "-" + ++runtimeResourceSequence;
    }

    private void clearRuntimeState() {
        enabled = false;
        formGeneration.invalidate();
        bedrockLots.clear();
        bedrockModes.clear();
        npcs.clear();
        guiController = null;
        npcListener = null;
        npcFactory = null;
        service = null;
        economyGateway = null;
        registrations = null;
    }
}
