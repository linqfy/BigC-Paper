package dev.linqfy.bigCasares.modules.alexscaves;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesConfigLoadResult;
import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettings;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockRegistry;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.NoteBlockListener;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.NoteBlockService;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.remap.PacketRemapService;
import dev.linqfy.bigCasares.modules.alexscaves.core.damage.ModDamageTypes;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectListener;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectService;
import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.EnchantmentGuardListener;
import dev.linqfy.bigCasares.modules.alexscaves.core.enchant.ModEnchantments;
import dev.linqfy.bigCasares.modules.alexscaves.core.item.AlexsCavesCraftGuard;
import dev.linqfy.bigCasares.modules.alexscaves.core.item.ModCookingRecipes;
import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetismListener;
import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetismService;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobListener;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobType;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModModelInstaller;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.ModSpawner;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.DatapackInstaller;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.WorldgenService;
import dev.linqfy.bigCasares.modules.alexscaves.integration.BossIntegrationListener;
import dev.linqfy.bigCasares.modules.alexscaves.integration.BossIntegrations;
import dev.linqfy.bigCasares.modules.alexscaves.magnetic.MagneticBlocks;
import dev.linqfy.bigCasares.modules.alexscaves.magnetic.MagneticItemAbilities;
import dev.linqfy.bigCasares.modules.alexscaves.toxic.Hazmat;
import dev.linqfy.bigCasares.items.CustomItemRegistry;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Services of one enabled generation of the module. {@link #shutdown()} is idempotent. */
final class AlexsCavesRuntime {
    private final Plugin plugin;
    private final AlexsCavesConfigLoadResult config;
    private final ModDamageTypes damageTypes;
    private final ModEffectService effects;
    private final WorldgenService worldgen;
    private final ModMobService mobs;
    private final ModSpawner spawner;
    private final NoteBlockService blocks;
    private final PacketRemapService packetRemap;
    private final MagnetismService magnetismService;
    private final ModEnchantments enchantments;
    private final MagneticItemAbilities magneticItems;
    private final MagneticBlocks magneticBlocks;
    private final CustomItemRegistry items;
    private final AlexsCavesContent content;
    private final AtomicBoolean stopped = new AtomicBoolean();
    /** Content listeners with state to undo at shutdown (riders, frozen mounts). */
    private final List<AutoCloseable> closeables = new ArrayList<>();
    private final List<NamespacedKey> cookingRecipes = new ArrayList<>();

    AlexsCavesRuntime(Plugin plugin, AlexsCavesConfigLoadResult config, AlexsCavesContent content,
                      JavaModelGateway models, CustomItemRegistry items, ModBlockRegistry blockRegistry) {
        this.plugin = plugin;
        this.items = items;
        this.content = content;
        this.blocks = new NoteBlockService(plugin, blockRegistry, items);

        this.config = config;
        AlexsCavesSettings settings = config.settings();
        this.packetRemap = new PacketRemapService(plugin, blockRegistry, settings.blocks().packetRemap());
        this.damageTypes = ModDamageTypes.lookup();
        this.effects = new ModEffectService(plugin, settings.effects(), damageTypes,
            entity -> Hazmat.pieces(entity, items::resolveItemId));
        this.magnetismService = new MagnetismService(plugin, settings.magnetism(), blocks, effects);
        this.enchantments = ModEnchantments.lookup();
        this.magneticItems = new MagneticItemAbilities(plugin, items, enchantments);
        this.magneticBlocks = new MagneticBlocks(plugin, blocks, magnetismService);
        this.worldgen = new WorldgenService(plugin, settings.worldgen(), content.features());
        this.mobs = new ModMobService(plugin, content.mobs(), effects, damageTypes, models, items);
        this.mobs.configure(settings);
        this.spawner = new ModSpawner(settings.spawning(), settings.worldgen().worlds(), content.mobs(), mobs);
    }

    void start(BukkitRuntimeRegistrations registrations, DatapackInstaller installer) {
        registrations.registerListener("alexs-caves-effects", new ModEffectListener(effects));
        registrations.scheduleRepeating("alexs-caves-effect-tick", effects::tick, 1L, 1L);
        for (var player : plugin.getServer().getOnlinePlayers()) {
            effects.load(player);
        }
        registrations.registerListener("alexs-caves-blocks",
            new NoteBlockListener(blocks, items, block -> {
                magnetismService.onBlockRemoved(block);
                packetRemap.onBlockRemoved(block);
            }));
        registrations.registerListener("alexs-caves-petrified-slab",
            new dev.linqfy.bigCasares.modules.alexscaves.core.block.PetrifiedSlabDrops(items));
        registrations.registerListener("alexs-caves-potted-plants",
            new dev.linqfy.bigCasares.modules.alexscaves.core.block.potted.PottedPlantListener(plugin, items));
        registrations.scheduleRepeating("alexs-caves-block-migration", blocks::tickMigration, 1L, 1L);
        packetRemap.start(registrations);
        warnAboutBlockUpdates(NoteBlockService.loadPaperGlobal(plugin));
        registrations.registerListener("alexs-caves-craft-guard", new AlexsCavesCraftGuard(items));
        cookingRecipes.addAll(ModCookingRecipes.register(plugin.getServer(), items, plugin.getLogger()));
        // The recipes' exact choices carry the catalog revision, so they follow every catalog reload.
        Runnable cookingListener = items.addCatalogListener(catalog -> {
            if (stopped.get()) {
                return;
            }
            ModCookingRecipes.unregister(plugin.getServer(), cookingRecipes);
            cookingRecipes.clear();
            cookingRecipes.addAll(ModCookingRecipes.register(plugin.getServer(), items, plugin.getLogger()));
        });
        closeables.add(cookingListener::run);
        if (config.settings().enchantments().enabled()) {
            registrations.registerListener("alexs-caves-enchant-guard", new EnchantmentGuardListener(enchantments, items));
        }
        registrations.registerListener("alexs-caves-magnetic-items", magneticItems);
        registrations.scheduleRepeating("alexs-caves-magnetic-items-tick", magneticItems::tick, 1L, 1L);
        registrations.registerListener("alexs-caves-magnetic-blocks", magneticBlocks);
        registrations.scheduleRepeating("alexs-caves-magnetic-blocks-tick", magneticBlocks::tick, 1L, 1L);
        blocks.visitLoadedChunks();
        registrations.registerListener("alexs-caves-magnetism", new MagnetismListener(magnetismService));
        registrations.scheduleRepeating("alexs-caves-magnetism-tick", magnetismService::tick, 1L, 1L);
        for (var world : plugin.getServer().getWorlds()) {
            for (var chunk : world.getLoadedChunks()) {
                magnetismService.indexChunk(chunk);
            }
        }
        registrations.registerListener("alexs-caves-mobs", new ModMobListener(mobs));
        var bossIntegration = new BossIntegrationListener(mobs,
            BossIntegrations.rewards(plugin, config.settings().integration()), config.settings().integration());
        registrations.registerListener("alexs-caves-boss-integration", bossIntegration);
        closeables.add(bossIntegration::close);
        int listenerIndex = 0;
        for (var factory : content.listeners()) {
            var listener = factory.apply(mobs);
            String name = "alexs-caves-content-" + listenerIndex++;
            registrations.registerListener(name, listener);
            // Content that needs a per-tick update (mount control, following) is also a Runnable.
            if (listener instanceof Runnable ticked) {
                registrations.scheduleRepeating(name + "-tick", ticked, 1L, 1L);
            }
            if (listener instanceof AutoCloseable closeable) {
                closeables.add(closeable);
            }
        }
        installModels();
        // BetterModel may still be initializing right after enable; repeat like the other modules do.
        registrations.scheduleDelayed("alexs-caves-models-delayed", this::installModels, 60L);
        registrations.scheduleRepeating("alexs-caves-mob-tick", mobs::tick, 1L, 1L);
        registrations.scheduleRepeating("alexs-caves-spawner", () -> spawner.tick(plugin.getServer().getOnlinePlayers()),
            40L, config.settings().spawning().intervalTicks());
        for (var world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                mobs.bind(entity);
            }
        }
        worldgen.start(installer);
        if (!damageTypes.loaded()) {
            plugin.getLogger().warning("[AlexsCaves] Damage types del datapack sin cargar: se usan tipos vanilla hasta reiniciar.");
        }
    }

    /**
     * Without Paper's {@code disable-tripwire-updates} and {@code disable-chorus-plant-updates} the blocks that are
     * not full cubes cannot use tripwire / chorus plant states (vanilla would rewrite them from their neighbours),
     * so they stay on the note block states, which the client draws as full cubes. Without
     * {@code disable-noteblock-updates}, vanilla still rewrites note block states in places no event reaches
     * (piston shape updates, or any change while the module is off), and a vanilla note block can end up looking
     * like a module block. They are server settings, so only warn.
     */
    private void warnAboutBlockUpdates(YamlConfiguration paperGlobal) {
        List<String> missing = new ArrayList<>();
        if (!NoteBlockService.updatesDisabled(paperGlobal, "tripwire")) {
            missing.add("disable-tripwire-updates");
        }
        if (!NoteBlockService.updatesDisabled(paperGlobal, "chorus-plant")) {
            missing.add("disable-chorus-plant-updates");
        }
        if (!missing.isEmpty()) {
            plugin.getLogger().warning("[AlexsCaves] Falta en config/paper-global.yml (block-updates): "
                + String.join(" y ", missing) + ": true. Sin eso los bloques que no son cubos completos (mejillones, "
                + "gusanos tubícolas y de hueso, ramas de thornwood, anémonas, esponjas, costillas y barbas de ballena, "
                + "capas de guano) siguen usando estados de note block y se ven como cubos transparentes, dejando ver "
                + "a través del piso. Activá las dos opciones y reiniciá el servidor: los chunks ya generados se "
                + "convierten solos al cargarse.");
        } else {
            plugin.getLogger().info("[AlexsCaves] Bloques que no son cubos completos en estados de tripwire y chorus plant "
                + "(disable-tripwire-updates y disable-chorus-plant-updates activos).");
        }
        if (!NoteBlockService.noteblockUpdatesDisabled(paperGlobal)) {
            plugin.getLogger().warning("[AlexsCaves] Recomendado: block-updates.disable-noteblock-updates: true en "
                + "config/paper-global.yml. Sin eso, un note block vanilla puede terminar con el estado de un bloque del "
                + "módulo (pistones, o cambios hechos con el módulo apagado).");
        }
    }

    private void installModels() {
        List<String> keys = content.mobs().types().stream()
            .map(ModMobType::modelKey).filter(Objects::nonNull).distinct().toList();
        ModModelInstaller.install(plugin, keys);
    }

    void shutdown() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception failure) {
                plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Falló al cerrar contenido", failure);
            }
        }
        closeables.clear();
        ModCookingRecipes.unregister(plugin.getServer(), cookingRecipes);
        cookingRecipes.clear();
        worldgen.stop();
        packetRemap.shutdown();
        blocks.clearMigration();
        magneticItems.shutdown();
        magneticBlocks.shutdown();
        magnetismService.clear();
        mobs.shutdown();
        effects.shutdown();
    }

    ModEffectService effects() {
        return effects;
    }

    WorldgenService worldgen() {
        return worldgen;
    }

    NoteBlockService blocks() {
        return blocks;
    }

    PacketRemapService packetRemap() {
        return packetRemap;
    }

    ModMobService mobs() {
        return mobs;
    }

    List<String> infoLines() {
        List<String> lines = new ArrayList<>();
        lines.add("§6§lAlex's Caves §7(módulo alexs-caves)");
        lines.add("§7Configuración: " + (config.valid() ? "§aválida" : "§c" + config.errors().size() + " error(es), ver log"));
        lines.add("§7Biomas: " + (worldgen.active() ? "§agenerando en chunks nuevos" : "§eapagados"));
        for (String line : worldgen.status()) {
            lines.add("§8 - §7" + line);
        }
        lines.add("§7Mobs del módulo cargados: §f" + mobs.trackedCount());
        lines.add("§7Encantamientos del datapack cargados: §f" + enchantments.loadedCount());
        lines.add("§7Damage types del datapack: " + (damageTypes.loaded() ? "§acargados" : "§ependientes de reinicio"));
        lines.addAll(packetRemap.statusLines());
        return lines;
    }
}
