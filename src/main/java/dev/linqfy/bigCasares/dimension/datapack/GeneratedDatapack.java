package dev.linqfy.bigCasares.dimension.datapack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * An immutable, path-sorted datapack. Its {@link #contentHash()} identifies the
 * exact bytes and is used to decide whether an installed pack is current.
 */
public final class GeneratedDatapack {

    /** Data pack format of Minecraft 26.2 ({@code data_pack_version} 107.1). */
    public static final int PACK_FORMAT_MAJOR = 107;
    public static final int PACK_FORMAT_MINOR = 1;

    private final SortedMap<String, byte[]> files;
    private final String contentHash;

    GeneratedDatapack(SortedMap<String, byte[]> files) {
        TreeMap<String, byte[]> copy = new TreeMap<>();
        files.forEach((path, bytes) -> copy.put(path, bytes.clone()));
        this.files = Collections.unmodifiableSortedMap(copy);
        this.contentHash = hash(copy);
    }

    public Set<String> paths() {
        return files.keySet();
    }

    public byte[] file(String path) {
        byte[] bytes = files.get(path);
        return bytes == null ? null : bytes.clone();
    }

    public String text(String path) {
        byte[] bytes = files.get(path);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    public int size() {
        return files.size();
    }

    public String contentHash() {
        return contentHash;
    }

    /** Writes the pack into an empty directory. */
    public void writeTo(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Path target = directory.resolve(entry.getKey()).normalize();
            if (!target.startsWith(directory.normalize())) {
                throw new IOException("Datapack path escapes target: " + entry.getKey());
            }
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue());
        }
    }

    /** Reproducible ZIP (fixed timestamps, sorted entries). */
    public void writeZip(OutputStream output) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTime(0L);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
    }

    private static String hash(SortedMap<String, byte[]> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(entry.getValue());
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
