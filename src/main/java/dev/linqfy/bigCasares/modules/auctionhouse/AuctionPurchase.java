package dev.linqfy.bigCasares.modules.auctionhouse;

import java.util.Objects;
import java.util.UUID;

/** A payment whose external Vault outcome may require manual reconciliation. */
public record AuctionPurchase(UUID buyer, Phase phase) {
    public enum Phase { WITHDRAW, PAYOUT, REFUND }

    public AuctionPurchase {
        Objects.requireNonNull(buyer, "buyer");
        Objects.requireNonNull(phase, "phase");
    }
}
