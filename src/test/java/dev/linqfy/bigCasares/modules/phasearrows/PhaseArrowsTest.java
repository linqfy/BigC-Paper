package dev.linqfy.bigCasares.modules.phasearrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;
import java.util.function.DoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhaseArrowsTest {

    @Test
    void exposesStableModuleId() {
        assertEquals("phase-arrows", new PhaseArrowsModule(null).getId());
    }

    @Test
    void crossesAThinWallAndExitsPastIt() {
        OptionalDouble exit = PhasePath.exitDistance(wall(0.5, 1.5), 2.0);
        assertTrue(exit.isPresent());
        assertTrue(exit.getAsDouble() > 1.5, "exit must be beyond the wall");
        assertTrue(exit.getAsDouble() < 2.5, "exit must stay close to the wall");
    }

    @Test
    void stopsAtWallsThickerThanTheLimit() {
        assertEquals(OptionalDouble.empty(), PhasePath.exitDistance(wall(0.2, 3.8), 2.0));
        assertTrue(PhasePath.exitDistance(wall(0.2, 3.8), 4.0).isPresent());
    }

    @Test
    void neverCrossesUnphaseableBlocks() {
        DoubleFunction<PhasePath.Cell> bedrock = distance -> distance >= 0.4 && distance < 1.4
            ? PhasePath.Cell.BLOCKED : PhasePath.Cell.OPEN;
        assertEquals(OptionalDouble.empty(), PhasePath.exitDistance(bedrock, 2.0));
    }

    @Test
    void wallsBackedByUnphaseableBlocksStopTheArrow() {
        DoubleFunction<PhasePath.Cell> backed = distance -> distance < 0.4 ? PhasePath.Cell.OPEN
            : distance < 1.4 ? PhasePath.Cell.SOLID : PhasePath.Cell.BLOCKED;
        assertEquals(OptionalDouble.empty(), PhasePath.exitDistance(backed, 3.0));
    }

    @Test
    void doesNothingWithoutAWallOrWithZeroThickness() {
        assertEquals(OptionalDouble.empty(), PhasePath.exitDistance(distance -> PhasePath.Cell.OPEN, 2.0));
        assertEquals(OptionalDouble.empty(), PhasePath.exitDistance(wall(0.5, 1.5), 0.0));
    }

    @Test
    void settingsClampAndKeepDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("phase-arrows.max-thickness", 50.0);
        PhaseArrowSettings settings = PhaseArrowSettings.load(config);
        assertEquals(8.0, settings.maxThickness());
        assertEquals(1, settings.maxPhases());
        assertTrue(settings.unphaseable().contains("BEDROCK"));
    }

    private static DoubleFunction<PhasePath.Cell> wall(double from, double to) {
        return distance -> distance >= from && distance < to ? PhasePath.Cell.SOLID : PhasePath.Cell.OPEN;
    }
}
