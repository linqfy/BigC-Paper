package dev.linqfy.bigCasares.modules.tideframe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TideframeScreenInstallerTest {
    private static TideframeScreenInstaller.BundledScreen screen(String id, String json) {
        return new TideframeScreenInstaller.BundledScreen(id, json.getBytes(StandardCharsets.UTF_8));
    }

    private static List<TideframeScreenInstaller.Outcome> outcomes(List<TideframeScreenInstaller.Result> results) {
        return results.stream().map(TideframeScreenInstaller.Result::outcome).toList();
    }

    @Test
    void installsOnceUpdatesItsOwnFileAndKeepsAnEditedOne(@TempDir Path root) throws Exception {
        Path screens = root.resolve("Tideframe").resolve("screens"), own = root.resolve("BigCasares").resolve("tideframe");
        var v1 = List.of(screen("bigc_tienda", "{\"v\":1}"), screen("bigc_misiones", "{\"m\":1}"));
        assertEquals(List.of(TideframeScreenInstaller.Outcome.INSTALLED, TideframeScreenInstaller.Outcome.INSTALLED),
            outcomes(TideframeScreenInstaller.install(screens, own, v1)));
        assertEquals("{\"v\":1}", Files.readString(screens.resolve("bigc_tienda.json")));
        assertEquals(List.of(TideframeScreenInstaller.Outcome.UNCHANGED, TideframeScreenInstaller.Outcome.UNCHANGED),
            outcomes(TideframeScreenInstaller.install(screens, own, v1)), "nothing rewritten: Tideframe rebuilds nothing");

        // A new BigCasares version: its own untouched file is updated.
        var v2 = List.of(screen("bigc_tienda", "{\"v\":2}"), screen("bigc_misiones", "{\"m\":2}"));
        Files.writeString(screens.resolve("bigc_misiones.json"), "{\"m\":1,\"edited\":true}");
        assertEquals(List.of(TideframeScreenInstaller.Outcome.UPDATED, TideframeScreenInstaller.Outcome.KEPT_EDITED),
            outcomes(TideframeScreenInstaller.install(screens, own, v2)));
        assertEquals("{\"v\":2}", Files.readString(screens.resolve("bigc_tienda.json")));
        assertEquals("{\"m\":1,\"edited\":true}", Files.readString(screens.resolve("bigc_misiones.json")), "the edit stays");
        assertEquals("{\"m\":2}", Files.readString(own.resolve("bigc_misiones.bundled.json")), "the new one is left to compare");
        assertEquals(List.of(TideframeScreenInstaller.Outcome.UNCHANGED, TideframeScreenInstaller.Outcome.KEPT_EDITED),
            outcomes(TideframeScreenInstaller.install(screens, own, v2)), "and is never overwritten later either");
    }

    @Test
    void aScreenSavedWhileBigCasaresWritesItsOwnIsKept(@TempDir Path root) throws Exception {
        Path screens = root.resolve("screens"), own = root.resolve("own");
        TideframeScreenInstaller.install(screens, own, List.of(screen("bigc_tienda", "{\"v\":1}")));
        Path tienda = screens.resolve("bigc_tienda.json"), misiones = screens.resolve("bigc_misiones.json");
        // The editor publishes an edit while a new BigCasares version is updating the screen.
        assertEquals(List.of(TideframeScreenInstaller.Outcome.KEPT_EDITED), outcomes(TideframeScreenInstaller.install(screens, own,
            List.of(screen("bigc_tienda", "{\"v\":2}")), () -> save(tienda, "{\"v\":1,\"edited\":true}"))));
        assertEquals("{\"v\":1,\"edited\":true}", Files.readString(tienda), "the edit stays");
        assertEquals("{\"v\":2}", Files.readString(own.resolve("bigc_tienda.bundled.json")), "the new one is left to compare");
        // A screen with a BigCasares id created meanwhile is not replaced either, nor recorded as BigCasares'.
        assertEquals(List.of(TideframeScreenInstaller.Outcome.KEPT_EDITED), outcomes(TideframeScreenInstaller.install(screens, own,
            List.of(screen("bigc_misiones", "{\"m\":1}")), () -> save(misiones, "{\"mine\":true}"))));
        assertEquals("{\"mine\":true}", Files.readString(misiones));
        assertFalse(Files.readString(own.resolve("installed-screens.properties")).contains("bigc_misiones="));
        try (var left = Files.list(screens)) {
            assertEquals(java.util.Set.of("bigc_tienda.json", "bigc_misiones.json"),
                left.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()), "no temporary file left behind");
        }
    }

    @Test
    void anUpdateInterruptedHalfwayIsRepairedOnTheNextStart(@TempDir Path root) throws Exception {
        Path screens = root.resolve("screens"), own = root.resolve("own");
        TideframeScreenInstaller.install(screens, own, List.of(screen("bigc_tienda", "{\"v\":1}")));
        Path tienda = screens.resolve("bigc_tienda.json"), aside = screens.resolve("bigc_tienda.json.replacing");
        // The server stopped right after moving an edited screen aside, before checking it.
        Files.writeString(tienda, "{\"v\":1,\"edited\":true}");
        Files.move(tienda, aside);
        assertEquals(List.of(TideframeScreenInstaller.Outcome.KEPT_EDITED),
            outcomes(TideframeScreenInstaller.install(screens, own, List.of(screen("bigc_tienda", "{\"v\":2}")))));
        assertEquals("{\"v\":1,\"edited\":true}", Files.readString(tienda), "the edit is back in place, and kept");
        assertFalse(Files.exists(aside));
    }

    private static void save(Path file, String json) {
        try {
            Files.writeString(file, json);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    @Test
    void aFileAlreadyEqualToTheBundledOneIsAdoptedAndOnlyBigCasaresIdsAreAccepted(@TempDir Path root) throws Exception {
        Path screens = root.resolve("screens"), own = root.resolve("own");
        Files.createDirectories(screens);
        Files.writeString(screens.resolve("bigc_tienda.json"), "{\"v\":1}");
        assertEquals(List.of(TideframeScreenInstaller.Outcome.UNCHANGED),
            outcomes(TideframeScreenInstaller.install(screens, own, List.of(screen("bigc_tienda", "{\"v\":1}")))));
        assertEquals(List.of(TideframeScreenInstaller.Outcome.UPDATED),
            outcomes(TideframeScreenInstaller.install(screens, own, List.of(screen("bigc_tienda", "{\"v\":2}")))), "now it is ours");
        assertTrue(Files.readString(own.resolve("installed-screens.properties")).contains("bigc_tienda="));
        assertThrows(IllegalArgumentException.class, () -> screen("visual_menu09", "{}"), "never another plugin's screen");
        assertThrows(IllegalArgumentException.class, () -> screen("bigc_../x", "{}"));
    }
}
