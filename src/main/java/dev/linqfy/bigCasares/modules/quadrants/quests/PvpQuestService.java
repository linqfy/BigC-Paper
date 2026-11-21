package dev.linqfy.bigCasares.modules.quadrants.quests;

import dev.linqfy.bigCasares.modules.missions.RewardGateway;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/** Progreso y cobro; el llamador valida la zona y llama en el hilo principal. */
public final class PvpQuestService {
    public interface Store {
        PvpQuestState load(UUID player) throws IOException;
        void save(UUID player, PvpQuestState state) throws IOException;
    }

    private final Store store;
    private final int rivalGoal, dominationGoal, streakGoal;
    private final double rivalReward, dominationReward;

    public PvpQuestService(Store store, int rivalGoal, int dominationGoal, int streakGoal,
                           double rivalReward, double dominationReward) {
        this.store = Objects.requireNonNull(store);
        if (rivalGoal < 1 || dominationGoal < 1 || streakGoal < 1 || !Double.isFinite(rivalReward)
            || !Double.isFinite(dominationReward) || rivalReward <= 0 || dominationReward <= 0) {
            throw new IllegalArgumentException("Metas o recompensas PvP inválidas");
        }
        this.rivalGoal = rivalGoal; this.dominationGoal = dominationGoal; this.streakGoal = streakGoal;
        this.rivalReward = rivalReward; this.dominationReward = dominationReward;
    }

    public PvpQuestState status(UUID player, String week) throws IOException {
        PvpQuestState state = store.load(player).copy();
        state.roll(week);
        return state;
    }

    public boolean start(UUID player, String quest, String week) throws IOException {
        PvpQuestState state = status(player, week);
        switch (quest) {
            case "rivals" -> {
                if (state.rivalStarted || state.rivalClaim != PvpQuestState.Claim.NONE) return false;
                state.rivalStarted = true;
            }
            case "domination" -> {
                if (state.dominationStarted || state.dominationClaim != PvpQuestState.Claim.NONE) return false;
                state.dominationStarted = true;
            }
            default -> { return false; }
        }
        store.save(player, state);
        return true;
    }

    public void kill(UUID killer, UUID victim, String week) throws IOException {
        if (killer.equals(victim)) return;
        PvpQuestState winner = status(killer, week);
        PvpQuestState loser = status(victim, week);
        boolean winnerChanged = false;
        if (winner.rivalStarted && winner.rivalClaim == PvpQuestState.Claim.NONE
            && winner.rivals.size() < rivalGoal) winnerChanged |= winner.rivals.add(victim);
        if (winner.dominationStarted && winner.dominationClaim == PvpQuestState.Claim.NONE
            && winner.dominated.size() < dominationGoal && !winner.dominated.contains(victim)) {
            int streak = winner.streaks.merge(victim, 1, (old, one) -> Math.min(streakGoal, old + one));
            if (streak >= streakGoal) winner.dominated.add(victim);
            winnerChanged = true;
        }
        if (loser.dominationStarted && loser.streaks.remove(killer) != null) store.save(victim, loser);
        if (winnerChanged) store.save(killer, winner);
    }

    public boolean ready(UUID player, String quest, String week) throws IOException {
        return ready(status(player, week), quest);
    }

    private boolean ready(PvpQuestState state, String quest) {
        return switch (quest) {
            case "rivals" -> state.rivalStarted && state.rivalClaim == PvpQuestState.Claim.NONE
                && state.rivals.size() >= rivalGoal;
            case "domination" -> state.dominationStarted && state.dominationClaim == PvpQuestState.Claim.NONE
                && state.dominated.size() >= dominationGoal;
            default -> false;
        };
    }

    public boolean claim(UUID player, String quest, String week, RewardGateway gateway) throws IOException {
        PvpQuestState state = status(player, week);
        if (!ready(state, quest)) return false;
        if (quest.equals("rivals")) state.rivalClaim = PvpQuestState.Claim.PENDING;
        else state.dominationClaim = PvpQuestState.Claim.PENDING;
        store.save(player, state.copy()); // Debe llegar a disco antes de llamar a Vault.
        gateway.deposit(player, quest.equals("rivals") ? rivalReward : dominationReward);
        if (quest.equals("rivals")) state.rivalClaim = PvpQuestState.Claim.PAID;
        else state.dominationClaim = PvpQuestState.Claim.PAID;
        store.save(player, state.copy()); // Si falla queda PENDING y no se vuelve a pagar.
        return true;
    }

    public int rivalGoal() { return rivalGoal; }
    public int dominationGoal() { return dominationGoal; }
    public double rivalReward() { return rivalReward; }
    public double dominationReward() { return dominationReward; }
}
