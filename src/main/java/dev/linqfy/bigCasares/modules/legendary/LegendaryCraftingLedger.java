package dev.linqfy.bigCasares.modules.legendary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** One persisted record contains reservation, inventory transaction, clock and notices. */
public final class LegendaryCraftingLedger {
    public record Notice(LegendaryCraftingSession session, int index) { }
    private final LegendaryStateStore store;
    private final RandomGenerator random;
    private final Map<LegendaryWeapon, LegendaryCraftingSession> sessions = new EnumMap<>(LegendaryWeapon.class);

    public LegendaryCraftingLedger(LegendaryStateStore store, RandomGenerator random) {
        this.store = store; this.random = random;
        for (var weapon : LegendaryWeapon.values()) {
            String encoded = store.get(key(weapon));
            if (encoded != null) {
                var session = LegendaryCraftingSession.decode(encoded);
                if (session.weapon() != weapon) throw new IllegalArgumentException("Registro de arma inconsistente.");
                sessions.put(weapon, session);
            }
        }
    }
    public synchronized Optional<LegendaryCraftingSession> find(LegendaryWeapon weapon) { return Optional.ofNullable(sessions.get(weapon)); }
    public synchronized List<LegendaryCraftingSession> all() {
        return sessions.values().stream().filter(session -> session.weapon().isAvailable()).toList();
    }
    public synchronized List<LegendaryCraftingSession> history() { return List.copyOf(sessions.values()); }
    public synchronized boolean protectedCell(UUID world, int x, int y, int z) {
        return all().stream().anyMatch(s -> s.protectsTable() && s.world().equals(world)
            && s.x() == x && s.z() == z && (s.y() == y || s.y() + 1 == y));
    }
    public synchronized boolean pending(UUID player) {
        return sessions.values().stream().anyMatch(s -> s.transactions().stream().anyMatch(t -> t.player().equals(player)));
    }
    public synchronized LegendaryCraftingSession start(LegendaryWeapon weapon, UUID world, String dimension, int x, int y, int z,
                                                        long now, LegendaryCraftingSession.Pending transaction) throws IOException {
        if (!weapon.isAvailable()) throw new IllegalArgumentException("Esta arma fue retirada.");
        if (sessions.containsKey(weapon)) throw new IllegalArgumentException("Esta arma ya fue reservada o fabricada.");
        if (transaction.kind() != LegendaryCraftingSession.TransactionKind.START) throw new IllegalArgumentException("Transacción incorrecta.");
        if (pending(transaction.player())) throw new IllegalArgumentException("Hay una transacción pendiente.");
        if (all().stream().anyMatch(s -> s.protectsTable() && s.world().equals(world)
            && s.x() == x && s.y() == y && s.z() == z)) throw new IllegalArgumentException("Esta mesa ya tiene un evento.");
        var session = new LegendaryCraftingSession(UUID.randomUUID(), weapon, world, dimension, x, y, z, now,
            Math.addExact(now, LegendaryCraftingSession.DURATION_MILLIS), LegendarySearchAreas.create(x, z, random),
            LegendaryCraftingSession.Stage.CRAFTING, -1, List.of(transaction));
        save(session);
        return session;
    }
    public synchronized LegendaryCraftingSession claim(LegendaryWeapon weapon, LegendaryCraftingSession.Pending transaction) throws IOException {
        if (!weapon.isAvailable()) throw new IllegalArgumentException("Esta arma fue retirada.");
        var session = find(weapon).orElseThrow();
        if (transaction.kind() != LegendaryCraftingSession.TransactionKind.CLAIM) throw new IllegalArgumentException("Transacción incorrecta.");
        if (session.stage() != LegendaryCraftingSession.Stage.READY_TO_CLAIM) {
            throw new IllegalArgumentException("El arma todavía no está disponible para reclamar.");
        }
        if (pending(transaction.player())) throw new IllegalArgumentException("Hay una transacción pendiente.");
        var transactions = new ArrayList<>(session.transactions());
        transactions.add(transaction);
        var claimed = session.update(LegendaryCraftingSession.Stage.CLAIMED, session.announced(), transactions);
        save(claimed);
        return claimed;
    }
    public synchronized void confirm(LegendaryWeapon weapon, UUID nonce) throws IOException {
        var session = find(weapon).orElseThrow();
        if (session.transactions().stream().noneMatch(t -> t.nonce().equals(nonce))) return;
        save(session.update(session.stage(), session.announced(), session.transactions().stream().filter(t -> !t.nonce().equals(nonce)).toList()));
    }
    public synchronized List<Notice> advance(long now) throws IOException {
        List<Notice> notices = new ArrayList<>();
        for (var original : all()) {
            var session = original;
            if (session.stage() == LegendaryCraftingSession.Stage.CRAFTING && now >= session.deadline()) {
                session = session.update(LegendaryCraftingSession.Stage.READY_TO_CLAIM, session.announced(), session.transactions());
                save(session);
            }
            int due = session.dueNotice(now);
            for (int index = session.announced() + 1; index <= due; index++) {
                session = session.update(session.stage(), index, session.transactions());
                save(session); // Commit before broadcasting: a restart cannot duplicate this notice.
                notices.add(new Notice(session, index));
            }
        }
        return List.copyOf(notices);
    }
    private void save(LegendaryCraftingSession session) throws IOException {
        try { store.put(key(session.weapon()), session.encode()); }
        finally {
            // A rename can commit even when directory fsync reports failure.
            String committed = store.get(key(session.weapon()));
            if (committed != null) sessions.put(session.weapon(), LegendaryCraftingSession.decode(committed));
        }
    }
    private static String key(LegendaryWeapon weapon) { return "pvp." + weapon.id; }
}
