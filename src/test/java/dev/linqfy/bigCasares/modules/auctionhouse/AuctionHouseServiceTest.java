package dev.linqfy.bigCasares.modules.auctionhouse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionHouseServiceTest {

    private static final UUID SELLER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BUYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-11T12:00:00Z"));
    private final FakeBank bank = new FakeBank();
    private final MemoryStore store = new MemoryStore();
    private AuctionHouseService<String> service;

    @BeforeEach
    void setUp() {
        service = new AuctionHouseService<>(
            new AuctionHouseSettings(2, 10, 1_000, 10, Duration.ofHours(1)), bank, store, clock);
    }

    @Test
    void validatesPriceAndListingLimit() {
        assertEquals(AuctionHouseService.ListResult.PRICE_TOO_LOW, service.list(SELLER, "Seller", "dirt", 9));
        assertEquals(AuctionHouseService.ListResult.PRICE_TOO_HIGH, service.list(SELLER, "Seller", "dirt", 1_001));
        assertEquals(AuctionHouseService.ListResult.LISTED, service.list(SELLER, "Seller", "dirt", 10));
        assertEquals(AuctionHouseService.ListResult.LISTED, service.list(SELLER, "Seller", "stone", 1_000));
        assertEquals(AuctionHouseService.ListResult.TOO_MANY_LISTINGS, service.list(SELLER, "Seller", "sand", 50));
        assertEquals(2, service.active().size());
    }

    @Test
    void buyingMovesMoneyMinusTaxAndRemovesTheListing() {
        bank.balances.put(BUYER, 500L);
        service.list(SELLER, "Seller", "diamond", 200);
        long id = service.active().getFirst().id();

        AuctionHouseService.BuyResult<String> result = service.buy(BUYER, id);

        assertEquals(AuctionHouseService.BuyStatus.BOUGHT, result.status());
        assertEquals("diamond", result.listing().item());
        assertEquals(300L, bank.balance(BUYER));
        assertEquals(180L, bank.balance(SELLER));
        assertTrue(service.active().isEmpty());
        assertTrue(store.saved.listings().isEmpty());
        assertEquals(AuctionHouseService.BuyStatus.NOT_FOUND, service.buy(BUYER, id).status());
    }

    @Test
    void refusesOwnListingsAndMissingFunds() {
        bank.balances.put(BUYER, 50L);
        service.list(SELLER, "Seller", "diamond", 200);
        long id = service.active().getFirst().id();

        assertEquals(AuctionHouseService.BuyStatus.OWN_LISTING, service.buy(SELLER, id).status());
        assertEquals(AuctionHouseService.BuyStatus.INSUFFICIENT_FUNDS, service.buy(BUYER, id).status());
        assertEquals(50L, bank.balance(BUYER));
        assertEquals(1, service.active().size());
    }

    @Test
    void refundsTheBuyerWhenTheSellerCannotBePaid() {
        bank.balances.put(BUYER, 500L);
        bank.failDepositsTo = SELLER;
        service.list(SELLER, "Seller", "diamond", 200);
        long id = service.active().getFirst().id();

        assertEquals(AuctionHouseService.BuyStatus.PAYMENT_FAILED, service.buy(BUYER, id).status());
        assertEquals(500L, bank.balance(BUYER));
        assertEquals(1, service.active().size());
    }

    @Test
    void expiredListingsGoToTheClaimBox() {
        bank.balances.put(BUYER, 500L);
        service.list(SELLER, "Seller", "diamond", 200);
        long id = service.active().getFirst().id();
        clock.advance(Duration.ofHours(1));

        assertTrue(service.active().isEmpty());
        assertEquals(AuctionHouseService.BuyStatus.NOT_FOUND, service.buy(BUYER, id).status());
        assertEquals(1, service.expire());
        assertEquals(List.of("diamond"), service.claims(SELLER));
        assertEquals(List.of("diamond"), store.saved.claims().get(SELLER));
        assertEquals(List.of("diamond"), service.takeClaims(SELLER));
        assertTrue(service.claims(SELLER).isEmpty());
        assertEquals(500L, bank.balance(BUYER));
    }

    @Test
    void onlyTheSellerCanCancelAndAdminRemovalKeepsTheItem() {
        service.list(SELLER, "Seller", "diamond", 200);
        service.list(SELLER, "Seller", "emerald", 300);
        long emerald = service.active().get(0).id();
        long diamond = service.active().get(1).id();

        assertEquals(Optional.empty(), service.cancel(BUYER, diamond));
        assertEquals(Optional.of("diamond"), service.cancel(SELLER, diamond));
        assertEquals("emerald", service.remove(emerald).orElseThrow().item());
        assertEquals(List.of("emerald"), service.claims(SELLER));
        assertTrue(service.listingsOf(SELLER).isEmpty());
    }

    @Test
    void reloadsStateAndKeepsIdsUnique() {
        service.list(SELLER, "Seller", "diamond", 200);
        service.addClaims(BUYER, List.of("leftover"));

        AuctionHouseService<String> reloaded = new AuctionHouseService<>(
            AuctionHouseSettings.defaults(), bank, store, clock);
        reloaded.list(BUYER, "Buyer", "stone", 20);

        assertEquals(2, reloaded.active().size());
        assertEquals(2, reloaded.active().stream().map(AuctionListing::id).distinct().count());
        assertEquals(List.of("leftover"), reloaded.claims(BUYER));
    }

    private static final class FakeBank implements AuctionBank {
        private final Map<UUID, Long> balances = new HashMap<>();
        private UUID failDepositsTo;

        @Override
        public long balance(UUID player) {
            return balances.getOrDefault(player, 0L);
        }

        @Override
        public boolean withdraw(UUID player, long amount) {
            if (balance(player) < amount) {
                return false;
            }
            balances.put(player, balance(player) - amount);
            return true;
        }

        @Override
        public boolean deposit(UUID player, long amount) {
            if (player.equals(failDepositsTo)) {
                return false;
            }
            balances.put(player, balance(player) + amount);
            return true;
        }
    }

    private static final class MemoryStore implements AuctionStore<String> {
        private AuctionSnapshot<String> saved = AuctionSnapshot.empty();

        @Override
        public AuctionSnapshot<String> load() {
            return saved;
        }

        @Override
        public void save(AuctionSnapshot<String> snapshot) {
            saved = snapshot;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
