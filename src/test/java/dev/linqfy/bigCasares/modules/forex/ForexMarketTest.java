package dev.linqfy.bigCasares.modules.forex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForexMarketTest {

    private static final long MINUTE = 60_000L;

    private final UUID creator = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final long[] now = {1_000_000_000L};
    private FakeBank bank;
    private ScriptedRandom random;
    private ForexMarket market;

    @BeforeEach
    void setUp() {
        bank = new FakeBank().give(creator, 100_000).give(alice, 100_000).give(bob, 100_000);
        random = new ScriptedRandom();
        market = new ForexMarket(ForexSettings.defaults(), bank, random, () -> now[0]);
    }

    @Test
    void emeraldsAreBoughtAndSoldWithVaultMoneyMinusTheSmallFee() {
        TradeReceipt buy = market.buyEmeralds(alice, 10_000);
        assertEquals(90_000, bank.balance(alice));
        assertEquals(50, buy.fees());
        assertEquals(99.5, market.emeralds(alice), 1e-9);

        TradeReceipt sell = market.sellEmeralds(alice, Double.MAX_VALUE);
        assertEquals(0, market.emeralds(alice));
        assertEquals(9_950 - 49, sell.money());
        assertEquals(90_000 + 9_901, bank.balance(alice));
        assertThrows(ForexException.class, () -> market.sellEmeralds(alice, 1));
        assertThrows(ForexException.class, () -> market.buyEmeralds(alice, 5), "below the minimum trade");
        assertThrows(ForexException.class, () -> market.buyEmeralds(alice, 1_000_000), "cannot spend money it lacks");
    }

    @Test
    void launchingACoinCostsTheListingFeeAndValidatesTickers() {
        Shitcoin coin = market.create(creator, "$moon", "Moon Coin");
        assertEquals("MOON", coin.ticker());
        assertEquals(95_000, bank.balance(creator));
        assertThrows(ForexException.class, () -> market.create(alice, "MOON", "Otra"), "duplicate ticker");
        assertThrows(ForexException.class, () -> market.create(alice, "VEMR", "Falsa"), "reserved ticker");
        assertThrows(ForexException.class, () -> market.create(alice, "1234", "Solo numeros"));
        assertThrows(ForexException.class, () -> market.create(alice, "TOOLONGX", "Largo"));
        assertThrows(ForexException.class, () -> market.create(alice, "OK", "§a§l"), "name without visible text");
        assertThrows(ForexException.class, () -> market.create(creator, "SECOND", "Segunda"), "one coin per creator");
    }

    @Test
    void tradesPayTheCreatorFeeAndSetAsideTheStakersShare() {
        market.create(creator, "MOON", "Moon Coin");
        long before = bank.balance(creator);
        TradeReceipt buy = market.buy(alice, "MOON", 10_000);
        Shitcoin coin = market.coin("moon").orElseThrow();

        assertEquals(400 + 100, buy.fees(), "4% creator fee plus 1% platform fee");
        assertEquals(9_500, coin.pool());
        assertEquals(100, coin.dividendPot(), "25% of the creator fee waits for stakers");
        assertEquals(before + 300, bank.balance(creator));
        assertEquals(buy.quantity(), coin.balance(alice), 1e-9);
        assertEquals(coin.supply(), coin.balance(alice), 1e-9);

        TradeReceipt sell = market.sell(alice, "MOON", Double.MAX_VALUE);
        assertEquals(0, coin.pool(), "with no hype change the pool covers exactly the holders");
        assertEquals(0, sell.shortfall());
        assertEquals(9_500 - 380 - 47, sell.money(), "holding the whole supply makes alice a whale: half fee");
    }

    @Test
    void creatorsTuneTheirFeeInsideTheAllowedRange() {
        market.create(creator, "MOON", "Moon Coin");
        market.setCreatorFee(creator, "MOON", 0.05);
        market.setStakingShare(creator, "MOON", 1.0);
        assertThrows(ForexException.class, () -> market.setCreatorFee(creator, "MOON", 0.20));
        assertThrows(ForexException.class, () -> market.setCreatorFee(alice, "MOON", 0.03), "only the creator");

        market.buy(alice, "MOON", 10_000);
        Shitcoin coin = market.coin("MOON").orElseThrow();
        assertEquals(500, coin.dividendPot(), "everything goes to stakers");
    }

    @Test
    void aPumpLeavesThePoolShortSoTheLastSellersFindItEmpty() {
        market.create(creator, "MOON", "Moon Coin");
        market.buy(alice, "MOON", 10_000);
        market.buy(bob, "MOON", 10_000);
        Shitcoin coin = market.coin("MOON").orElseThrow();
        coin.hype(coin.hype() * 1.3);

        TradeReceipt first = market.sell(alice, "MOON", Double.MAX_VALUE);
        assertTrue(first.money() > 10_000, "the first one out profits from the pump");
        assertEquals(0, first.shortfall());
        TradeReceipt second = market.sell(bob, "MOON", Double.MAX_VALUE);
        assertTrue(second.shortfall() > 0, "the pool could not pay what the price promised");
        assertEquals(0, coin.pool());

        market.buy(alice, "MOON", 100);
        coin.pool(0);
        ForexException empty = assertThrows(ForexException.class, () -> market.sell(alice, "MOON", 1));
        assertTrue(empty.getMessage().contains("vacío"));
    }

    @Test
    void stakedTokensAreLockedAndEarnTheCreatorsDividends() {
        market.create(creator, "MOON", "Moon Coin");
        market.buy(alice, "MOON", 10_000);
        market.buy(bob, "MOON", 10_000);
        Shitcoin coin = market.coin("MOON").orElseThrow();
        market.stake(alice, "MOON", coin.balance(alice));
        market.stake(bob, "MOON", coin.balance(bob) / 2);

        assertThrows(ForexException.class, () -> market.sell(alice, "MOON", 1), "staked tokens cannot be sold");
        assertThrows(ForexException.class, () -> market.unstake(alice, "MOON", 1), "still locked");

        long pot = coin.dividendPot();
        assertTrue(pot > 0);
        long aliceBefore = bank.balance(alice);
        long bobBefore = bank.balance(bob);
        now[0] += 30 * MINUTE;
        List<MarketEvent> events = market.tick();
        MarketEvent.Dividends dividends = events.stream().filter(MarketEvent.Dividends.class::isInstance)
            .map(MarketEvent.Dividends.class::cast).findFirst().orElseThrow();
        assertTrue(dividends.total() <= pot);
        long aliceGot = bank.balance(alice) - aliceBefore;
        long bobGot = bank.balance(bob) - bobBefore;
        assertTrue(aliceGot > bobGot, "alice staked more");
        assertEquals(pot - dividends.total(), coin.dividendPot());

        now[0] += 60 * MINUTE;
        market.unstake(alice, "MOON", Double.MAX_VALUE);
        assertEquals(0, coin.staked(alice));
        market.sell(alice, "MOON", Double.MAX_VALUE);
    }

    @Test
    void aRugPullStealsMostOfThePoolAndRuinsTheCreatorsCredit() {
        market.create(creator, "MOON", "Moon Coin");
        market.buy(alice, "MOON", 30_000);
        market.buy(bob, "MOON", 10_000);
        Shitcoin coin = market.coin("MOON").orElseThrow();
        long pool = coin.pool();
        long pot = coin.dividendPot();
        long creatorBefore = bank.balance(creator);
        random.queue(0.5);

        RugPull rug = market.rugPull(creator, "MOON");

        assertEquals(0.85, rug.fraction(), 1e-9);
        assertEquals((long) Math.floor(pool * 0.85), rug.stolen());
        assertEquals(creatorBefore + rug.stolen(), bank.balance(creator));
        long refunded = rug.refunds().values().stream().mapToLong(Long::longValue).sum();
        assertTrue(refunded <= pool - rug.stolen() + pot);
        assertTrue(rug.refunds().get(alice) > rug.refunds().get(bob), "the leftover is split by holdings");
        assertTrue(market.coin("MOON").isEmpty());
        assertEquals(DeadCoin.Fate.RUG_PULL, market.graveyard().getFirst().fate());
        assertEquals(1, market.rugPulls(creator));
        assertEquals(1.5, market.creditMultiplier(creator), 1e-9);
        assertThrows(ForexException.class, () -> market.create(creator, "AGAIN", "De nuevo"), "cooldown");
        now[0] += 12 * 60 * MINUTE;
        market.create(creator, "AGAIN", "De nuevo");
        assertThrows(ForexException.class, () -> market.rugPull(alice, "AGAIN"), "only the creator");
    }

    @Test
    void randomPumpsHitOneCycleInTen() {
        market.create(creator, "MOON", "Moon Coin");
        market.buy(alice, "MOON", 10_000);
        Shitcoin coin = market.coin("MOON").orElseThrow();
        double before = market.price(coin);
        // emerald correction check, emerald growth, then the coin's pump roll and size
        random.queue(0.99, 0.5, 0.05, 0.0);
        List<MarketEvent> events = market.forceCycle();
        MarketEvent.Pump pump = events.stream().filter(MarketEvent.Pump.class::isInstance)
            .map(MarketEvent.Pump.class::cast).findFirst().orElseThrow();
        assertEquals(0.25, pump.gain(), 1e-9);
        double drift = Math.exp(ForexSettings.defaults().coins().drift());
        assertEquals(before * drift * 1.25, market.price(coin), 1e-9);

        random.queue(0.99, 0.5, 0.10);
        assertTrue(market.forceCycle().stream().noneMatch(MarketEvent.Pump.class::isInstance));
    }

    @Test
    void onlyABigBubbleGetsTheFiftyFiftyAndTailsIsTotalCollapse() {
        market.create(creator, "MOON", "Moon Coin");
        List<UUID> crowd = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            UUID holder = UUID.randomUUID();
            bank.give(holder, 10_000);
            market.buy(holder, "MOON", 6_000);
            crowd.add(holder);
        }
        assertTrue(market.tick().stream().noneMatch(MarketEvent.BubbleFormed.class::isInstance), "5 holders is small");

        market.buy(alice, "MOON", 1_000);
        List<MarketEvent> formed = market.tick();
        MarketEvent.BubbleFormed bubble = assertInstanceOf(MarketEvent.BubbleFormed.class,
            formed.stream().filter(MarketEvent.BubbleFormed.class::isInstance).findFirst().orElseThrow());
        assertEquals(now[0] + 180 * MINUTE, bubble.flipAt());

        now[0] += 151 * MINUTE;
        assertTrue(market.tick().stream().anyMatch(MarketEvent.FlipWarning.class::isInstance), "30 minute warning");
        assertTrue(market.tick().stream().noneMatch(MarketEvent.FlipWarning.class::isInstance), "warned once");

        Shitcoin coin = market.coin("MOON").orElseThrow();
        double before = market.price(coin);
        now[0] += 30 * MINUTE;
        // the market cycle that runs first: no emerald correction, growth, no pump; then the flip's gain
        random.flips(true).queue(0.99, 0.5, 0.99, 0.0);
        List<MarketEvent> heads = market.tick();
        MarketEvent.FlipSurvived survived = heads.stream().filter(MarketEvent.FlipSurvived.class::isInstance)
            .map(MarketEvent.FlipSurvived.class::cast).findFirst().orElseThrow();
        assertEquals(1, survived.survived());
        double drift = Math.exp(ForexSettings.defaults().coins().drift());
        assertEquals(before * drift * 1.25, market.price(coin), 1e-9);

        now[0] += 180 * MINUTE;
        random.flips(false);
        List<MarketEvent> tails = market.tick();
        MarketEvent.Collapse collapse = tails.stream().filter(MarketEvent.Collapse.class::isInstance)
            .map(MarketEvent.Collapse.class::cast).findFirst().orElseThrow();
        assertTrue(collapse.victims().containsAll(crowd));
        assertTrue(collapse.victims().contains(alice));
        assertTrue(collapse.burned() > 25_000);
        assertTrue(market.coin("MOON").isEmpty());
        assertEquals(DeadCoin.Fate.COLLAPSE, market.graveyard().getFirst().fate());
    }

    @Test
    void theFlipIsCalledOffWhenTheBubbleDeflates() {
        market.create(creator, "MOON", "Moon Coin");
        List<UUID> crowd = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID holder = UUID.randomUUID();
            bank.give(holder, 10_000);
            market.buy(holder, "MOON", 6_000);
            crowd.add(holder);
        }
        assertTrue(market.tick().stream().anyMatch(MarketEvent.BubbleFormed.class::isInstance));
        market.sell(crowd.getFirst(), "MOON", Double.MAX_VALUE);
        assertTrue(market.tick().stream().anyMatch(MarketEvent.BubbleDeflated.class::isInstance));
        assertFalse(market.coin("MOON").orElseThrow().isBubble());
    }

    @Test
    void clanPassWhalesAndBankingPrestige() {
        market.create(creator, "CLAN", "Token del Clan");
        market.linkTeam(creator, "CLAN", "team-1");
        market.buy(alice, "CLAN", 1_000);
        market.buy(bob, "CLAN", 200);

        assertTrue(market.hasClanPass(alice, "team-1"), "more than 500 tokens");
        assertFalse(market.hasClanPass(bob, "team-1"));
        assertFalse(market.hasClanPass(alice, "team-2"));
        assertTrue(market.isWhale(alice));
        assertEquals(1.0, market.creditMultiplier(creator), 1e-9, "small coin, no prestige yet");

        bank.give(alice, 500_000);
        market.buy(alice, "CLAN", 500_000);
        assertTrue(market.marketCap(market.coin("CLAN").orElseThrow()) >= 250_000);
        assertTrue(market.creditMultiplier(creator) <= 0.70, "a big cap lowers Don Casares' rates");

        market.create(alice, "OTRA", "Otra moneda");
        assertThrows(ForexException.class, () -> market.linkTeam(alice, "OTRA", "team-1"), "one coin per clan");
    }

    @Test
    void whalesPayHalfThePlatformFee() {
        market.create(creator, "MOON", "Moon Coin");
        market.buy(alice, "MOON", 10_000);
        TradeReceipt whale = market.buy(alice, "MOON", 10_000);
        assertEquals(400 + 50, whale.fees());
    }
}
