package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.datapack.DatapackInstaller;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Installs the mythic-dungeon datapack into {@code <level>/datapacks/bigcasares-mythic-dungeons}
 * (the main world, like the dimension module). Worldgen registries are read only at startup,
 * so a new or changed pack needs a server restart and only affects chunks generated afterwards.
 * Failures are logged and reported, never thrown.
 */
public final class MythicDatapackInstaller {

    private static final String PREFIX = "[MythicDungeons] ";
    /** Bump when the generated pack changes without a new plugin jar (it is part of the build key). */
    static final int GENERATOR_VERSION = 1;
    /** Build key and pack hash of the last install, in the plugin's {@code mythicdungeons} folder. */
    static final String KEY_FILE = "datapack-build.key";
    private static final Object LOCK = new Object();
    private static boolean observed;
    private static String startupHash;

    /**
     * @param installed      the pack is on disk and current
     * @param changed        the pack was written or replaced in this call
     * @param restartPending the running server did not load this pack version at startup
     */
    public record InstallReport(boolean installed, boolean changed, boolean restartPending, String contentHash,
                                Path directory, String error) {
    }

    private MythicDatapackInstaller() {
    }

    /**
     * Everything an install needs, gathered on the main thread so the build can run elsewhere.
     *
     * @param buildKey        identifies the generator inputs (see {@link #buildKey}); {@code null} always rebuilds
     * @param loadedAtStartup hash of the pack the running server loaded, or {@code null}
     */
    record Request(List<MythicTemplateSource> sources, Path datapacks, Path backups, Path keyFile, String buildKey,
                   String loadedAtStartup) {
        Request {
            sources = List.copyOf(sources);
            Objects.requireNonNull(datapacks, "datapacks");
            Objects.requireNonNull(backups, "backups");
            Objects.requireNonNull(keyFile, "keyFile");
        }
    }

    /** Reads the paths, the build key and the startup hash. Main thread only (Bukkit API). */
    static Request request(JavaPlugin plugin, List<MythicTemplateSource> sources) {
        Path datapacks = plugin.getServer().getLevelDirectory().resolve("datapacks");
        Path state = plugin.getDataFolder().toPath().resolve("mythicdungeons");
        String loadedAtStartup = observeStartup(
            DatapackInstaller.installedHash(datapacks.resolve(MythicDungeonDatapack.FOLDER)).orElse(null));
        return new Request(sources, datapacks, state.resolve("datapack-backups"), state.resolve(KEY_FILE),
            buildKey(plugin.getPluginMeta().getVersion(), pluginJar(plugin), sources), loadedAtStartup);
    }

    /** Builds (unless the installed pack already matches the build key) and installs, synchronously. */
    public static InstallReport install(JavaPlugin plugin, List<MythicTemplateSource> sources) {
        Logger logger = plugin.getLogger();
        try {
            return run(request(plugin, sources), MythicDungeonDatapack::build, logger, Clock.systemDefaultZone(),
                () -> false);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, PREFIX + "No se pudo generar el datapack de mazmorras mitologicas", exception);
            return new InstallReport(false, false, false, null, null, String.valueOf(exception.getMessage()));
        }
    }

    /**
     * Pure core of an install: no Bukkit API, so it may run off the main thread. Skips the build when the
     * key file says the installed pack was built from the same inputs; {@code cancelled} is checked before
     * building and before writing (returns {@code null} when it stopped there).
     */
    static InstallReport run(Request request, Function<List<MythicTemplateSource>, GeneratedDatapack> builder,
                             Logger logger, Clock clock, BooleanSupplier cancelled) {
        try {
            Path packDirectory = request.datapacks().resolve(MythicDungeonDatapack.FOLDER);
            String cached = cachedHash(request.buildKey(), request.keyFile(), packDirectory);
            if (cached != null) {
                boolean restartPending = !cached.equals(request.loadedAtStartup());
                if (restartPending) {
                    logger.warning(PREFIX + "El datapack de mazmorras mitologicas ya esta instalado pero el servidor"
                        + " arranco con otra version: hace falta reiniciar.");
                }
                return new InstallReport(true, false, restartPending, cached, packDirectory, null);
            }
            if (cancelled.getAsBoolean()) {
                return null;
            }
            GeneratedDatapack pack;
            try {
                // the normal case: every source is fine, so the pack is built once
                pack = builder.apply(request.sources());
            } catch (RuntimeException broken) {
                pack = builder.apply(usableSources(request.sources(), logger, builder));
            }
            if (cancelled.getAsBoolean()) {
                return null;
            }
            InstallReport report = install(pack, request.datapacks(), request.backups(), request.loadedAtStartup(),
                logger, clock);
            if (report.installed() && request.buildKey() != null) {
                writeKey(request.keyFile(), request.buildKey(), pack.contentHash(), logger);
            }
            return report;
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, PREFIX + "No se pudo generar el datapack de mazmorras mitologicas", exception);
            return new InstallReport(false, false, false, null, null, String.valueOf(exception.getMessage()));
        }
    }

    /**
     * Cheap identity of the generator inputs: the pack is a pure function of the plugin jar (code and
     * shipped templates) and the source list. {@code null} (always rebuild) when the jar is unknown.
     */
    static String buildKey(String pluginVersion, Path jar, List<MythicTemplateSource> sources) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return null;
        }
        try {
            StringBuilder key = new StringBuilder("generator=").append(GENERATOR_VERSION)
                .append(";version=").append(pluginVersion)
                .append(";jar=").append(Files.size(jar)).append('@').append(Files.getLastModifiedTime(jar).toMillis())
                .append(";sources=");
            for (MythicTemplateSource source : sources) {
                key.append(source.getClass().getName()).append(',');
            }
            return key.toString();
        } catch (IOException exception) {
            return null;
        }
    }

    /** Hash of the installed pack when the key file matches {@code buildKey} and the pack on disk; else null. */
    static String cachedHash(String buildKey, Path keyFile, Path packDirectory) {
        if (buildKey == null || !Files.isRegularFile(keyFile) || !Files.isRegularFile(packDirectory.resolve("pack.mcmeta"))) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(keyFile, StandardCharsets.UTF_8);
            String installed = DatapackInstaller.installedHash(packDirectory).orElse(null);
            if (lines.size() >= 2 && buildKey.equals(lines.get(0)) && lines.get(1).equals(installed)) {
                return installed;
            }
            return null;
        } catch (IOException exception) {
            return null;
        }
    }

    private static void writeKey(Path keyFile, String buildKey, String contentHash, Logger logger) {
        try {
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, buildKey + "\n" + contentHash + "\n", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // sin la clave el proximo arranque solo vuelve a generar el pack
            logger.log(Level.WARNING, PREFIX + "No se pudo guardar " + keyFile, exception);
        }
    }

    private static Path pluginJar(JavaPlugin plugin) {
        try {
            CodeSource source = plugin.getClass().getProtectionDomain().getCodeSource();
            return source == null ? null : Path.of(source.getLocation().toURI());
        } catch (URISyntaxException | RuntimeException exception) {
            return null;
        }
    }

    /** Keeps the sources whose pieces build on their own; broken ones are logged and dropped. */
    static List<MythicTemplateSource> usableSources(List<MythicTemplateSource> sources, Logger logger) {
        return usableSources(sources, logger, MythicDungeonDatapack::build);
    }

    private static List<MythicTemplateSource> usableSources(List<MythicTemplateSource> sources, Logger logger,
                                                            Function<List<MythicTemplateSource>, GeneratedDatapack> builder) {
        List<MythicTemplateSource> usable = new ArrayList<>();
        for (MythicTemplateSource source : sources) {
            try {
                builder.apply(List.of(source));
                usable.add(source);
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, PREFIX + "Fuente de plantillas descartada (" + source.getClass().getName()
                    + "): " + exception.getMessage(), exception);
            }
        }
        return usable;
    }

    /** Pure core: installs {@code pack} and logs (in Spanish) whether a restart is needed. */
    static InstallReport install(GeneratedDatapack pack, Path datapacks, Path backups, String loadedAtStartup,
                                 Logger logger, Clock clock) {
        Objects.requireNonNull(pack, "pack");
        boolean restartPending = !pack.contentHash().equals(loadedAtStartup);
        try {
            DatapackInstaller.InstallResult result = new DatapackInstaller(clock)
                .install(pack, datapacks, MythicDungeonDatapack.FOLDER, backups);
            if (result.changed()) {
                logger.warning(PREFIX + "Datapack de mazmorras mitologicas "
                    + (result.outcome() == DatapackInstaller.Outcome.INSTALLED ? "instalado" : "actualizado")
                    + " en " + result.directory() + " (" + pack.size() + " archivos). REINICIA el servidor para que"
                    + " se generen las mazmorras: el worldgen solo se lee al arrancar y solo afecta a chunks nuevos.");
            } else if (restartPending) {
                logger.warning(PREFIX + "El datapack de mazmorras mitologicas ya esta instalado pero el servidor"
                    + " arranco con otra version: hace falta reiniciar.");
            }
            return new InstallReport(true, result.changed(), restartPending, pack.contentHash(), result.directory(), null);
        } catch (IOException | RuntimeException exception) {
            logger.log(Level.SEVERE, PREFIX + "No se pudo instalar el datapack de mazmorras mitologicas en "
                + datapacks + "; el plugin sigue sin las estructuras nuevas.", exception);
            return new InstallReport(false, false, false, pack.contentHash(),
                datapacks.resolve(MythicDungeonDatapack.FOLDER), String.valueOf(exception.getMessage()));
        }
    }

    /** Hash on disk when this JVM first looked, i.e. the pack the server loaded; survives plugin reloads. */
    private static String observeStartup(String hashOnDisk) {
        synchronized (LOCK) {
            if (!observed) {
                observed = true;
                startupHash = hashOnDisk;
            }
            return startupHash;
        }
    }
}
