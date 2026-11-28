package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyGroupAssignerTest {
    private static UUID player(int index) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", index));
    }

    private static Map<UUID, Double> players(double... levels) {
        Map<UUID, Double> map = new LinkedHashMap<>();
        for (int index = 0; index < levels.length; index++) {
            map.put(player(index + 1), levels[index]);
        }
        return map;
    }

    private static Map<String, Integer> sizes(ToffyGroupAssigner.Result result) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        result.assigned().values().forEach(key -> counts.merge(key, 1, Integer::sum));
        return counts;
    }

    @Test
    void parejoSpreadsPlayersSoThatTheSizesDifferByAtMostOne() {
        Map<UUID, Double> levels = players(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        List<ToffyGroupAssigner.Slot> slots = List.of(
            new ToffyGroupAssigner.Slot("a", null, null, null, 0),
            new ToffyGroupAssigner.Slot("b", null, null, null, 0),
            new ToffyGroupAssigner.Slot("c", null, null, null, 0));

        ToffyGroupAssigner.Result result = ToffyGroupAssigner.assign(levels, slots, true, new Random(42));

        assertEquals(10, result.assigned().size());
        assertTrue(result.ungrouped().isEmpty());
        Map<String, Integer> counts = sizes(result);
        assertEquals(10, counts.get("a") + counts.get("b") + counts.get("c"));
        assertTrue(Math.abs(counts.get("a") - counts.get("b")) <= 1);
        assertTrue(Math.abs(counts.get("b") - counts.get("c")) <= 1);
        assertTrue(Math.abs(counts.get("a") - counts.get("c")) <= 1);
    }

    @Test
    void parejoIsReproducibleWithTheSameSeed() {
        Map<UUID, Double> levels = players(10, 20, 30, 40, 50, 60);
        List<ToffyGroupAssigner.Slot> slots = List.of(
            new ToffyGroupAssigner.Slot("a", null, null, null, 0),
            new ToffyGroupAssigner.Slot("b", null, null, null, 0));

        assertEquals(ToffyGroupAssigner.assign(levels, slots, true, new Random(9)).assigned(),
            ToffyGroupAssigner.assign(levels, slots, true, new Random(9)).assigned());
    }

    @Test
    void parejoRespectsCapacitiesAndCountsTheMembersAlreadyThere() {
        Map<UUID, Double> levels = players(10, 20, 30, 40, 50, 60, 70, 80, 90, 100);
        List<ToffyGroupAssigner.Slot> slots = List.of(
            new ToffyGroupAssigner.Slot("chica", 2, null, null, 0),
            new ToffyGroupAssigner.Slot("grande", 5, null, null, 1),
            new ToffyGroupAssigner.Slot("media", 4, null, null, 0));

        ToffyGroupAssigner.Result result = ToffyGroupAssigner.assign(levels, slots, true, new Random(3));

        Map<String, Integer> counts = sizes(result);
        assertTrue(counts.getOrDefault("chica", 0) <= 2);
        assertTrue(counts.getOrDefault("grande", 0) <= 4);
        assertTrue(counts.getOrDefault("media", 0) <= 4);
        // 2 + (5 - 1) + 4 seats for the new players: all ten fit
        assertEquals(10, result.assigned().size());
        assertTrue(result.ungrouped().isEmpty());
    }

    @Test
    void playersOnlyGoToGroupsWhoseRangeFitsTheirLevel() {
        Map<UUID, Double> levels = players(10, 90, 150);
        List<ToffyGroupAssigner.Slot> slots = List.of(
            new ToffyGroupAssigner.Slot("bajos", null, 0.0, 50.0, 0),
            new ToffyGroupAssigner.Slot("altos", null, 60.0, null, 0));

        ToffyGroupAssigner.Result result = ToffyGroupAssigner.assign(levels, slots, true, new Random(5));

        assertEquals("bajos", result.assigned().get(player(1)));
        assertEquals("altos", result.assigned().get(player(2)));
        assertEquals("altos", result.assigned().get(player(3)));
        Map<String, Integer> counts = new LinkedHashMap<>();
        result.assigned().values().forEach(key -> counts.merge(key, 1, Integer::sum));
        assertEquals(1, counts.get("bajos"));
        assertEquals(2, counts.get("altos"));
    }

    @Test
    void aPlayerThatFitsNoGroupStaysUngrouped() {
        Map<UUID, Double> levels = players(90);
        List<ToffyGroupAssigner.Slot> slots = List.of(new ToffyGroupAssigner.Slot("bajos", null, 0.0, 20.0, 0));

        ToffyGroupAssigner.Result result = ToffyGroupAssigner.assign(levels, slots, true, new Random(1));

        assertTrue(result.assigned().isEmpty());
        assertEquals(List.of(player(1)), result.ungrouped());
    }

    @Test
    void nivelSendsEachPlayerToTheEligibleGroupWithFewestMembersAndTheNarrowestRange() {
        // The wide group takes everyone, the narrow one only the players between 40 and 60.
        Map<UUID, Double> levels = players(55, 10, 45, 50);
        List<ToffyGroupAssigner.Slot> slots = List.of(
            new ToffyGroupAssigner.Slot("amplia", null, 0.0, 200.0, 0),
            new ToffyGroupAssigner.Slot("estrecha", null, 40.0, 60.0, 0));

        ToffyGroupAssigner.Result result = ToffyGroupAssigner.assign(levels, slots, false, new Random(0));

        // Sorted by level: 10 (only amplia), 45 (estrecha has fewer), 50 (tie, estrecha is narrower), 55 (amplia has fewer).
        assertEquals("amplia", result.assigned().get(player(2)));
        assertEquals("estrecha", result.assigned().get(player(3)));
        assertEquals("estrecha", result.assigned().get(player(4)));
        assertEquals("amplia", result.assigned().get(player(1)));
        assertTrue(result.ungrouped().isEmpty());
    }

    @Test
    void nivelCountsTheMembersThatAGroupAlreadyHas() {
        Map<UUID, Double> levels = players(50);
        List<ToffyGroupAssigner.Slot> slots = List.of(
            new ToffyGroupAssigner.Slot("uno", null, null, null, 3),
            new ToffyGroupAssigner.Slot("dos", null, null, null, 1));

        assertEquals("dos", ToffyGroupAssigner.assign(levels, slots, false, new Random(0)).assigned().get(player(1)));
    }

    @Test
    void nivelOrdersTheWholeListByLevel() {
        List<Double> order = new ArrayList<>();
        Map<UUID, Double> levels = players(30, 5, 20);
        ToffyGroupAssigner.assign(levels, List.of(new ToffyGroupAssigner.Slot("unica", 1, null, null, 0)), false,
            new Random(0)).assigned().keySet().forEach(id -> order.add(levels.get(id)));

        // Only the first one in level order fits the single seat.
        assertEquals(List.of(5.0), order);
    }
}
