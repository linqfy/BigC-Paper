package dev.linqfy.bigCasares.modules.duels;

import java.util.UUID;

/**
 * A pending challenge.
 *
 * @param kitId null when the kit is chosen in the ban/pick menu
 */
record DuelRequest(UUID challenger, UUID target, String kitId, DuelArenaSize arena, long wager, long expiresAt) {

    boolean expired(long now) {
        return now >= expiresAt;
    }
}
