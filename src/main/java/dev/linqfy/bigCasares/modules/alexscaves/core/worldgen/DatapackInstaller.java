package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Copies the module's bundled datapack into a world's {@code datapacks/}
 * folder. Files are written only when their bytes differ, so a restart is
 * requested only after a real change. Files that are no longer shipped are
 * removed from the installed copy, which is owned by the module.
 */
public final class DatapackInstaller {
    public static final String FOLDER_NAME = "bigcasares-alexscaves";
    public static final String MANIFEST = "alexscaves/datapack/files.txt";
    public static final String RESOURCE_ROOT = "alexscaves/datapack/";

    private final ResourceSource resources;

    public DatapackInstaller(ResourceSource resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    /** Reads the manifest of bundled files; a missing manifest is a packaging error. */
    public List<String> bundledFiles() {
        try (InputStream input = resources.open(MANIFEST)) {
            if (input == null) {
                throw new IllegalStateException("Falta " + MANIFEST + " en el JAR: el datapack de Alex's Caves no está empaquetado");
            }
            List<String> files = new ArrayList<>();
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.contains("..") || trimmed.startsWith("/") || trimmed.contains("\\")) {
                    throw new IllegalStateException("Ruta inválida en " + MANIFEST + ": " + trimmed);
                }
                files.add(trimmed);
            }
            if (!files.contains("pack.mcmeta")) {
                throw new IllegalStateException(MANIFEST + " no incluye pack.mcmeta");
            }
            return List.copyOf(files);
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo leer " + MANIFEST, exception);
        }
    }

    /** Verifies that every listed file is packaged; run at enable so packaging mistakes fail loudly. */
    public void verifyPackaged() {
        for (String file : bundledFiles()) {
            try (InputStream input = resources.open(RESOURCE_ROOT + file)) {
                if (input == null) {
                    throw new IllegalStateException("Falta " + RESOURCE_ROOT + file + " en el JAR");
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    public InstallResult install(Path datapacksDirectory) throws IOException {
        Path target = datapacksDirectory.resolve(FOLDER_NAME).toAbsolutePath().normalize();
        List<String> files = bundledFiles();
        int written = 0;
        for (String file : files) {
            byte[] bytes;
            try (InputStream input = resources.open(RESOURCE_ROOT + file)) {
                if (input == null) {
                    throw new IllegalStateException("Falta " + RESOURCE_ROOT + file + " en el JAR");
                }
                bytes = input.readAllBytes();
            }
            Path destination = target.resolve(file).normalize();
            if (!destination.startsWith(target)) {
                throw new IllegalStateException("Ruta fuera del datapack: " + file);
            }
            if (Files.isRegularFile(destination) && MessageDigest.isEqual(digest(Files.readAllBytes(destination)), digest(bytes))) {
                continue;
            }
            Files.createDirectories(destination.getParent());
            Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.write(temporary, bytes);
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            written++;
        }
        int removed = removeStale(target, files);
        return new InstallResult(target, written, removed);
    }

    private static int removeStale(Path target, List<String> keep) throws IOException {
        if (!Files.isDirectory(target)) {
            return 0;
        }
        List<Path> stale = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(target)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                String relative = target.relativize(path).toString().replace('\\', '/');
                if (!keep.contains(relative)) {
                    stale.add(path);
                }
            });
        }
        for (Path path : stale) {
            Files.delete(path);
        }
        return stale.size();
    }

    /** Finds the level folder (the one holding {@code level.dat}) starting from a world path. */
    public static Optional<Path> findLevelRoot(Path worldPath) {
        Path current = worldPath;
        for (int depth = 0; depth < 5 && current != null; depth++) {
            if (Files.isRegularFile(current.resolve("level.dat"))) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** @param written files created or replaced; when positive the server must restart */
    public record InstallResult(Path folder, int written, int removed) {
        public boolean changed() {
            return written > 0 || removed > 0;
        }
    }

    @FunctionalInterface
    public interface ResourceSource {
        /** Returns the packaged resource or {@code null} when it does not exist. */
        InputStream open(String path) throws IOException;
    }
}
