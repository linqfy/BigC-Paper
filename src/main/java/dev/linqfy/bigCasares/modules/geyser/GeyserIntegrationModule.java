package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.celular.CelularModule;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class GeyserIntegrationModule implements PluginModule {
    private static final String RUNTIME_BRIDGE = "dev.linqfy.bigCasares.modules.geyser.GeyserRuntimeBridge";

    private final BigCasares plugin;
    private Optional<GeyserApiFacade> facade;
    private GeyserPlatformGateway platformGateway;
    private RuntimeRegistrationScope compatibilityScope;

    public GeyserIntegrationModule(BigCasares plugin) {
        this(plugin, null);
    }

    GeyserIntegrationModule(BigCasares plugin, GeyserApiFacade initialFacade) {
        this.plugin = plugin;
        this.facade = Optional.ofNullable(initialFacade);
        this.platformGateway = new GeyserPlatformGateway(facade);
    }

    @Override
    public String getId() {
        return "geyser-integration";
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        this.compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        closeFacade();
        if (plugin == null) {
            return;
        }
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        GeyserSettings settings = GeyserSettings.load(plugin.getConfig());
        if (!settings.enabled()) {
            return;
        }
        if (plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) {
            plugin.getLogger().info("Geyser no está instalado; BigCasares continúa en modo Java.");
            return;
        }

        try {
            Class<?> type = Class.forName(RUNTIME_BRIDGE, true, plugin.getClass().getClassLoader());
            Consumer<Runnable> mainThreadExecutor = task -> {
                if (plugin.isEnabled()) {
                    registrations.scheduleImmediate("geyser-main-thread-callback", task);
                }
            };
            GeyserApiFacade runtime = (GeyserApiFacade) type
                .getConstructor(GeyserSettings.class, Consumer.class)
                .newInstance(settings, mainThreadExecutor);
            this.facade = Optional.of(runtime);
            this.platformGateway = new GeyserPlatformGateway(facade);
            scope.register("geyser-runtime", this::closeFacade);
            extractBedrockPack().ifPresent(pack -> {
                if (!runtime.registerResourcePack(pack)) {
                    plugin.getLogger().warning("El pack Bedrock no pudo registrarse; se mantienen los fallbacks seguros.");
                }
            });
            if (settings.customItems()) {
                customItemDefinitions().forEach(runtime::registerCustomItem);
                celularItemDefinitions().forEach(runtime::registerCustomItem);
            }
            if (settings.customEntities()) {
                runtime.registerCustomEntity(new GeyserCustomEntityDefinition(
                    "GHAST", "bigcasares:nexus", "nexus_id"));
                runtime.registerCustomEntity(new GeyserCustomEntityDefinition(
                    "WARDEN", "bigcasares:abyss_guardian", "pve_boss_id"));
                runtime.registerCustomEntity(new GeyserCustomEntityDefinition(
                    "WARDEN", "bigcasares:sahur", "sahur_boss_id"));
                runtime.registerCustomEntity(new GeyserCustomEntityDefinition(
                    "BAT", "bigcasares:sahur_bat", "sahur_bat_id"));
                runtime.registerCustomEntity(new GeyserCustomEntityDefinition(
                    "MANNEQUIN", "bigcasares:merchant", "shop_npc_id"));
                plugin.getLogger().warning("La API de entidades custom de Geyser 2.11 es experimental; los fallbacks siguen activos.");
            }
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                 | IllegalAccessException | InvocationTargetException | LinkageError ex) {
            plugin.getLogger().log(Level.WARNING,
                "La integración de Geyser no coincide con la API instalada; BigCasares usará fallbacks Java.", ex);
            closeFacade();
        }
    }

    @Override
    public void onDisable() {
        closeFacade();
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    public GeyserPlatformGateway platformGateway() {
        return platformGateway;
    }

    public Optional<GeyserShopFormGateway> shopForms() {
        return facade.filter(api -> api.capabilities().forms()).map(GeyserShopFormGateway::new);
    }

    public Optional<BedrockEntityGateway> customEntities() {
        return facade.filter(api -> api.capabilities().customEntities())
            .map(GeyserCustomEntityGateway::new);
    }

    private void closeFacade() {
        Optional<GeyserApiFacade> closing = facade;
        facade = Optional.empty();
        platformGateway = new GeyserPlatformGateway(Optional.empty());
        closing.ifPresent(runtime -> {
            try {
                runtime.close();
            } catch (RuntimeException ex) {
                if (plugin != null) {
                    plugin.getLogger().log(Level.WARNING, "No se pudo cerrar la integración de Geyser.", ex);
                }
            }
        });
    }

    private static java.util.List<GeyserCustomItemDefinition> customItemDefinitions() {
        return java.util.List.of(
            item("apple", "copper_apple", "Manzana de Cobre"),
            item("snowball", "smoke_bomb", "Bomba de Humo"),
            item("arrow", "prismarine_arrow", "Flecha de Prismarina"),
            item("arrow", "echo_arrow", "Flecha de Eco"),
            item("arrow", "golden_tipped_amethyst_arrow", "Flecha de Amatista con Punta de Oro"),
            item("crossbow", "crossbow_amethyst_shard", "Ballesta de Amatista"),
            item("crossbow", "crossbow_echo_shard", "Ballesta de Eco"),
            item("crossbow", "crossbow_ender_pearl", "Ballesta de Perla de Ender"),
            item("crossbow", "crossbow_firework_rocket", "Ballesta de Fuegos Artificiales"),
            item("iron_sword", "sahurs_bat", "Bate de Sahur")
        );
    }

    private java.util.List<GeyserCustomItemDefinition> celularItemDefinitions() {
        CelularModule celular = plugin.getCelularModule();
        if (celular == null) {
            return java.util.List.of();
        }
        return celular.itemModels().stream()
            .map(model -> new GeyserCustomItemDefinition(
                "minecraft:clock", model, model, CelularModule.BEDROCK_ICON, CelularModule.DISPLAY_NAME))
            .toList();
    }

    private static GeyserCustomItemDefinition item(String baseItem, String model, String displayName) {
        return new GeyserCustomItemDefinition(
            "minecraft:" + baseItem,
            "bigcasares:" + model,
            "bigcasares:" + model,
            "bigcasares." + model,
            displayName
        );
    }

    private Optional<Path> extractBedrockPack() {
        Path destination = plugin.getDataFolder().toPath().resolve("packs/bigcasares-bedrock.mcpack");
        try (InputStream input = plugin.getResource("generated-resourcepacks/bigcasares-bedrock.mcpack")) {
            if (input == null) {
                plugin.getLogger().warning("No se encontró el .mcpack generado dentro del plugin.");
                return Optional.empty();
            }
            Files.createDirectories(destination.getParent());
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(destination);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "No se pudo extraer el pack Bedrock", ex);
            return Optional.empty();
        }
    }
}
