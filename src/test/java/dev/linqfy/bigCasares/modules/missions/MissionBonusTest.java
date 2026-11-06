package dev.linqfy.bigCasares.modules.missions;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionBonusTest {

    @Test
    void offByDefaultAndInTheBundledConfig() {
        assertEquals(MissionBonus.OFF, MissionBonus.load(new YamlConfiguration()));
        assertEquals(MissionBonus.OFF, MissionBonus.load(
            YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"))));
        assertEquals(0, MissionBonus.OFF.rate(10, 1));
    }

    @Test
    void theStreakAddsPerDayUpToItsMaximumAndTheHappyHourAddsItsMultiplier() {
        MissionBonus streak = new MissionBonus(true, 0.05, 0.5);
        assertEquals(0, streak.rate(0, 1));
        assertEquals(0.15, streak.rate(3, 1), 1e-9);
        assertEquals(0.5, streak.rate(40, 1), 1e-9, "capped");
        assertEquals(1.15, streak.rate(3, 2), 1e-9, "a x2 happy hour adds 100%");
        assertEquals(MissionBonus.CAP, new MissionBonus(true, 0.2, 1).rate(40, 5), 1e-9, "+100% streak and x5 is capped at +400%");
        assertEquals(1.0, MissionBonus.OFF.rate(40, 2), 1e-9, "the happy hour works without the streak");
    }

    @Test
    void valuesAreClamped() {
        MissionBonus greedy = new MissionBonus(true, 3, 9);
        assertEquals(0.2, greedy.perDay());
        assertEquals(1, greedy.maximum());
    }
}
