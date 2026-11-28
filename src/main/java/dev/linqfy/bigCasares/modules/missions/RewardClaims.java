package dev.linqfy.bigCasares.modules.missions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Pays a player's completed, unclaimed missions one at a time, writing each receipt before its deposit.
 * A saved receipt is what stops a reward from being paid twice (double click, "Cobrar todo" after a single
 * claim, a reconnect or a late answer), so a reward is only deposited after its receipt was stored.
 */
final class RewardClaims {
    /** How a claim run ended. */
    enum Outcome {
        /** Every pending reward was paid. */
        PAID,
        /** The receipt could not be stored; that reward was not paid. */
        NOT_SAVED,
        /** The economy refused the deposit; that receipt was withdrawn. */
        REFUSED,
        /** The economy failed without saying whether it paid; the receipt stays so it is never paid twice. */
        UNCONFIRMED
    }

    /**
     * @param stored false when the file still holds a receipt that {@code state} no longer has (a refused reward whose
     *               receipt could not be withdrawn); the caller keeps {@code state} pending a save so the next one fixes it
     */
    record Result(MissionPlayerState state, double paid, int count, Outcome outcome, String failedMission, boolean stored) {}

    /**
     * Thrown by a {@link RewardGateway} when the provider answered that the deposit did not happen. It stays an
     * {@link IllegalStateException} for callers that already catch that type.
     */
    static final class Refused extends IllegalStateException {
        Refused(String message) {
            super(message);
        }
    }

    private RewardClaims() {
    }

    static Result claim(MissionPlayerState state, RewardGateway economy, Consumer<MissionPlayerState> save) {
        MissionPlayerState current = state;
        double paid = 0;
        int count = 0;
        for (MissionScope scope : MissionScope.values()) {
            for (MissionAssignment assignment : assignments(current, scope).values()) {
                MissionProgressSnapshot snapshot = assignment.snapshot();
                if (!snapshot.completed() || snapshot.claimed()) {
                    continue;
                }
                String id = assignment.definition().id();
                double reward = assignment.definition().reward();
                MissionPlayerState receipt = withClaimed(current, scope, id, true);
                try {
                    save.accept(receipt);
                } catch (RuntimeException failure) {
                    return new Result(current, paid, count, Outcome.NOT_SAVED, id, true);
                }
                try {
                    if (Double.isFinite(reward) && reward > 0) {
                        economy.deposit(current.playerId(), reward);
                    }
                } catch (Refused refused) {
                    boolean withdrawn = true;
                    try {
                        save.accept(current);
                    } catch (RuntimeException notWithdrawn) {
                        withdrawn = false;
                    }
                    return new Result(current, paid, count, Outcome.REFUSED, id, withdrawn);
                } catch (RuntimeException unknown) {
                    return new Result(receipt, paid, count, Outcome.UNCONFIRMED, id, true);
                }
                current = receipt;
                paid += Double.isFinite(reward) && reward > 0 ? reward : 0;
                count++;
            }
        }
        return new Result(current, paid, count, Outcome.PAID, null, true);
    }

    private static Map<String, MissionAssignment> assignments(MissionPlayerState state, MissionScope scope) {
        return scope == MissionScope.WEEKLY ? state.weeklyAssignments() : state.dailyAssignments();
    }

    private static MissionPlayerState withClaimed(MissionPlayerState state, MissionScope scope, String id, boolean claimed) {
        Map<String, MissionAssignment> source = assignments(state, scope);
        MissionAssignment assignment = source.get(id);
        MissionProgressSnapshot snapshot = assignment.snapshot();
        Map<String, MissionAssignment> updated = new LinkedHashMap<>(source);
        updated.put(id, new MissionAssignment(assignment.definition(),
            new MissionProgressSnapshot(snapshot.progress(), snapshot.completed(), claimed, snapshot.markers())));
        return scope == MissionScope.WEEKLY
            ? new MissionPlayerState(state.playerId(), state.generatedAt(), state.dailyResetsAt(), state.weeklyResetsAt(),
                state.dailyAssignments(), updated, state.dailyRerolledAt())
            : new MissionPlayerState(state.playerId(), state.generatedAt(), state.dailyResetsAt(), state.weeklyResetsAt(),
                updated, state.weeklyAssignments(), state.dailyRerolledAt());
    }
}
