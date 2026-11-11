package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.bounties.BountyEconomyGateway;
import dev.linqfy.bigCasares.modules.tideframe.MenuTitleStyle;
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
import java.util.function.Supplier;

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
    private final ShopFormGeneration formGeneration = new ShopFormGeneration();
    private boolean enabled;
    private BukkitRuntimeRegistrations registrations;
    private RuntimeRegistrationScope compatibilityScope;
    private long runtimeResourceSequence;

    private volatile Supplier<MenuTitleStyle> menuTitleStyle = () -> MenuTitleStyle.PLAIN;

    public ShopModule(BigCasares plugin) {
        this.plugin = plugin;
    }

    /** How the Java shop menu titles look (the tideframe-ui module draws a background); plain titles by default. */
    public void configureMenuTitleStyle(Supplier<MenuTitleStyle> style) {
        this.menuTitleStyle = style == null ? () -> MenuTitleStyle.PLAIN : style;
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
        this.service = new ShopService(
            new ShopItemResolver(plugin.getCustomItemRegistry()),
            economyGateway,
            plugin::enforceInventoryLimits
        );
        if (guiController != null) {
            guiController.closeAll();
            org.bukkit.event.HandlerList.unregisterAll(guiController);
        }
        this.guiController = new ShopGuiController(this, catalog, service, () -> menuTitleStyle.get());
        ShopGuiController ownedGuiController = guiController;
        registrations.registerListener(nextResourceId("shop-gui-listener"), ownedGuiController);
        registrations.ownCleanup(nextResourceId("shop-gui-windows"), ownedGuiController::closeAll);
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
        guiController.openCategories(player);
    }

    public void configurePlatform(ClientPlatformGateway platformGateway, BedrockFormSender bedrockFormSender) {
        this.platformGateway = platformGateway == null ? ignored -> ClientPlatform.JAVA : platformGateway;
        this.bedrockFormSender = bedrockFormSender;
    }

    private void openNpcShop(Player player, String shopId) {
        ShopCategory category = catalog.category(shopId).orElse(null);
        if (category == null) {
            openMainMenu(player);
            return;
        }
        if (platformGateway.resolvePlatform(player.getUniqueId()) == ClientPlatform.BEDROCK
            && bedrockFormSender != null) {
            openBedrockCategory(player, category);
        } else {
            guiController.openCategory(player, category);
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
            catalog.categories().stream()
                .map(category -> new ShopViewItem(category.id(), category.name(), "", "", null, true))
                .toList()
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
        ShopView view = new ShopView(
            category.id(),
            category.name(),
            "§7Tocá un objeto para comprarlo",
            economyGateway.format(economyGateway.balance(player)),
            category.entries().stream().map(entry -> new ShopViewItem(
                entry.id(),
                entry.displayName() == null ? entry.id() : entry.displayName(),
                String.join("\n", entry.lore()),
                economyGateway.format(entry.buyPrice()),
                null,
                economyGateway.has(player, entry.buyPrice())
            )).toList()
        );
        BukkitRuntimeRegistrations activeRegistrations = registrations;
        boolean sent = bedrockFormSender.send(playerId, view,
            index -> activeRegistrations.guard(
                () -> handleBedrockPurchaseResponse(playerId, generation, view, index)).run());
        if (!sent) {
            new ChatFallbackShopUi(plugin.getServer()).openShop(player.getUniqueId(), view);
        }
    }

    private void handleBedrockCategoryResponse(UUID playerId, long generation, ShopView view, int index) {
        Player player = resolveActiveFormPlayer(playerId, generation);
        if (player == null || index < 0 || index >= view.items().size()) {
            return;
        }
        String categoryId = view.items().get(index).id();
        catalog.category(categoryId).ifPresent(category -> openBedrockCategory(player, category));
    }

    private void handleBedrockPurchaseResponse(UUID playerId, long generation, ShopView view, int index) {
        Player player = resolveActiveFormPlayer(playerId, generation);
        if (player == null || index < 0 || index >= view.items().size()) {
            return;
        }
        ShopPurchaseRequest request = new BedrockShopFormMapper().purchaseForButton(playerId, view, index);
        catalog.entry(request.shopId(), request.entryId()).ifPresent(entry -> {
            ShopService activeService = service;
            if (activeService == null) {
                return;
            }
            ShopTransactionResult result = activeService.buy(player, entry);
            player.sendMessage((result.success() ? "§a" : "§c") + result.message());
        });
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
        npcs.clear();
        guiController = null;
        npcListener = null;
        npcFactory = null;
        service = null;
        economyGateway = null;
        registrations = null;
    }
}
