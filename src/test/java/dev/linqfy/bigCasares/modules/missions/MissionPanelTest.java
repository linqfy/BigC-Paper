package dev.linqfy.bigCasares.modules.missions;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionPanelTest {
    @Test
    void allAssignmentsAreReachableAndAResetClampsThePage() {
        Map<String, MissionAssignment> daily = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            var definition = mission("m" + i, Map.of());
            daily.put(definition.id(), new MissionAssignment(definition, MissionProgressSnapshot.fresh()));
        }
        var state = new MissionPlayerState(UUID.randomUUID(), Instant.EPOCH, Instant.EPOCH, Instant.EPOCH,
            daily, Map.of(), null);
        var middle = MissionPanel.Page.of(state, MissionScope.DAILY, 1);
        assertEquals(3, middle.count());
        assertEquals(List.of("m3", "m4", "m5"), middle.rows().stream().map(row -> row.definition().id()).toList());
        var last = MissionPanel.Page.of(state, MissionScope.DAILY, 15);
        assertEquals(2, last.index());
        assertEquals("m6", last.rows().getFirst().definition().id());
        var empty = MissionPanel.Page.of(state, MissionScope.WEEKLY, 2);
        assertEquals(0, empty.index());
        assertEquals(1, empty.count());
        assertTrue(empty.rows().isEmpty());
    }

    @Test
    void missionShowsItsMaterialWhenDrawableAndAnAvailableFallbackOtherwise() {
        var mission = mission("collect", Map.of("material", "TUFF"));
        assertEquals("tuff", MissionPanel.icon(mission, Set.of("tuff", "bundle", "paper")::contains));
        assertEquals("bundle", MissionPanel.icon(mission, Set.of("bundle", "paper")::contains));
        assertEquals("paper", MissionPanel.icon(mission, Set.of("paper")::contains));
    }

    private MissionDefinition mission(String id, Map<String, Object> params) {
        return new MissionDefinition(id, MissionScope.DAILY, id, "", MissionType.HOLD_EXACT_ITEM_COUNT, 30, 150, 1, true, params);
    }
}
