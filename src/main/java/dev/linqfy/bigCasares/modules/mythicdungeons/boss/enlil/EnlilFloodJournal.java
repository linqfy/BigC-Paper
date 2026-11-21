package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The flood journal on disk (no server types). Write-ahead: before a batch of water sources is placed, the journal is
 * rewritten (temp file + atomic move) to list every cell placed so far plus that batch, so it never lists a cell the
 * flood did not reach. A clean {@link #markRestored() restore} does not delete it: the dried blocks may still be only
 * in memory, so it is renamed to {@code enlil-flood-<boss>-<n>.restored} and {@link #recover} deletes it on a later
 * start, once the chunks loaded from disk show none of its cells is water anymore.
 */
final class EnlilFloodJournal {

    static final String PREFIX = "enlil-flood-";
    static final String ACTIVE = ".txt";
    static final String RESTORED = ".restored";
    private static final int UUID_LENGTH = 36;

    /** One journaled cell. */
    record Cell(String world, int x, int y, int z) {

        String line() {
            return world + ' ' + x + ' ' + y + ' ' + z;
        }

        /** {@code null} for a damaged line. */
        static Cell parse(String line) {
            String[] parts = line.trim().split(" ");
            if (parts.length != 4) {
                return null;
            }
            try {
                return new Cell(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
            } catch (NumberFormatException damaged) {
                return null;
            }
        }
    }

    /** What the world holds at a journaled cell. */
    enum State {
        /** The world is not loaded: the journal is kept for a later start. */
        NO_WORLD,
        WATER,
        OTHER
    }

    /** The world as the recovery sees it. */
    interface Cells {
        State state(Cell cell);

        /** Turns the water of the cell into air (with physics, so leaked water dries up). */
        void dry(Cell cell);
    }

    /** What one {@link #recover} pass did. */
    record Recovery(int dried, int deleted, int kept) {
    }

    private final Path dir;
    private final UUID bossId;
    private final StringBuilder written = new StringBuilder();
    private boolean any;

    EnlilFloodJournal(Path dir, UUID bossId) {
        this.dir = dir;
        this.bossId = bossId;
    }

    Path path() {
        return dir.resolve(PREFIX + bossId + ACTIVE);
    }

    /**
     * Rewrites the journal with the cells written so far plus {@code cells}, atomically. Returns {@code false} (and
     * keeps the previous content) when it could not be written: the caller must not place those cells.
     */
    boolean append(List<Cell> cells) {
        if (cells.isEmpty()) {
            return true;
        }
        StringBuilder text = new StringBuilder(written.length() + cells.size() * 24).append(written);
        for (Cell cell : cells) {
            text.append(cell.line()).append('\n');
        }
        try {
            writeAtomically(path(), text.toString());
        } catch (IOException failure) {
            return false;
        }
        written.setLength(0);
        written.append(text);
        any = true;
        return true;
    }

    /**
     * Renames the journal to a restored one (never deletes it). Returns the new path, or {@code null} when there was
     * nothing written; throws when it could not be renamed (the active journal is then recovered on a later start).
     */
    Path markRestored() throws IOException {
        if (!any || !Files.exists(path())) {
            return null;
        }
        Path target = restoredPath(dir, bossId);
        Files.move(path(), target, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    private static Path restoredPath(Path dir, UUID bossId) {
        for (int n = 0; ; n++) {
            Path candidate = dir.resolve(PREFIX + bossId + "-" + n + RESTORED);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
    }

    static void writeAtomically(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Dries the journals of floods that are not live: every listed cell that is still water. A journal is deleted
     * only when none of its cells was water and it was not dried or restored in this run ({@code fresh}): until the
     * chunks are saved, the dried cells may only be air in memory. One that dried something becomes a restored
     * journal and is added to {@code fresh}; one whose world is not loaded is kept as is.
     */
    static Recovery recover(Path dir, Set<UUID> live, Set<Path> fresh, Cells cells) throws IOException {
        int dried = 0;
        int deleted = 0;
        int kept = 0;
        if (!Files.isDirectory(dir)) {
            return new Recovery(0, 0, 0);
        }
        List<Path> journals = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, PREFIX + "*")) {
            for (Path path : files) {
                String name = path.getFileName().toString();
                if ((name.endsWith(ACTIVE) || name.endsWith(RESTORED)) && bossOf(name) != null) {
                    journals.add(path);
                }
            }
        }
        for (Path path : journals) {
            UUID boss = bossOf(path.getFileName().toString());
            if (live.contains(boss)) {
                continue;
            }
            int removed = 0;
            boolean complete = true;
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                Cell cell = Cell.parse(line);
                if (cell == null) {
                    continue;
                }
                switch (cells.state(cell)) {
                    case NO_WORLD -> complete = false;
                    case WATER -> {
                        cells.dry(cell);
                        removed++;
                    }
                    default -> {
                    }
                }
            }
            dried += removed;
            if (complete && removed == 0 && !fresh.contains(path)) {
                Files.deleteIfExists(path);
                deleted++;
                continue;
            }
            kept++;
            if (removed > 0) {
                Path restored = path;
                if (path.getFileName().toString().endsWith(ACTIVE)) {
                    restored = restoredPath(dir, boss);
                    Files.move(path, restored, StandardCopyOption.ATOMIC_MOVE);
                }
                fresh.add(restored);
            }
        }
        return new Recovery(dried, deleted, kept);
    }

    /** The boss of a journal file name, or {@code null} when it is not one. */
    static UUID bossOf(String name) {
        if (!name.startsWith(PREFIX) || name.length() < PREFIX.length() + UUID_LENGTH) {
            return null;
        }
        try {
            return UUID.fromString(name.substring(PREFIX.length(), PREFIX.length() + UUID_LENGTH)
                .toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException notOne) {
            return null;
        }
    }
}
