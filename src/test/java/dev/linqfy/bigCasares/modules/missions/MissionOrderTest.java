package dev.linqfy.bigCasares.modules.missions;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionOrderTest {
    private static final UUID JUAN = UUID.randomUUID();

    private static MissionAssignment mission(String id, int progress, boolean completed, boolean claimed) {
        var definition = new MissionDefinition(id, MissionScope.DAILY, "Misión " + id, "", MissionType.values()[0], 10, 100, 1, true, Map.of());
        return new MissionAssignment(definition, new MissionProgressSnapshot(progress, completed, claimed, Set.of()));
    }

    private static MissionPlayerState state(List<MissionAssignment> daily, List<MissionAssignment> weekly) {
        Map<String, MissionAssignment> d = new LinkedHashMap<>();
        daily.forEach(a -> d.put(a.definition().id(), a));
        Map<String, MissionAssignment> w = new LinkedHashMap<>();
        weekly.forEach(a -> w.put(a.definition().id(), a));
        return new MissionPlayerState(JUAN, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, d, w, null);
    }

    private static List<String> ids(List<MissionAssignment> assignments) {
        return assignments.stream().map(a -> a.definition().id()).toList();
    }

    @Test
    void readyFirstThenRunningThenClaimedKeepingTheirOwnOrder() {
        var sorted = MissionOrder.readyFirst(List.of(mission("a", 3, false, false), mission("b", 10, true, true),
            mission("c", 10, true, false), mission("d", 1, false, false), mission("e", 10, true, false)));
        assertEquals(List.of("c", "e", "a", "d", "b"), ids(sorted));
    }

    @Test
    void onlyMissionsThatJustBecameReadyAreAnnounced() {
        var before = state(List.of(mission("a", 9, false, false), mission("b", 10, true, false), mission("c", 2, false, false)),
            List.of(mission("w", 4, false, false)));
        var after = state(List.of(mission("a", 10, true, false), mission("b", 10, true, false), mission("c", 3, false, false)),
            List.of(mission("w", 10, true, false)));

        assertEquals(List.of("a", "w"), MissionOrder.newlyReady(before, after).stream().map(MissionDefinition::id).toList(),
            "b was already ready, c is still running");
        assertEquals(List.of(), MissionOrder.newlyReady(after, after), "the same update again says nothing");
    }

    @Test
    void pendingRewardAddsEveryReadyUnclaimedMissionOfBothLists() {
        var state = state(List.of(mission("a", 10, true, false), mission("b", 10, true, true), mission("c", 3, false, false)),
            List.of(mission("w", 10, true, false)));
        assertEquals(200.0, MissionOrder.pendingReward(state), "a and w, 100 each; b was claimed, c is running");
    }

    @Test
    void aMissionCompletedAndClaimedInOneStepIsNotAnnounced() {
        var before = state(List.of(mission("a", 9, false, false)), List.of());
        var after = state(List.of(mission("a", 10, true, true)), List.of());
        assertEquals(List.of(), MissionOrder.newlyReady(before, after));
    }
}
