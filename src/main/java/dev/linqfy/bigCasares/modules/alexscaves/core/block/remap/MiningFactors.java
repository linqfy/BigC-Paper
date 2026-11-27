package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Players currently digging a remapped block and the factor applied to their client's block break speed. Written
 * from the server thread, read from Netty threads (the outgoing attribute packets).
 */
final class MiningFactors {
    /** The entity id is the player's own (the packet's target). */
    record Active(int entityId, double factor) {
    }

    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    Active get(UUID player) {
        return active.get(player);
    }

    void put(UUID player, Active value) {
        active.put(player, value);
    }

    Active remove(UUID player) {
        return active.remove(player);
    }

    void clear() {
        active.clear();
    }

    int size() {
        return active.size();
    }
}
