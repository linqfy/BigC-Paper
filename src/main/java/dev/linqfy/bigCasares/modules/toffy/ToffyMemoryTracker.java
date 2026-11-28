package dev.linqfy.bigCasares.modules.toffy;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Keeps the memory of the players being checked, loading it lazily from storage. It is saved whenever the remembered
 * items change, and at most every {@link #LAST_SEEN_FLUSH_MILLIS} when only "last seen" times moved, on quit and on
 * {@link #saveAll()}. Main thread only.
 */
final class ToffyMemoryTracker {
    static final long LAST_SEEN_FLUSH_MILLIS = 5 * 60_000L;

    private final Supplier<ToffyMemoryService> rules;
    private final ToffyMemoryStorage storage;
    private final LongSupplier clock;
    private final Consumer<String> warnings;
    private final Map<UUID, ToffyMemory> memories = new HashMap<>();
    private final Map<UUID, Long> lastSaved = new HashMap<>();
    private final Set<UUID> dead = new HashSet<>();

    ToffyMemoryTracker(Supplier<ToffyMemoryService> rules, ToffyMemoryStorage storage, LongSupplier clock,
                           Consumer<String> warnings) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    /**
     * Updates the memory of {@code playerId} with what the player has now and returns both levels. While the player
     * is dead the memory is not updated: with forget-on-death it was already emptied by {@link #death}, otherwise the
     * remembered items still count. The inventory is only emptied after the death event, so nothing new is learned.
     */
    Check check(UUID playerId, ToffyService service, ToffyLoadout current) {
        ToffyResult currentResult = service.evaluate(current);
        ToffyMemoryService activeRules = rules.get();
        if (!activeRules.settings().enabled()) {
            return new Check(currentResult, currentResult, ToffyMemory.empty());
        }
        ToffyMemory previous = memory(playerId);
        if (dead.contains(playerId)) {
            return new Check(currentResult, service.evaluate(activeRules.realLoadout(current, previous)), previous);
        }
        long now = clock.getAsLong();
        ToffyMemory next = activeRules.observe(service, previous, currentResult, now);
        memories.put(playerId, next);
        if (!next.sameItems(previous)
            || (!next.equals(previous) && now - lastSaved.getOrDefault(playerId, 0L) >= LAST_SEEN_FLUSH_MILLIS)) {
            save(playerId, next);
        }
        ToffyResult real = service.evaluate(activeRules.realLoadout(current, next));
        return new Check(currentResult, real, next);
    }

    /**
     * Applies forget-on-death even while memory is disabled, so re-enabling it never brings back forgotten pieces.
     */
    void death(UUID playerId) {
        ToffyMemoryService activeRules = rules.get();
        if (activeRules.settings().enabled()) {
            ToffyMemory next = activeRules.afterDeath(memory(playerId));
            memories.put(playerId, next);
            dead.add(playerId);
            save(playerId, next);
        } else if (activeRules.settings().forgetOnDeath()) {
            memories.put(playerId, ToffyMemory.empty());
            save(playerId, ToffyMemory.empty());
        }
    }

    void respawn(UUID playerId) {
        dead.remove(playerId);
    }

    void quit(UUID playerId) {
        dead.remove(playerId);
        lastSaved.remove(playerId);
        ToffyMemory memory = memories.remove(playerId);
        if (memory != null) {
            save(playerId, memory);
        }
    }

    void saveAll() {
        for (Map.Entry<UUID, ToffyMemory> entry : List.copyOf(memories.entrySet())) {
            save(entry.getKey(), entry.getValue());
        }
    }

    /**
     * The memory of a player this tracker is checking, loading it the first time.
     */
    ToffyMemory memory(UUID playerId) {
        ToffyMemory memory = memories.get(playerId);
        if (memory == null) {
            memory = read(playerId);
            memories.put(playerId, memory);
            lastSaved.put(playerId, clock.getAsLong());
        }
        return memory;
    }

    /**
     * What {@code playerId} is remembered to own right now, without caching it, so offline players are not kept in
     * memory. Forgotten items are left out.
     */
    ToffyMemory peek(UUID playerId) {
        ToffyMemoryService activeRules = rules.get();
        ToffyMemory memory = memories.get(playerId);
        return activeRules.withoutExpired(memory == null ? read(playerId) : memory, clock.getAsLong());
    }

    private ToffyMemory read(UUID playerId) {
        try {
            return storage.load(playerId);
        } catch (IOException invalid) {
            warnings.accept("Could not read the equipment memory of " + playerId + ", starting empty: "
                + invalid.getMessage());
            return ToffyMemory.empty();
        }
    }

    private void save(UUID playerId, ToffyMemory memory) {
        try {
            storage.save(playerId, memory);
            lastSaved.put(playerId, clock.getAsLong());
        } catch (IOException failure) {
            warnings.accept("Could not save the equipment memory of " + playerId + ": " + failure.getMessage());
        }
    }

    /**
     * @param current the level with what the player has now
     * @param real    the level counting the remembered items too
     * @param memory  the memory after this check
     */
    record Check(ToffyResult current, ToffyResult real, ToffyMemory memory) {
    }
}
