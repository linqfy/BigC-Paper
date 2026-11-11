package dev.linqfy.bigCasares.modules.auctionhouse;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Fixed-price auction house. Sellers list an item, buyers pay the price through the {@link AuctionBank}
 * and the seller receives the price minus the configured tax. Expired or cancelled items that cannot be
 * handed back directly wait in the seller's claim box. Every change is saved through the {@link AuctionStore}.
 * Not thread-safe: call it from the server thread.
 */
public final class AuctionHouseService<I> {

    public enum ListResult { LISTED, PRICE_TOO_LOW, PRICE_TOO_HIGH, TOO_MANY_LISTINGS }

    public enum BuyStatus { BOUGHT, NOT_FOUND, OWN_LISTING, INSUFFICIENT_FUNDS, PAYMENT_FAILED }

    public record BuyResult<I>(BuyStatus status, AuctionListing<I> listing) {
    }

    private static final Comparator<AuctionListing<?>> NEWEST_FIRST =
        Comparator.comparing((AuctionListing<?> listing) -> listing.createdAt()).reversed()
            .thenComparing(Comparator.comparingLong((AuctionListing<?> listing) -> listing.id()).reversed());

    private final AuctionHouseSettings settings;
    private final AuctionBank bank;
    private final AuctionStore<I> store;
    private final Clock clock;
    private final Map<Long, AuctionListing<I>> listings = new LinkedHashMap<>();
    private final Map<UUID, List<I>> claims = new LinkedHashMap<>();
    private long nextId;

    public AuctionHouseService(AuctionHouseSettings settings, AuctionBank bank, AuctionStore<I> store, Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.bank = Objects.requireNonNull(bank, "bank");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        AuctionSnapshot<I> snapshot = store.load();
        snapshot.listings().forEach(listing -> listings.put(listing.id(), listing));
        snapshot.claims().forEach((player, items) -> claims.put(player, new ArrayList<>(items)));
        nextId = snapshot.nextId();
    }

    public AuctionHouseSettings settings() {
        return settings;
    }

    public ListResult list(UUID seller, String sellerName, I item, long price) {
        Objects.requireNonNull(item, "item");
        if (price < settings.minPrice()) {
            return ListResult.PRICE_TOO_LOW;
        }
        if (price > settings.maxPrice()) {
            return ListResult.PRICE_TOO_HIGH;
        }
        if (listingsOf(seller).size() >= settings.maxListingsPerPlayer()) {
            return ListResult.TOO_MANY_LISTINGS;
        }
        Instant now = clock.instant();
        AuctionListing<I> listing = new AuctionListing<>(
            nextId++, seller, sellerName, item, price, now, now.plus(settings.listingDuration()));
        listings.put(listing.id(), listing);
        save();
        return ListResult.LISTED;
    }

    /** Charges the buyer, pays the seller (minus tax) and removes the listing. The item is in the result. */
    public BuyResult<I> buy(UUID buyer, long listingId) {
        AuctionListing<I> listing = listings.get(listingId);
        if (listing == null || listing.expired(clock.instant())) {
            return new BuyResult<>(BuyStatus.NOT_FOUND, listing);
        }
        if (listing.seller().equals(buyer)) {
            return new BuyResult<>(BuyStatus.OWN_LISTING, listing);
        }
        if (bank.balance(buyer) < listing.price()) {
            return new BuyResult<>(BuyStatus.INSUFFICIENT_FUNDS, listing);
        }
        if (!bank.withdraw(buyer, listing.price())) {
            return new BuyResult<>(BuyStatus.PAYMENT_FAILED, listing);
        }
        long payout = listing.price() - settings.taxOf(listing.price());
        if (!bank.deposit(listing.seller(), payout)) {
            bank.deposit(buyer, listing.price());
            return new BuyResult<>(BuyStatus.PAYMENT_FAILED, listing);
        }
        listings.remove(listingId);
        save();
        return new BuyResult<>(BuyStatus.BOUGHT, listing);
    }

    /** Removes the seller's own listing and returns its item to the caller. */
    public Optional<I> cancel(UUID seller, long listingId) {
        AuctionListing<I> listing = listings.get(listingId);
        if (listing == null || !listing.seller().equals(seller)) {
            return Optional.empty();
        }
        listings.remove(listingId);
        save();
        return Optional.of(listing.item());
    }

    /** Admin removal: the item goes back to the seller's claim box. */
    public Optional<AuctionListing<I>> remove(long listingId) {
        AuctionListing<I> listing = listings.remove(listingId);
        if (listing == null) {
            return Optional.empty();
        }
        claims.computeIfAbsent(listing.seller(), ignored -> new ArrayList<>()).add(listing.item());
        save();
        return Optional.of(listing);
    }

    /** Moves expired listings into their sellers' claim boxes; returns how many expired. */
    public int expire() {
        Instant now = clock.instant();
        List<AuctionListing<I>> expired = listings.values().stream().filter(listing -> listing.expired(now)).toList();
        for (AuctionListing<I> listing : expired) {
            listings.remove(listing.id());
            claims.computeIfAbsent(listing.seller(), ignored -> new ArrayList<>()).add(listing.item());
        }
        if (!expired.isEmpty()) {
            save();
        }
        return expired.size();
    }

    public Optional<AuctionListing<I>> find(long listingId) {
        AuctionListing<I> listing = listings.get(listingId);
        return listing == null || listing.expired(clock.instant()) ? Optional.empty() : Optional.of(listing);
    }

    /** Listings that can still be bought, newest first. */
    public List<AuctionListing<I>> active() {
        Instant now = clock.instant();
        return listings.values().stream().filter(listing -> !listing.expired(now)).sorted(NEWEST_FIRST).toList();
    }

    /** Every listing of the seller that has not been moved to the claim box yet, newest first. */
    public List<AuctionListing<I>> listingsOf(UUID seller) {
        return listings.values().stream().filter(listing -> listing.seller().equals(seller)).sorted(NEWEST_FIRST).toList();
    }

    public List<I> claims(UUID player) {
        return List.copyOf(claims.getOrDefault(player, List.of()));
    }

    /** Empties the player's claim box and returns what was in it. */
    public List<I> takeClaims(UUID player) {
        List<I> taken = claims.remove(player);
        if (taken == null) {
            return List.of();
        }
        save();
        return List.copyOf(taken);
    }

    /** Puts items (for example leftovers that did not fit in an inventory) into the player's claim box. */
    public void addClaims(UUID player, List<I> items) {
        if (items.isEmpty()) {
            return;
        }
        claims.computeIfAbsent(player, ignored -> new ArrayList<>()).addAll(items);
        save();
    }

    private void save() {
        store.save(new AuctionSnapshot<>(nextId, List.copyOf(listings.values()), claims));
    }
}
