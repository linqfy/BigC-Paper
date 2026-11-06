package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightScreens;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.BossFightSettings;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.KeyChallenge;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BossFightConfigTest {
    /** The engine section of the config.yml in the JAR, as a server without its own config.yml reads it. */
    private static org.bukkit.configuration.ConfigurationSection shipped(Path dir) {
        return new EngineConfig(dir.resolve("config.yml"), () -> BossFightConfigTest.class.getResourceAsStream("/config.yml")).read();
    }

    @Test void bundledConfigDescribesExactlyTheDefaults(@TempDir Path dir) {
        assertEquals(BossFightSettings.defaults(), BossFightConfig.load(shipped(dir)));
    }

    @Test void ownersCanRestyleEverythingAndScreensFollow(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config.yml");
        Files.writeString(config, """
                boss-fight:
                  keys: [w, space]
                  lives: 3
                  time-start: 1.5
                  time-min: 0.5
                  colors:
                    key: "#202020"
                    key-letter: "#ffffff"
                    timer: "#ffcc00"
                  layout:
                    key-y: -0.9
                    key-size: 1.5
                  show:
                    lives: false
                    countdown: false
                  texts:
                    perfect: NICE!
                    lives: HEARTS
                  sounds:
                    miss: none
                """);
        var settings = BossFightConfig.load(config);
        assertEquals(List.of(KeyChallenge.Key.W, KeyChallenge.Key.SPACE), settings.keys());
        assertEquals(3, settings.lives()); assertEquals(30, settings.startTicks()); assertEquals(10, settings.minTicks());
        assertNull(settings.sound("miss"));
        var screens = BossFightScreens.build(settings);
        var key = screens.get(KeyChallenge.KEY_SCREEN);
        var face = key.elements().stream().filter(e -> e.id().equals("cap_face")).findFirst().orElseThrow();
        assertEquals("#202020", face.style().fillColor());
        assertTrue(face.height() > .7, "key-size 1.5 makes a bigger cap: " + face.height());
        assertEquals("#ffffff", key.elements().stream().filter(e -> e.id().equals("key")).findFirst().orElseThrow().style().textColor());
        assertEquals("#ffcc00", key.elements().stream().filter(e -> e.id().equals("timer")).findFirst().orElseThrow().style().textColor());
        assertTrue(key.elements().stream().noneMatch(e -> e.id().equals("lives")), "lives hidden");
        for (var screen : screens.values()) for (var e : screen.elements()) assertTrue(Math.abs(e.y()) <= 1, e.id() + " stays on the canvas");
        // The game uses the configured keys, lives and texts; no countdown.
        var game = new KeyChallenge(1, settings);
        assertEquals("", game.values().get("key"));
        for (int i = 0; i < 20 && game.phase() == KeyChallenge.Phase.COUNTDOWN; i++) game.tick();
        assertEquals(KeyChallenge.Phase.PROMPT, game.phase());
        assertTrue(settings.keys().contains(game.current()));
        game.press(game.current());
        assertTrue(List.of("NICE!", "GREAT!", "GOOD!").contains(game.values().get("feedback")));
        assertEquals("3/3", game.values().get("lives"));
    }

    @Test void invalidValuesAreRejectedWithTheKeyName() {
        var d = BossFightSettings.defaults();
        var bad = List.<Map<String, String>>of(Map.of("key", "white"), Map.of("bogus", "#ffffff"));
        for (var colors : bad) {
            var error = assertThrows(IllegalArgumentException.class, () -> new BossFightSettings(d.keys(), 5, 40, 14, 30, 6, 2, colors, Map.of(), Map.of(), Map.of(), Map.of()));
            assertTrue(error.getMessage().contains("colors"), error.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> new BossFightSettings(List.of(), 5, 40, 14, 30, 6, 2, Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BossFightSettings(d.keys(), 5, 10, 20, 30, 6, 2, Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BossFightSettings(d.keys(), 5, 40, 14, 30, 6, 2, Map.of(), Map.of("key-size", 3.0), Map.of(), Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BossFightSettings(d.keys(), 5, 40, 14, 30, 6, 2, Map.of(), Map.of(), Map.of(), Map.of("perfect", "ÉPICO"), Map.of()));
    }

    @Test void menusAreReadOnlyInTheGameByDefault(@TempDir Path dir) {
        var shipped = shipped(dir);
        assertTrue(shipped.contains("editing.in-game"), "the setting is in the shipped config.yml");
        assertFalse(shipped.getBoolean("editing.in-game", true), "read-only by default");
    }

    @Test void aServerConfigWithPartOfTheSectionKeepsItsValuesAndReadsTheRestFromTheJar(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config.yml");
        Files.writeString(config, "tideframe-ui:\n  missions: true\n  motor:\n    pack:\n      port: 9999\n    boss-fight:\n      lives: 3\n");
        var engine = new EngineConfig(config, () -> BossFightConfigTest.class.getResourceAsStream("/config.yml"));
        var read = engine.read();
        assertEquals(9999, read.getInt("pack.port"));
        assertEquals("127.0.0.1", read.getString("pack.bind"), "missing options come from the JAR");
        assertTrue(read.getBoolean("pack.send"));
        assertFalse(read.getBoolean("editing.in-game", true), "an explicit fallback still gets the JAR's value, not its own");
        assertEquals(1500, read.getLong("editor.auto-reload-delay-ms", 1));
        var settings = BossFightConfig.load(read);
        assertEquals(3, settings.lives());
        assertEquals(BossFightSettings.defaults().keys(), settings.keys(), "the rest of boss-fight keeps the defaults");
        assertEquals(BossFightSettings.defaults().text("victory"), settings.text("victory"));
        assertEquals(PluginSettings.defaults().displaySizes(), PluginSettings.load(read).displaySizes());
    }

    @Test void onlyChangesToTheEngineSectionChangeItsFingerprint(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config.yml");
        var engine = new EngineConfig(config, () -> BossFightConfigTest.class.getResourceAsStream("/config.yml"));
        Files.writeString(config, "shop:\n  enabled: true\ntideframe-ui:\n  motor:\n    sounds:\n      volume: 0.6\n");
        String before = engine.fingerprint();
        Files.writeString(config, "shop:\n  enabled: false\ntideframe-ui:\n  motor:\n    sounds:\n      volume: 0.6\n");
        assertEquals(before, engine.fingerprint(), "another module's setting reloads nothing");
        Files.writeString(config, "shop:\n  enabled: false\ntideframe-ui:\n  motor:\n    sounds:\n      volume: 0.9\n");
        assertNotEquals(before, engine.fingerprint());
    }

    @Test void singleKeyFightsStillWork() {
        var d = BossFightSettings.defaults();
        var settings = new BossFightSettings(List.of(KeyChallenge.Key.CLICK), 5, 40, 14, 30, 6, 2, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        var game = new KeyChallenge(4, settings);
        while (game.phase() == KeyChallenge.Phase.COUNTDOWN) game.tick();
        assertEquals(KeyChallenge.Key.CLICK, game.current());
        game.press(KeyChallenge.Key.CLICK);
        while (game.phase() == KeyChallenge.Phase.GAP) game.tick();
        assertEquals(KeyChallenge.Key.CLICK, game.current());
        assertEquals(KeyChallenge.WIDE_SCREEN, game.frameId());
        assertTrue(BossFightScreens.build(d).values().stream().flatMap(s -> s.elements().stream()).anyMatch(LiveText::isBar));
    }
}
