package dev.linqfy.bigCasares.modules.legendary;

import com.google.gson.Gson;
import java.util.Objects;
import java.util.UUID;

public record LegendaryCraftingSession(UUID id, LegendaryWeapon weapon, UUID world, String dimension, int x, int y, int z,
                                       long started, long deadline, LegendarySearchAreas areas,
                                       Stage stage, int announced, java.util.List<Pending> transactions) {
    public enum Stage { AVAILABLE, CRAFTING, READY_TO_CLAIM, CLAIMED }
    public enum TransactionKind { START, CLAIM }
    public record Pending(UUID player, UUID nonce, String inventory, TransactionKind kind) {
        public Pending { Objects.requireNonNull(player); Objects.requireNonNull(nonce); Objects.requireNonNull(inventory); Objects.requireNonNull(kind); }
    }
    public static final long DURATION_MILLIS = 1_200_000;
    private static final Gson JSON = new Gson();

    public LegendaryCraftingSession {
        Objects.requireNonNull(id); Objects.requireNonNull(weapon); Objects.requireNonNull(world); Objects.requireNonNull(dimension);
        Objects.requireNonNull(areas); Objects.requireNonNull(stage);
        transactions = java.util.List.copyOf(transactions);
        if (deadline != Math.addExact(started, DURATION_MILLIS) || !areas.outer().contains(x, z)
            || !areas.inner().contains(x, z) || announced < -1 || announced > 5 || stage == Stage.AVAILABLE) {
            throw new IllegalArgumentException("Estado de fabricación inválido.");
        }
    }
    public String encode() { return JSON.toJson(this); }
    public static LegendaryCraftingSession decode(String encoded) {
        return Objects.requireNonNull(JSON.fromJson(encoded, LegendaryCraftingSession.class));
    }
    public long remaining(long now) { return Math.max(0, deadline - now); }
    public boolean protectsTable() {
        return stage != Stage.CLAIMED || transactions.stream().anyMatch(t -> t.kind == TransactionKind.CLAIM);
    }
    public LegendaryCraftingSession update(Stage next, int notice, java.util.List<Pending> pending) {
        return new LegendaryCraftingSession(id, weapon, world, dimension, x, y, z, started, deadline, areas, next, notice, pending);
    }
    public int dueNotice(long now) {
        if (stage == Stage.CLAIMED && transactions.stream().anyMatch(t -> t.kind == TransactionKind.CLAIM)) return announced;
        if (stage == Stage.CLAIMED) return 5;
        if (now >= deadline) return 4;
        long elapsed = now - started;
        return elapsed >= 900_000 ? 3 : elapsed >= 600_000 ? 2 : elapsed >= 300_000 ? 1 : 0;
    }
}
