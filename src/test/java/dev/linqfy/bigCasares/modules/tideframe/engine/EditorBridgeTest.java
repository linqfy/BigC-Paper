package dev.linqfy.bigCasares.modules.tideframe.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** The two files the web editor and the plugin share: the load report and the open-in-game request. */
class EditorBridgeTest {
    @Test void theStatusIsJsonWithScreensWarningsFileTimesAndTheLastOpenAnswer(@TempDir Path folder) throws Exception {
        String json = EditorBridge.status(42, true, null, List.of("menu", "tienda"), Map.of("tienda", List.of("calls \"x\""), "menu", List.of()),
            Map.of("tienda", 1730000000123L), "3f9a opened tienda for Steve");
        var parsed = (Map<?, ?>) dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec.parse(json);
        assertEquals(42L, ((Number) parsed.get("time")).longValue());
        assertEquals(true, parsed.get("ok"));
        assertNull(parsed.get("error"));
        assertEquals(List.of("menu", "tienda"), parsed.get("screens"));
        assertEquals(Map.of("tienda", List.of("calls \"x\"")), parsed.get("problems"), "screens without warnings are left out");
        assertEquals(1730000000123L, ((Number) ((Map<?, ?>) parsed.get("stamps")).get("tienda")).longValue(), "a file time survives as an exact number");
        assertEquals("3f9a opened tienda for Steve", parsed.get("open"));
        EditorBridge.writeStatus(folder, json);
        assertEquals(json, Files.readString(folder.resolve(EditorBridge.STATUS)));
        try (var files = Files.list(folder)) { assertEquals(1, files.count(), "no temporary file left"); }
        var failed = (Map<?, ?>) dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec.parse(EditorBridge.status(1, false, "bad\nfile", List.of(), Map.of(), Map.of(), null));
        assertEquals(false, failed.get("ok")); assertEquals("bad\nfile", failed.get("error")); assertNull(failed.get("open"));
    }

    @Test void theStampIsTheFileTimeTheEditorReadsBack(@TempDir Path folder) throws Exception {
        var store = new dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenStore(folder);
        store.save(dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen.demos().get("menu"));
        Files.setLastModifiedTime(folder.resolve("menu.json"), FileTime.fromMillis(1730000000123L));
        assertEquals(1730000000123L, store.stamp("menu"));
        assertThrows(java.nio.file.NoSuchFileException.class, () -> store.stamp("tienda"));
    }

    @Test void onlyARealScreenIdAPlayerNameAndARequestIdAreAccepted() {
        assertEquals(new EditorBridge.OpenRequest("tienda", "Steve", "a1b2"), EditorBridge.openRequest("{\"screen\":\"tienda\",\"player\":\"Steve\",\"request\":\"a1b2\"}"));
        assertEquals(new EditorBridge.OpenRequest("tienda", null, null), EditorBridge.openRequest("{\"screen\":\"tienda\"}"));
        assertEquals(new EditorBridge.OpenRequest("tienda", null, null), EditorBridge.openRequest("{\"screen\":\"tienda\",\"player\":\"not a name!\",\"request\":\"x y\"}"),
            "a bad name or request id means none");
        for (String bad : new String[]{"{\"screen\":\"Tienda\"}", "{\"screen\":\"../x\"}", "{\"player\":\"Steve\"}", "[]", "not json", "{\"screen\":5}"})
            assertNull(EditorBridge.openRequest(bad), bad);
    }
}
