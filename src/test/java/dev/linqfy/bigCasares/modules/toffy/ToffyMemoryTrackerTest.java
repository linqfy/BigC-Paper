package dev.linqfy.bigCasares.modules.toffy;

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

class ToffyMemoryTrackerTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000007");

    private final ToffySettings settings = ToffySettingsLoaderTest.bundledSettings();
    private final ToffyService service = new ToffyService(settings);
    private final InMemoryStorage storage = new InMemoryStorage();
    private final AtomicLong clock = new AtomicLong();
    private final List<String> warnings = new ArrayList<>();
    private ToffyMemorySettings memorySettings = new ToffyMemorySettings(true, 3_600_000L, true, 20);
    private final ToffyMemoryTracker tracker = new ToffyMemoryTracker(
        () -> new ToffyMemoryService(memorySettings), storage, clock::get, warnings::add);

    private static ToffyLoadout netherite() {
        ToffyLoadout.Builder builder = ToffyLoadout.builder();
        for (ToffyArmorSlot slot : ToffyArmorSlot.values()) {
            builder.armor(slot, ToffyItem.of("netherite_" + slot.configKey()));
        }
        return builder.weapon(ToffyItem.of("netherite_sword")).build();
    }

    @Test
    void checksReturnTheCurrentAndTheRealLevel() {
        tracker.check(PLAYER, service, netherite());
        clock.set(1_000L);

        ToffyMemoryTracker.Check check = tracker.check(PLAYER, service, ToffyLoadout.empty());

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

        tracker.check(PLAYER, service, ToffyLoadout.builder()
            .armor(ToffyArmorSlot.HELMET, ToffyItem.of("netherite_helmet", Map.of("protection", 4)))
            .build());
        assertEquals(2, storage.saves);
    }

    @Test
    void quittingSavesAndForgetsTheCachedMemory() {
        tracker.check(PLAYER, service, netherite());
        clock.set(9_000L);
        tracker.check(PLAYER, service, netherite());

        tracker.quit(PLAYER);

        assertEquals(9_000L, storage.stored.get(PLAYER).get(ToffyMemorySlot.HELMET).lastSeenMillis());
        storage.stored.put(PLAYER, ToffyMemory.empty());
        assertTrue(tracker.memory(PLAYER).isEmpty());
    }

    @Test
    void theMemoryIsLoadedFromStorageTheFirstTime() {
        storage.stored.put(PLAYER, new ToffyMemory(Map.of(ToffyMemorySlot.WEAPON,
            new ToffyMemory.Remembered(ToffyItem.of("trident"), 0L))));

        ToffyMemoryTracker.Check check = tracker.check(PLAYER, service, ToffyLoadout.empty());

        assertEquals("trident", check.real().weapon().family());
    }

    @Test
    void dyingClearsAndSavesTheMemory() {
        tracker.check(PLAYER, service, netherite());

        tracker.death(PLAYER);

        assertTrue(storage.stored.get(PLAYER).isEmpty());
        assertEquals(0.0, tracker.check(PLAYER, service, ToffyLoadout.empty()).real().value());
    }

    @Test
    void nothingIsRememberedBetweenTheDeathAndTheRespawn() {
        tracker.check(PLAYER, service, netherite());
        tracker.death(PLAYER);

        ToffyMemoryTracker.Check whileDead = tracker.check(PLAYER, service, netherite());
        assertTrue(whileDead.memory().isEmpty());

        tracker.respawn(PLAYER);
        assertTrue(!tracker.check(PLAYER, service, netherite()).memory().isEmpty());
    }

    @Test
    void lastSeenTimesAreSavedEveryFewMinutes() {
        tracker.check(PLAYER, service, netherite());
        clock.set(ToffyMemoryTracker.LAST_SEEN_FLUSH_MILLIS - 1);
        tracker.check(PLAYER, service, netherite());
        assertEquals(1, storage.saves);

        clock.set(ToffyMemoryTracker.LAST_SEEN_FLUSH_MILLIS);
        tracker.check(PLAYER, service, netherite());
        assertEquals(2, storage.saves);
        assertEquals(ToffyMemoryTracker.LAST_SEEN_FLUSH_MILLIS,
            storage.stored.get(PLAYER).get(ToffyMemorySlot.HELMET).lastSeenMillis());
    }

    @Test
    void peekingAnOfflinePlayerLeavesOutForgottenItemsAndDoesNotKeepIt() {
        storage.stored.put(PLAYER, new ToffyMemory(Map.of(
            ToffyMemorySlot.WEAPON, new ToffyMemory.Remembered(ToffyItem.of("trident"), 0L),
            ToffyMemorySlot.TOOL, new ToffyMemory.Remembered(ToffyItem.of("shears"), 3_000_000L))));
        clock.set(3_600_000L);

        ToffyMemory peeked = tracker.peek(PLAYER);
        tracker.saveAll();

        assertEquals(1, peeked.slots().size());
        assertEquals("minecraft:shears", peeked.get(ToffyMemorySlot.TOOL).item().key());
        assertEquals(0, storage.saves);
    }

    @Test
    void anUnreadableFileStartsEmptyWithAWarning() {
        storage.failLoad = true;

        assertTrue(tracker.memory(PLAYER).isEmpty());
        assertEquals(1, warnings.size());
    }

    @Test
    void whenDisabledTheStoredMemoryIsLeftAloneWithoutForgetOnDeath() {
        ToffyMemory stored = new ToffyMemory(Map.of(ToffyMemorySlot.WEAPON,
            new ToffyMemory.Remembered(ToffyItem.of("trident"), 0L)));
        storage.stored.put(PLAYER, stored);
        memorySettings = new ToffyMemorySettings(false, 3_600_000L, false, 20);

        ToffyMemoryTracker.Check check = tracker.check(PLAYER, service, ToffyLoadout.empty());
        tracker.death(PLAYER);

        assertEquals(check.current(), check.real());
        assertEquals(stored, storage.stored.get(PLAYER));
        assertEquals(0, storage.saves);
    }

    @Test
    void aDeathWhileDisabledStillForgetsWithForgetOnDeath() {
        storage.stored.put(PLAYER, new ToffyMemory(Map.of(ToffyMemorySlot.WEAPON,
            new ToffyMemory.Remembered(ToffyItem.of("trident"), 0L))));
        memorySettings = new ToffyMemorySettings(false, 3_600_000L, true, 20);

        tracker.death(PLAYER);
        memorySettings = new ToffyMemorySettings(true, 3_600_000L, true, 20);

        assertTrue(storage.stored.get(PLAYER).isEmpty());
        assertEquals(1, storage.saves);
        assertTrue(tracker.check(PLAYER, service, ToffyLoadout.empty()).memory().isEmpty());
        assertTrue(!tracker.check(PLAYER, service, ToffyLoadout.empty()).real().hasWeapon());
    }

    private static final class InMemoryStorage implements ToffyMemoryStorage {
        private final Map<UUID, ToffyMemory> stored = new HashMap<>();
        private int saves;
        private boolean failLoad;

        @Override
        public ToffyMemory load(UUID playerId) throws IOException {
            if (failLoad) {
                throw new IOException("broken file");
            }
            return stored.getOrDefault(playerId, ToffyMemory.empty());
        }

        @Override
        public void save(UUID playerId, ToffyMemory memory) {
            stored.put(playerId, memory);
            saves++;
        }
    }
}
