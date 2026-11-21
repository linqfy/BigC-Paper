package dev.linqfy.bigCasares.modules.auctionhouse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent state of listings, claim boxes, and purchases awaiting reconciliation. */
public record AuctionSnapshot<I>(long nextId, List<AuctionListing<I>> listings, Map<UUID, List<I>> claims,
                                 Map<Long, AuctionPurchase> pendingPurchases) {

    public AuctionSnapshot {
        listings = List.copyOf(listings);
        pendingPurchases = Map.copyOf(pendingPurchases);
        if (!listings.stream().map(AuctionListing::id).collect(java.util.stream.Collectors.toSet())
            .containsAll(pendingPurchases.keySet())) {
            throw new IllegalArgumentException("Pending purchase without listing");
        }
        claims = claims.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        long highestId = listings.stream().mapToLong(AuctionListing::id).max().orElse(0);
        nextId = Math.max(nextId, highestId + 1);
    }

    public AuctionSnapshot(long nextId, List<AuctionListing<I>> listings, Map<UUID, List<I>> claims) {
        this(nextId, listings, claims, Map.of());
    }

    public static <I> AuctionSnapshot<I> empty() {
        return new AuctionSnapshot<>(1, List.of(), Map.of());
    }
}
