package dev.linqfy.bigCasares.modules.piranhas;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiranhasTest {

    private final PiranhaPolicy policy = new PiranhaPolicy(PiranhaSettings.defaults());

    @Test
    void exposesStableModuleId() {
        assertEquals("piranhas", new PiranhasModule(null).getId());
    }

    @Test
    void spawnsOnlyInJungleAndSwampWatersBelowTheCap() {
        assertTrue(policy.shouldSpawn("jungle", 0, 0.1));
        assertTrue(policy.shouldSpawn("minecraft:mangrove_swamp", 7, 0.1));
        assertFalse(policy.shouldSpawn("plains", 0, 0.0));
        assertFalse(policy.shouldSpawn("swamp", 8, 0.0), "cap reached");
        assertFalse(policy.shouldSpawn("swamp", 0, 0.9), "chance missed");
        assertFalse(policy.shouldSpawn(null, 0, 0.0));
    }

    @Test
    void schoolSizeStaysWithinBounds() {
        assertEquals(3, policy.schoolSize(0.0));
        assertEquals(5, policy.schoolSize(0.999));
        assertEquals(4, policy.schoolSize(0.5));
    }

    @Test
    void bleedingSwimmersMakeThemFaster() {
        double base = PiranhaSettings.defaults().speed();
        assertEquals(base, policy.huntSpeed(20.0, 20.0), 1.0E-9);
        assertEquals(base * PiranhaPolicy.FRENZY_SPEED_MULTIPLIER, policy.huntSpeed(10.0, 20.0), 1.0E-9);
    }

    @Test
    void bitesNeedRangeAndCooldown() {
        assertTrue(policy.canBite(1.0, 100L, 50L));
        assertFalse(policy.canBite(1.0, 100L, 90L));
        assertFalse(policy.canBite(3.0, 100L, 0L));
    }

    @Test
    void settingsLoadAndNormalizeBiomes() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("piranhas.biomes", List.of("minecraft:RIVER"));
        config.set("piranhas.school-min", 6);
        config.set("piranhas.school-max", 2);
        PiranhaSettings settings = PiranhaSettings.load(config);
        assertTrue(settings.biomes().contains("river"));
        assertEquals(6, settings.schoolMin());
        assertEquals(6, settings.schoolMax(), "max never below min");
    }
}
