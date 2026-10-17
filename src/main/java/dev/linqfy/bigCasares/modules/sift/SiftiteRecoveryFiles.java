package dev.linqfy.bigCasares.modules.sift;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the stacks a player is owed in {@code <directory>/<uuid>.bin}: a magic number, a version, the number
 * of entries and each entry as a length-prefixed byte array (the bytes are {@code ItemStack#serializeAsBytes},
 * which carry the data version, so they survive server updates). A save writes a temporary file and moves it
 * over the old one; an empty list deletes the file. A file that cannot be read is moved aside as
 * {@code <uuid>.bin.corrupt-<millis>} instead of being deleted.
 */
final class SiftiteRecoveryFiles implements SiftiteRecoveryBook.Store {

    private static final int MAGIC = 0x53465452;
    private static final int VERSION = 1;
    private static final int MAX_ENTRIES = 100_000;
    private static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;

    private final Path directory;
    private final Logger logger;

    SiftiteRecoveryFiles(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    @Override
    public List<byte[]> load(UUID player) throws IOException {
        Path file = file(player);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return decode(Files.readAllBytes(file));
        } catch (MalformedStoreException malformed) {
            Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            logger.log(Level.SEVERE, "[TheSift] The Siftite recovery file of " + player + " is unreadable; moved to "
                + aside.getFileName(), malformed);
            return List.of();
        }
    }

    @Override
    public void save(UUID player, List<byte[]> entries) throws IOException {
        Path file = file(player);
        if (entries.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(directory);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, encode(entries));
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    Path file(UUID player) {
        return directory.resolve(player + ".bin");
    }

    static byte[] encode(List<byte[]> entries) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(entries.size());
            for (byte[] entry : entries) {
                out.writeInt(entry.length);
                out.write(entry);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory write failed", impossible);
        }
    }

    static List<byte[]> decode(byte[] data) throws MalformedStoreException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readInt() != MAGIC) {
                throw new MalformedStoreException("not a Siftite recovery file");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new MalformedStoreException("unsupported version " + version);
            }
            int count = in.readInt();
            if (count < 0 || count > MAX_ENTRIES) {
                throw new MalformedStoreException("bad entry count " + count);
            }
            List<byte[]> entries = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int length = in.readInt();
                if (length < 0 || length > MAX_ENTRY_BYTES || length > in.available()) {
                    throw new MalformedStoreException("bad entry length " + length);
                }
                byte[] entry = new byte[length];
                in.readFully(entry);
                entries.add(entry);
            }
            if (in.available() != 0) {
                throw new MalformedStoreException("trailing bytes");
            }
            return entries;
        } catch (IOException truncated) {
            throw new MalformedStoreException("truncated file", truncated);
        }
    }

    /** A file that is not a valid recovery file. */
    static final class MalformedStoreException extends IOException {
        MalformedStoreException(String message) {
            super(message);
        }

        MalformedStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
