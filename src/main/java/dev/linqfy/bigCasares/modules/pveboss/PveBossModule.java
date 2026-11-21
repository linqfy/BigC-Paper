package dev.linqfy.bigCasares.modules.pveboss;

import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.model.BetterModelAssetInstaller;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

public final class PveBossModule implements PluginModule {

    public static final String MODULE_ID = "pve-boss-system";

    private final PveBossService service = new PveBossService();
    private final BigCasares plugin;
    private final ClientPlatformGateway platformGateway;
    private final Function<UUID, Boolean> resourcePackLoaded;
    private final JavaModelGateway javaModels;
    private final Map<String, PaperAbyssGuardianRuntime> runtimes = new LinkedHashMap<>();
    private final Map<String, PaperAbyssGuardianRuntime> externalRuntimes = new LinkedHashMap<>();
    private final Map<String, AbyssGuardianDefinition> externalDefinitions = new LinkedHashMap<>();
    private List<AbyssGuardianDefinition> builtInDefinitions = List.of();
    private RuntimeRegistrationScope activeScope;
    private BossDefinitionCatalog definitions = new BossDefinitionCatalog(List.of());
    private SahurRewardService rewardService;
    private SahurRewardListener rewardListener;
    private boolean enabled;
    private RuntimeRegistrationScope compatibilityScope;

    public PveBossModule() {
        this(null, ignored -> ClientPlatform.JAVA, ignored -> false);
    }

    public PveBossModule(
        BigCasares plugin,
        ClientPlatformGateway platformGateway,
        Function<UUID, Boolean> resourcePackLoaded
    ) {
        this(plugin, platformGateway, resourcePackLoaded, unavailableModels());
    }

    public PveBossModule(
        BigCasares plugin,
        ClientPlatformGateway platformGateway,
        Function<UUID, Boolean> resourcePackLoaded,
        JavaModelGateway javaModels
    ) {
        this.plugin = plugin;
        this.platformGateway = platformGateway;
        this.resourcePackLoaded = resourcePackLoaded;
        this.javaModels = java.util.Objects.requireNonNull(javaModels, "javaModels");
    }

    private static JavaModelGateway unavailableModels() {
        return new JavaModelGateway() {
            @Override
            public JavaModelHandle attach(org.bukkit.entity.Entity anchor, String modelKey) {
                throw new IllegalStateException("Java model gateway is required for live boss rendering");
            }

            @Override
            public boolean animate(JavaModelHandle handle, String animationKey) {
                throw new IllegalStateException("Java model gateway is required for live boss rendering");
            }

            @Override
            public boolean animateOnce(JavaModelHandle handle, String animationKey, Runnable onEnd) {
                throw new IllegalStateException("Java model gateway is required for live boss rendering");
            }

            @Override
            public boolean scale(JavaModelHandle handle, float factor) {
                throw new IllegalStateException("Java model gateway is required for live boss rendering");
            }

            @Override
            public void close(JavaModelHandle handle) {
                throw new IllegalStateException("Java model gateway is required for live boss rendering");
            }
        };
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    public static List<String> supportedBossIds() {
        return List.of("abyss-guardian", "tung-tung-sahur");
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        this.compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        enabled = true;
        activeScope = scope;
        if (plugin == null) {
            return;
        }
        scope.register("module-state", this::clearRuntimeState);
        BetterModelAssetInstaller.installSahurModels(plugin);
        org.bukkit.scheduler.BukkitTask delayedModelReload = plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> BetterModelAssetInstaller.installSahurModels(plugin), 60L);
        scope.register("sahur-delayed-model-reload", delayedModelReload::cancel);
        rewardService = new SahurRewardService(
            new YamlBossRewardStore(Path.of(plugin.getDataFolder().getPath(), "boss-rewards.yml")),
            new PaperSahurRewardDelivery(plugin.getCustomItemRegistry()));
        rewardListener = new SahurRewardListener(rewardService);
        plugin.getServer().getPluginManager().registerEvents(rewardListener, plugin);
        scope.register("sahur-reward-listener", () -> HandlerList.unregisterAll(rewardListener));
        rewardService.deliverPendingForOnline(plugin.getServer().getOnlinePlayers().stream()
            .map(org.bukkit.entity.Player::getUniqueId).toList());
        AbyssGuardianDefinitionLoader loader = new AbyssGuardianDefinitionLoader();
        List<AbyssGuardianDefinition> loaded = supportedBossIds().stream().map(id -> {
            String resourcePath = "bosses/" + id + ".yml";
            plugin.saveResource(resourcePath, false);
            File file = new File(plugin.getDataFolder(), resourcePath);
            return loader.load(YamlConfiguration.loadConfiguration(file));
        }).toList();
        builtInDefinitions = loaded;
        definitions = new BossDefinitionCatalog(loaded);

        for (AbyssGuardianDefinition definition : loaded) {
            PaperAbyssGuardianRuntime runtime = new PaperAbyssGuardianRuntime(
                plugin, definition, service, platformGateway, resourcePackLoaded, javaModels, standings -> {
                    if (definition.id().equals("tung-tung-sahur")) {
                        standings.stream().filter(standing -> standing.placement() <= 3).forEach(standing -> {
                            BossReward reward = BossReward.forSahurPlacement(standing.placement());
                            rewardService.award(standing.playerId(), reward);
                            org.bukkit.entity.Player winner = plugin.getServer().getPlayer(standing.playerId());
                            if (winner != null) {
                                winner.sendMessage("§6Tung Tung Tung Sahur §7- Puesto #" + standing.placement()
                                    + " con §c" + String.format(java.util.Locale.ROOT, "%.1f", standing.damage())
                                    + " de daño§7. Premio: §e" + reward.amount() + "x " + reward.itemId() + "§7.");
                            }
                        });
                    }
                    awardDangerPlacements(standings);
                });
            runtime.start();
            runtimes.put(definition.id(), runtime);
            scope.register("paper-boss-runtime-" + definition.id(), runtime::stop);
        }
        plugin.getLogger().info("PvE Boss System listo: Guardián del Abismo y Tung Tung Tung Sahur cargados.");
    }

    private void awardDangerPlacements(List<BossDamageStanding> standings) {
        if (plugin.getDangerModule() == null) return;
        plugin.getDangerModule().service().ifPresent(danger -> standings.forEach(standing -> {
            danger.awardBossPlacement(standing.playerId(), standing.placement());
            plugin.getDangerModule().refreshPresentation(standing.playerId());
        }));
    }

    /**
     * Adds a scripted boss owned by another module. Its definition is {@code bosses/<bossId>.yml}, loaded like
     * the built-in ones; the runtime lives until {@link #unregisterExternalBoss}, module disable or reload.
     * Placements still feed the danger module; {@code onDefeat} receives the top three after a defeat.
     *
     * @throws IllegalStateException    when the module is disabled or has no live plugin (tests)
     * @throws IllegalArgumentException for a built-in id, an id already registered or a mismatching definition
     */
    public void registerExternalBoss(
        String bossId,
        BossEncounterControllerFactory factory,
        Consumer<List<BossDamageStanding>> onDefeat
    ) {
        String id = normalizeBossId(bossId);
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(onDefeat, "onDefeat");
        if (!enabled) {
            throw new IllegalStateException("PvE Boss System no está disponible.");
        }
        if (supportedBossIds().contains(id)) {
            throw new IllegalArgumentException("El boss " + id + " es interno y no se puede registrar.");
        }
        if (externalRuntimes.containsKey(id)) {
            throw new IllegalArgumentException("El boss " + id + " ya está registrado.");
        }
        if (plugin == null || activeScope == null) {
            throw new IllegalStateException("Registrar un boss externo requiere el plugin en ejecución.");
        }
        String resourcePath = "bosses/" + id + ".yml";
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
        AbyssGuardianDefinition definition = new AbyssGuardianDefinitionLoader()
            .load(YamlConfiguration.loadConfiguration(file));
        if (!normalizeBossId(definition.id()).equals(id)) {
            throw new IllegalArgumentException(
                resourcePath + " declara el id " + definition.id() + " en lugar de " + id);
        }
        List<AbyssGuardianDefinition> catalog = new java.util.ArrayList<>(builtInDefinitions);
        catalog.addAll(externalDefinitions.values());
        catalog.add(definition);
        BossDefinitionCatalog updated = new BossDefinitionCatalog(catalog);
        PaperAbyssGuardianRuntime runtime = new PaperAbyssGuardianRuntime(
            plugin, definition, service, platformGateway, resourcePackLoaded, javaModels, standings -> {
                try {
                    onDefeat.accept(standings);
                } catch (RuntimeException failure) {
                    plugin.getLogger().log(Level.WARNING, "Fallo al premiar el boss " + id, failure);
                }
                awardDangerPlacements(standings);
            }, factory);
        runtime.start();
        try {
            activeScope.register(externalScopeId(id), runtime::stop);
        } catch (RuntimeException failure) {
            runtime.stop();
            throw failure;
        }
        externalRuntimes.put(id, runtime);
        externalDefinitions.put(id, definition);
        definitions = updated;
    }

    /** Stops and forgets an external boss and all its live instances; unknown ids are ignored. */
    public void unregisterExternalBoss(String bossId) {
        String id = normalizeBossId(bossId);
        PaperAbyssGuardianRuntime runtime = externalRuntimes.remove(id);
        if (externalDefinitions.remove(id) != null) {
            List<AbyssGuardianDefinition> catalog = new java.util.ArrayList<>(builtInDefinitions);
            catalog.addAll(externalDefinitions.values());
            definitions = new BossDefinitionCatalog(catalog);
        }
        if (runtime == null) {
            return;
        }
        if (activeScope != null) {
            activeScope.forget(externalScopeId(id));
        }
        runtime.stop();
    }

    public List<String> externalBossIds() {
        return List.copyOf(externalRuntimes.keySet());
    }

    public boolean isActive(UUID bossInstanceId) {
        Objects.requireNonNull(bossInstanceId, "bossInstanceId");
        return allRuntimes().stream().anyMatch(runtime -> runtime.isActive(bossInstanceId));
    }

    /** Admin removal of one live boss instance: no ranking, no rewards. */
    public boolean removeBoss(UUID bossInstanceId) {
        Objects.requireNonNull(bossInstanceId, "bossInstanceId");
        for (PaperAbyssGuardianRuntime runtime : allRuntimes()) {
            if (runtime.removeBoss(bossInstanceId)) {
                return true;
            }
        }
        return false;
    }

    private List<PaperAbyssGuardianRuntime> allRuntimes() {
        List<PaperAbyssGuardianRuntime> all = new java.util.ArrayList<>(runtimes.values());
        all.addAll(externalRuntimes.values());
        return all;
    }

    private static String externalScopeId(String bossId) {
        return "paper-boss-runtime-external-" + bossId;
    }

    private static String normalizeBossId(String bossId) {
        String id = Objects.requireNonNull(bossId, "bossId").strip().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            throw new IllegalArgumentException("boss id must not be blank");
        }
        return id;
    }

    @Override
    public void onDisable() {
        if (plugin != null) {
            runtimes.values().forEach(PaperAbyssGuardianRuntime::stop);
            externalRuntimes.values().forEach(PaperAbyssGuardianRuntime::stop);
        }
        runtimes.clear();
        externalRuntimes.clear();
        externalDefinitions.clear();
        activeScope = null;
        service.shutdown(Instant.now());
        enabled = false;
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PveBossService service() {
        return service;
    }

    public java.util.Optional<SahurRewardService> rewardService() {
        return java.util.Optional.ofNullable(rewardService);
    }

    public UUID spawnAbyssGuardian(Location location) {
        return spawn("abyss-guardian", location);
    }

    public UUID spawn(String bossId, Location location) {
        if (!enabled) {
            throw new IllegalStateException("PvE Boss System no está disponible.");
        }
        AbyssGuardianDefinition definition = definitions.require(bossId);
        PaperAbyssGuardianRuntime runtime = runtimes.get(definition.id());
        if (runtime == null) {
            runtime = externalRuntimes.get(normalizeBossId(definition.id()));
        }
        if (runtime == null) {
            throw new IllegalStateException("El boss " + definition.displayName() + " no está disponible.");
        }
        return runtime.spawn(location);
    }

    public int activeBossCount() {
        return allRuntimes().stream().mapToInt(PaperAbyssGuardianRuntime::activeCount).sum();
    }

    private void clearRuntimeState() {
        runtimes.clear();
        externalRuntimes.clear();
        externalDefinitions.clear();
        builtInDefinitions = List.of();
        activeScope = null;
        definitions = new BossDefinitionCatalog(List.of());
        rewardListener = null;
        rewardService = null;
        enabled = false;
    }
}
