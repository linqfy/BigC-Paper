package dev.linqfy.bigCasares.modules.auctionhouse;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One item offered on the auction house at a fixed price. */
public record AuctionListing<I>(
    long id,
    UUID seller,
    String sellerName,
    I item,
    long price,
    Instant createdAt,
    Instant expiresAt
) {
    public AuctionListing {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        sellerName = sellerName == null || sellerName.isBlank() ? seller.toString() : sellerName;
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }

    public boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
