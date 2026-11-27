package dev.linqfy.bigCasares.modules.alexscaves;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.alexscaves.abyssal.AbyssalFeatures;
import dev.linqfy.bigCasares.modules.alexscaves.candy.CandyFeatures;
import dev.linqfy.bigCasares.modules.alexscaves.candy.CandyFolk;
import dev.linqfy.bigCasares.modules.alexscaves.candy.CandyGumWorm;
import dev.linqfy.bigCasares.modules.alexscaves.candy.CandyItems;
import dev.linqfy.bigCasares.modules.alexscaves.candy.CandyMobListener;
import dev.linqfy.bigCasares.modules.alexscaves.candy.CandyMobs;
import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesConfigLoadResult;
import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettingsLoader;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockRegistry;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.NoteBlockService;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectService;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.DatapackInstaller;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureLootListener;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.WorldgenService;
import dev.linqfy.bigCasares.modules.alexscaves.forlorn.ForlornFeatures;
import dev.linqfy.bigCasares.modules.alexscaves.magnetic.MagneticFeatures;
import dev.linqfy.bigCasares.modules.alexscaves.magnetic.MagneticMobListener;
import dev.linqfy.bigCasares.modules.alexscaves.magnetic.MagneticMobs;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.Atlatitan;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.BossTerrain;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.AmberMonolith;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.Luxtructosaurus;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialBlocks;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.Tephra;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.VolcanicCores;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialArmor;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialBreeding;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialDrops;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialFeatures;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialMobListener;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialMobs;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialSpear;
import dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialTaming;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.ToxicAcid;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.ToxicFeatures;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.ToxicMobListener;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.ToxicMobs;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.ToxicNuclearBomb;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.ToxicRadiation;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.ToxicRaygun;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.ToxicVents;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Unofficial recreation of the Alex's Caves mod. Programmer data (bundled
 * datapack, catalogs) is validated strictly; configuration and environment
 * problems only switch the affected feature off, because a failing module
 * rolls back every BigCasares module.
 */
public final class AlexsCavesModule implements PluginModule {
    public static final String MODULE_ID = "alexs-caves";
    static final String CONFIG_FILE = "alexs-caves.yml";
    static final String COMMAND = "alexscaves";
    static final String BLOCKS_FILE = "alexscaves/blocks.yml";

    private final BigCasares plugin;
    private AlexsCavesItemMenu itemMenu;
    private final JavaModelGateway models;
    private AlexsCavesRuntime runtime;
    private RuntimeRegistrationScope compatibilityScope;

    public AlexsCavesModule(BigCasares plugin, JavaModelGateway models) {
        this.plugin = plugin;
        this.models = models;
        // ModuleManager never calls onEnable for a module switched off, so the Geyser mappings of an
        // earlier run are removed here; otherwise Bedrock would draw vanilla note blocks as module blocks.
        if (plugin != null && !isExplicitlyEnabled(plugin.getConfig())) {
            AlexsCavesGeyserMappings.uninstall(plugin);
        }
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
        if (!isExplicitlyEnabled(plugin.getConfig())) {
            // Missing switches count as enabled for BigCasares, so an existing server upgrading the
            // JAR would turn this module on without anyone asking. It needs an explicit opt-in.
            plugin.getLogger().info("[AlexsCaves] Apagado: para activarlo poné modules.alexs-caves.enabled: true en config.yml.");
            AlexsCavesGeyserMappings.uninstall(plugin);
            return;
        }
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        DatapackInstaller installer = new DatapackInstaller(path -> plugin.getResource(path));
        installer.verifyPackaged();

        AlexsCavesConfigLoadResult config = loadConfig();
        ModBlockRegistry blockRegistry = loadBlockRegistry(
            NoteBlockService.shapedBasesActive(NoteBlockService.loadPaperGlobal(plugin)));
        runtime = new AlexsCavesRuntime(plugin, config, content(new ModBlockPalette(blockRegistry).preload()), models,
            plugin.getCustomItemRegistry(), blockRegistry);
        AlexsCavesRuntime ownedRuntime = runtime;
        scope.register("alexs-caves-runtime", ownedRuntime::shutdown);

        PluginCommand pluginCommand = plugin.getCommand(COMMAND);
        if (pluginCommand == null) {
            throw new IllegalStateException("Required command is not declared: " + COMMAND);
        }
        itemMenu = registrations.registerListener("alexs-caves-item-menu",
            new AlexsCavesItemMenu(plugin.getCustomItemRegistry()));
        AlexsCavesCommand command = new AlexsCavesCommand(this);
        registrations.bindCommand("alexs-caves-command", pluginCommand, command, command);
        ownedRuntime.start(registrations, installer);
        AlexsCavesGeyserMappings.install(plugin);
        plugin.getLogger().info("[AlexsCaves] Módulo habilitado. Créditos: /alexscaves creditos");
    }

    @Override
    public void onDisable() {
        if (itemMenu != null) {
            AlexsCavesItemMenu.closeAll();
        }
        if (runtime != null) {
            runtime.shutdown();
        }
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        runtime = null;
        itemMenu = null;
    }

    /** Content registered by each biome package (mobs, features). Duplicate ids fail fast. */
    static AlexsCavesContent content(ModBlockPalette palette) {
        AlexsCavesContent content = new AlexsCavesContent();
        MagneticFeatures.create(palette).forEach(content::addFeature);
        PrimordialFeatures.create(palette).forEach(content::addFeature);
        ToxicFeatures.create(palette).forEach(content::addFeature);
        CandyFeatures.create(palette).forEach(content::addFeature);
        AbyssalFeatures.create(palette).forEach(content::addFeature);
        ForlornFeatures.create(palette).forEach(content::addFeature);
        content.addListener(ToxicRadiation::new);
        content.addListener(ToxicAcid::new);
        content.addListener(ToxicMobListener::new);
        content.addListener(StructureLootListener::new);
        content.addListener(mobs -> new dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks());
        content.addListener(ToxicRaygun::new);
        BlockData nuclearBomb = palette.get("nuclear_bomb");
        content.addListener(mobs -> new ToxicNuclearBomb(mobs, nuclearBomb));
        Set<BlockData> vents = Set.of(palette.get("geothermal_vent"), palette.get("geothermal_vent_medium"),
            palette.get("geothermal_vent_thin"));
        content.addListener(mobs -> new ToxicVents(mobs, vents));
        ToxicMobs.register(content.mobs());
        MagneticMobs.register(content.mobs());
        content.addListener(MagneticMobListener::new);
        PrimordialBlocks bossBlocks = new PrimordialBlocks(palette);
        PrimordialMobs.register(content.mobs(), bossBlocks);
        content.addListener(PrimordialMobListener::new);
        // Hito 7: the Primordial boss, its volcano, the amber monolith and the Atlatitan.
        Luxtructosaurus.register(content.mobs(), bossBlocks);
        Atlatitan.register(content.mobs(), bossBlocks);
        content.addListener(Atlatitan.Listener::new);
        content.addListener(mobs -> new Tephra(mobs.plugin(), bossBlocks));
        content.addListener(mobs -> new BossTerrain(mobs.plugin(), bossBlocks));
        content.addListener(mobs -> new VolcanicCores(mobs, bossBlocks));
        content.addListener(mobs -> new AmberMonolith(mobs, bossBlocks));
        content.addListener(PrimordialTaming::new);
        content.addListener(PrimordialDrops::new);
        BlockData cycadTip = palette.get("cycad");
        BlockData cycadTrunk = palette.get("cycad", "trunk");
        content.addListener(mobs -> new dev.linqfy.bigCasares.modules.alexscaves.primordial.PrimordialCycads(mobs, cycadTip,
            cycadTrunk));
        content.addListener(PrimordialArmor::new);
        content.addListener(PrimordialSpear::new);
        BlockData fernThatch = palette.get("fern_thatch");
        content.addListener(mobs -> new PrimordialBreeding(mobs, fernThatch));
        BlockData smoothLimestone = palette.get("smooth_limestone");
        content.addListener(mobs -> new dev.linqfy.bigCasares.modules.alexscaves.core.block.CavePaintingListener(mobs,
            smoothLimestone));
        BlockData[] thinBone = {palette.get("thin_bone", "x_0"), palette.get("thin_bone", "y_0"),
            palette.get("thin_bone", "z_0")};
        content.addListener(mobs -> new dev.linqfy.bigCasares.modules.alexscaves.primordial.DinosaurChops(mobs, thinBone));
        // Tremorzilla: a dinosaur (taming, riding and eggs of the Primordial package) hatched by nukes of the Toxic Caves.
        dev.linqfy.bigCasares.modules.alexscaves.primordial.Tremorzilla.register(content.mobs(), nuclearBomb);
        content.addListener(dev.linqfy.bigCasares.modules.alexscaves.primordial.Tremorzilla.Listener::new);
        CandyMobs.register(content.mobs(), palette.get("block_of_frosted_chocolate"));
        CandyFolk.register(content.mobs(), palette);
        CandyGumWorm.register(content.mobs());
        content.addListener(CandyItems::new);
        BlockData[] frostmint = {palette.get("frostmint"), palette.get("frostmint", "top"), palette.get("frostmint", "double")};
        content.addListener(mobs -> new dev.linqfy.bigCasares.modules.alexscaves.candy.CandyFrostmint(mobs, frostmint[0],
            frostmint[1], frostmint[2]));
        content.addListener(CandyMobListener::new);
        dev.linqfy.bigCasares.modules.alexscaves.abyssal.AbyssalMobs.register(content.mobs(), palette.get("muck"),
            palette.get("abyssmarine"));
        content.addListener(dev.linqfy.bigCasares.modules.alexscaves.abyssal.AbyssalItems::new);
        dev.linqfy.bigCasares.modules.alexscaves.abyssal.DeepOnes.register(content.mobs());
        content.addListener(dev.linqfy.bigCasares.modules.alexscaves.abyssal.DeepOneItems::new);
        dev.linqfy.bigCasares.modules.alexscaves.abyssal.Hullbreaker.register(content.mobs());
        BlockData muckBlock = palette.get("muck");
        content.addListener(mobs -> new dev.linqfy.bigCasares.modules.alexscaves.abyssal.DivingSuit(mobs, muckBlock));
        // Mod ActivatedByAltar: the blocks an active altar lights up, from their dark state to their lit one.
        java.util.Map<BlockData, BlockData> lit = new java.util.HashMap<>();
        lit.put(palette.get("abyssmarine_bricks"), palette.get("abyssmarine_bricks", "active"));
        lit.put(palette.get("abyssmarine_tiles"), palette.get("abyssmarine_tiles", "active"));
        for (String axis : new String[] {"x", "y", "z"}) {
            lit.put(palette.get("abyssmarine_pillar", axis), palette.get("abyssmarine_pillar", axis + "_active"));
        }
        BlockData altar = palette.get("abyssal_altar");
        BlockData activeAltar = palette.get("abyssal_altar", "active");
        content.addListener(mobs -> new dev.linqfy.bigCasares.modules.alexscaves.abyssal.AbyssalAltar(mobs, altar, activeAltar, lit));
        dev.linqfy.bigCasares.modules.alexscaves.forlorn.ForlornMobs.register(content.mobs(), palette);
        content.addListener(mobs -> new dev.linqfy.bigCasares.modules.alexscaves.forlorn.ForlornListener(mobs, palette));
        content.addListener(mobs -> new dev.linqfy.bigCasares.modules.alexscaves.forlorn.ForlornItems(mobs, palette));
        content.addListener(dev.linqfy.bigCasares.modules.alexscaves.forlorn.Dreadbow::new);
        content.addListener(dev.linqfy.bigCasares.modules.alexscaves.forlorn.TotemOfPossession::new);
        content.addListener(dev.linqfy.bigCasares.modules.alexscaves.forlorn.DesolateDagger::new);
        content.addListener(dev.linqfy.bigCasares.modules.alexscaves.forlorn.DarknessArmor::new);
        return content;
    }

    /** The note block allocation is packaged data: a missing or broken file is a build error. */
    private ModBlockRegistry loadBlockRegistry(boolean shapedBasesActive) {
        try (InputStream input = plugin.getResource(BLOCKS_FILE)) {
            if (input == null) {
                throw new IllegalStateException("Falta " + BLOCKS_FILE + " en el JAR");
            }
            return ModBlockRegistry.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8), shapedBasesActive);
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo leer " + BLOCKS_FILE, exception);
        }
    }

    private AlexsCavesConfigLoadResult loadConfig() {
        File file = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!file.isFile()) {
            plugin.saveResource(CONFIG_FILE, false);
        }
        AlexsCavesConfigLoadResult result = AlexsCavesSettingsLoader.load(YamlConfiguration.loadConfiguration(file));
        for (String warning : result.warnings()) {
            plugin.getLogger().warning("[AlexsCaves] " + warning);
        }
        for (String error : result.errors()) {
            plugin.getLogger().severe("[AlexsCaves] " + CONFIG_FILE + ": " + error + " (se apaga esa sección)");
        }
        return result;
    }

    /**
     * True only when the switch is explicitly {@code true}: nested {@code modules.alexs-caves.enabled}
     * or the legacy flat {@code modules.alexs-caves} boolean, the two forms ModuleManager accepts.
     */
    public static boolean isExplicitlyEnabled(ConfigurationSection config) {
        if (config == null) {
            return false;
        }
        String flat = "modules." + MODULE_ID;
        if (config.isBoolean(flat)) {
            return config.getBoolean(flat);
        }
        return config.isBoolean(flat + ".enabled") && config.getBoolean(flat + ".enabled");
    }

    List<String> infoLines() {
        return runtime == null ? List.of("§cAlex's Caves no está activo.") : runtime.infoLines();
    }

    Optional<dev.linqfy.bigCasares.modules.alexscaves.core.block.remap.PacketRemapService> packetRemap() {
        return runtime == null ? Optional.empty() : Optional.of(runtime.packetRemap());
    }

    Optional<ModEffectService> effects() {
        return runtime == null ? Optional.empty() : Optional.of(runtime.effects());
    }

    Optional<AlexsCavesItemMenu> itemMenu() {
        return Optional.ofNullable(itemMenu);
    }

    BigCasares plugin() {
        return plugin;
    }

    Optional<WorldgenService> worldgen() {
        return runtime == null ? Optional.empty() : Optional.of(runtime.worldgen());
    }

    Optional<ModMobService> mobs() {
        return runtime == null ? Optional.empty() : Optional.of(runtime.mobs());
    }

    JavaModelGateway models() {
        return models;
    }
}
