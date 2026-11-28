package dev.linqfy.bigCasares.modules.toffy;

import java.io.IOException;
import java.util.UUID;

interface ToffyMemoryStorage {
    /**
     * The stored memory of {@code playerId}, or an empty memory when nothing was stored yet.
     */
    ToffyMemory load(UUID playerId) throws IOException;

    void save(UUID playerId, ToffyMemory memory) throws IOException;
}
