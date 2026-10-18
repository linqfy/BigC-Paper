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
import dev.linqfy.bigCasares.modules.sift.feature.SoulCanyonLandmarks;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates;
import dev.linqfy.bigCasares.modules.sift.recipe.SiftRecipeRegistrar;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        installSiftiteRecovery(scope);
        new BukkitRuntimeRegistrations(plugin, scope).registerListener("sift-charoite", new SiftCharoite());
        Optional<DimensionPlatform> dimensions = platform.get();
        if (dimensions.isEmpty()) {
            plugin.getLogger().warning("[TheSift] dimension-framework is disabled; the Sift dimension is not registered.");
            return;
        }
        SiftData data = SiftData.fromClasspath();
        SiftWorldgen worldgen = new SiftWorldgen(data, plugin.getLogger());
        SiftCreatures creatures = new SiftCreatures(plugin, models);
        SiftSifters sifters = new SiftSifters(plugin, models, items::resolve,
            SifterLoot.parse(data.json("loot_table/entities/sifter.json")));
        Path landmarksFile = plugin.getDataFolder().toPath().resolve("data/the-sift/landmarks.bin");
        SiftLandmarks landmarks = loadLandmarks(landmarksFile);
        SiftSingers singers = new SiftSingers(plugin, models, items::resolve, landmarks);
        SiftEchoGolems golems = new SiftEchoGolems(plugin, models, items::resolve, landmarks);
        if (settings.echoGolems()) {
            singers.setGolems(golems);
        }
        if (settings.singers()) {
            golems.setSingers(singers);
        }
        SiftDarkSniffers darkSniffers = new SiftDarkSniffers(plugin, models, items::resolve, data, creatures);
        installModels();
        // BetterModel may still be initializing right after plugin enable; the
        // delayed pass repairs the model registry like the other model modules do.
        BukkitTask delayedModels = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (installModels()) {
                creatures.redress();
                sifters.redress();
                singers.redress();
                golems.redress();
                darkSniffers.redress();
            }
        }, 60L);
        scope.register("sift-models-delayed-install", delayedModels::cancel);
        SiftFeatures features = SiftFeatures.create(data, canyonLandmarks(landmarks));
        SiftSoils soils = new SiftSoils(plugin, platform, blocks);
        SiftRifts rifts = new SiftRifts(plugin, platform, models);
        SiftMainPortal mainPortal = settings.mainPortal()
            ? new SiftMainPortal(plugin, platform, new SiftTemplates(data), rifts) : null;
        if (mainPortal != null) {
            rifts.mainPortal(mainPortal::arrival);
        }
        SiftRuntime runtime = new SiftRuntime(plugin, settings, worldgen::customPlacements, features, creatures,
            rifts, sifters, singers, golems, darkSniffers, mainPortal,
            items::resolve, new SiftIchorSnowfall(soils::update));
        try {
            DimensionDefinition definition = SiftDimension.create(settings, worldgen, List.of(runtime));
            DimensionStatus status = dimensions.get().register(definition);
            plugin.getLogger().info("[TheSift] Registered " + SiftDimension.KEY + ": " + status.state()
                + (status.message().isBlank() ? "" : " (" + status.message() + ")"));
            scope.register("sift-dimension", () -> dimensions.get().unregister(SiftDimension.KEY));
            BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
            registrations.scheduleRepeating("sift-landmarks-save", () -> saveLandmarks(landmarks, landmarksFile), 600L, 600L);
            scope.register("sift-landmarks-flush", () -> saveLandmarks(landmarks, landmarksFile));
            registrations.registerListener("sift-advancements", new SiftAdvancementTriggers(platform, blocks));
            registrations.registerListener("sift-ichor-items", new SiftIchorItems(platform, items));
            registrations.registerListener("sift-ichor-cauldrons", new SiftIchorCauldrons(platform, items));
            registrations.registerListener("sift-soils", soils);
            registrations.registerListener("sift-willow-saplings", new SiftWillowSaplings(plugin, platform, items, features));
            if (settings.sculkflowers()) {
                new SiftSculkflowers(plugin, items::resolve, blocks, data).activate(registrations);
            }
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

    /** The mod's Siftite death recovery; independent of the dimension, so it works even without the framework. */
    private void installSiftiteRecovery(RuntimeRegistrationScope scope) {
        try {
            SiftiteRecovery.install(plugin, new BukkitRuntimeRegistrations(plugin, scope),
                plugin.getDataFolder().toPath().resolve("data/the-sift/siftite-recovery"));
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "[TheSift] Could not enable the Siftite death recovery", failure);
        }
    }

    /** The Soul Canyon landmarks saved by an earlier run; a file that cannot be read is kept aside and the registry starts empty. */
    private SiftLandmarks loadLandmarks(Path file) {
        try {
            return SiftLandmarks.load(file);
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[TheSift] Could not read " + file + "; Echo Golems start without known canyons", failure);
            try {
                Files.move(file, file.resolveSibling(file.getFileName() + ".unreadable"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // The next save replaces it.
            }
            return new SiftLandmarks();
        }
    }

    private void saveLandmarks(SiftLandmarks landmarks, Path file) {
        try {
            landmarks.saveIfDirty(file);
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[TheSift] Could not save " + file, failure);
        }
    }

    /** Hands the landmarks of every canyon the generator carves to the registry the Echo Golems read. */
    private static SoulCanyonLandmarks canyonLandmarks(SiftLandmarks landmarks) {
        return (entrance, midpoint, cluster, soulBlocks) -> {
            landmarks.record(SiftLandmarks.Kind.CANYON_ENTRANCE, pos(entrance));
            landmarks.record(SiftLandmarks.Kind.CANYON_MIDPOINT, pos(midpoint));
            landmarks.record(SiftLandmarks.Kind.CANYON_CLUSTER, pos(cluster));
            landmarks.record(SiftLandmarks.Kind.GOLEM_PENDING, pos(entrance));
            soulBlocks.forEach(soul -> landmarks.record(SiftLandmarks.Kind.SOUL_BLOCK, pos(soul)));
        };
    }

    private static SiftPos pos(SoulCanyonLandmarks.Position position) {
        return new SiftPos(position.x(), position.y(), position.z());
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
