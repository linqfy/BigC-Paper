package dev.linqfy.bigCasares.modules.alexscaves.integration;

import java.util.UUID;

/** Receives each paid placement of a defeated boss; implementations adapt Danger, Vault, etc. */
@FunctionalInterface
public interface PlacementReward {
    void award(UUID playerId, String bossId, int placement);
}
