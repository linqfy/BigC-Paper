package dev.linqfy.bigCasares.dimension.datapack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Installs a generated datapack as a folder under the level's {@code datapacks/}
 * directory. Writing is staged outside the datapacks folder and swapped in, so
 * the server never sees a half-written pack; the previous version is backed up.
 *
 * <p>Minecraft enables newly discovered world datapacks automatically at startup.
 * Dimension, dimension-type and biome registries are only read at startup, so any
 * change reported as {@link Outcome#INSTALLED}/{@link Outcome#UPDATED} needs a
 * restart to take effect.</p>
 */
public final class DatapackInstaller {

    /** Marker file stored inside the pack folder; ignored by Minecraft. */
    public static final String HASH_FILE = "bigcasares-datapack.sha256";
    private static final int BACKUPS_KEPT = 3;
    private static final DateTimeFormatter BACKUP_NAME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    public enum Outcome {
        UNCHANGED, INSTALLED, UPDATED
    }

    public record InstallResult(Outcome outcome, String previousHash, String installedHash, Path directory) {
        public boolean changed() {
            return outcome != Outcome.UNCHANGED;
        }
    }

    private final Clock clock;

    public DatapackInstaller(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Hash recorded in an installed pack folder, if any. */
    public static Optional<String> installedHash(Path packDirectory) {
        Path marker = packDirectory.resolve(HASH_FILE);
        if (!Files.isRegularFile(marker)) {
            return Optional.empty();
        }
        try {
            String value = Files.readString(marker, StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? Optional.empty() : Optional.of(value);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    public InstallResult install(GeneratedDatapack pack, Path datapacksDirectory, String folderName, Path backupDirectory)
        throws IOException {
        Objects.requireNonNull(pack, "pack");
        if (!folderName.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Unsafe datapack folder name: " + folderName);
        }
        Path datapacks = datapacksDirectory.toAbsolutePath().normalize();
        Path target = datapacks.resolve(folderName);
        Optional<String> previous = installedHash(target);
        if (previous.isPresent() && previous.get().equals(pack.contentHash()) && Files.isRegularFile(target.resolve("pack.mcmeta"))) {
            return new InstallResult(Outcome.UNCHANGED, previous.get(), pack.contentHash(), target);
        }

        Files.createDirectories(datapacks);
        Path stagingRoot = datapacks.getParent() == null ? datapacks : datapacks.getParent();
        Path staging = stagingRoot.resolve(".bigcasares-datapack-staging-" + UUID.randomUUID());
        try {
            Files.createDirectories(staging);
            pack.writeTo(staging);
            Files.writeString(staging.resolve(HASH_FILE), pack.contentHash() + "\n", StandardCharsets.UTF_8);

            boolean existed = Files.exists(target);
            if (existed) {
                Path backup = backupDirectory.toAbsolutePath().normalize()
                    .resolve(folderName + "-" + BACKUP_NAME.format(clock.instant().atZone(clock.getZone())));
                Files.createDirectories(backup.getParent());
                move(target, backup);
                pruneBackups(backupDirectory, folderName);
            }
            move(staging, target);
            return new InstallResult(existed ? Outcome.UPDATED : Outcome.INSTALLED,
                previous.orElse(null), pack.contentHash(), target);
        } finally {
            deleteRecursively(staging);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static void pruneBackups(Path backupDirectory, String folderName) throws IOException {
        if (!Files.isDirectory(backupDirectory)) {
            return;
        }
        List<Path> backups;
        try (Stream<Path> entries = Files.list(backupDirectory)) {
            backups = entries.filter(path -> path.getFileName().toString().startsWith(folderName + "-"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        }
        for (int index = 0; index < backups.size() - BACKUPS_KEPT; index++) {
            deleteRecursively(backups.get(index));
        }
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
