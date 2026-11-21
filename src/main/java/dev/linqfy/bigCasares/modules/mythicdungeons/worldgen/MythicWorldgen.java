package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Punto de entrada de la generacion de las mazmorras (las estructuras {@code bigc_myth:<cultura>} del datapack). El
 * modulo lo llama una vez al prenderse: junta en el hilo principal lo que necesita de Bukkit y arma e instala el
 * datapack en un hilo aparte (es puro y el worldgen solo lo lee al arrancar), asi un {@code /bc reload} no frena el
 * servidor; si el pack instalado ya salio del mismo jar ni siquiera se arma. El resultado se escribe en el log desde
 * el hilo principal. Los cambios de worldgen recien se ven despues de reiniciar y solo en chunks nuevos.
 *
 * <p>{@code worldgen.install: false} en {@code mythic-dungeons.yml} apaga la instalacion; un pack ya instalado
 * queda en {@code <mundo>/datapacks} hasta borrarlo a mano.</p>
 */
public final class MythicWorldgen {

    static final String CONFIG_FILE = "mythic-dungeons.yml";
    static final String INSTALL_KEY = "worldgen.install";
    private static final String PREFIX = "[MythicDungeons] ";
    private static final long REPORT_PERIOD_TICKS = 20L;
    /** One worker thread: installs run in order, so a reload never writes the pack twice at once. */
    private static final ThreadPoolExecutor WORKER = worker();

    private MythicWorldgen() {
    }

    public static void install(JavaPlugin plugin, BukkitRuntimeRegistrations registrations) {
        install(plugin, registrations, List.of());
    }

    /** Igual que {@link #install(JavaPlugin, BukkitRuntimeRegistrations)} con piezas extra empaquetadas en el plugin. */
    public static void install(JavaPlugin plugin, BukkitRuntimeRegistrations registrations,
                               List<MythicTemplateSource> sources) {
        // instalar nunca tira el modulo abajo: los errores quedan en el log
        Logger logger = plugin.getLogger();
        try {
            File configFile = new File(plugin.getDataFolder(), CONFIG_FILE);
            if (!installEnabled(YamlConfiguration.loadConfiguration(configFile))) {
                Path installed = plugin.getServer().getLevelDirectory().resolve("datapacks")
                    .resolve(MythicDungeonDatapack.FOLDER);
                logger.info(PREFIX + "Instalacion del datapack de mazmorras apagada (" + INSTALL_KEY + ": false)."
                    + (Files.exists(installed) ? " El pack ya instalado sigue en " + installed
                    + "; borralo a mano y reinicia para sacar las mazmorras de los chunks nuevos." : ""));
                return;
            }
            MythicDatapackInstaller.Request request = MythicDatapackInstaller.request(plugin, sources);
            AtomicBoolean stopped = new AtomicBoolean();
            // al apagar el modulo una instalacion en curso no escribe nada ni bloquea el apagado
            registrations.ownCleanup("mythic-worldgen-build", () -> stopped.set(true));
            CompletableFuture<List<LogRecord>> job = start(WORKER, request, MythicDungeonDatapack::build,
                stopped::get);
            AtomicReference<BukkitTask> poll = new AtomicReference<>();
            poll.set(registrations.scheduleRepeating("mythic-worldgen-report", () -> {
                if (!job.isDone()) {
                    return;
                }
                replay(job.getNow(List.of()), logger);
                BukkitTask task = poll.get();
                if (task != null) {
                    task.cancel();
                }
            }, REPORT_PERIOD_TICKS, REPORT_PERIOD_TICKS));
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, PREFIX + "No se pudo preparar el datapack de mazmorras mitologicas", exception);
        }
    }

    /** {@code worldgen.install}, true when missing. */
    static boolean installEnabled(ConfigurationSection config) {
        return config.getBoolean(INSTALL_KEY, true);
    }

    /**
     * Runs the install on {@code executor} and completes with its log records, to be replayed on the main
     * thread. Touches no Bukkit API; {@code stopped} drops the install before it builds or writes.
     */
    static CompletableFuture<List<LogRecord>> start(Executor executor, MythicDatapackInstaller.Request request,
                                                    Function<List<MythicTemplateSource>, GeneratedDatapack> builder,
                                                    BooleanSupplier stopped) {
        Objects.requireNonNull(request, "request");
        return CompletableFuture.supplyAsync(() -> {
            List<LogRecord> records = new ArrayList<>();
            try {
                MythicDatapackInstaller.run(request, builder, buffering(records), Clock.systemDefaultZone(), stopped);
            } catch (RuntimeException | LinkageError failure) {
                LogRecord record = new LogRecord(Level.SEVERE,
                    PREFIX + "No se pudo generar el datapack de mazmorras mitologicas");
                record.setThrown(failure);
                records.add(record);
            }
            return List.copyOf(records);
        }, executor).exceptionally(failure -> {
            LogRecord record = new LogRecord(Level.SEVERE, PREFIX + "No se pudo generar el datapack de mazmorras mitologicas");
            record.setThrown(failure);
            return List.of(record);
        });
    }

    private static void replay(List<LogRecord> records, Logger logger) {
        for (LogRecord record : records) {
            logger.log(record.getLevel(), record.getMessage(), record.getThrown());
        }
    }

    private static Logger buffering(List<LogRecord> records) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                synchronized (records) {
                    records.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    private static ThreadPoolExecutor worker() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "BigCasares-mythic-worldgen");
                thread.setDaemon(true);
                return thread;
            });
        // el hilo se va solo cuando no hay trabajo: no queda vivo despues de apagar el plugin
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
