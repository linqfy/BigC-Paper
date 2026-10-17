package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.blocks.CustomBlockDefinition;
import dev.linqfy.bigCasares.blocks.CustomBlockRegistry;
import dev.linqfy.bigCasares.content.ContentItem;
import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.DimensionPlatform;
import dev.linqfy.bigCasares.dimension.DimensionStatus;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.model.BetterModelAssetInstaller;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.sift.feature.SiftFeatures;
import dev.linqfy.bigCasares.modules.sift.recipe.SiftRecipeRegistrar;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * {@code the-sift}: The Sift dimension, ported from Mielon's The Sift (MIT) and
 * built on the dimension framework. It contributes content (custom blocks and
 * items) and registers the dimension; a failure here is logged and leaves the
 * rest of the plugin running.
 */
public final class SiftModule implements PluginModule {

    public static final String MODULE_ID = "the-sift";

    private final JavaPlugin plugin;
    private final Supplier<Optional<DimensionPlatform>> platform;
    private final CustomBlockRegistry blocks;
    private final ContentItemFactory items;
    private final JavaModelGateway models;
    private RuntimeRegistrationScope compatibilityScope;
    private SiftSettings settings;

    public SiftModule(
        JavaPlugin plugin, Supplier<Optional<DimensionPlatform>> platform, CustomBlockRegistry blocks,
        ContentItemFactory items, JavaModelGateway models
    ) {
        this.plugin = plugin;
        this.platform = platform;
        this.blocks = blocks;
        this.items = items;
        this.models = models;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        settings = SiftSettings.load(plugin.getConfig());
        settings.warnings().forEach(warning -> plugin.getLogger().warning("[TheSift] " + warning));
        registerContent();
        registerRecipes(scope);
        Optional<DimensionPlatform> dimensions = platform.get();
        if (dimensions.isEmpty()) {
            plugin.getLogger().warning("[TheSift] dimension-framework is disabled; the Sift dimension is not registered.");
            return;
        }
        SiftData data = SiftData.fromClasspath();
        SiftWorldgen worldgen = new SiftWorldgen(data, plugin.getLogger());
        SiftCreatures creatures = new SiftCreatures(plugin, models);
        installModels();
        // BetterModel may still be initializing right after plugin enable; the
        // delayed pass repairs the model registry like the other model modules do.
        BukkitTask delayedModels = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (installModels()) {
                creatures.redress();
            }
        }, 60L);
        scope.register("sift-models-delayed-install", delayedModels::cancel);
        SiftRuntime runtime = new SiftRuntime(plugin, settings, worldgen::customPlacements, SiftFeatures.create(data), creatures,
            new SiftRifts(plugin, platform, models),
            items::resolve);
        try {
            DimensionDefinition definition = SiftDimension.create(settings, worldgen, List.of(runtime));
            DimensionStatus status = dimensions.get().register(definition);
            plugin.getLogger().info("[TheSift] Registered " + SiftDimension.KEY + ": " + status.state()
                + (status.message().isBlank() ? "" : " (" + status.message() + ")"));
            scope.register("sift-dimension", () -> dimensions.get().unregister(SiftDimension.KEY));
            BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
            registrations.registerListener("sift-advancements", new SiftAdvancementTriggers(platform, blocks));
            registrations.registerListener("sift-ichor-items", new SiftIchorItems(platform, items));
            if (settings.ancientCityRitual()) {
                new SiftRitual(plugin, registrations, platform, models).start();
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "[TheSift] Could not register the Sift dimension", failure);
        }
    }

    /** Registers the mod's recipes that the registered content makes possible; the scope removes them on disable. */
    private void registerRecipes(RuntimeRegistrationScope scope) {
        try {
            new SiftRecipeRegistrar(plugin, items, new BukkitRuntimeRegistrations(plugin, scope)).register(SiftData.fromClasspath());
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[TheSift] Could not register the Sift recipes", failure);
        }
    }

    private boolean installModels() {
        try {
            BetterModelAssetInstaller.installSiftModels(plugin);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(Level.WARNING, "[TheSift] Could not install creature models", failure);
            return false;
        }
    }

    private void registerContent() {
        for (CustomBlockDefinition block : SiftBlocks.definitions()) {
            if (blocks.byId(block.id()).isEmpty()) {
                blocks.register(block);
            }
        }
        for (ContentItem item : SiftItems.items()) {
            if (items.registry().find(item.id()).isEmpty()) {
                items.registry().register(item);
            }
        }
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    public Optional<SiftSettings> settings() {
        return Optional.ofNullable(settings);
    }
}
