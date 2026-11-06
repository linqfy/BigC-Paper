package dev.linqfy.bigCasares.modules.missions;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionRerollTest {
    private static final ZoneId UTC = ZoneOffset.UTC;

    private static MissionPlayerState state(boolean ready, boolean claimed, Instant rerolledAt) {
        var definition = new MissionDefinition("a", MissionScope.DAILY, "A", "", MissionType.values()[0], 10, 100, 1, true, Map.of());
        var assignment = new MissionAssignment(definition, new MissionProgressSnapshot(ready ? 10 : 3, ready, claimed, Set.of()));
        return new MissionPlayerState(UUID.randomUUID(), Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, Map.of("a", assignment), Map.of(),
            rerolledAt);
    }

    @Test
    void theDailyPeriodStartsAtTheResetHourOfTodayOrYesterday() {
        assertEquals(Instant.parse("2026-11-04T06:00:00Z"), MissionReroll.periodStart(Instant.parse("2026-11-04T10:30:00Z"), 6, UTC));
        assertEquals(Instant.parse("2026-11-03T06:00:00Z"), MissionReroll.periodStart(Instant.parse("2026-11-04T05:59:00Z"), 6, UTC));
    }

    @Test
    void onceAPeriodAndNeverOverAReadyReward() {
        Instant period = Instant.parse("2026-11-04T00:00:00Z");
        assertEquals(MissionReroll.Check.ALLOWED, MissionReroll.check(state(false, false, null), period));
        assertEquals(MissionReroll.Check.ALLOWED, MissionReroll.check(state(true, true, period.minusSeconds(1)), period),
            "claimed rewards are safe; yesterday's change does not count");
        assertEquals(MissionReroll.Check.USED_TODAY, MissionReroll.check(state(false, false, period.plusSeconds(60)), period));
        assertEquals(MissionReroll.Check.CLAIM_FIRST, MissionReroll.check(state(true, false, null), period),
            "a ready, unclaimed daily reward would be lost");
    }
}
