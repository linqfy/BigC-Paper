package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.datapack.DatapackBuilder;
import dev.linqfy.bigCasares.dimension.datapack.GeneratedDatapack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicDatapackInstallerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void installsOnceAndAsksForARestart(@TempDir Path root) {
        GeneratedDatapack pack = MythicDungeonDatapack.build();
        List<String> messages = new ArrayList<>();
        Logger logger = capturing(messages);
        Path datapacks = root.resolve("world/datapacks");

        MythicDatapackInstaller.InstallReport first = MythicDatapackInstaller.install(pack, datapacks, root.resolve("backups"),
            null, logger, CLOCK);
        assertTrue(first.installed() && first.changed() && first.restartPending());
        assertTrue(Files.isRegularFile(datapacks.resolve(MythicDungeonDatapack.FOLDER).resolve("pack.mcmeta")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("REINICIA")));

        MythicDatapackInstaller.InstallReport second = MythicDatapackInstaller.install(pack, datapacks, root.resolve("backups"),
            pack.contentHash(), logger, CLOCK);
        assertTrue(second.installed());
        assertFalse(second.changed());
        assertFalse(second.restartPending());
    }

    @Test
    void ioFailureIsReportedNotThrown(@TempDir Path root) throws Exception {
        Path blocked = root.resolve("datapacks");
        Files.writeString(blocked, "not a directory");
        List<String> messages = new ArrayList<>();
        MythicDatapackInstaller.InstallReport report = MythicDatapackInstaller.install(MythicDungeonDatapack.build(), blocked,
            root.resolve("backups"), null, capturing(messages), CLOCK);
        assertFalse(report.installed());
        assertNotNull(report.error());
        assertTrue(messages.stream().anyMatch(message -> message.contains("No se pudo instalar")));
    }

    @Test
    void unchangedBuildKeySkipsTheBuild(@TempDir Path root) throws Exception {
        Path jar = Files.writeString(root.resolve("plugin.jar"), "jar");
        String key = MythicDatapackInstaller.buildKey("1.0", jar, List.of());
        assertNotNull(key);
        MythicDatapackInstaller.Request request = request(root, key);
        AtomicInteger builds = new AtomicInteger();
        Function<List<MythicTemplateSource>, GeneratedDatapack> builder = sources -> {
            builds.incrementAndGet();
            return tinyPack("one");
        };
        Logger logger = capturing(new ArrayList<>());

        assertTrue(MythicDatapackInstaller.run(request, builder, logger, CLOCK, () -> false).changed());
        assertEquals(1, builds.get());
        MythicDatapackInstaller.InstallReport cached = MythicDatapackInstaller.run(request, builder, logger, CLOCK, () -> false);
        assertEquals(1, builds.get(), "an unchanged key must not build");
        assertTrue(cached.installed());
        assertFalse(cached.changed());
        assertEquals(tinyPack("one").contentHash(), cached.contentHash());

        // a new jar (or a pack removed by hand) builds again
        Files.writeString(jar, "new jar");
        String newKey = MythicDatapackInstaller.buildKey("1.0", jar, List.of());
        assertNotEquals(key, newKey);
        MythicDatapackInstaller.run(request(root, newKey), builder, logger, CLOCK, () -> false);
        assertEquals(2, builds.get());
        MythicDatapackInstaller.run(request(root, newKey), builder, logger, CLOCK, () -> false);
        assertEquals(2, builds.get());
        deleteTree(root.resolve("world/datapacks").resolve(MythicDungeonDatapack.FOLDER));
        MythicDatapackInstaller.run(request(root, newKey), builder, logger, CLOCK, () -> false);
        assertEquals(3, builds.get());
        // without a known jar there is no key and every install builds
        assertNull(MythicDatapackInstaller.buildKey("1.0", root.resolve("missing.jar"), List.of()));
    }

    @Test
    void asyncInstallRunsOnTheExecutorAndStopsWhenTheModuleStops(@TempDir Path root) throws Exception {
        List<Runnable> queued = new ArrayList<>();
        AtomicReference<Thread> buildThread = new AtomicReference<>();
        Function<List<MythicTemplateSource>, GeneratedDatapack> builder = sources -> {
            buildThread.set(Thread.currentThread());
            return tinyPack("async");
        };
        CompletableFuture<List<LogRecord>> job = MythicWorldgen.start(queued::add, request(root, null), builder,
            () -> false);
        assertFalse(job.isDone());
        assertNull(buildThread.get(), "nothing runs on the calling thread");
        assertEquals(1, queued.size());
        Thread worker = new Thread(queued.getFirst());
        worker.start();
        worker.join();
        assertTrue(job.isDone());
        assertSame(worker, buildThread.get());
        assertTrue(job.get().stream().anyMatch(record -> record.getMessage().contains("REINICIA")));
        assertTrue(Files.isRegularFile(root.resolve("world/datapacks").resolve(MythicDungeonDatapack.FOLDER)
            .resolve("pack.mcmeta")));

        // stopped before it ran: no build, nothing written
        Path other = root.resolve("other");
        AtomicInteger builds = new AtomicInteger();
        CompletableFuture<List<LogRecord>> stopped = MythicWorldgen.start(Runnable::run, request(other, null),
            sources -> {
                builds.incrementAndGet();
                return tinyPack("stopped");
            }, () -> true);
        assertTrue(stopped.get().isEmpty());
        assertEquals(0, builds.get());
        assertFalse(Files.exists(other.resolve("world/datapacks")));
    }

    @Test
    void worldgenInstallCanBeTurnedOff() throws Exception {
        assertTrue(MythicWorldgen.installEnabled(new YamlConfiguration()));
        YamlConfiguration off = new YamlConfiguration();
        off.loadFromString("worldgen:\n  install: false\n");
        assertFalse(MythicWorldgen.installEnabled(off));
    }

    private static MythicDatapackInstaller.Request request(Path root, String key) {
        return new MythicDatapackInstaller.Request(List.of(), root.resolve("world/datapacks"), root.resolve("backups"),
            root.resolve("state").resolve(MythicDatapackInstaller.KEY_FILE), key, null);
    }

    private static GeneratedDatapack tinyPack(String content) {
        return new DatapackBuilder().file("data/test/" + content + ".txt", content.getBytes(StandardCharsets.UTF_8))
            .build("test");
    }

    private static Logger capturing(List<String> messages) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
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

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
