package dev.linqfy.bigCasares.modules.tideframe.engine.edit;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Watches the screens folder and reports a settled change: events are debounced, and the callback runs only
 * when the folder's fingerprint (names, sizes, modification times) differs from the last built one, so the
 * plugin's own writes during a reload never trigger another reload.
 */
public final class ScreenWatcher implements AutoCloseable {
    private final Path folder;
    private final String glob;
    private final PathMatcher matcher;
    private final long debounceMillis;
    private final Supplier<String> currentFingerprint;
    private final Supplier<String> builtFingerprint;
    private final Consumer<String> onChange;
    private final WatchService service;
    private final Thread thread;
    private volatile boolean running = true;

    public ScreenWatcher(Path folder, long debounceMillis, Supplier<String> builtFingerprint, Consumer<String> onChange) throws IOException {
        this(folder, "*.json", debounceMillis, builtFingerprint, onChange);
    }
    /** Watch only the files matching {@code glob} (for example "config.yml"). */
    public ScreenWatcher(Path folder, String glob, long debounceMillis, Supplier<String> builtFingerprint, Consumer<String> onChange) throws IOException {
        this(folder, glob, debounceMillis, null, builtFingerprint, onChange);
    }
    /**
     * Watch the files matching {@code glob}, but compare {@code currentFingerprint} (for example the digest of one
     * section of a file) instead of the files' names, sizes and times.
     */
    public ScreenWatcher(Path folder, String glob, long debounceMillis, Supplier<String> currentFingerprint, Supplier<String> builtFingerprint,
                         Consumer<String> onChange) throws IOException {
        this.folder = folder.toAbsolutePath().normalize();
        this.glob = glob;
        this.currentFingerprint = currentFingerprint != null ? currentFingerprint : () -> fingerprint(this.folder, glob);
        this.matcher = this.folder.getFileSystem().getPathMatcher("glob:" + glob);
        this.debounceMillis = debounceMillis;
        this.builtFingerprint = Objects.requireNonNull(builtFingerprint);
        this.onChange = Objects.requireNonNull(onChange);
        Files.createDirectories(this.folder);
        service = this.folder.getFileSystem().newWatchService();
        this.folder.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
        thread = new Thread(this::run, "Tideframe-screen-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stable digest of every *.json in the folder (name, size, mtime). Empty folder gives a fixed value. */
    public static String fingerprint(Path folder) { return fingerprint(folder, "*.json"); }
    public static String fingerprint(Path folder, String glob) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            if (Files.isDirectory(folder)) {
                var names = new java.util.TreeSet<String>();
                try (var entries = Files.newDirectoryStream(folder, glob)) { for (var entry : entries) names.add(entry.getFileName().toString()); }
                for (String name : names) {
                    var file = folder.resolve(name);
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    digest.update((name + ':' + Files.size(file) + ':' + Files.getLastModifiedTime(file).toMillis() + '\n').getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | java.security.NoSuchAlgorithmException unreadable) {
            return "unreadable:" + unreadable.getMessage();
        }
    }

    private boolean relevant(WatchKey key) {
        boolean relevant = false;
        for (var event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) { relevant = true; continue; }
            if (event.context() instanceof Path name && matcher.matches(name)) relevant = true;
        }
        key.reset();
        return relevant;
    }

    private void run() {
        try {
            while (running) {
                WatchKey key = service.take();
                if (!relevant(key)) continue;
                // Wait until the folder has been quiet for the debounce period (editors save in several writes).
                while (running) {
                    WatchKey more = service.poll(debounceMillis, TimeUnit.MILLISECONDS);
                    if (more == null) break;
                    relevant(more);
                }
                String now = currentFingerprint.get();
                if (running && !now.equals(builtFingerprint.get())) onChange.accept(now);
            }
        } catch (InterruptedException | ClosedWatchServiceException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    @Override public void close() {
        running = false;
        try { service.close(); } catch (IOException ignored) { }
        thread.interrupt();
    }
}
