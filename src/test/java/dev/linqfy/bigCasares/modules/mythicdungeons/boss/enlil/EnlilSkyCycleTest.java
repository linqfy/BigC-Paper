package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnlilSkyCycleTest {

    private static Map<Integer, List<EnlilCue>> run(EnlilSkyCycle cycle, int ticks) {
        Map<Integer, List<EnlilCue>> cues = new LinkedHashMap<>();
        for (int tick = 1; tick <= ticks; tick++) {
            List<EnlilCue> now = cycle.advance();
            if (!now.isEmpty()) {
                cues.put(tick, new ArrayList<>(now));
            }
        }
        return cues;
    }

    @Test
    void oneAirborneStretchTelegraphsEveryAttackBeforeItLands() {
        Map<Integer, List<EnlilCue>> cues = run(new EnlilSkyCycle(), 284);
        assertEquals(List.of(EnlilCue.GUST_WARN), cues.get(40));
        assertEquals(List.of(EnlilCue.GUST), cues.get(60));
        assertEquals(List.of(EnlilCue.TORNADO_WARN), cues.get(120));
        assertEquals(List.of(EnlilCue.TORNADO), cues.get(144));
        assertEquals(List.of(EnlilCue.GUST_WARN), cues.get(200));
        assertEquals(List.of(EnlilCue.GUST), cues.get(220));
        assertEquals(List.of(EnlilCue.DESCEND_WARN), cues.get(260));
        assertEquals(List.of(EnlilCue.DESCEND), cues.get(284));
        assertTrue(EnlilTuning.GUST_WARN_TICKS >= 16 && EnlilTuning.TORNADO_WARN_TICKS >= 16
            && EnlilTuning.DESCEND_WARN_TICKS >= 16, "telegraphs last at least 0.8 s");
    }

    @Test
    void vulnerableOnlyWhileGroundedThenRisesAndRepeats() {
        EnlilSkyCycle cycle = new EnlilSkyCycle();
        for (int tick = 0; tick < 284; tick++) {
            cycle.advance();
            assertFalse(cycle.vulnerable());
            assertEquals(EnlilTuning.HOVER_HEIGHT, cycle.height(), 1.0e-9);
        }
        assertEquals(EnlilSkyCycle.State.DESCENDING, cycle.state());
        for (int tick = 1; tick < EnlilTuning.MOVE_TICKS; tick++) {
            cycle.advance();
        }
        assertEquals(List.of(EnlilCue.LANDED), cycle.advance());
        assertTrue(cycle.vulnerable());
        assertEquals(0.0, cycle.height(), 1.0e-9);
        assertEquals(EnlilTuning.GROUNDED_TICKS, cycle.groundedTicksLeft());
        for (int tick = 1; tick < EnlilTuning.GROUNDED_TICKS; tick++) {
            cycle.advance();
            assertTrue(cycle.vulnerable());
        }
        assertEquals(List.of(EnlilCue.ASCEND), cycle.advance());
        assertFalse(cycle.vulnerable());
        for (int tick = 1; tick < EnlilTuning.MOVE_TICKS; tick++) {
            cycle.advance();
        }
        assertEquals(List.of(EnlilCue.AIRBORNE), cycle.advance());
        assertEquals(List.of(EnlilCue.GUST_WARN), run(cycle, 40).get(40));
    }

    @Test
    void heightIsInterpolatedWhileMoving() {
        EnlilSkyCycle cycle = new EnlilSkyCycle();
        for (int tick = 0; tick < 284 + EnlilTuning.MOVE_TICKS / 2; tick++) {
            cycle.advance();
        }
        assertEquals(EnlilTuning.HOVER_HEIGHT / 2.0, cycle.height(), 1.0e-9);
    }

    @Test
    void landingIsWarnedBeforeTheDescent() {
        EnlilSkyCycle cycle = new EnlilSkyCycle();
        for (int tick = 0; tick < 259; tick++) {
            cycle.advance();
            assertFalse(cycle.landingWarned());
        }
        cycle.advance();
        assertTrue(cycle.landingWarned());
    }
}
