package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.dimension.datapack.DatapackInstaller;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * Cai chet: tres lugares encadenados. Este es solo el cableado: arma las piezas y las prende y apaga.
 * <ul>
 *   <li>el desierto ({@code caichet:cai_chet}, una dimension del datapack de {@link CaiChetDatapack}): se entra tocando
 *   el Bloque de Dimension ({@link PortalListener}); ahi pasa el tren ({@link TrainDirector}) con la gente de tu team
 *   arriba ({@link TrainPassengers}), los liquidos suben ({@link LiquidRiser}), los items sueltos flotan
 *   ({@link ItemFloater}) y andan los dobles ({@link Doppelganger}), que dan el Reloj de los Sueños;</li>
 *   <li>el hotel ({@link HotelWorld}): se llega con el totem Despertar ({@link DespertarListener}); tiene luces que
 *   titilan ({@link FlickerLights}), figuras que te miran ({@link Watchers}), cofres y puertas con llave
 *   ({@link HotelLoot}, {@link KeyDoors});</li>
 *   <li>el Sueño ({@link DreamWorld}): se llega por una puerta con llave; tiene sus criaturas ({@link DreamEntities}),
 *   su filtro ({@link DreamFilter}) y el ojo con alas ({@link EyeBossManager}), que al vencerlo te despierta.</li>
 *   <li>el pozo ({@link PitStructure}): una ruina rara en el mundo principal cuyo fondo negro lleva al desierto.</li>
 * </ul>
 * Las texturas y modelos viven en el pack de BigCasares (resourcepack/java/assets/caichet y demon_train).
 */
public final class CaiChetModule implements PluginModule {

    public static final String MODULE_ID = "caichet";
    /** Id de la dimension del desierto, el mismo que el archivo data/caichet/dimension/cai_chet.json del datapack. */
    public static final NamespacedKey DIMENSION = new NamespacedKey("caichet", "cai_chet");

    /**
     * Los recursos que el modulo registra en su scope. El scope rechaza un id repetido (y eso tira abajo el encendido),
     * asi que todos salen de aca y un test comprueba que no se repitan.
     */
    enum Resource {
        RUNTIME("caichet-runtime"),
        PORTALS("caichet-portals"),
        LIQUIDS("caichet-liquids"),
        DOUBLES("caichet-doubles"),
        ARMOR("caichet-armor"),
        SPAWNS("caichet-spawns"),
        STANDS("caichet-stands"),
        DESPERTAR("caichet-despertar"),
        DESPERTAR_RECIPE("caichet-despertar-recipe"),
        DREAM_FILTER("caichet-dream-filter"),
        DREAM_PROPS("caichet-dream-props"),
        EYE("caichet-eye"),
        LOOT("caichet-loot"),
        LIGHTS("caichet-lights"),
        PIT("caichet-pit"),
        COMMAND("caichet-command");

        final String id;

        Resource(String id) {
            this.id = id;
        }
    }

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private boolean running;
    private TrainDirector director;
    private HotelLoot hotelLoot;
    private Watchers watchers;
    private FlickerLights flicker;
    private KeyDoors keyDoors;
    private DreamProps dreamProps;
    private DreamEntities dreamEntities;
    private EyeBossManager eyeBosses;
    private PitStructure pits;

    public CaiChetModule(BigCasares plugin) {
        this.plugin = plugin;
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
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.ownCleanup(Resource.RUNTIME.id, this::shutdown);
        CaiChetTasks tasks = new CaiChetTasks(registrations);
        CaiChetRuntime.install(plugin, tasks);
        running = true;

        installDatapack();

        // el desierto
        director = new TrainDirector();
        registrations.registerListener(Resource.PORTALS.id, new PortalListener());
        registrations.registerListener(Resource.LIQUIDS.id, new LiquidListener());
        registrations.registerListener(Resource.DOUBLES.id, new ChickenListener());
        registrations.registerListener(Resource.ARMOR.id, new ArmorSnapshot());
        registrations.registerListener(Resource.SPAWNS.id, new SpawnGuard());
        registrations.registerListener(Resource.STANDS.id, new StandGuard());
        tasks.repeating("train", director, 20L, 1L);
        tasks.repeating("items", new ItemFloater(), 20L, 1L);
        tasks.repeating("doubles", new ChickenSpawner(), 20L, 1L);
        registrations.registerRecipe(Resource.DESPERTAR_RECIPE.id, DreamItems.RECIPE_KEY, DreamItems.recipe());

        // el hotel y el Sueño
        HotelWorld.load(plugin);
        DreamWorld.load(plugin);
        registrations.registerListener(Resource.DREAM_FILTER.id, new DreamFilter());
        dreamProps = registrations.registerListener(Resource.DREAM_PROPS.id, new DreamProps());
        dreamProps.loadExisting();
        eyeBosses = registrations.registerListener(Resource.EYE.id, new EyeBossManager(plugin));
        tasks.repeating("eye", eyeBosses, 40L, 1L);
        dreamEntities = new DreamEntities();
        tasks.repeating("dream", dreamEntities, 40L, 1L);
        registrations.registerListener(Resource.DESPERTAR.id, new DespertarListener());
        hotelLoot = registrations.registerListener(Resource.LOOT.id, new HotelLoot());
        watchers = new Watchers();
        hotelLoot.setWatchers(watchers);
        keyDoors = new KeyDoors(plugin.getLogger());
        hotelLoot.setDoors(keyDoors);
        tasks.repeating("doors", keyDoors, 20L, 1L);
        flicker = registrations.registerListener(Resource.LIGHTS.id, new FlickerLights());
        flicker.loadExisting();
        tasks.repeating("lights", flicker, 40L, FlickerLights.PERIOD_TICKS);
        tasks.repeating("watchers", watchers, 40L, 1L);

        // el pozo: aparece en chunks nuevos y su fondo negro lleva al desierto
        pits = registrations.registerListener(Resource.PIT.id, new PitStructure(PitZoneRules.nexus(plugin)));
        tasks.repeating("pit-step", pits::step, 40L, PitStructure.STEP_PERIOD_TICKS);

        PluginCommand pluginCommand = plugin.getCommand("caichet");
        if (pluginCommand == null) {
            throw new IllegalStateException("Required command is not declared: caichet");
        }
        CaiChetCommand command = new CaiChetCommand(director, pits);
        registrations.bindCommand(Resource.COMMAND.id, pluginCommand, command, command);

        if (Bukkit.getWorld(DIMENSION) == null) {
            plugin.getLogger().warning("[CaiChet] La dimension " + DIMENSION + " todavia no esta cargada: reinicia el"
                + " servidor para que Minecraft lea el datapack.");
        }
        plugin.getLogger().info("[CaiChet] Module enabled.");
    }

    private void installDatapack() {
        try {
            Path backups = plugin.getDataFolder().toPath().resolve("caichet/datapack-backups");
            DatapackInstaller.InstallResult result = CaiChetDatapack.install(plugin.getClass().getClassLoader(),
                plugin.getServer().getLevelDirectory(), backups);
            if (result.changed()) {
                plugin.getLogger().warning("[CaiChet] Datapack " + result.outcome().name().toLowerCase() + " en "
                    + result.directory() + ". Reinicia el servidor: las dimensiones y los biomas solo se leen al"
                    + " arrancar.");
            }
        } catch (IOException | RuntimeException exception) {
            // sin datapack no hay desierto ni colores del Sueño, pero el resto del servidor sigue
            plugin.getLogger().log(Level.WARNING, "[CaiChet] No se pudo instalar el datapack", exception);
        }
    }

    @Override
    public void onDisable() {
        shutdown();
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }

    /** Idempotente: la corre onDisable y tambien la limpieza del scope. */
    private void shutdown() {
        if (!running) {
            return;
        }
        running = false;
        CaiChetRuntime.stopping();
        if (director != null) {
            director.shutdown();
        }
        Doppelganger.removeAll();
        if (watchers != null) {
            watchers.removeAll();
        }
        if (flicker != null) {
            flicker.restoreAll();
        }
        if (keyDoors != null) {
            keyDoors.closeAll();
        }
        if (dreamProps != null) {
            dreamProps.removeAll();
        }
        if (dreamEntities != null) {
            dreamEntities.removeAll();
        }
        if (eyeBosses != null) {
            eyeBosses.removeAll();
        }
        CaiChetBedrockEntities.releaseAll();
        if (hotelLoot != null) {
            hotelLoot.settleOwedFood();
        }
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            DreamFilter.unequip(online);
        }
        if (pits != null) {
            pits.clear();
        }
        LiquidRiser.reset();
        TeamLookup.clear();
        CaiChetSounds.clear();
        director = null;
        watchers = null;
        flicker = null;
        keyDoors = null;
        dreamProps = null;
        dreamEntities = null;
        eyeBosses = null;
        hotelLoot = null;
        pits = null;
        CaiChetRuntime.clear();
    }
}
