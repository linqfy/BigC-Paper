package dev.linqfy.bigCasares.modules.tideframe.engine;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** config.yml open: keys, menu items, zones and signs; and the reopen after a reload. */
class OpenTriggersTest {
    private static OpenTriggers parse(String yaml, List<String> warnings) throws Exception {
        var config = new YamlConfiguration(); config.loadFromString(yaml);
        return OpenTriggers.parse(config.getConfigurationSection("open"), warnings::add);
    }

    @Test void theBundledDefaultsTurnEverythingOff() throws Exception {
        var warnings = new ArrayList<String>();
        var triggers = parse("open:\n  keys: {}\n  items: {}\n  zones: {}\n  signs: false\n", warnings);
        assertFalse(triggers.any()); assertEquals(List.of(), warnings);
        assertSame(OpenTriggers.NONE, OpenTriggers.parse(null, warnings::add), "configs without the section");
        var bundled = new YamlConfiguration();
        try (var in = OpenTriggersTest.class.getResourceAsStream("/config.yml")) {
            bundled.loadFromString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        assertFalse(OpenTriggers.parse(bundled.getConfigurationSection(EngineConfig.PATH + ".open"), warnings::add).any(), "the shipped config.yml opens nothing by itself");
    }

    @Test void keysItemsZonesAndSignsAreReadAndBadEntriesAreReported() throws Exception {
        var warnings = new ArrayList<String>();
        var triggers = parse("""
            open:
              keys: {F: menu, shift+f: perfil, Q: tienda, L: "Bad Id"}
              items:
                menu: {material: compass, name: "<aqua>Menú", slot: 4, give-on-join: true}
                shop: {material: "not a material!"}
              zones:
                plaza: {world: world, from: [10, 70, -5], to: [0, 60, 5], screen: menu, cooldown-seconds: 20}
                broken: {world: world, from: [0, 0], to: [1, 1, 1], screen: menu}
              signs: true
            """, warnings);
        assertEquals("menu", triggers.keyScreen(false)); assertEquals("perfil", triggers.keyScreen(true));
        assertEquals(new OpenTriggers.Item("menu", "COMPASS", "<aqua>Menú", 4, true), triggers.items.get("menu"));
        assertNull(triggers.items.get("shop"));
        var plaza = triggers.zones.get(0);
        assertEquals(List.of(0, 60, -5, 10, 70, 5), List.of(plaza.minX(), plaza.minY(), plaza.minZ(), plaza.maxX(), plaza.maxY(), plaza.maxZ()), "corners in any order");
        assertEquals(1, triggers.zones.size());
        assertTrue(triggers.signs);
        assertEquals(4, warnings.size(), String.join("\n", warnings));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("open.keys.Q")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("open.keys.L")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("open.items.shop")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("open.zones.broken")));
    }

    @Test void aZoneOpensOncePerVisitAndWaitsForItsCooldown() {
        var zone = new OpenTriggers.Zone("plaza", "world", 0, 60, 0, 10, 70, 10, "menu", 30);
        var triggers = new OpenTriggers(java.util.Map.of(), java.util.Map.of(), List.of(zone), false);
        assertSame(zone, triggers.zoneAt("world", 10, 70, 0), "edges are inside");
        assertNull(triggers.zoneAt("world", 11, 65, 5)); assertNull(triggers.zoneAt("world_nether", 5, 65, 5));
        var visits = new OpenTriggers.Visits(); var player = UUID.randomUUID();
        assertTrue(visits.entered(player, zone, 0), "walking in opens");
        assertFalse(visits.entered(player, zone, 1_000), "moving inside does not open again");
        assertFalse(visits.entered(player, null, 2_000));
        assertFalse(visits.entered(player, zone, 10_000), "back in before the cooldown");
        visits.entered(player, null, 11_000);
        assertTrue(visits.entered(player, zone, 31_000), "after 30 s it opens again");
        assertTrue(visits.entered(UUID.randomUUID(), zone, 31_000), "every player has their own visits");
    }

    @Test void signsNeedTheMarkAndAScreenId() {
        assertEquals("menu", OpenTriggers.signScreen("[Tideframe]", " Menu "));
        assertEquals("tienda", OpenTriggers.signScreen(" [TIDEFRAME] ", "tienda"));
        assertNull(OpenTriggers.signScreen("Tideframe", "menu"));
        assertNull(OpenTriggers.signScreen("[Tideframe]", "dos palabras"));
        assertNull(OpenTriggers.signScreen(null, "menu"));
    }

    @Test void aReloadReopensEachMenuOnceButNotBossPrompts() {
        var reopen = new ReopenAfterReload(); var a = UUID.randomUUID(); var b = UUID.randomUUID(); var c = UUID.randomUUID();
        reopen.remember(a, "tienda"); reopen.remember(b, "challenge_key"); reopen.remember(c, null);
        assertEquals("tienda", reopen.take(a)); assertNull(reopen.take(a), "only once");
        assertNull(reopen.take(b), "a boss prompt belongs to the challenge the reload ended");
        assertNull(reopen.take(c));
        reopen.remember(a, "menu"); reopen.forget(a); assertNull(reopen.take(a), "declined pack or quit");
    }
}
