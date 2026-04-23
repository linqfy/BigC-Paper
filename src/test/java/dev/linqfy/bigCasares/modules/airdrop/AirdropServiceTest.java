package dev.linqfy.bigCasares.modules.airdrop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirdropServiceTest {

    private static final int RADIUS = 500;
    private static final int DROP_HEIGHT = 30;

    private AirdropService service;
    private InMemoryAirdropStorage storage;
    private AirdropWorldGateway gateway;
    private int groundY;

    @BeforeEach
    void setUp() {
        AirdropSettings settings = new AirdropSettings(30, RADIUS, DROP_HEIGHT);
        groundY = 64;
        gateway = new FakeWorldGateway(groundY, true, true, true);
        storage = new InMemoryAirdropStorage();
        service = new AirdropService(settings, gateway, storage, new Random(1L));
    }

    @Test
    void generatesPositionWithinRadius() {
        for (int i = 0; i < 50; i++) {
            Optional<AirdropData> result = service.spawnAirdrop();
            assertTrue(result.isPresent());
            AirdropPosition pos = result.get().position();
            assertTrue(pos.x() >= -RADIUS && pos.x() <= RADIUS, "x out of radius: " + pos.x());
            assertTrue(pos.z() >= -RADIUS && pos.z() <= RADIUS, "z out of radius: " + pos.z());
            service.markLanded(new AirdropPosition(pos.x(), groundY + 1, pos.z()));
            service.claim();
        }
    }

    @Test
    void spawnYIsGroundPlusDropHeight() {
        Optional<AirdropData> result = service.spawnAirdrop();
        assertTrue(result.isPresent());
        int expectedY = groundY + DROP_HEIGHT;
        assertEquals(expectedY, result.get().position().y());
    }

    @Test
    void retriesInvalidLocation() {
        groundY = 64;
        gateway = new FakeWorldGateway(groundY, false, true, true);
        storage = new InMemoryAirdropStorage();
        AirdropSettings settings = new AirdropSettings(30, RADIUS, DROP_HEIGHT);
        service = new AirdropService(settings, gateway, storage, new Random(2L));

        Optional<AirdropData> result = service.spawnAirdrop();
        assertTrue(result.isEmpty(), "Should fail all 5 retries when all locations are invalid");
    }

    @Test
    void retriesUntilValidLocationFound() {
        gateway = new CountdownGateway(3);
        storage = new InMemoryAirdropStorage();
        AirdropSettings settings = new AirdropSettings(30, RADIUS, DROP_HEIGHT);
        service = new AirdropService(settings, gateway, storage, new Random(3L));

        Optional<AirdropData> result = service.spawnAirdrop();
        assertTrue(result.isPresent(), "Should succeed after retries");
    }

    @Test
    void retriesWhenChestSpaceIsBlocked() {
        groundY = 64;
        gateway = new FakeWorldGateway(groundY, true, false, true);
        storage = new InMemoryAirdropStorage();
        AirdropSettings settings = new AirdropSettings(30, RADIUS, DROP_HEIGHT);
        service = new AirdropService(settings, gateway, storage, new Random(4L));

        Optional<AirdropData> result = service.spawnAirdrop();
        assertTrue(result.isEmpty());
    }

    @Test
    void retriesWhenHeadSpaceIsBlocked() {
        groundY = 64;
        gateway = new FakeWorldGateway(groundY, true, true, false);
        storage = new InMemoryAirdropStorage();
        AirdropSettings settings = new AirdropSettings(30, RADIUS, DROP_HEIGHT);
        service = new AirdropService(settings, gateway, storage, new Random(5L));

        Optional<AirdropData> result = service.spawnAirdrop();
        assertTrue(result.isEmpty());
    }

    @Test
    void selectsRandomDropType() {
        boolean foundHE = false;
        boolean foundLuxury = false;
        boolean foundEnchant = false;
        for (int i = 0; i < 300; i++) {
            AirdropType type = service.selectRandomType();
            if (type == AirdropType.HE) foundHE = true;
            if (type == AirdropType.LUXURY) foundLuxury = true;
            if (type == AirdropType.ENCHANT) foundEnchant = true;
            if (foundHE && foundLuxury && foundEnchant) break;
        }
        assertTrue(foundHE, "HE should appear in random selection");
        assertTrue(foundLuxury, "LUXURY should appear in random selection");
        assertTrue(foundEnchant, "ENCHANT should appear in random selection");
    }

    @Test
    void cannotSpawnWhenActive() {
        service.spawnAirdrop();
        assertTrue(service.isActive());

        Optional<AirdropData> second = service.spawnAirdrop();
        assertTrue(second.isEmpty(), "Should not spawn a second airdrop while one is active");
    }

    @Test
    void claimResetsState() {
        AirdropData spawned = service.spawnAirdrop().orElseThrow();
        service.markLanded(new AirdropPosition(spawned.position().x(), groundY + 1, spawned.position().z()));
        assertTrue(service.isActive());

        boolean claimed = service.claim();
        assertTrue(claimed);
        assertFalse(service.isActive());
    }

    @Test
    void claimReturnsFalseWhenNothingActive() {
        assertFalse(service.claim());
    }

    @Test
    void claimReturnsFalseWhileDropIsStillFalling() {
        service.spawnAirdrop();

        assertFalse(service.claim());
    }

    @Test
    void transitionsFromFallingToLandedToClaimed() {
        Optional<AirdropData> spawned = service.spawnAirdrop();

        assertTrue(spawned.isPresent());
        assertEquals(AirdropPhase.FALLING, spawned.get().phase());

        AirdropPosition landedPosition = new AirdropPosition(spawned.get().position().x(), groundY + 1, spawned.get().position().z());
        Optional<AirdropData> landed = service.markLanded(landedPosition);
        assertTrue(landed.isPresent());
        assertEquals(AirdropPhase.LANDED, landed.get().phase());
        assertEquals(landedPosition, landed.get().position());

        assertTrue(service.claim());
        assertEquals(AirdropPhase.CLAIMED, service.getCurrentDrop().orElseThrow().phase());
    }

    @Test
    void getLootForCurrentReturnsEntriesForActiveType() {
        service.spawnAirdrop();
        assertTrue(service.getLootForCurrent().size() > 0);
    }

    @Test
    void getLootForCurrentReturnsEmptyWhenNoActiveDrop() {
        assertTrue(service.getLootForCurrent().isEmpty());
    }

    @Test
    void spawnedDropIsPersisted() {
        service.spawnAirdrop();
        assertNotNull(storage.lastSaved);
        assertEquals(AirdropPhase.FALLING, storage.lastSaved.phase());
    }

    @Test
    void landedDropIsPersisted() {
        AirdropData spawned = service.spawnAirdrop().orElseThrow();
        service.markLanded(new AirdropPosition(spawned.position().x(), groundY + 1, spawned.position().z()));
        assertEquals(AirdropPhase.LANDED, storage.lastSaved.phase());
    }

    @Test
    void claimedDropIsPersistedAsClaimed() {
        AirdropData spawned = service.spawnAirdrop().orElseThrow();
        service.markLanded(new AirdropPosition(spawned.position().x(), groundY + 1, spawned.position().z()));
        service.claim();
        assertEquals(AirdropPhase.CLAIMED, storage.lastSaved.phase());
    }

    @Test
    void restoredActiveDropPreventsDuplicateSpawn() {
        storage.stored = new AirdropData(new AirdropPosition(0, 90, 0), AirdropType.HE, AirdropPhase.LANDED);
        AirdropSettings settings = new AirdropSettings(30, RADIUS, DROP_HEIGHT);
        service = new AirdropService(settings, gateway, storage, new Random(6L));

        assertTrue(service.spawnAirdrop().isEmpty());
    }

    @Test
    void restoredClaimedDropAllowsNewSpawn() {
        storage.stored = new AirdropData(new AirdropPosition(0, 90, 0), AirdropType.HE, AirdropPhase.CLAIMED);
        AirdropSettings settings = new AirdropSettings(30, RADIUS, DROP_HEIGHT);
        service = new AirdropService(settings, gateway, storage, new Random(7L));

        assertTrue(service.spawnAirdrop().isPresent());
    }

    private static final class InMemoryAirdropStorage implements AirdropStorage {
        private AirdropData lastSaved;
        private AirdropData stored;

        @Override
        public void save(AirdropData data) {
            lastSaved = data;
            stored = data;
        }

        @Override
        public Optional<AirdropData> load() {
            return Optional.ofNullable(stored);
        }

        @Override
        public void clear() {
            stored = null;
            lastSaved = null;
        }
    }

    private static final class FakeWorldGateway implements AirdropWorldGateway {
        private final int groundY;
        private final boolean safeGround;
        private final boolean safeChestSpace;
        private final boolean safeHeadSpace;

        private FakeWorldGateway(int groundY, boolean safeGround, boolean safeChestSpace, boolean safeHeadSpace) {
            this.groundY = groundY;
            this.safeGround = safeGround;
            this.safeChestSpace = safeChestSpace;
            this.safeHeadSpace = safeHeadSpace;
        }

        @Override
        public int getHighestBlockY(int x, int z) {
            return groundY;
        }

        @Override
        public boolean isSafeLandingBlock(int x, int y, int z) {
            return safeGround;
        }

        @Override
        public boolean isSafeOpenSpace(int x, int y, int z) {
            if (y == groundY + 1) {
                return safeChestSpace;
            }
            if (y == groundY + 2) {
                return safeHeadSpace;
            }
            return true;
        }
    }

    private static final class CountdownGateway implements AirdropWorldGateway {
        private int remainingInvalid;

        private CountdownGateway(int failCount) {
            this.remainingInvalid = failCount;
        }

        @Override
        public int getHighestBlockY(int x, int z) {
            return 64;
        }

        @Override
        public boolean isSafeLandingBlock(int x, int y, int z) {
            if (remainingInvalid > 0) {
                remainingInvalid--;
                return false;
            }
            return true;
        }

        @Override
        public boolean isSafeOpenSpace(int x, int y, int z) {
            return true;
        }
    }
}
