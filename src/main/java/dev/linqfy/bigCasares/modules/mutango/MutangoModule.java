package dev.linqfy.bigCasares.modules.mutango;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.moderation.AbuseSignal;
import dev.linqfy.bigCasares.modules.moderation.ModerationModule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Boss Mutango (la secuela de Don Pollos). Cada Mutango es una vaca sin IA (que BetterModel esconde) con el modelo encima;
 * las animaciones idle y walk las pone BetterModel solo. Este es solo el cableado: arma las piezas y las prende y apaga.
 * <ul>
 *   <li>{@link MutangoFights}: las peleas y las entidades (cada invocacion es una pelea independiente);</li>
 *   <li>{@link MutangoBattle}: como pelean Lef, Lihuen y Lican (fase 1: embisten como cabras; fase 2: pinas y se apilan
 *   para lanzarse); cuando mueren los tres aparece Neguechen, la fase 3 ({@link MutangoPhaseThree});</li>
 *   <li>{@link MutangoCombat}: los golpes, la armadura y las muertes; {@link MutangoRewards}: victorias y premios;</li>
 *   <li>{@link MutangoShrine} y {@link MutangoRuins}: el santuario que los despierta, en las ruinas del mundo;</li>
 *   <li>{@link MutangoBedrockItems}, {@link MutangoModelInstaller}, {@link MutangoRecovery}, {@link MutangoCommand}.</li>
 * </ul>
 * La configuracion vive en {@code mutango.yml}; los datos que persisten en {@code data/mutango/}; las texturas de las botas
 * y la cruz en el pack de BigCasares (resourcepack/java/assets/mutango) y los modelos en BetterModel (bettermodel/models).
 */
public final class MutangoModule implements PluginModule {

    public static final String MODULE_ID = "mutango";
    static final String CONFIG_FILE = "mutango.yml";
    /**
     * La marca que BigC reconoce como "entidad propia de un sistema" (mob-scaling no la escala y Jeremy no la toma: solo
     * distingue el valor "jeremy"). Va en cada entidad que crea el modulo, adentro del consumer de spawn, antes de que
     * salga el CreatureSpawnEvent.
     */
    public static final String SYSTEM_MOB_KEY = "custom_entity";
    public static final String SYSTEM_MOB_VALUE = "mutango";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private MutangoBlockJournal journal;
    private MutangoFights fights;
    private MutangoShrine shrine;
    private MutangoRelics relics;
    private MutangoRewards rewards;
    private MutangoBedrockItems bedrockItems;
    private MutangoRuins ruins;

    public MutangoModule(BigCasares plugin) {
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
        registrations.ownCleanup("mutango-runtime", this::shutdown);
        MutangoTasks tasks = new MutangoTasks(registrations);

        File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!configFile.isFile()) {
            plugin.saveResource(CONFIG_FILE, false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        MutangoKeys keys = new MutangoKeys(plugin);
        MutangoPending pending = new MutangoPending(new File(plugin.getDataFolder(), "data/mutango/pendientes.yml"));
        journal = new MutangoBlockJournal(new File(plugin.getDataFolder(), "data/mutango/bloques-por-devolver.log"));

        MutangoBedrock bedrock = new MutangoBedrock();
        boolean bedrockModels = MutangoBedrock.bedrockModels(config.getString(MutangoBedrock.MODELS_SETTING, "auto"),
            plugin.getServer().getPluginManager().getPlugin(MutangoBedrock.MODEL_ENGINE_PLUGIN) != null);
        plugin.getLogger().info(bedrockModels
            ? "[Mutango] Bedrock: los jugadores de Bedrock ven los modelos 3D (GeyserModelEngine)."
            : "[Mutango] Bedrock: los jugadores de Bedrock ven las vacas de abajo (falta GeyserModelEngine).");
        bedrock.install(plugin);
        MutangoCinematic.Display display = MutangoCinematic.Display.of(bedrock::isBedrock,
            config.getString("bedrock.texto-en-pantalla", "titulo"));

        boolean modelsReady = false;
        try {
            MutangoModelInstaller.install(plugin);
            modelsReady = true;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "[Mutango] No se pudieron instalar los modelos", exception);
        }
        bedrockItems = new MutangoBedrockItems(plugin);
        bedrockItems.start(config.getBoolean("bedrock.items-personalizados", true));

        fights = new MutangoFights(plugin, keys, journal, display, bedrock, bedrockModels);
        shrine = new MutangoShrine(shrineHost(fights), new NamespacedKey(plugin, "santuario"),
            new NamespacedKey(plugin, "santuario_izq"), new NamespacedKey(plugin, "santuario_cerrado"));
        relics = new MutangoRelics(plugin, new MutangoModeration(this::moderationSink, plugin.getLogger()), tasks);
        rewards = new MutangoRewards(plugin, keys, pending, relics, tasks);
        fights.wire(shrine, rewards, modelsReady);

        registrations.registerListener("mutango-fights", fights);
        registrations.registerListener("mutango-rewards", rewards);
        registrations.registerListener("mutango-combat", new MutangoCombat(plugin, keys, fights, rewards, shrine, tasks));
        MutangoRecovery recovery = new MutangoRecovery(plugin, journal, tasks);
        registrations.registerListener("mutango-recovery", recovery);
        registrations.registerListener("mutango-relics", relics);

        PluginCommand pluginCommand = plugin.getCommand("mutango");
        if (pluginCommand == null) {
            throw new IllegalStateException("Required command is not declared: mutango");
        }
        MutangoCommand command = new MutangoCommand(fights, shrine, relics, () -> ruins);
        registrations.bindCommand("mutango-command", pluginCommand, command, command);

        fights.attachAll();
        tasks.repeating("tick", this::tick, 20L, 1L);
        try {
            ruins = MutangoRuins.create(plugin, tasks);
            ruins.setShrinePlacer(shrine::place);
            registrations.registerListener("mutango-ruins", ruins);
            ruins.start();
        } catch (IOException | RuntimeException exception) {
            // las ruinas son opcionales: un archivo roto no puede tirar abajo el resto de BigCasares
            ruins = null;
            plugin.getLogger().warning("[Mutango] Ruinas desactivadas: " + exception);
        }
        recovery.restoreLeftoverBlocks();
        plugin.getServer().getOnlinePlayers().forEach(rewards::claimPending);
        plugin.getLogger().info("[Mutango] Module enabled. Usa /mutango para spawnear los tres.");
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
        if (ruins != null) {
            ruins.shutdown();
            ruins = null;
        }
        if (bedrockItems != null) {
            bedrockItems.stop();
            bedrockItems = null;
        }
        if (fights != null) {
            fights.shutdown();
            fights = null;
        }
        if (relics != null) {
            relics.shutdown();
            relics = null;
        rewards = null;
        }
        flushJournal();
        journal = null;
        shrine = null;
    }

    /** A donde van los intentos de cheat de las botas: el modulo de moderacion de BigC (si esta prendido). */
    private Consumer<AbuseSignal> moderationSink() {
        ModerationModule moderation = plugin.getModerationModule();
        return moderation == null ? null : moderation::acceptSignal;
    }

    /** Cada tick: las peleas, los premios (botas), el santuario y el diario de bloques (cada medio segundo). */
    private void tick() {
        if (fights == null) {
            return;
        }
        fights.tick();
        relics.tick();
        shrine.tick();
        int now = plugin.getServer().getCurrentTick();
        if (now % 10 == 0) {
            flushJournal();
        }
        if (now % 100 == 0) {
            rewards.retryOnline(); // a quien le faltaba lugar en el inventario
        }
    }

    private void flushJournal() {
        if (journal == null) {
            return;
        }
        try {
            journal.flush();
        } catch (IllegalStateException failed) {
            plugin.getLogger().warning(failed.getMessage());
        }
    }

    /** Lo que el santuario necesita: crear sus piezas y empezar una pelea nueva cuando despierta. */
    private MutangoShrine.Host shrineHost(MutangoFights fights) {
        return new MutangoShrine.Host() {
            @Override
            public Mob spawnPart(String model, Location location) {
                return fights.spawnShrinePart(model, location);
            }

            @Override
            public UUID beginAwakening(Player summoner, Location closed) {
                // cada despertar es una pelea nueva: con la dificultad de quien corrio el cubo
                MutangoFight fight = fights.beginFight(summoner);
                fight.awakening = true;
                fight.cinematic.awakening(closed);
                return fight.id;
            }

            @Override
            public void awaken(UUID fightId, List<Location> spots) {
                MutangoFight fight = fights.get(fightId);
                if (fight == null) {
                    return; // la sacaron mientras despertaba (/mutango quitar)
                }
                fight.awakening = false;
                fights.spawnTrio(fight, spots);
                fight.battle.setPhase(1, fights.aliveOf(fight));
            }

            @Override
            public boolean isWaiting(UUID fightId) {
                MutangoFight fight = fights.get(fightId);
                return fight != null && fight.awakening;
            }

            @Override
            public void cancelAwakening(UUID fightId) {
                MutangoFight fight = fights.get(fightId);
                if (fight != null) {
                    fight.awakening = false;
                }
            }
        };
    }
}
