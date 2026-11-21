package dev.linqfy.bigCasares.modules.items;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.items.CustomItemRegistry;
import dev.linqfy.bigCasares.items.catalog.CustomItemCatalog;
import dev.linqfy.bigCasares.items.catalog.BukkitCatalogRecipeGateway;
import dev.linqfy.bigCasares.items.catalog.CatalogRecipeRuntime;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogLoader;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogReloadCoordinator;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogReloadOperation;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogReloadResult;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogSeeder;
import dev.linqfy.bigCasares.items.catalog.ItemCatalogValidator;
import dev.linqfy.bigCasares.items.catalog.OnlineItemReconciler;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Material;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class ItemCatalogModule implements PluginModule {

    public static final String MODULE_ID = "custom-item-catalog";

    private static final List<String> DEFAULT_FILES = List.of(
        "copper_apple.yml",
        "smoke_bomb.yml",
        "prismarine_arrow.yml",
        "nexus.yml",
        "echo_arrow.yml",
        "golden_tipped_amethyst_arrow.yml",
        "tracker_compass.yml",
        "nuke_shot.yml",
        "sahurs_bat.yml",
        "potassium_nitrate.yml",
        "nitric_acid.yml",
        "grappling_hook_1.yml",
        "grappling_hook_2.yml",
        "grappling_hook_3.yml",
        "grappling_hook_4.yml",
        "grappling_hook_5.yml",
        "grappling_hook_6.yml",
        "glider_tier_1.yml",
        "glider_tier_2.yml",
        "glider_tier_3.yml",
        "glider_tier_4.yml",
        "glider_tier_5.yml",
        "glider_tier_6.yml",
        "keep_inventory_totem.yml",
        "hate_fragment_greek.yml",
        "boss_summoner_greek.yml",
        "hate_fragment_norse.yml",
        "boss_summoner_norse.yml",
        "hate_fragment_medieval.yml",
        "boss_summoner_medieval.yml",
        "hate_fragment_egyptian.yml",
        "boss_summoner_egyptian.yml",
        "hate_fragment_mesopotamian.yml",
        "boss_summoner_mesopotamian.yml",
        "hate_fragment_japanese.yml",
        "boss_summoner_japanese.yml",
        "hate_fragment_mesoamerican.yml",
        "boss_summoner_mesoamerican.yml",
        "hate_fragment_arabian.yml",
        "boss_summoner_arabian.yml",
        "hate_fragment_andean.yml",
        "boss_summoner_andean.yml",
        "mandinga_guitar.yml"
    );

    private final BigCasares plugin;
    private final CustomItemRegistry registry;
    private final ItemCatalogReloadCoordinator reloadCoordinator = new ItemCatalogReloadCoordinator();
    private RuntimeRegistrationScope compatibilityScope;
    private CatalogRecipeRuntime recipes;
    private OnlineItemReconciler reconciler;

    public ItemCatalogModule(BigCasares plugin, CustomItemRegistry registry) {
        this.plugin = plugin;
        this.registry = Objects.requireNonNull(registry, "registry");
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
        if (plugin == null) {
            throw new IllegalStateException("ItemCatalogModule needs a plugin instance before it can be enabled");
        }
        scope.register("module-state", this::clearRuntimeState);
        CatalogRecipeRuntime ownedRecipes = new CatalogRecipeRuntime(
            new BukkitCatalogRecipeGateway(plugin, registry.stackFactory()));
        recipes = ownedRecipes;
        scope.register("catalog-recipes", ownedRecipes::clear);
        OnlineItemReconciler ownedReconciler = new OnlineItemReconciler(
            plugin, registry, plugin.getConfig().getInt("custom-item-catalog.reconciliation.stacks-per-tick", 36)
        );
        reconciler = ownedReconciler;
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.scheduleRepeating("online-item-reconciliation", ownedReconciler, 1L, 1L);
        scope.register("online-item-reconciliation-state", ownedReconciler::clear);
        seedDefaults();
        commit(loadCandidate());
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
            clearRuntimeState();
        }
    }

    public ItemCatalogReloadResult reloadCatalog() {
        return reloadCoordinator.execute(new ItemCatalogReloadOperation() {
            @Override
            public java.util.Optional<CustomItemCatalog> activeCatalog() {
                return registry.catalog();
            }

            @Override
            public CustomItemCatalog loadCandidate() {
                return ItemCatalogModule.this.loadCandidate();
            }

            @Override
            public dev.linqfy.bigCasares.items.catalog.ItemReconciliationResult commit(
                CustomItemCatalog candidate
            ) {
                return ItemCatalogModule.this.commit(candidate);
            }
        });
    }

    public boolean isCatalogReloadRunning() {
        return reloadCoordinator.isRunning();
    }

    private dev.linqfy.bigCasares.items.catalog.ItemReconciliationResult commit(CustomItemCatalog candidate) {
        CatalogRecipeRuntime activeRecipes = recipes;
        if (activeRecipes == null) {
            throw new IllegalStateException("item catalog recipes are not active");
        }
        CustomItemCatalog previous = registry.catalog().orElse(null);
        activeRecipes.replace(candidate);
        registry.installCatalog(candidate);
        OnlineItemReconciler activeReconciler = reconciler;
        if (activeReconciler != null) {
            activeReconciler.request(previous, candidate);
            return activeReconciler.result();
        }
        return new dev.linqfy.bigCasares.items.catalog.ItemReconciliationResult(0, 0, 0, 0);
    }

    private void seedDefaults() {
        new ItemCatalogSeeder(DEFAULT_FILES).seed(
            name -> plugin.getResource("content/items/" + name), itemDirectory());
    }

    private CustomItemCatalog loadCandidate() {
        CustomItemCatalog candidate = new ItemCatalogLoader().load(itemDirectory());
        new ItemCatalogValidator(ItemCatalogModule::isAvailableMaterial).validate(candidate, contentDirectory().resolve("pack"));
        return candidate;
    }

    private Path contentDirectory() {
        return plugin.getDataFolder().toPath().resolve("content");
    }

    private Path itemDirectory() {
        return contentDirectory().resolve("items");
    }

    private static boolean isAvailableMaterial(String materialName) {
        return Material.matchMaterial(materialName) != null;
    }

    private void clearRuntimeState() {
        recipes = null;
        reconciler = null;
    }
}
