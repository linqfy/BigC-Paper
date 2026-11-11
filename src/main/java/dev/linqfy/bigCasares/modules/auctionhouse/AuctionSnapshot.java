package dev.linqfy.bigCasares.modules.auctionhouse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent state of the auction house: active listings and items waiting to be claimed. */
public record AuctionSnapshot<I>(long nextId, List<AuctionListing<I>> listings, Map<UUID, List<I>> claims) {

    public AuctionSnapshot {
        listings = List.copyOf(listings);
        claims = claims.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        long highestId = listings.stream().mapToLong(AuctionListing::id).max().orElse(0);
        nextId = Math.max(nextId, highestId + 1);
    }

    public static <I> AuctionSnapshot<I> empty() {
        return new AuctionSnapshot<>(1, List.of(), Map.of());
    }
}
