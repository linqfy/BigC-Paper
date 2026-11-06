package dev.linqfy.bigCasares.modules.missions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** How the mission screens list and announce missions: ready to claim first, then running, then claimed. */
final class MissionOrder {
    private MissionOrder() {
    }

    /** {@code assignments} with the ready ones first; otherwise in their own order (the sort is stable). */
    static List<MissionAssignment> readyFirst(Collection<MissionAssignment> assignments) {
        List<MissionAssignment> sorted = new ArrayList<>(assignments);
        sorted.sort(Comparator.comparingInt(MissionOrder::rank));
        return sorted;
    }

    /**
     * Missions ready to claim in {@code after} that were not complete in {@code before}: the ones to announce. A
     * mission missing from {@code before} (a new assignment) counts as not complete.
     */
    static List<MissionDefinition> newlyReady(MissionPlayerState before, MissionPlayerState after) {
        List<MissionDefinition> ready = new ArrayList<>();
        collect(before.dailyAssignments(), after.dailyAssignments(), ready);
        collect(before.weeklyAssignments(), after.weeklyAssignments(), ready);
        return ready;
    }

    /** The money "Reclamar" would pay now: every ready, unclaimed reward, daily and weekly. */
    static double pendingReward(MissionPlayerState state) {
        double total = 0;
        for (Map<String, MissionAssignment> assignments : List.of(state.dailyAssignments(), state.weeklyAssignments())) {
            for (MissionAssignment assignment : assignments.values()) {
                if (assignment.snapshot().completed() && !assignment.snapshot().claimed()) {
                    total += assignment.definition().reward();
                }
            }
        }
        return total;
    }

    private static void collect(Map<String, MissionAssignment> before, Map<String, MissionAssignment> after,
                                List<MissionDefinition> ready) {
        for (MissionAssignment now : after.values()) {
            MissionAssignment was = before.get(now.definition().id());
            boolean wasComplete = was != null && was.snapshot().completed();
            if (now.snapshot().completed() && !now.snapshot().claimed() && !wasComplete) {
                ready.add(now.definition());
            }
        }
    }

    private static int rank(MissionAssignment assignment) {
        MissionProgressSnapshot snapshot = assignment.snapshot();
        return snapshot.claimed() ? 2 : snapshot.completed() ? 0 : 1;
    }
}
