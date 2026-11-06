package dev.linqfy.bigCasares.modules.tideframe.engine.edit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScreenWatcherTest {
    @Test void reportsASettledJsonChangeOnceAndIgnoresAlreadyBuiltState(@TempDir Path folder) throws Exception {
        var built = new java.util.concurrent.atomic.AtomicReference<>(ScreenWatcher.fingerprint(folder));
        var changes = new LinkedBlockingQueue<String>();
        try (var watcher = new ScreenWatcher(folder, 200, built::get, changes::add)) {
            Files.writeString(folder.resolve("shop.json"), "{}");
            Files.writeString(folder.resolve("shop.json"), "{\"a\":1}"); // a second write inside the quiet period
            String reported = changes.poll(10, TimeUnit.SECONDS);
            assertNotNull(reported, "change reported");
            assertEquals(ScreenWatcher.fingerprint(folder), reported);
            assertNull(changes.poll(800, TimeUnit.MILLISECONDS), "one report per settled change");
            // After the rebuild the fingerprint matches: touching unrelated files does nothing.
            built.set(reported);
            Files.writeString(folder.resolve("notes.txt"), "ignored");
            assertNull(changes.poll(800, TimeUnit.MILLISECONDS));
        }
    }

    @Test void fingerprintTracksJsonFilesOnly(@TempDir Path folder) throws Exception {
        String empty = ScreenWatcher.fingerprint(folder);
        Files.writeString(folder.resolve("readme.txt"), "x");
        assertEquals(empty, ScreenWatcher.fingerprint(folder));
        Files.writeString(folder.resolve("menu.json"), "{}");
        assertNotEquals(empty, ScreenWatcher.fingerprint(folder));
        assertEquals(ScreenWatcher.fingerprint(folder), ScreenWatcher.fingerprint(folder));
    }
}
