package dev.linqfy.bigCasares.modules.missions;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMissionServiceTest {

    @Test
    void marksMissionCompletedWhenProgressReachesGoal() {
        MissionCatalog catalog = MissionCatalog.of(List.of(
            new MissionDefinition("tuff-67", MissionScope.DAILY, "Toba exacta", "Consegui 67 bloques de toba", MissionType.HOLD_EXACT_ITEM_COUNT, 67, 100.0, 1, true, Map.of("material", "TUFF"))
        ));

        PlayerMissionService service = new PlayerMissionService(
            catalog,
            new MissionRotationPolicy(new Random(5)),
            () -> Instant.parse("2026-04-19T10:00:00Z"),
            1,
            0,
            () -> Instant.parse("2026-04-20T00:00:00Z"),
            () -> Instant.parse("2026-04-26T00:00:00Z")
        );

        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        MissionPlayerState state = service.getOrCreateState(playerId, null);
        MissionPlayerState updated = service.updateAbsoluteProgress(state, MissionScope.DAILY, "tuff-67", 67);

        assertTrue(updated.dailyAssignments().get("tuff-67").snapshot().completed());
        assertEquals(67, updated.dailyAssignments().get("tuff-67").snapshot().progress());
    }

    @Test
    void marksClaimedAfterSuccessfulClaim() {
        MissionCatalog catalog = MissionCatalog.of(List.of(
            new MissionDefinition("tuff-67", MissionScope.DAILY, "Toba exacta", "Consegui 67 bloques de toba", MissionType.HOLD_EXACT_ITEM_COUNT, 67, 100.0, 1, true, Map.of("material", "TUFF"))
        ));

        FakeRewardGateway rewardGateway = new FakeRewardGateway();
        PlayerMissionService service = new PlayerMissionService(
            catalog,
            new MissionRotationPolicy(new Random(5)),
            () -> Instant.parse("2026-04-19T10:00:00Z"),
            1,
            0,
            () -> Instant.parse("2026-04-20T00:00:00Z"),
            () -> Instant.parse("2026-04-26T00:00:00Z")
        );

        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        MissionPlayerState state = service.updateAbsoluteProgress(service.getOrCreateState(playerId, null), MissionScope.DAILY, "tuff-67", 67);

        MissionPlayerState claimed = service.claimAllCompleted(playerId, state, rewardGateway);

        assertTrue(claimed.dailyAssignments().get("tuff-67").snapshot().claimed());
        assertEquals(100.0, rewardGateway.totalDeposited);
    }

    @Test
    void rerollsExpiredDailyAssignments() {
        MissionCatalog catalog = MissionCatalog.of(List.of(
            new MissionDefinition("daily-a", MissionScope.DAILY, "A", "A", MissionType.HOLD_EXACT_ITEM_COUNT, 1, 10.0, 1, true, Map.of("material", "TUFF")),
            new MissionDefinition("daily-b", MissionScope.DAILY, "B", "B", MissionType.HOLD_EXACT_ITEM_COUNT, 1, 10.0, 1, true, Map.of("material", "TUFF")),
            new MissionDefinition("weekly-a", MissionScope.WEEKLY, "W", "W", MissionType.HOLD_EXACT_ITEM_COUNT, 1, 10.0, 1, true, Map.of("material", "TUFF"))
        ));

        PlayerMissionService service = new PlayerMissionService(
            catalog,
            new MissionRotationPolicy(new Random(5)),
            () -> Instant.parse("2026-04-21T10:00:00Z"),
            1,
            1,
            () -> Instant.parse("2026-04-22T00:00:00Z"),
            () -> Instant.parse("2026-04-26T00:00:00Z")
        );

        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        MissionPlayerState oldState = new MissionPlayerState(
            playerId,
            Instant.parse("2026-04-19T10:00:00Z"),
            Instant.parse("2026-04-20T00:00:00Z"),
            Instant.parse("2026-04-26T00:00:00Z"),
            Map.of("daily-a", new MissionAssignment(catalog.byScope(MissionScope.DAILY).getFirst(), MissionProgressSnapshot.fresh())),
            Map.of("weekly-a", new MissionAssignment(catalog.byScope(MissionScope.WEEKLY).getFirst(), MissionProgressSnapshot.fresh())),
            null
        );

        MissionPlayerState refreshed = service.getOrCreateState(playerId, oldState);

        assertEquals(Instant.parse("2026-04-22T00:00:00Z"), refreshed.dailyResetsAt());
        assertEquals(Instant.parse("2026-04-26T00:00:00Z"), refreshed.weeklyResetsAt());
    }

    @Test
    void completedMissionStaysCompletedWhenPassiveProgressDrops() {
        PlayerMissionService service = serviceFor(new MissionDefinition(
            "tuff-67", MissionScope.DAILY, "Toba", "Toba", MissionType.HOLD_EXACT_ITEM_COUNT, 67, 100.0, 1, true, Map.of("material", "TUFF")));
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        MissionPlayerState done = service.updateAbsoluteProgress(service.getOrCreateState(playerId, null), MissionScope.DAILY, "tuff-67", 67);

        MissionPlayerState dropped = service.updateAbsoluteProgress(done, MissionScope.DAILY, "tuff-67", 68);

        assertTrue(dropped.dailyAssignments().get("tuff-67").snapshot().completed());
    }

    @Test
    void sameTargetProgressOnlyCountsTheFirstTarget() {
        PlayerMissionService service = serviceFor(new MissionDefinition(
            "clan", MissionScope.DAILY, "Clan", "Clan", MissionType.KILL_SAME_TEAMMATE, 3, 100.0, 1, true, Map.of()));
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        MissionPlayerState state = service.getOrCreateState(playerId, null);

        state = service.recordSameTargetProgress(state, MissionScope.DAILY, "clan", "victim-a");
        state = service.recordSameTargetProgress(state, MissionScope.DAILY, "clan", "victim-b");
        state = service.recordSameTargetProgress(state, MissionScope.DAILY, "clan", "victim-a");
        assertEquals(2, state.dailyAssignments().get("clan").snapshot().progress());
        assertFalse(state.dailyAssignments().get("clan").snapshot().completed());

        state = service.recordSameTargetProgress(state, MissionScope.DAILY, "clan", "victim-a");
        assertTrue(state.dailyAssignments().get("clan").snapshot().completed());
    }

    @Test
    void forceCompleteFinishesAdminApprovalMission() {
        PlayerMissionService service = serviceFor(new MissionDefinition(
            "idea", MissionScope.DAILY, "Idea", "Idea", MissionType.ADMIN_APPROVAL, 1, 2000.0, 1, true, Map.of()));
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        MissionPlayerState approved = service.forceComplete(service.getOrCreateState(playerId, null), MissionScope.DAILY, "idea");

        assertTrue(approved.dailyAssignments().get("idea").snapshot().completed());
        assertFalse(approved.dailyAssignments().get("idea").snapshot().claimed());
    }

    private static PlayerMissionService serviceFor(MissionDefinition definition) {
        return new PlayerMissionService(
            MissionCatalog.of(List.of(definition)),
            new MissionRotationPolicy(new Random(5)),
            () -> Instant.parse("2026-04-19T10:00:00Z"),
            1,
            0,
            () -> Instant.parse("2026-04-20T00:00:00Z"),
            () -> Instant.parse("2026-04-26T00:00:00Z")
        );
    }

    private static final class FakeRewardGateway implements RewardGateway {
        private double totalDeposited;

        @Override
        public void deposit(UUID playerId, double amount) {
            totalDeposited += amount;
        }
    }
}
