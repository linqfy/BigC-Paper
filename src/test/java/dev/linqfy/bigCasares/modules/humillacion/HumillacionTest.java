package dev.linqfy.bigCasares.modules.humillacion;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumillacionTest {
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void exposesStableModuleId() {
        assertEquals("humillacion", new HumillacionModule(null, null).getId());
    }

    @Test
    void onlyAHugeOverkillHumiliates() {
        HumillacionPolicy policy = new HumillacionPolicy(HumillacionSettings.defaults());

        assertFalse(policy.shouldHumiliate(25.0, 20.0, 0.0, true, null, NOW), "plain kill, 5 left over");
        assertTrue(policy.shouldHumiliate(40.0, 20.0, 0.0, true, null, NOW));
        assertFalse(policy.shouldHumiliate(40.0, 20.0, 4.0, true, null, NOW), "absorption soaks overkill");
        assertTrue(policy.shouldHumiliate(30.0, 6.0, 0.0, true, null, NOW));
    }

    @Test
    void requiresAPlayerAttackerByDefault() {
        HumillacionPolicy policy = new HumillacionPolicy(HumillacionSettings.defaults());
        HumillacionPolicy anySource = new HumillacionPolicy(new HumillacionSettings(20.0, 2.0, false, 60L, true));

        assertFalse(policy.shouldHumiliate(100.0, 20.0, 0.0, false, null, NOW));
        assertTrue(anySource.shouldHumiliate(100.0, 20.0, 0.0, false, null, NOW));
    }

    @Test
    void recentlyHumiliatedPlayersDieNormally() {
        HumillacionPolicy policy = new HumillacionPolicy(HumillacionSettings.defaults());

        assertFalse(policy.shouldHumiliate(100.0, 2.0, 0.0, true, NOW.minusSeconds(30), NOW));
        assertTrue(policy.shouldHumiliate(100.0, 2.0, 0.0, true, NOW.minusSeconds(60), NOW));
    }

    @Test
    void leavesOneHeartWithinMaxHealth() {
        HumillacionPolicy policy = new HumillacionPolicy(HumillacionSettings.defaults());

        assertEquals(2.0, policy.healthAfter(20.0));
        assertEquals(1.0, policy.healthAfter(1.0));
    }

    @Test
    void tombstoneGoesToTheRightHandSide() {
        // Yaw 0 faces south (+Z): right hand is west (-X). Yaw 90 faces west: right hand is north (-Z).
        double[] south = HumillacionPolicy.besideOffset(0f);
        assertEquals(-1.5, south[0], 1e-9);
        assertEquals(0.0, south[1], 1e-9);
        double[] west = HumillacionPolicy.besideOffset(90f);
        assertEquals(0.0, west[0], 1e-9);
        assertEquals(-1.5, west[1], 1e-9);
    }

    @Test
    void loadsConfigAndFallsBackOnInvalidValues() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("humillacion.overkill-threshold", 30.0);
        yaml.set("humillacion.broadcast", false);
        HumillacionSettings loaded = HumillacionSettings.load(yaml);
        assertEquals(30.0, loaded.overkillThreshold());
        assertFalse(loaded.broadcast());

        yaml.set("humillacion.overkill-threshold", -1.0);
        assertEquals(HumillacionSettings.defaults(), HumillacionSettings.load(yaml));
        assertThrows(IllegalArgumentException.class, () -> new HumillacionSettings(20.0, 0.0, true, 60L, true));
    }
}
