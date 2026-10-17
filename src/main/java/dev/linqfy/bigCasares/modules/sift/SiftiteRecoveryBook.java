package dev.linqfy.bigCasares.modules.sift;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The bookkeeping of the Siftite recovery ({@code PENDING} and {@code DUE_AT} of the mod's
 * {@code SiftiteRecoveryManager}): the serialised stacks each player is owed, persisted through a
 * {@link Store}, and when each player's recovery is due. It holds bytes, not stacks, so it is tested
 * without a server; {@link SiftiteRecovery} serialises and deserialises the stacks.
 *
 * <p>The recovery starts {@value #RECOVERY_DELAY_TICKS} ticks after a respawn ({@code AFTER_RESPAWN}
 * replaces an earlier schedule) or after a join ({@code JOIN} keeps one that exists), and only for players
 * who are owed something. Capturing a death appends to what is already owed, so deaths before a recovery
 * accumulate.</p>
 *
 * <p>Differences: the mod hands the whole list to a mini rift entity and clears its storage at that moment,
 * after which the items live in entities. Here an entry stays in the store until the item has reached the
 * player's inventory ({@link #delivered}), so a restart or a disconnect in the middle of a recovery loses
 * nothing and the recovery simply starts again at the next join. The mod keeps its storage in the
 * server's command storage; this one lives in the plugin's data folder ({@link SiftiteRecoveryFiles}).</p>
 */
final class SiftiteRecoveryBook {

    /** Ticks between a respawn or join and the start of the recovery. */
    static final long RECOVERY_DELAY_TICKS = 40L;

    /** Persistence of one player's owed stacks; an empty list means "nothing owed". */
    interface Store {
        List<byte[]> load(UUID player) throws IOException;

        void save(UUID player, List<byte[]> entries) throws IOException;
    }

    private final Store store;
    private final Map<UUID, List<byte[]>> owed = new HashMap<>();
    private final Map<UUID, Long> dueAt = new LinkedHashMap<>();

    SiftiteRecoveryBook(Store store) {
        this.store = store;
    }

    /** What the player is owed, oldest first. */
    List<byte[]> pending(UUID player) throws IOException {
        return List.copyOf(current(player));
    }

    boolean hasPending(UUID player) throws IOException {
        return !current(player).isEmpty();
    }

    /** Adds stacks to what the player is owed and persists. On failure nothing changes. */
    void capture(UUID player, List<byte[]> entries) throws IOException {
        if (entries.isEmpty()) {
            return;
        }
        List<byte[]> merged = new ArrayList<>(current(player));
        entries.forEach(entry -> merged.add(entry.clone()));
        store.save(player, merged);
        owed.put(player, merged);
    }

    /** {@code AFTER_RESPAWN}: due in {@value #RECOVERY_DELAY_TICKS} ticks, replacing an earlier schedule. */
    boolean afterRespawn(UUID player, long now) throws IOException {
        if (!hasPending(player)) {
            return false;
        }
        dueAt.put(player, now + RECOVERY_DELAY_TICKS);
        return true;
    }

    /** {@code JOIN}: due in {@value #RECOVERY_DELAY_TICKS} ticks unless a schedule exists. */
    boolean afterJoin(UUID player, long now) throws IOException {
        if (!hasPending(player)) {
            return false;
        }
        dueAt.putIfAbsent(player, now + RECOVERY_DELAY_TICKS);
        return true;
    }

    /** Schedules a fresh recovery, for a recovery that had to be interrupted. */
    void reschedule(UUID player, long now) {
        dueAt.put(player, now + RECOVERY_DELAY_TICKS);
    }

    /** Players whose time has come and who pass {@code eligible} (online and alive), in schedule order. */
    List<UUID> ready(long now, Predicate<UUID> eligible) {
        List<UUID> ready = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : dueAt.entrySet()) {
            if (now >= entry.getValue() && eligible.test(entry.getKey())) {
                ready.add(entry.getKey());
            }
        }
        return ready;
    }

    /** The recovery of this player has started: it is no longer due. */
    void started(UUID player) {
        dueAt.remove(player);
    }

    boolean isDue(UUID player) {
        return dueAt.containsKey(player);
    }

    /** An item reached the player: forget its entry (the first equal one) and persist. */
    boolean delivered(UUID player, byte[] entry) throws IOException {
        List<byte[]> remaining = new ArrayList<>(current(player));
        if (!removeFirst(remaining, entry)) {
            return false;
        }
        store.save(player, remaining);
        owed.put(player, remaining);
        return true;
    }

    /** Part of an item reached the player: replace its entry by what is left. */
    boolean partlyDelivered(UUID player, byte[] entry, byte[] leftover) throws IOException {
        List<byte[]> remaining = new ArrayList<>(current(player));
        for (int index = 0; index < remaining.size(); index++) {
            if (Arrays.equals(remaining.get(index), entry)) {
                remaining.set(index, leftover.clone());
                store.save(player, remaining);
                owed.put(player, remaining);
                return true;
            }
        }
        return false;
    }

    /** The player left: drop what is cached (the store keeps the entries) and any schedule. */
    void forget(UUID player) {
        owed.remove(player);
        dueAt.remove(player);
    }

    /** {@code clearTransientState}: forget every schedule and cached list; the store is untouched. */
    void clearTransient() {
        owed.clear();
        dueAt.clear();
    }

    private List<byte[]> current(UUID player) throws IOException {
        List<byte[]> cached = owed.get(player);
        if (cached == null) {
            cached = new ArrayList<>(store.load(player));
            owed.put(player, cached);
        }
        return cached;
    }

    private static boolean removeFirst(List<byte[]> entries, byte[] entry) {
        for (Iterator<byte[]> iterator = entries.iterator(); iterator.hasNext(); ) {
            if (Arrays.equals(iterator.next(), entry)) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }
}
