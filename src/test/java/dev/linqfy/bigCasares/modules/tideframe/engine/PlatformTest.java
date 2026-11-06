package dev.linqfy.bigCasares.modules.tideframe.engine;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PlatformTest {
    @Test void acceptsOnlyRequestedReleaseLines() {
        assertTrue(PlatformProbe.supports("1.21"));
        assertTrue(PlatformProbe.supports("1.21.11"));
        assertTrue(PlatformProbe.supports("26.1.2"));
        assertTrue(PlatformProbe.supports("26.3"));
        assertFalse(PlatformProbe.supports("1.21.12"));
        assertFalse(PlatformProbe.supports("26.4"));
        assertFalse(PlatformProbe.supports("26.3-pre1"));
        assertFalse(PlatformProbe.supports("junk"));
    }
    @Test void mapsJavaRequirementsWithoutAssumingOldVersionScheme() {
        assertEquals(21, PlatformProbe.minimumJava("1.21.11"));
        assertEquals(25, PlatformProbe.minimumJava("26.2"));
        assertThrows(IllegalArgumentException.class, () -> PlatformProbe.minimumJava("1.20"));
    }
    @Test void invalidSettingsCannotCreateUnsafeOrUnboundedWork() {
        assertThrows(IllegalArgumentException.class, () -> new PluginSettings("127.0.0.1",0,"http://localhost/packs/",false,2.5,35,.08,24));
        assertThrows(IllegalArgumentException.class, () -> new PluginSettings("127.0.0.1",8136,"file:/tmp/",false,2.5,35,.08,24));
        assertThrows(IllegalArgumentException.class, () -> new PluginSettings("127.0.0.1",8136,"http://localhost/packs/",false,Double.NaN,35,.08,24));
        assertEquals(128, PluginSettings.defaults().elements());
        assertEquals(128,new PluginSettings("127.0.0.1",8136,"http://localhost/packs/",false,2.5,35,.08,128).elements());
        assertThrows(IllegalArgumentException.class,()->new PluginSettings("127.0.0.1",8136,"http://localhost/packs/",false,2.5,35,.08,129));
    }
}
