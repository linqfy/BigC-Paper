package dev.linqfy.bigCasares.modules.cosmicniv;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosmicNivMemoryTrackerTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000007");

    private final CosmicNivSettings settings = CosmicNivSettingsLoaderTest.bundledSettings();
    private final CosmicNivService service = new CosmicNivService(settings);
    private final InMemoryStorage storage = new InMemoryStorage();
    private final AtomicLong clock = new AtomicLong();
    private final List<String> warnings = new ArrayList<>();
    private CosmicNivMemorySettings memorySettings = new CosmicNivMemorySettings(true, 3_600_000L, true, 20);
    private final CosmicNivMemoryTracker tracker = new CosmicNivMemoryTracker(
        () -> new CosmicNivMemoryService(memorySettings), storage, clock::get, warnings::add);

    private static CosmicNivLoadout netherite() {
        CosmicNivLoadout.Builder builder = CosmicNivLoadout.builder();
        for (CosmicNivArmorSlot slot : CosmicNivArmorSlot.values()) {
            builder.armor(slot, CosmicNivItem.of("netherite_" + slot.configKey()));
        }
        return builder.weapon(CosmicNivItem.of("netherite_sword")).build();
    }

    @Test
    void checksReturnTheCurrentAndTheRealLevel() {
        tracker.check(PLAYER, service, netherite());
        clock.set(1_000L);

        CosmicNivMemoryTracker.Check check = tracker.check(PLAYER, service, CosmicNivLoadout.empty());

        assertEquals(0.0, check.current().value());
        assertEquals(service.evaluate(netherite()).value(), check.real().value());
    }

    @Test
    void itSavesWhenTheRememberedItemsChangeButNotForTimeOnly() {
        tracker.check(PLAYER, service, netherite());
        tracker.check(PLAYER, service, netherite());
        clock.set(5_000L);
        tracker.check(PLAYER, service, netherite());

        assertEquals(1, storage.saves);

        tracker.check(PLAYER, service, CosmicNivLoadout.builder()
            .armor(CosmicNivArmorSlot.HELMET, CosmicNivItem.of("netherite_helmet", Map.of("protection", 4)))
            .build());
        assertEquals(2, storage.saves);
    }

    @Test
    void quittingSavesAndForgetsTheCachedMemory() {
        tracker.check(PLAYER, service, netherite());
        clock.set(9_000L);
        tracker.check(PLAYER, service, netherite());

        tracker.quit(PLAYER);

        assertEquals(9_000L, storage.stored.get(PLAYER).get(CosmicNivMemorySlot.HELMET).lastSeenMillis());
        storage.stored.put(PLAYER, CosmicNivMemory.empty());
        assertTrue(tracker.memory(PLAYER).isEmpty());
    }

    @Test
    void theMemoryIsLoadedFromStorageTheFirstTime() {
        storage.stored.put(PLAYER, new CosmicNivMemory(Map.of(CosmicNivMemorySlot.WEAPON,
            new CosmicNivMemory.Remembered(CosmicNivItem.of("trident"), 0L))));

        CosmicNivMemoryTracker.Check check = tracker.check(PLAYER, service, CosmicNivLoadout.empty());

        assertEquals("trident", check.real().weapon().family());
    }

    @Test
    void dyingClearsAndSavesTheMemory() {
        tracker.check(PLAYER, service, netherite());

        tracker.death(PLAYER);

        assertTrue(storage.stored.get(PLAYER).isEmpty());
        assertEquals(0.0, tracker.check(PLAYER, service, CosmicNivLoadout.empty()).real().value());
    }

    @Test
    void nothingIsRememberedBetweenTheDeathAndTheRespawn() {
        tracker.check(PLAYER, service, netherite());
        tracker.death(PLAYER);

        CosmicNivMemoryTracker.Check whileDead = tracker.check(PLAYER, service, netherite());
        assertTrue(whileDead.memory().isEmpty());

        tracker.respawn(PLAYER);
        assertTrue(!tracker.check(PLAYER, service, netherite()).memory().isEmpty());
    }

    @Test
    void lastSeenTimesAreSavedEveryFewMinutes() {
        tracker.check(PLAYER, service, netherite());
        clock.set(CosmicNivMemoryTracker.LAST_SEEN_FLUSH_MILLIS - 1);
        tracker.check(PLAYER, service, netherite());
        assertEquals(1, storage.saves);

        clock.set(CosmicNivMemoryTracker.LAST_SEEN_FLUSH_MILLIS);
        tracker.check(PLAYER, service, netherite());
        assertEquals(2, storage.saves);
        assertEquals(CosmicNivMemoryTracker.LAST_SEEN_FLUSH_MILLIS,
            storage.stored.get(PLAYER).get(CosmicNivMemorySlot.HELMET).lastSeenMillis());
    }

    @Test
    void peekingAnOfflinePlayerLeavesOutForgottenItemsAndDoesNotKeepIt() {
        storage.stored.put(PLAYER, new CosmicNivMemory(Map.of(
            CosmicNivMemorySlot.WEAPON, new CosmicNivMemory.Remembered(CosmicNivItem.of("trident"), 0L),
            CosmicNivMemorySlot.TOOL, new CosmicNivMemory.Remembered(CosmicNivItem.of("shears"), 3_000_000L))));
        clock.set(3_600_000L);

        CosmicNivMemory peeked = tracker.peek(PLAYER);
        tracker.saveAll();

        assertEquals(1, peeked.slots().size());
        assertEquals("minecraft:shears", peeked.get(CosmicNivMemorySlot.TOOL).item().key());
        assertEquals(0, storage.saves);
    }

    @Test
    void anUnreadableFileStartsEmptyWithAWarning() {
        storage.failLoad = true;

        assertTrue(tracker.memory(PLAYER).isEmpty());
        assertEquals(1, warnings.size());
    }

    @Test
    void whenDisabledTheStoredMemoryIsLeftAlone() {
        CosmicNivMemory stored = new CosmicNivMemory(Map.of(CosmicNivMemorySlot.WEAPON,
            new CosmicNivMemory.Remembered(CosmicNivItem.of("trident"), 0L)));
        storage.stored.put(PLAYER, stored);
        memorySettings = new CosmicNivMemorySettings(false, 3_600_000L, true, 20);

        CosmicNivMemoryTracker.Check check = tracker.check(PLAYER, service, CosmicNivLoadout.empty());
        tracker.death(PLAYER);

        assertEquals(check.current(), check.real());
        assertEquals(stored, storage.stored.get(PLAYER));
        assertEquals(0, storage.saves);
    }

    private static final class InMemoryStorage implements CosmicNivMemoryStorage {
        private final Map<UUID, CosmicNivMemory> stored = new HashMap<>();
        private int saves;
        private boolean failLoad;

        @Override
        public CosmicNivMemory load(UUID playerId) throws IOException {
            if (failLoad) {
                throw new IOException("broken file");
            }
            return stored.getOrDefault(playerId, CosmicNivMemory.empty());
        }

        @Override
        public void save(UUID playerId, CosmicNivMemory memory) {
            stored.put(playerId, memory);
            saves++;
        }
    }
}
