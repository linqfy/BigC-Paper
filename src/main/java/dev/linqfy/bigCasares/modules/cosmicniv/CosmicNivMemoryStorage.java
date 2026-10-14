package dev.linqfy.bigCasares.modules.cosmicniv;

import java.io.IOException;
import java.util.UUID;

interface CosmicNivMemoryStorage {
    /**
     * The stored memory of {@code playerId}, or an empty memory when nothing was stored yet.
     */
    CosmicNivMemory load(UUID playerId) throws IOException;

    void save(UUID playerId, CosmicNivMemory memory) throws IOException;
}
