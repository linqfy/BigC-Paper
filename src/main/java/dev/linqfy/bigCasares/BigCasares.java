package dev.linqfy.bigCasares;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import dev.linqfy.bigCasares.communication.EmojiAliasService;
import dev.linqfy.bigCasares.command.BigCasaresCommand;
import dev.linqfy.bigCasares.command.RequiredCommandBindings;
import dev.linqfy.bigCasares.module.ModuleManager;
import dev.linqfy.bigCasares.module.ModuleLifecycleReport;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeCleanupOutcome;
import dev.linqfy.bigCasares.module.runtime.RuntimeCleanupReport;
import dev.linqfy.bigCasares.module.runtime.RuntimeGeneration;
import dev.linqfy.bigCasares.modules.bounties.BountyModule;
import dev.linqfy.bigCasares.modules.copperapple.CopperAppleModule;
import dev.linqfy.bigCasares.modules.customcrossbow.CustomCrossbowModule;
import dev.linqfy.bigCasares.modules.inventorylimit.InventoryLimitModule;
import dev.linqfy.bigCasares.modules.items.ItemCatalogModule;
import dev.linqfy.bigCasares.modules.geyser.BedrockShopForm;
import dev.linqfy.bigCasares.modules.geyser.GeyserIntegrationModule;
import dev.linqfy.bigCasares.modules.danger.DangerModule;
import dev.linqfy.bigCasares.modules.tombstone.TombstoneModule;
import dev.linqfy.bigCasares.modules.specialitems.SpecialItemsModule;
import dev.linqfy.bigCasares.modules.warp.WarpModule;

import dev.linqfy.bigCasares.modules.missions.MissionModule;
import dev.linqfy.bigCasares.modules.airdrop.AirdropModule;
import dev.linqfy.bigCasares.modules.acidrain.AcidRainModule;
import dev.linqfy.bigCasares.modules.bloodmoon.BloodMoonModule;
import dev.linqfy.bigCasares.modules.jeremy.JeremyModule;
import dev.linqfy.bigCasares.modules.donpollos.DonPollosModule;
import dev.linqfy.bigCasares.modules.mobscaling.MobScalingModule;
import dev.linqfy.bigCasares.modules.grapplinghook.GrapplingHookModule;
import dev.linqfy.bigCasares.modules.glider.GliderModule;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelGatewayFactory;
import dev.linqfy.bigCasares.modules.nexus.NexusModule;
import dev.linqfy.bigCasares.modules.pveboss.PveBossModule;
import dev.linqfy.bigCasares.modules.resourcepack.ResourcePackModule;
import dev.linqfy.bigCasares.modules.shop.ShopModule;
import dev.linqfy.bigCasares.modules.skillrating.SkillRatingModule;
import dev.linqfy.bigCasares.modules.smokebomb.SmokeBombModule;
import dev.linqfy.bigCasares.modules.teams.TeamModule;
import dev.linqfy.bigCasares.modules.discord.DiscordIntegrationModule;
import dev.linqfy.bigCasares.modules.endevent.EndEventModule;
import dev.linqfy.bigCasares.modules.moderation.ModerationModule;
import dev.linqfy.bigCasares.modules.servercontrol.ServerControlModule;
import dev.linqfy.bigCasares.modules.servercontrol.VanishSessionRegistry;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.reload.ReloadCoordinator;
import dev.linqfy.bigCasares.reload.ReloadOperation;
import dev.linqfy.bigCasares.reload.ReloadResult;
import dev.linqfy.bigCasares.reload.ReloadStatus;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public final class BigCasares extends JavaPlugin {

    private static final List<String> ROOT_COMMAND_NAMES = List.of(
        "bigcasares", "shop", "rating", "team", "teammanage", "nexus", "boss"
    );

    private ModuleManager moduleManager;
    private CustomItemRegistry customItemRegistry;
    private MissionModule missionModule;
    private BountyModule bountyModule;
    private ShopModule shopModule;
    private InventoryLimitModule inventoryLimitModule;
    private SkillRatingModule skillRatingModule;
    private DangerModule dangerModule;
    private TombstoneModule tombstoneModule;
    private SpecialItemsModule specialItemsModule;
    private WarpModule warpModule;

    private ResourcePackModule resourcePackModule;
    private ItemCatalogModule itemCatalogModule;
    private TeamModule teamModule;
    private NexusModule nexusModule;
    private PveBossModule pveBossModule;
    private GeyserIntegrationModule geyserIntegrationModule;
    private AirdropModule airdropModule;
    private AcidRainModule acidRainModule;
    private BloodMoonModule bloodMoonModule;
    private JeremyModule jeremyModule;
    private DonPollosModule donPollosModule;
    private GrapplingHookModule grapplingHookModule;
    private GliderModule gliderModule;
    private MobScalingModule mobScalingModule;
    private ModerationModule moderationModule;
    private ServerControlModule serverControlModule;
    private DiscordIntegrationModule discordIntegrationModule;
    private EndEventModule endEventModule;
    private EmojiAliasService emojiAliasService;
    private final VanishSessionRegistry vanishSessionRegistry = new VanishSessionRegistry();
    private final ReloadCoordinator reloadCoordinator = new ReloadCoordinator();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            ModuleLifecycleReport activation = initializeRuntime(new RuntimeGeneration(0));
            if (activation.hasFailures()) {
                throw new IllegalStateException(
                    "BigCasares runtime activation failed",
                    activation.failures().getFirst().failure()
                );
            }
            registerCommands();
        } catch (RuntimeException | Error failure) {
            cleanupFailedStartup(failure);
            throw failure;
        }

        getLogger().info("BigCasares enabled. Active modules: "
            + moduleManager.getActiveModuleCount() + "/"
            + moduleManager.getRegisteredModuleCount());

    }

    @Override
    public void onDisable() {
        disableCurrentRuntime();
        logCleanupFailures(cleanupBukkitRuntime());
    }

    public CustomItemRegistry getCustomItemRegistry() {
        return customItemRegistry;
    }

    public MissionModule getMissionModule() {
        return missionModule;
    }

    public BountyModule getBountyModule() {
        return bountyModule;
    }

    public ShopModule getShopModule() {
        return shopModule;
    }

    public InventoryLimitModule getInventoryLimitModule() {
        return inventoryLimitModule;
    }

    public SkillRatingModule getSkillRatingModule() {
        return skillRatingModule;
    }


    
    public DangerModule getDangerModule() {
        return dangerModule;
    }

    public TombstoneModule getTombstoneModule() {
        return tombstoneModule;
    }
    
    public SpecialItemsModule getSpecialItemsModule() {
        return specialItemsModule;
    }

    public WarpModule getWarpModule() {
        return warpModule;
    }

    public ResourcePackModule getResourcePackModule() {
        return resourcePackModule;
    }

    public ItemCatalogModule getItemCatalogModule() {
        return itemCatalogModule;
    }

    public TeamModule getTeamModule() {
        return teamModule;
    }

    public NexusModule getNexusModule() {
        return nexusModule;
    }

    public PveBossModule getPveBossModule() {
        return pveBossModule;
    }

    public GeyserIntegrationModule getGeyserIntegrationModule() {
        return geyserIntegrationModule;
    }

    public AirdropModule getAirdropModule() {
        return airdropModule;
    }

    public AcidRainModule getAcidRainModule() {
        return acidRainModule;
    }

    public BloodMoonModule getBloodMoonModule() {
        return bloodMoonModule;
    }

    public JeremyModule getJeremyModule() {
        return jeremyModule;
    }

    public DonPollosModule getDonPollosModule() {
        return donPollosModule;
    }

    public MobScalingModule getMobScalingModule() {
        return mobScalingModule;
    }

    public ModerationModule getModerationModule() {
        return moderationModule;
    }

    public ServerControlModule getServerControlModule() {
        return serverControlModule;
    }

    public DiscordIntegrationModule getDiscordIntegrationModule() {
        return discordIntegrationModule;
    }

    public EndEventModule getEndEventModule() {
        return endEventModule;
    }

    public EmojiAliasService getEmojiAliasService() {
        return emojiAliasService;
    }

    public VanishSessionRegistry getVanishSessionRegistry() {
        return vanishSessionRegistry;
    }

    public boolean isReloadingPluginState() {
        return reloadCoordinator.isReloading();
    }

    public static List<String> frameworkV2ModuleOrder() {
        return List.of(
            "resource-pack-system",
            "team-system",
            "nexus-system",
            "entity-shop-system",
            "pve-boss-system",
            "geyser-integration"
        );
    }

    public ReloadResult reloadPluginState() {
        ReloadResult result = reloadCoordinator.execute(new ReloadOperation() {
            @Override
            public ModuleLifecycleReport disableRuntime() {
                return disableCurrentRuntime();
            }

            @Override
            public RuntimeCleanupReport cleanupRuntime() {
                RuntimeCleanupReport report = cleanupBukkitRuntime();
                logCleanupFailures(report);
                return report;
            }

            @Override
            public void reloadConfiguration() {
                reloadConfig();
            }

            @Override
            public ModuleLifecycleReport initializeRuntime(RuntimeGeneration generation) {
                return BigCasares.this.initializeRuntime(generation);
            }

            @Override
            public void bindCommands() {
                registerCommands();
            }

            @Override
            public int activeModuleCount() {
                return moduleManager == null ? 0 : moduleManager.getActiveModuleCount();
            }

            @Override
            public int registeredModuleCount() {
                return moduleManager == null ? 0 : moduleManager.getRegisteredModuleCount();
            }
        });
        if (result.status() == ReloadStatus.FAILED) {
            logReloadFailure(result);
        }
        return result;
    }

    public void openShop(Player player) {
        if (shopModule == null || !shopModule.isEnabled()) {
            player.sendMessage("§cEl shop no esta disponible.");
            return;
        }
        shopModule.openMainMenu(player);
    }

    public int enforceInventoryLimits(Player player) {
        return inventoryLimitModule == null ? 0 : inventoryLimitModule.enforce(player);
    }

    private ModuleLifecycleReport initializeRuntime(RuntimeGeneration generation) {
        this.customItemRegistry = new CustomItemRegistry();
        this.emojiAliasService = loadEmojiAliases();
        this.moduleManager = new ModuleManager(this, getConfig());
        this.missionModule = new MissionModule(this);
        this.bountyModule = new BountyModule(this);
        this.shopModule = new ShopModule(this);
        this.inventoryLimitModule = new InventoryLimitModule(this);
        this.skillRatingModule = new SkillRatingModule(this);
        
        this.dangerModule = new DangerModule(this);
        this.tombstoneModule = new TombstoneModule(this);
        this.specialItemsModule = new SpecialItemsModule(this);
        this.warpModule = new WarpModule(this);
        this.geyserIntegrationModule = new GeyserIntegrationModule(this);
        this.airdropModule = new AirdropModule(this);
        JavaModelGateway javaModels = JavaModelGatewayFactory.create(this);
        this.acidRainModule = new AcidRainModule(this, javaModels);
        this.bloodMoonModule = new BloodMoonModule(this, this::acidRainEventActive);
        this.jeremyModule = new JeremyModule(this);
        this.donPollosModule = new DonPollosModule(this);
        this.grapplingHookModule = new GrapplingHookModule(this, javaModels);
        this.gliderModule = new GliderModule(this);
        this.mobScalingModule = new MobScalingModule(this);
        this.resourcePackModule = new ResourcePackModule(this, this::resolveClientPlatform);
        this.itemCatalogModule = new ItemCatalogModule(this, customItemRegistry);
        this.teamModule = new TeamModule(this);
        this.nexusModule = new NexusModule(this, playerId ->
            teamModule.service().flatMap(service -> service.findByMember(playerId)).map(team -> team.id()),
            teamId -> teamModule.service().flatMap(service -> service.findById(teamId))
                .map(team -> team.color().legacyCode() + team.name().toUpperCase() + " [" + team.tag() + "]"),
            javaModels);
        this.pveBossModule = new PveBossModule(
            this,
            this::resolveClientPlatform,
            playerId -> resourcePackModule != null
                && resourcePackModule.service().map(service -> service.hasLoadedPack(playerId)).orElse(false),
            javaModels
        );
        this.moderationModule = new ModerationModule(this);
        this.serverControlModule = new ServerControlModule(
            this, moderationModule.audit(), moderationModule::acceptSignal, emojiAliasService
        );
        this.endEventModule = new EndEventModule(this);
        this.tombstoneModule.setDeathCaptureEligibility(playerId ->
            endEventModule == null || !endEventModule.bypassTombstone(playerId));
        this.discordIntegrationModule = new DiscordIntegrationModule(
            this, moderationModule.audit(), serverControlModule, emojiAliasService
        );
        this.shopModule.configurePlatform(this::resolveClientPlatform, this::sendBedrockShopForm);

        moduleManager.register(resourcePackModule);
        moduleManager.register(itemCatalogModule);
        moduleManager.register(new CopperAppleModule(this));
        moduleManager.register(new SmokeBombModule(this));
        moduleManager.register(new CustomCrossbowModule(this));
        moduleManager.register(missionModule);
        moduleManager.register(bountyModule);
        moduleManager.register(inventoryLimitModule);
        moduleManager.register(skillRatingModule);
        moduleManager.register(dangerModule);
        moduleManager.register(tombstoneModule);
        moduleManager.register(specialItemsModule);
        moduleManager.register(warpModule);

        moduleManager.register(airdropModule);
        moduleManager.register(grapplingHookModule);
        moduleManager.register(gliderModule);
        moduleManager.register(mobScalingModule);

        moduleManager.register(teamModule);
        moduleManager.register(nexusModule);
        moduleManager.register(acidRainModule);
        moduleManager.register(bloodMoonModule);
        moduleManager.register(jeremyModule);
        moduleManager.register(donPollosModule);
        moduleManager.register(shopModule);
        moduleManager.register(pveBossModule);
        moduleManager.register(geyserIntegrationModule);
        moduleManager.register(moderationModule);
        moduleManager.register(serverControlModule);
        moduleManager.register(endEventModule);
        moduleManager.register(discordIntegrationModule);
        return moduleManager.enableRegisteredModules(generation);
    }

    private boolean acidRainEventActive() {
        try {
            return acidRainModule != null && acidRainModule.isEventRunning();
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    public ClientPlatform resolvePlayerPlatform(UUID playerId) {
        return resolveClientPlatform(playerId);
    }

    private EmojiAliasService loadEmojiAliases() {
        var section = getConfig().getConfigurationSection("emoji-aliases");
        if (section == null) {
            return EmojiAliasService.defaults();
        }
        java.util.Map<String, String> aliases = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            aliases.put(key.startsWith(":") ? key : ":" + key + ":", section.getString(key, ""));
        }
        return new EmojiAliasService(aliases);
    }

    private ClientPlatform resolveClientPlatform(UUID playerId) {
        return geyserIntegrationModule == null
            ? ClientPlatform.JAVA
            : geyserIntegrationModule.platformGateway().resolvePlatform(playerId);
    }

    private boolean sendBedrockShopForm(
        UUID playerId,
        dev.linqfy.bigCasares.modules.shop.ShopView view,
        java.util.function.IntConsumer responseHandler
    ) {
        if (geyserIntegrationModule == null) {
            return false;
        }
        return geyserIntegrationModule.shopForms()
            .map(forms -> forms.show(playerId, new BedrockShopForm(
                view.title(),
                view.description() + "\n\n§fSaldo: §a" + view.balance(),
                view.items().stream().map(item -> new BedrockShopForm.Button(
                    item.name() + (item.price().isBlank() ? "" : "\n§7" + item.price()),
                    item.iconUrl()
                )).toList(),
                (ignored, index) -> responseHandler.accept(index)
            )))
            .orElse(false);
    }

    private void registerCommands() {
        BigCasaresCommand handler = new BigCasaresCommand(this);
        List<PluginCommand> commands = RequiredCommandBindings.resolve(this::getCommand, ROOT_COMMAND_NAMES);
        for (PluginCommand command : commands) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }
        
        dev.linqfy.bigCasares.command.TimerCommand timerCmd = new dev.linqfy.bigCasares.command.TimerCommand(this);
        PluginCommand timerPluginCmd = getCommand("timer");
        if (timerPluginCmd != null) {
            timerPluginCmd.setExecutor(timerCmd);
            timerPluginCmd.setTabCompleter(timerCmd);
            getServer().getPluginManager().registerEvents(timerCmd, this);
        }

        dev.linqfy.bigCasares.command.WarpCommand warpCmd = new dev.linqfy.bigCasares.command.WarpCommand(warpModule);
        PluginCommand warpPluginCmd = getCommand("warp");
        if (warpPluginCmd != null) {
            warpPluginCmd.setExecutor(warpCmd);
            warpPluginCmd.setTabCompleter(warpCmd);
        }
    }

    private ModuleLifecycleReport disableCurrentRuntime() {
        ModuleManager retiringManager = moduleManager;
        try {
            return retiringManager == null
                ? ModuleLifecycleReport.empty()
                : retiringManager.disableActiveModules();
        } finally {
            clearRuntimeReferences();
        }
    }

    private RuntimeCleanupReport cleanupBukkitRuntime() {
        return BukkitRuntimeRegistrations.runTemporaryPluginWideFallback(this);
    }

    private void cleanupFailedStartup(Throwable failure) {
        try {
            ModuleLifecycleReport shutdown = disableCurrentRuntime();
            for (var outcome : shutdown.failures()) {
                failure.addSuppressed(outcome.failure());
            }
        } catch (Throwable shutdownFailure) {
            failure.addSuppressed(shutdownFailure);
        }
        RuntimeCleanupReport cleanup = cleanupBukkitRuntime();
        logCleanupFailures(cleanup);
        for (RuntimeCleanupOutcome outcome : cleanup.failures()) {
            failure.addSuppressed(outcome.failure());
        }
    }

    private void logCleanupFailures(RuntimeCleanupReport report) {
        for (RuntimeCleanupOutcome failure : report.failures()) {
            getLogger().log(
                java.util.logging.Level.SEVERE,
                "Failed temporary runtime cleanup: " + failure.resourceId(),
                failure.failure()
            );
        }
    }

    private void clearRuntimeReferences() {
        moduleManager = null;
        customItemRegistry = null;
        missionModule = null;
        bountyModule = null;
        shopModule = null;
        inventoryLimitModule = null;
        skillRatingModule = null;
        dangerModule = null;
        tombstoneModule = null;
        specialItemsModule = null;
        warpModule = null;

        resourcePackModule = null;
        itemCatalogModule = null;
        teamModule = null;
        nexusModule = null;
        pveBossModule = null;
        geyserIntegrationModule = null;
        airdropModule = null;
        acidRainModule = null;
        grapplingHookModule = null;
        mobScalingModule = null;
        moderationModule = null;
        serverControlModule = null;
        endEventModule = null;
        discordIntegrationModule = null;
        emojiAliasService = null;
    }

    private void logReloadFailure(ReloadResult result) {
        Throwable failure = result.failure();
        String message = "Plugin-state reload failed during " + result.failurePhase();
        if (failure == null) {
            getLogger().severe(message);
        } else {
            getLogger().log(java.util.logging.Level.SEVERE, message, failure);
        }
    }
}
