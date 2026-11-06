package dev.linqfy.bigCasares.modules.tideframe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/**
 * Puts BigCasares' screens (schema 9 JSON, editable in Tideframe's web editor) into Tideframe's screens folder, once per
 * version: Tideframe notices the new files and rebuilds its pack by itself, so nothing here rebuilds packs per player
 * or per tick. A file someone edited after BigCasares wrote it is never overwritten; the newer bundled one is left in
 * BigCasares' folder to compare. The state file remembers the hash of what BigCasares wrote last.
 */
final class TideframeScreenInstaller {
    enum Outcome { INSTALLED, UPDATED, UNCHANGED, KEPT_EDITED }

    record BundledScreen(String id, byte[] json) {
        BundledScreen {
            if (id == null || !id.matches("bigc_[a-z0-9_]{1,27}")) {
                throw new IllegalArgumentException("Invalid BigCasares screen id " + id);
            }
            json = json.clone();
        }
    }

    record Result(String id, Outcome outcome) {
    }

    private TideframeScreenInstaller() {
    }

    /**
     * {@code screens}: the engine's plugins/BigCasares/tideframe/engine/screens; {@code ownFolder}: BigCasares' folder for the state file
     * and the bundled copies of screens kept as edited.
     */
    static List<Result> install(Path screens, Path ownFolder, List<BundledScreen> bundled) throws IOException {
        return install(screens, ownFolder, bundled, () -> { });
    }

    /**
     * {@code beforeWrite} runs right before a screen file is put in place, once the file being replaced has been moved
     * aside (tests save an edit there).
     */
    static List<Result> install(Path screens, Path ownFolder, List<BundledScreen> bundled, Runnable beforeWrite) throws IOException {
        Files.createDirectories(screens);
        Files.createDirectories(ownFolder);
        Path stateFile = ownFolder.resolve("installed-screens.properties");
        Properties state = new Properties();
        if (Files.isRegularFile(stateFile)) {
            try (InputStream in = Files.newInputStream(stateFile)) {
                state.load(in);
            }
        }
        List<Result> results = new ArrayList<>();
        for (BundledScreen screen : bundled) {
            Path target = screens.resolve(screen.id() + ".json");
            restoreAside(target);
            String wanted = sha256(screen.json());
            String written = state.getProperty(screen.id());
            Outcome outcome;
            if (!Files.exists(target)) {
                outcome = put(target, screen.json(), null, beforeWrite) ? Outcome.INSTALLED : Outcome.KEPT_EDITED;
            } else {
                String current = sha256(Files.readAllBytes(target));
                if (current.equals(wanted)) {
                    outcome = Outcome.UNCHANGED;
                } else if (current.equals(written) && put(target, screen.json(), current, beforeWrite)) {
                    outcome = Outcome.UPDATED;
                } else {
                    outcome = Outcome.KEPT_EDITED;
                }
            }
            if (outcome == Outcome.KEPT_EDITED) {
                // Edited (in Tideframe's editor or by hand) after BigCasares wrote it, or saved meanwhile: the edit stays.
                write(ownFolder.resolve(screen.id() + ".bundled.json"), screen.json());
            } else {
                state.setProperty(screen.id(), wanted);
            }
            results.add(new Result(screen.id(), outcome));
        }
        Path temporary = Files.createTempFile(ownFolder, "installed-screens", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                state.store(out, "BigCasares screens written to Tideframe (SHA-256 of the last version written)");
            }
            move(temporary, stateFile);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return List.copyOf(results);
    }

    /**
     * Puts a screen file in place without ever losing a save made meanwhile; the editor takes no lock.
     * <ul>
     *   <li>A new file never replaces one created in between.</li>
     *   <li>An update first moves the current file aside (one atomic rename) and checks there that it is the
     *   {@code expected} version. An edit saved before that rename is found and put back. A save made after it
     *   creates the file again, and the new version then gives way to it.</li>
     * </ul>
     */
    private static boolean put(Path target, byte[] bytes, String expected, Runnable beforeWrite) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            Path aside = aside(target);
            if (expected != null) {
                try {
                    move(target, aside);
                } catch (IOException goneOrInUse) {
                    // Deleted meanwhile, or open in another program: left alone, and tried again on the next start.
                    return false;
                }
                if (!expected.equals(sha256(Files.readAllBytes(aside)))) {
                    restoreAside(target);
                    return false;
                }
            }
            beforeWrite.run();
            boolean placed = publish(temporary, target);
            if (expected != null) {
                // Placed: the version moved aside was BigCasares' own. Not placed: someone saved meanwhile, and that wins.
                Files.deleteIfExists(aside);
            }
            return placed;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Puts {@code source} at {@code target} only if no file is there, then removes {@code source}; false when one is. A
     * hard link refuses a taken name atomically on Windows and Unix alike. Files.move without REPLACE_EXISTING only checks
     * first and renames after on Unix, so it is the fallback where hard links do not exist (FAT, some shares).
     */
    private static boolean publish(Path source, Path target) throws IOException {
        try {
            Files.createLink(target, source);
        } catch (FileAlreadyExistsException taken) {
            return false;
        } catch (UnsupportedOperationException | FileSystemException noLinks) {
            try {
                Files.move(source, target);
                return true;
            } catch (FileAlreadyExistsException taken) {
                return false;
            }
        }
        Files.deleteIfExists(source);
        return true;
    }

    /** Where the screen being replaced waits; not a .json file, so neither Tideframe nor its editor reads it. */
    private static Path aside(Path target) {
        return target.resolveSibling(target.getFileName() + ".replacing");
    }

    /**
     * After an update stopped halfway (a crash between the two renames) or found an edit: the file moved aside goes back,
     * unless a newer one was saved in its place meanwhile.
     */
    private static void restoreAside(Path target) throws IOException {
        Path aside = aside(target);
        if (Files.exists(aside) && !publish(aside, target)) {
            Files.deleteIfExists(aside);
        }
    }

    private static void write(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
