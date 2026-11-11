package dev.linqfy.bigCasares.modules.missions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class PlayerMissionService {

    private final MissionCatalog catalog;
    private final MissionRotationPolicy rotationPolicy;
    private final Supplier<Instant> nowSupplier;
    private final int dailyCount;
    private final int weeklyCount;
    private final Supplier<Instant> nextDailyResetSupplier;
    private final Supplier<Instant> nextWeeklyResetSupplier;

    public PlayerMissionService(
        MissionCatalog catalog,
        MissionRotationPolicy rotationPolicy,
        Supplier<Instant> nowSupplier,
        int dailyCount,
        int weeklyCount,
        Supplier<Instant> nextDailyResetSupplier,
        Supplier<Instant> nextWeeklyResetSupplier
    ) {
        this.catalog = catalog;
        this.rotationPolicy = rotationPolicy;
        this.nowSupplier = nowSupplier;
        this.dailyCount = dailyCount;
        this.weeklyCount = weeklyCount;
        this.nextDailyResetSupplier = nextDailyResetSupplier;
        this.nextWeeklyResetSupplier = nextWeeklyResetSupplier;
    }

    public MissionPlayerState getOrCreateState(UUID playerId, MissionPlayerState existingState) {
        if (existingState == null) {
            return createFreshState(playerId);
        }

        Instant now = nowSupplier.get();
        boolean dailyExpired = !now.isBefore(existingState.dailyResetsAt());
        boolean weeklyExpired = !now.isBefore(existingState.weeklyResetsAt());
        if (!dailyExpired && !weeklyExpired) {
            return existingState;
        }

        MissionPlayerState freshState = createFreshState(playerId);
        return new MissionPlayerState(
            playerId,
            freshState.generatedAt(),
            dailyExpired ? freshState.dailyResetsAt() : existingState.dailyResetsAt(),
            weeklyExpired ? freshState.weeklyResetsAt() : existingState.weeklyResetsAt(),
            dailyExpired ? freshState.dailyAssignments() : existingState.dailyAssignments(),
            weeklyExpired ? freshState.weeklyAssignments() : existingState.weeklyAssignments(),
            dailyExpired ? null : existingState.dailyRerolledAt()
        );
    }

    public MissionPlayerState updateAbsoluteProgress(MissionPlayerState state, MissionScope scope, String missionId, int progress) {
        return withUpdatedAssignment(state, scope, missionId, assignment -> {
            if (assignment.snapshot().completed()) {
                // A finished mission stays finished until it is claimed or rotated out, even when the
                // player later drops items, takes off gear or walks away.
                return assignment;
            }
            int clampedProgress = Math.max(0, progress);
            boolean completed = MissionProgressEngine.isCompleted(assignment.definition(), clampedProgress);
            return new MissionAssignment(
                assignment.definition(),
                new MissionProgressSnapshot(clampedProgress, completed, assignment.snapshot().claimed(), assignment.snapshot().markers())
            );
        });
    }

    public MissionPlayerState addProgressMarker(
        MissionPlayerState state,
        MissionScope scope,
        String missionId,
        String marker
    ) {
        return withUpdatedAssignment(state, scope, missionId, assignment -> {
            if (assignment.snapshot().completed()) {
                return assignment;
            }
            java.util.Set<String> markers = new java.util.LinkedHashSet<>(assignment.snapshot().markers());
            markers.add(marker);
            int progress = markers.size();
            return new MissionAssignment(assignment.definition(), new MissionProgressSnapshot(
                progress, MissionProgressEngine.isCompleted(assignment.definition(), progress),
                assignment.snapshot().claimed(), markers
            ));
        });
    }

    /**
     * Counts progress against a single target. The first target seen is remembered; later events only
     * count when they hit that same target again.
     */
    public MissionPlayerState recordSameTargetProgress(
        MissionPlayerState state,
        MissionScope scope,
        String missionId,
        String targetKey
    ) {
        return withUpdatedAssignment(state, scope, missionId, assignment -> {
            MissionProgressSnapshot snapshot = assignment.snapshot();
            if (snapshot.completed() || targetKey == null || targetKey.isBlank()) {
                return assignment;
            }
            if (!snapshot.markers().isEmpty() && !snapshot.markers().contains(targetKey)) {
                return assignment;
            }
            int progress = snapshot.progress() + 1;
            return new MissionAssignment(assignment.definition(), new MissionProgressSnapshot(
                progress, MissionProgressEngine.isCompleted(assignment.definition(), progress),
                snapshot.claimed(), java.util.Set.of(targetKey)
            ));
        });
    }

    /** Marks an assigned mission as completed regardless of its progress, e.g. after an admin approval. */
    public MissionPlayerState forceComplete(MissionPlayerState state, MissionScope scope, String missionId) {
        return withUpdatedAssignment(state, scope, missionId, assignment -> {
            if (assignment.snapshot().completed()) {
                return assignment;
            }
            int progress = Math.max(assignment.snapshot().progress(), assignment.definition().goal());
            return new MissionAssignment(assignment.definition(), new MissionProgressSnapshot(
                progress, true, assignment.snapshot().claimed(), assignment.snapshot().markers()
            ));
        });
    }

    public MissionPlayerState claimAllCompleted(UUID playerId, MissionPlayerState state, RewardGateway rewardGateway) {
        MissionPlayerState updated = state;
        updated = claimScope(playerId, updated, MissionScope.DAILY, rewardGateway);
        updated = claimScope(playerId, updated, MissionScope.WEEKLY, rewardGateway);
        return updated;
    }

    private MissionPlayerState claimScope(UUID playerId, MissionPlayerState state, MissionScope scope, RewardGateway rewardGateway) {
        Map<String, MissionAssignment> assignments = assignmentsForScope(state, scope);
        MissionPlayerState updated = state;
        for (MissionAssignment assignment : assignments.values()) {
            if (assignment.snapshot().completed() && !assignment.snapshot().claimed()) {
                rewardGateway.deposit(playerId, assignment.definition().reward());
                updated = withUpdatedAssignment(updated, scope, assignment.definition().id(), current ->
                    new MissionAssignment(
                        current.definition(),
                        new MissionProgressSnapshot(current.snapshot().progress(), true, true, current.snapshot().markers())
                    )
                );
            }
        }
        return updated;
    }

    public MissionPlayerState rerollDailyAssignments(UUID playerId, MissionPlayerState state, Instant now) {
        MissionPlayerState freshDaily = rotationPolicy.createFreshState(
            playerId, catalog, dailyCount, 0, now,
            nextDailyResetSupplier.get(), nextWeeklyResetSupplier.get()
        );
        return new MissionPlayerState(
            playerId,
            state.generatedAt(),
            state.dailyResetsAt(),
            state.weeklyResetsAt(),
            freshDaily.dailyAssignments(),
            state.weeklyAssignments(),
            now
        );
    }

    private MissionPlayerState createFreshState(UUID playerId) {
        return rotationPolicy.createFreshState(
            playerId,
            catalog,
            dailyCount,
            weeklyCount,
            nowSupplier.get(),
            nextDailyResetSupplier.get(),
            nextWeeklyResetSupplier.get()
        );
    }

    private MissionPlayerState withUpdatedAssignment(
        MissionPlayerState state,
        MissionScope scope,
        String missionId,
        MissionAssignmentUpdater updater
    ) {
        Map<String, MissionAssignment> source = assignmentsForScope(state, scope);
        MissionAssignment current = source.get(missionId);
        if (current == null) {
            return state;
        }

        Map<String, MissionAssignment> updatedAssignments = new LinkedHashMap<>(source);
        updatedAssignments.put(missionId, updater.update(current));

        return scope == MissionScope.DAILY
            ? new MissionPlayerState(
                state.playerId(),
                state.generatedAt(),
                state.dailyResetsAt(),
                state.weeklyResetsAt(),
                updatedAssignments,
                state.weeklyAssignments(),
                state.dailyRerolledAt()
            )
            : new MissionPlayerState(
                state.playerId(),
                state.generatedAt(),
                state.dailyResetsAt(),
                state.weeklyResetsAt(),
                state.dailyAssignments(),
                updatedAssignments,
                state.dailyRerolledAt()
            );
    }

    private Map<String, MissionAssignment> assignmentsForScope(MissionPlayerState state, MissionScope scope) {
        return scope == MissionScope.DAILY ? state.dailyAssignments() : state.weeklyAssignments();
    }

    @FunctionalInterface
    private interface MissionAssignmentUpdater {
        MissionAssignment update(MissionAssignment assignment);
    }
}
