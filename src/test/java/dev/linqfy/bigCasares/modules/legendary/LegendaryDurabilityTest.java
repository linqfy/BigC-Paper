package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegendaryDurabilityTest {
    @Test void windowsDoesNotAttemptUnsupportedDirectoryChannels() throws Exception {
        LegendaryDurability.forceDirectory(Path.of("."), "Windows 11", directory -> {
            throw new IOException("Windows directory handles are unsupported by FileChannel");
        });
    }
    @Test void unixStillForcesDirectoryAndDoesNotHideRealIoFailures() throws Exception {
        var calls = new AtomicInteger();
        LegendaryDurability.forceDirectory(Path.of("."), "Linux", directory -> calls.incrementAndGet());
        assertEquals(1, calls.get());
        assertThrows(IOException.class, () -> LegendaryDurability.forceDirectory(Path.of("."), "Linux", directory -> {
            throw new IOException("disk failed");
        }));
    }
}
