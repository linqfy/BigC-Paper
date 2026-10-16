package dev.linqfy.bigCasares.dimension.environment;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnvironmentCycleTest {

    private static EnvironmentCycle tides() {
        return EnvironmentCycle.builder("tides")
            .phase("flow", 1000)
            .phase("endure", 500)
            .phase("thrive", 300)
            .build();
    }

    @Test
    void snapshotsFollowTheClockAcrossPhaseBoundariesAndCycles() {
        EnvironmentCycle cycle = tides();
        assertEquals(1800, cycle.period());
        assertEquals("flow", cycle.snapshotAt(0).phase().id());
        assertEquals("flow", cycle.snapshotAt(999).phase().id());
        assertEquals(1, cycle.snapshotAt(999).ticksUntilNextPhase());
        EnvironmentCycle.Snapshot endure = cycle.snapshotAt(1000);
        assertEquals("endure", endure.phase().id());
        assertEquals(0, endure.ticksIntoPhase());
        assertEquals("thrive", cycle.snapshotAt(1799).phase().id());
        EnvironmentCycle.Snapshot nextCycle = cycle.snapshotAt(1800 + 1200);
        assertEquals("endure", nextCycle.phase().id());
        assertEquals(1, nextCycle.cycleNumber());
        assertEquals(1000 + 1800, cycle.nextStartOf("endure", 1001));
    }

    @Test
    void theTimelineCoversThePeriod() {
        var json = tides().timelineJson(NamespacedId.of("example", "clock"));
        assertEquals("example:clock", json.get("clock").getAsString());
        assertEquals(1800, json.get("period_ticks").getAsInt());
    }

    @Test
    void invalidCyclesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EnvironmentCycle.builder("x").build());
        assertThrows(IllegalArgumentException.class, () -> EnvironmentCycle.builder("x").phase("a", 10).build());
        assertThrows(IllegalArgumentException.class,
            () -> EnvironmentCycle.builder("x").phase("a", 100).phase("a", 100).build());
        assertThrows(IllegalArgumentException.class, () -> EnvironmentCycle.builder("Bad Id").phase("a", 100).build());
    }
}
