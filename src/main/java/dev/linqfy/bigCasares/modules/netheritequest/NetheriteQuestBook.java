package dev.linqfy.bigCasares.modules.netheritequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure quest rules: activation cost, boss soul usage, progress, completion and expiry.
 *
 * <p>Each activation consumes one boss soul. A boss can back only one armor piece per player, so the full set
 * needs souls of four different bosses; redoing a piece may reuse the boss that piece already used.
 */
public final class NetheriteQuestBook {

    private final NetheriteQuestSettings settings;
    private final Map<UUID, PlayerQuests> players = new LinkedHashMap<>();

    public NetheriteQuestBook(NetheriteQuestSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public NetheriteQuestSettings settings() {
        return settings;
    }

    /** Levels the next activation of this player costs: free the first time, then {@code step} more each time. */
    public int nextActivationCost(UUID playerId) {
        return (int) Math.min(Integer.MAX_VALUE, (long) state(playerId).activations * settings.levelCostStep());
    }

    public Activation activate(UUID playerId, ArmorQuestPiece piece, Collection<String> heldSoulBosses,
                               int playerLevels, long now) {
        Objects.requireNonNull(piece, "piece");
        PlayerQuests state = state(playerId);
        expire(playerId, state, now);
        if (state.active.containsKey(piece)) {
            return Activation.refused(ActivationStatus.ALREADY_ACTIVE);
        }
        if (heldSoulBosses == null || heldSoulBosses.isEmpty()) {
            return Activation.refused(ActivationStatus.NO_SOUL);
        }
        Optional<String> soul = heldSoulBosses.stream()
            .filter(Objects::nonNull)
            .filter(boss -> soulUsable(state, piece, boss))
            .findFirst();
        if (soul.isEmpty()) {
            return Activation.refused(ActivationStatus.SOUL_ALREADY_USED);
        }
        int cost = nextActivationCost(playerId);
        if (playerLevels < cost) {
            return new Activation(ActivationStatus.NOT_ENOUGH_LEVELS, cost, null);
        }
        state.activations++;
        state.active.put(piece, new ActiveQuest(now, soul.get(), 0.0));
        return new Activation(ActivationStatus.STARTED, cost, soul.get());
    }

    /** Undoes an activation that could not be paid for. */
    public void cancelActivation(UUID playerId, ArmorQuestPiece piece) {
        PlayerQuests state = players.get(playerId);
        if (state != null && state.active.remove(piece) != null) {
            state.activations = Math.max(0, state.activations - 1);
        }
    }

    public boolean soulUsable(UUID playerId, ArmorQuestPiece piece, String bossId) {
        return soulUsable(state(playerId), piece, bossId);
    }

    public boolean hasActive(UUID playerId, ArmorQuestPiece piece) {
        PlayerQuests state = players.get(playerId);
        return state != null && state.active.containsKey(piece);
    }

    /** Adds progress to an active quest; completing it frees the slot and binds its soul's boss to the piece. */
    public Optional<Progress> addProgress(UUID playerId, ArmorQuestPiece piece, double amount, long now) {
        PlayerQuests state = players.get(playerId);
        if (state == null || !(amount > 0.0)) {
            return Optional.empty();
        }
        ActiveQuest quest = state.active.get(piece);
        if (quest == null) {
            return Optional.empty();
        }
        if (isExpired(quest, now)) {
            state.active.remove(piece);
            return Optional.of(new Progress(piece, quest.progress(), quest.progress(), settings.target(piece),
                ProgressStatus.EXPIRED));
        }
        int target = settings.target(piece);
        double updated = Math.min(target, quest.progress() + amount);
        if (updated >= target) {
            state.active.remove(piece);
            state.souls.put(piece, quest.soulBoss());
            state.completions.merge(piece, 1, Integer::sum);
            return Optional.of(new Progress(piece, quest.progress(), updated, target, ProgressStatus.COMPLETED));
        }
        state.active.put(piece, quest.withProgress(updated));
        return Optional.of(new Progress(piece, quest.progress(), updated, target, ProgressStatus.ADVANCED));
    }

    /** Removes every quest past its deadline. */
    public List<Expired> expireAll(long now) {
        List<Expired> expired = new ArrayList<>();
        players.forEach((playerId, state) -> expire(playerId, state, now).forEach(piece ->
            expired.add(new Expired(playerId, piece))));
        return expired;
    }

    public long remainingMillis(ActiveQuest quest, long now) {
        return Math.max(0L, quest.startedAt() + settings.durationMillis() - now);
    }

    public void resetPlayer(UUID playerId) {
        PlayerQuests state = players.get(playerId);
        if (state != null) {
            state.activations = 0;
            state.active.clear();
            state.souls.clear();
            state.completions.clear();
        }
    }

    public void addPendingSoul(UUID playerId, String bossId, String bossName, int amount) {
        if (amount < 1) {
            return;
        }
        state(playerId).pendingSouls.merge(bossId, new PendingSoul(bossName, amount),
            (old, added) -> new PendingSoul(added.bossName(), old.amount() + added.amount()));
    }

    /** Takes the souls kept while the player was offline. */
    public Map<String, PendingSoul> drainPendingSouls(UUID playerId) {
        PlayerQuests state = players.get(playerId);
        if (state == null || state.pendingSouls.isEmpty()) {
            return Map.of();
        }
        Map<String, PendingSoul> drained = new LinkedHashMap<>(state.pendingSouls);
        state.pendingSouls.clear();
        return drained;
    }

    public PlayerView view(UUID playerId) {
        PlayerQuests state = state(playerId);
        return new PlayerView(state.activations, Collections.unmodifiableMap(new EnumMap<>(state.active)),
            Collections.unmodifiableMap(new EnumMap<>(state.souls)),
            Collections.unmodifiableMap(new EnumMap<>(state.completions)),
            Collections.unmodifiableMap(new LinkedHashMap<>(state.pendingSouls)));
    }

    public Map<UUID, PlayerView> snapshot() {
        Map<UUID, PlayerView> snapshot = new LinkedHashMap<>();
        players.keySet().forEach(playerId -> snapshot.put(playerId, view(playerId)));
        return snapshot;
    }

    /** Replaces a player's state from storage. */
    public void restore(UUID playerId, PlayerView view) {
        PlayerQuests state = new PlayerQuests();
        state.activations = Math.max(0, view.activations());
        state.active.putAll(view.active());
        state.souls.putAll(view.souls());
        state.completions.putAll(view.completions());
        state.pendingSouls.putAll(view.pendingSouls());
        players.put(playerId, state);
    }

    private boolean soulUsable(PlayerQuests state, ArmorQuestPiece piece, String bossId) {
        for (ArmorQuestPiece other : ArmorQuestPiece.values()) {
            if (other == piece) {
                continue;
            }
            ActiveQuest active = state.active.get(other);
            if (bossId.equals(state.souls.get(other)) || (active != null && bossId.equals(active.soulBoss()))) {
                return false;
            }
        }
        return true;
    }

    private List<ArmorQuestPiece> expire(UUID playerId, PlayerQuests state, long now) {
        List<ArmorQuestPiece> expired = new ArrayList<>();
        state.active.entrySet().removeIf(entry -> {
            if (isExpired(entry.getValue(), now)) {
                expired.add(entry.getKey());
                return true;
            }
            return false;
        });
        return expired;
    }

    private boolean isExpired(ActiveQuest quest, long now) {
        return now >= quest.startedAt() + settings.durationMillis();
    }

    private PlayerQuests state(UUID playerId) {
        return players.computeIfAbsent(Objects.requireNonNull(playerId, "playerId"), ignored -> new PlayerQuests());
    }

    public enum ActivationStatus { STARTED, ALREADY_ACTIVE, NO_SOUL, SOUL_ALREADY_USED, NOT_ENOUGH_LEVELS }

    public enum ProgressStatus { ADVANCED, COMPLETED, EXPIRED }

    /** @param soulBoss the boss whose soul must be consumed when {@code status} is STARTED */
    public record Activation(ActivationStatus status, int cost, String soulBoss) {
        static Activation refused(ActivationStatus status) {
            return new Activation(status, 0, null);
        }
    }

    public record ActiveQuest(long startedAt, String soulBoss, double progress) {
        public ActiveQuest {
            Objects.requireNonNull(soulBoss, "soulBoss");
        }

        ActiveQuest withProgress(double updated) {
            return new ActiveQuest(startedAt, soulBoss, updated);
        }
    }

    public record Progress(ArmorQuestPiece piece, double previous, double current, int target,
                           ProgressStatus status) {
    }

    public record Expired(UUID playerId, ArmorQuestPiece piece) {
    }

    public record PendingSoul(String bossName, int amount) {
    }

    /**
     * @param souls       boss whose soul each completed piece used
     * @param completions how many times each piece was completed
     */
    public record PlayerView(int activations, Map<ArmorQuestPiece, ActiveQuest> active,
                             Map<ArmorQuestPiece, String> souls, Map<ArmorQuestPiece, Integer> completions,
                             Map<String, PendingSoul> pendingSouls) {
    }

    private static final class PlayerQuests {
        private int activations;
        private final EnumMap<ArmorQuestPiece, ActiveQuest> active = new EnumMap<>(ArmorQuestPiece.class);
        private final EnumMap<ArmorQuestPiece, String> souls = new EnumMap<>(ArmorQuestPiece.class);
        private final EnumMap<ArmorQuestPiece, Integer> completions = new EnumMap<>(ArmorQuestPiece.class);
        private final Map<String, PendingSoul> pendingSouls = new LinkedHashMap<>();
    }
}
