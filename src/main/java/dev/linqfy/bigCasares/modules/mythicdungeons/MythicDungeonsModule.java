package dev.linqfy.bigCasares.modules.mythicdungeons;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.items.CustomItemRegistry;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.BedrockStandIns;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.BossSoulBridge;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossRegistry;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossRewards;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil.EnlilEncounter;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama.PachamamaEncounter;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicWorldgen;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty.ThirdPartySources;
import dev.linqfy.bigCasares.modules.pveboss.BossDamageStanding;
import dev.linqfy.bigCasares.modules.pveboss.PveBossModule;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Mazmorras miticas: nueve culturas, cada una con su mazmorra (estructura {@code bigc_myth:<cultura>}) y sus mobs; las
 * que ya tienen jefe (un jefe externo del sistema pve-boss) suman su Fragmento de Odio y su Invocador. Este es el
 * cableado:
 * <ul>
 *   <li>{@link MythicMobs}: los mobs (spawn, modelos, conductas, drops) y {@link MythicDungeonSpawns}: quien nace adentro;</li>
 *   <li>{@link MythicSummoners}: el Invocador sobre el altar y la mesa de crafteo;</li>
 *   <li>{@code boss/}: los encuentros registrados en pve-boss; {@code worldgen/}: la generacion;</li>
 *   <li>{@link MythicModelInstaller} y {@link MythicDungeonCommand} ({@code /mazmorra}).</li>
 * </ul>
 * La configuracion vive en {@code mythic-dungeons.yml}. Sin el modulo pve-boss el resto anda igual y el altar explica
 * que el sistema de jefes esta apagado.
 */
public final class MythicDungeonsModule implements PluginModule {

    public static final String MODULE_ID = "mythic-dungeons";
    static final String CONFIG_FILE = "mythic-dungeons.yml";
    static final String COMMAND = "mazmorra";
    /** La receta propia del Invocador: cuatro items del material de los fragmentos, decidida por id en la mesa. */
    static final String SUMMONER_RECIPE = "mythic_summoner_by_fragment_id";

    private final BigCasares plugin;
    private final JavaModelGateway javaModels;
    private final PveBossModule pveBossModule;
    private final CustomItemRegistry customItems;
    private final ClientPlatformGateway platforms;
    private final DungeonBossRegistry summons = new DungeonBossRegistry();
    private final Set<String> registeredBosses = new LinkedHashSet<>();
    private RuntimeRegistrationScope compatibilityScope;
    private MythicMobs mobs;
    private MythicBossSupport support;
    private PaperDungeonLocator locator;
    private MythicDeliveries deliveries;

    public MythicDungeonsModule(BigCasares plugin, JavaModelGateway javaModels, PveBossModule pveBossModule,
                                CustomItemRegistry customItems, ClientPlatformGateway platforms) {
        this.plugin = plugin;
        this.javaModels = javaModels;
        this.pveBossModule = pveBossModule;
        this.customItems = customItems;
        this.platforms = platforms;
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
            return;
        }
        Objects.requireNonNull(javaModels, "javaModels");
        Objects.requireNonNull(customItems, "customItems");
        Objects.requireNonNull(platforms, "platforms");
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        // se registra primero: corre ultimo, despues de bajar los jefes y sus entidades
        registrations.ownCleanup("mythic-dungeons-runtime", this::shutdown);

        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.isFile()) {
            plugin.saveResource(CONFIG_FILE, false);
        }
        MythicDungeonSettings settings = MythicDungeonSettings.load(YamlConfiguration.loadConfiguration(configFile));
        settings.warnings().forEach(warning -> plugin.getLogger().warning("[Mazmorras] " + warning));

        MythicKeys keys = new MythicKeys(plugin);
        MythicScheduler scheduler = new MythicScheduler(registrations);
        PaperDungeonLocator ownedLocator = new PaperDungeonLocator();
        locator = ownedLocator;
        MythicBossHost host = bossHost();
        FragmentDropRoll fragments = new FragmentDropRoll(settings.lootingBonusPerLevel(),
            () -> ThreadLocalRandom.current().nextDouble());
        MythicMobs ownedMobs = new MythicMobs(keys, settings.mobs(), javaModels, scheduler, plugin.getLogger(),
            fragments, this::catalogItem, host::isActive);
        mobs = ownedMobs;

        try {
            MythicModelInstaller.install(plugin,
                MythicModelInstaller.knownKeys(settings.mobs(), MythicBossRegistry.extraModelKeys()));
        } catch (RuntimeException | LinkageError failure) {
            plugin.getLogger().log(Level.WARNING, "[Mazmorras] No se pudieron instalar los modelos", failure);
        }

        registrations.registerListener("mythic-mobs", ownedMobs);
        registrations.registerListener("mythic-spawns", new MythicDungeonSpawns(ownedMobs, ownedLocator, settings));
        NamespacedKey summonerRecipe = new NamespacedKey(plugin, SUMMONER_RECIPE);
        registrations.registerListener("mythic-summoners", new MythicSummoners(customItems, ownedLocator, settings,
            host, summons, plugin.getLogger(), summonerRecipe));
        registerSummonerRecipe(registrations, summonerRecipe);
        registrations.registerListener("mythic-guitar",
            new MandingaGuitarListener(plugin, customItems, scheduler));
        MythicDeliveries ownedDeliveries = new MythicDeliveries(plugin.getServer(), new MythicPendingDeliveries(
            new File(plugin.getDataFolder(), MythicPendingDeliveries.FILE).toPath(), plugin.getLogger()),
            this::catalogItem, plugin.getLogger());
        deliveries = ownedDeliveries;
        registrations.registerListener("mythic-deliveries", ownedDeliveries);

        MythicBossSupport ownedSupport = new MythicBossSupport(plugin, ownedMobs, javaModels, ownedLocator, scheduler,
            platforms);
        support = ownedSupport;
        BedrockStandIns standIns = new BedrockStandIns(plugin, ownedSupport::isBedrock);
        ownedSupport.attachBedrockStandIns(standIns);
        registrations.registerListener("mythic-bedrock-stand-ins", standIns);
        registrations.scheduleRepeating("mythic-bedrock-stand-ins-tick", standIns::tick, 1L, 1L);
        registrations.ownCleanup("mythic-bedrock-stand-ins-entities", standIns::clear);
        registrations.ownCleanup("mythic-boss-entities", ownedSupport::cleanupAll);
        registerBosses(registrations, ownedSupport, settings, new MythicBossEndings(this::bossEnded));

        PluginCommand command = plugin.getCommand(COMMAND);
        if (command == null) {
            throw new IllegalStateException("Required command is not declared: " + COMMAND);
        }
        MythicDungeonCommand executor = new MythicDungeonCommand(customItems, ownedMobs, ownedLocator, host, summons);
        registrations.bindCommand("mythic-command", command, executor, executor);

        registrations.scheduleRepeating("mythic-mob-behaviours", ownedMobs::tick, MythicMobs.TICK_PERIOD,
            MythicMobs.TICK_PERIOD);
        ownedMobs.adoptLoaded(plugin.getServer().getWorlds());
        try {
            // agua de una inundacion de Enlil que quedo en el mundo si el servidor se cayo en plena pelea
            EnlilEncounter.recoverOrphanFloods(plugin);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[Mazmorras] No se pudo revisar el agua de Enlil", failure);
        }
        try {
            // piso pintado o agua de Pachamama que quedo en el mundo si el servidor se cayo en plena pelea
            PachamamaEncounter.recoverOrphans(plugin);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[Mazmorras] No se pudo revisar el piso de Pachamama", failure);
        }
        MythicWorldgen.install(plugin, registrations, ThirdPartySources.all());
        plugin.getLogger().info("[Mazmorras] Mazmorras míticas listas: " + settings.mobs().all().size() + " mobs, "
            + registeredBosses.size() + "/" + MythicBossRegistry.entries().size() + " jefes registrados.");
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        shutdown();
    }

    /** Idempotente: la corre onDisable y tambien la limpieza del scope. */
    private void shutdown() {
        unregisterBosses();
        if (support != null) {
            support.cleanupAll();
            support = null;
        }
        if (mobs != null) {
            mobs.shutdown();
            mobs = null;
        }
        if (locator != null) {
            locator.clearCache();
            locator = null;
        }
        summons.clear();
        deliveries = null;
    }

    private void registerBosses(BukkitRuntimeRegistrations registrations, MythicBossSupport ownedSupport,
                                MythicDungeonSettings settings, MythicBossEndings endings) {
        if (pveBossModule == null || !pveBossModule.isEnabled()) {
            plugin.getLogger().warning("[Mazmorras] El módulo pve-boss está apagado: los invocadores no van a andar.");
            return;
        }
        for (MythicBossRegistry.Entry entry : MythicBossRegistry.entries()) {
            MythicCulture culture = entry.culture();
            try {
                pveBossModule.registerExternalBoss(entry.bossId(),
                    endings.wrap(culture, entry.factory().apply(ownedSupport)), standings -> {
                        // primero: la instancia que pve-boss baja ahora es una derrota (ver MythicBossEndings)
                        endings.defeated(culture);
                        reward(culture, standings, settings);
                    });
                registeredBosses.add(entry.bossId());
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "[Mazmorras] No se pudo registrar el jefe " + entry.bossId(),
                    failure);
            }
        }
        registrations.ownCleanup("mythic-bosses", this::unregisterBosses);
    }

    /**
     * Un jefe mitico termino. Si no fue derrotado (chunk descargado, recarga, modulo apagado, reinicio, comando), el
     * Invocador vuelve a quien lo gasto: ya si esta conectado, o cuando entre.
     */
    private void bossEnded(UUID bossInstanceId, MythicCulture culture, boolean defeated) {
        Optional<DungeonBossRegistry.Summon> summon = summons.byBoss(bossInstanceId);
        summons.forget(bossInstanceId);
        MythicDeliveries owned = deliveries;
        if (defeated || owned == null || summon.isEmpty()) {
            return;
        }
        summon.get().summoner().ifPresent(player -> owned.deliver(
            MythicPendingDeliveries.Delivery.summoner(player, summon.get().culture())));
    }

    private void unregisterBosses() {
        if (pveBossModule != null) {
            for (String bossId : List.copyOf(registeredBosses)) {
                try {
                    pveBossModule.unregisterExternalBoss(bossId);
                } catch (RuntimeException failure) {
                    if (plugin != null) {
                        plugin.getLogger().warning("[Mazmorras] No se pudo quitar el jefe " + bossId + ": " + failure);
                    }
                }
            }
        }
        registeredBosses.clear();
    }

    private void reward(MythicCulture culture, List<BossDamageStanding> standings, MythicDungeonSettings settings) {
        List<MythicBossRewards.Grant> grants = MythicBossRewards.grants(standings, settings.rewardFor(culture),
            settings.rewardTopPlayers());
        List<String> podium = new ArrayList<>();
        for (MythicBossRewards.Grant grant : grants) {
            Player player = plugin.getServer().getPlayer(grant.playerId());
            String name = player != null ? player.getName()
                : Optional.ofNullable(plugin.getServer().getOfflinePlayer(grant.playerId()).getName()).orElse("?");
            double damage = standings.stream().filter(standing -> standing.playerId().equals(grant.playerId()))
                .mapToDouble(BossDamageStanding::damage).findFirst().orElse(0.0);
            podium.add("§e" + grant.placement() + ". " + name + " §7(" + String.format(Locale.ROOT, "%.0f", damage)
                + ")");
            // si no esta conectado queda guardado y se le da al entrar
            MythicDeliveries owned = deliveries;
            if (owned != null) {
                owned.deliver(MythicPendingDeliveries.Delivery.reward(grant.playerId(), culture, grant.placement(),
                    grant.xp(), grant.items()));
            }
        }
        // el alma del jefe para el podio (sistema de misiones de netherita, si esta version lo trae)
        new BossSoulBridge(plugin, (message, cause) -> plugin.getLogger().log(Level.WARNING, "[Mazmorras] " + message,
            cause)).award(culture.requireBoss().id(), culture.requireBoss().displayName(), standings);
        plugin.getServer().broadcastMessage("§5§l[Mazmorra] §f" + culture.requireBoss().displayName() + " §7cayó en "
            + culture.dungeonName() + "." + (podium.isEmpty() ? "" : " Mejores: " + String.join("§7, ", podium)));
    }

    /**
     * La receta exacta del catalogo solo reconoce fragmentos creados con la misma revision del catalogo (cualquier
     * cambio en cualquier item la cambia, y los fragmentos guardados en cofres no se actualizan). Esta receta acepta
     * cuatro items del material de los fragmentos y {@link MythicSummoners} decide por id de catalogo que Invocador
     * sale (o que no sale nada, por ejemplo con cuatro fragmentos de jefes distintos o items vanilla).
     */
    private void registerSummonerRecipe(BukkitRuntimeRegistrations registrations, NamespacedKey key) {
        Set<Material> materials = new LinkedHashSet<>();
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            culture.fragmentItemId().flatMap(this::catalogItem).map(ItemStack::getType).ifPresent(materials::add);
        }
        if (materials.isEmpty()) {
            plugin.getLogger().warning("[Mazmorras] El catálogo no tiene Fragmentos de Odio: los Invocadores solo se"
                + " craftean con la receta del catálogo.");
            return;
        }
        ItemStack placeholder = MythicCulture.bossCultures().getFirst().summonerItemId().flatMap(this::catalogItem)
            .orElseGet(() -> new ItemStack(Material.NETHER_STAR));
        ShapelessRecipe recipe = new ShapelessRecipe(key, placeholder);
        for (int slot = 0; slot < SummonerRecipeRule.FRAGMENTS_PER_SUMMONER; slot++) {
            recipe.addIngredient(new RecipeChoice.MaterialChoice(List.copyOf(materials)));
        }
        try {
            if (!registrations.registerRecipe("mythic-summoner-recipe", key, recipe)) {
                plugin.getLogger().warning("[Mazmorras] No se pudo registrar la receta propia del Invocador.");
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "[Mazmorras] No se pudo registrar la receta propia del Invocador",
                failure);
        }
    }

    private Optional<ItemStack> catalogItem(String itemId) {
        try {
            return Optional.of(customItems.createItemStack(itemId, 1));
        } catch (RuntimeException unavailable) {
            plugin.getLogger().warning("[Mazmorras] No se pudo crear " + itemId + ": " + unavailable.getMessage());
            return Optional.empty();
        }
    }

    private MythicBossHost bossHost() {
        return new MythicBossHost() {
            @Override
            public boolean available(MythicCulture culture) {
                return pveBossModule != null && pveBossModule.isEnabled()
                    && registeredBosses.contains(culture.requireBoss().id());
            }

            @Override
            public UUID spawn(MythicCulture culture, Location location) {
                if (!available(culture)) {
                    throw new IllegalStateException("el sistema de jefes no está disponible");
                }
                return pveBossModule.spawn(culture.requireBoss().id(), location);
            }

            @Override
            public boolean isActive(UUID bossInstanceId) {
                if (pveBossModule == null) {
                    return false;
                }
                try {
                    return pveBossModule.isActive(bossInstanceId);
                } catch (RuntimeException unavailable) {
                    return false;
                }
            }

            @Override
            public boolean remove(UUID bossInstanceId) {
                return pveBossModule != null && pveBossModule.removeBoss(bossInstanceId);
            }
        };
    }
}
