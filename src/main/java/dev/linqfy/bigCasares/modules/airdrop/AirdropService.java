package dev.linqfy.bigCasares.modules.airdrop;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class AirdropService {

    private static final int MAX_LOCATION_RETRIES = 5;

    private final AirdropSettings settings;
    private final AirdropWorldGateway world;
    private final AirdropStorage storage;
    private final Random random;

    private Optional<AirdropData> currentDrop;

    public AirdropService(AirdropSettings settings, AirdropWorldGateway world, AirdropStorage storage) {
        this(settings, world, storage, new Random());
    }

    AirdropService(AirdropSettings settings, AirdropWorldGateway world, AirdropStorage storage, Random random) {
        this.settings = settings;
        this.world = world;
        this.storage = storage;
        this.random = random;
        this.currentDrop = storage.load();
    }

    public boolean isActive() {
        return currentDrop.map(AirdropData::isActive).orElse(false);
    }

    public Optional<AirdropData> spawnAirdrop() {
        if (isActive()) {
            return Optional.empty();
        }

        Optional<AirdropPosition> position = generateValidPosition();
        if (position.isEmpty()) {
            return Optional.empty();
        }

        AirdropType type = selectRandomType();
        AirdropData data = new AirdropData(position.get(), type, AirdropPhase.FALLING);
        currentDrop = Optional.of(data);
        storage.save(data);
        return Optional.of(data);
    }

    public Optional<AirdropData> markLanded(AirdropPosition landedPosition) {
        if (currentDrop.isEmpty() || currentDrop.get().phase() != AirdropPhase.FALLING) {
            return Optional.empty();
        }

        AirdropData landed = currentDrop.get().withPositionAndPhase(landedPosition, AirdropPhase.LANDED);
        currentDrop = Optional.of(landed);
        storage.save(landed);
        return Optional.of(landed);
    }

    public boolean claim() {
        if (currentDrop.isEmpty() || currentDrop.get().phase() != AirdropPhase.LANDED) {
            return false;
        }

        AirdropData claimed = currentDrop.get().withPhase(AirdropPhase.CLAIMED);
        currentDrop = Optional.of(claimed);
        storage.save(claimed);
        return true;
    }

    public Optional<AirdropData> getCurrentDrop() {
        return currentDrop;
    }

    public List<AirdropLootEntry> getLootForCurrent() {
        return currentDrop
                .filter(AirdropData::isActive)
                .map(AirdropData::type)
                .map(AirdropLootTable::forType)
                .orElseGet(List::of);
    }

    public AirdropType selectRandomType() {
        AirdropType[] values = AirdropType.values();
        return values[random.nextInt(values.length)];
    }

    private Optional<AirdropPosition> generateValidPosition() {
        for (int attempt = 0; attempt < MAX_LOCATION_RETRIES; attempt++) {
            int x = random.nextInt(settings.radius() * 2 + 1) - settings.radius();
            int z = random.nextInt(settings.radius() * 2 + 1) - settings.radius();
            int groundY = world.getHighestBlockY(x, z);
            int chestY = groundY + 1;

            if (!world.isSafeLandingBlock(x, groundY, z)) {
                continue;
            }
            if (!world.isSafeOpenSpace(x, chestY, z)) {
                continue;
            }
            if (!world.isSafeOpenSpace(x, chestY + 1, z)) {
                continue;
            }

            int spawnY = groundY + settings.dropHeight();
            return Optional.of(new AirdropPosition(x, spawnY, z));
        }
        return Optional.empty();
    }

    public AirdropSettings getSettings() {
        return settings;
    }
}
