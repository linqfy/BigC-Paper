package dev.linqfy.bigCasares.modules.forex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * The trading platform: virtual emeralds plus every player-launched shitcoin, all settled in Vault money.
 *
 * <p>Money that enters a shitcoin sits in its pool. Creator fees are paid out on every trade; the configured
 * share of them is set aside for stakers. The market tick moves prices, fires random pumps, flips the 50/50 on
 * coins that grew into bubbles and pays dividends. Nothing here touches Bukkit.
 */
public final class ForexMarket {

    public static final String EMERALD_TICKER = "vEMR";
    private static final Pattern TICKER = Pattern.compile("[A-Z0-9]{2,6}");
    private static final Set<String> RESERVED = Set.of("EMR", "VEMR", "ESMERA", "TODO", "ALL", "TOP", "MENU");
    private static final int GRAVEYARD = 20;
    private static final long MINUTE = 60_000L;

    /** A player's position in one asset, valued at the current price. */
    public record Position(String ticker, double balance, double staked, double price, long value) {
    }

    public record Portfolio(double emeralds, long emeraldValue, List<Position> coins) {

        public long totalValue() {
            return emeraldValue + coins.stream().mapToLong(Position::value).sum();
        }
    }

    static final class Account {
        double emeralds;
        int rugPulls;
        long lastRugAt;
        boolean auraHidden;

        boolean isEmpty() {
            return emeralds <= Shitcoin.DUST && rugPulls == 0 && !auraHidden;
        }
    }

    private final ForexSettings settings;
    private final ForexBank bank;
    private final RandomGenerator random;
    private final LongSupplier clock;
    private final BondingCurve curve;
    private final EmeraldMarket emerald;
    private final Map<String, Shitcoin> coins = new LinkedHashMap<>();
    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Deque<DeadCoin> graveyard = new ArrayDeque<>();
    private long nextCycleAt;
    private long cycleVolume;

    public ForexMarket(ForexSettings settings, ForexBank bank, RandomGenerator random, LongSupplier clock) {
        this.settings = settings;
        this.bank = bank;
        this.random = random;
        this.clock = clock;
        this.curve = new BondingCurve(settings.coins().basePrice(), settings.coins().curveDepth());
        this.emerald = new EmeraldMarket(settings);
    }

    public ForexSettings settings() {
        return settings;
    }

    public EmeraldMarket emerald() {
        return emerald;
    }

    public long now() {
        return clock.getAsLong();
    }

    // ---------------------------------------------------------------- virtual emeralds

    public double emeralds(UUID player) {
        Account account = accounts.get(player);
        return account == null ? 0 : account.emeralds;
    }

    public TradeReceipt buyEmeralds(UUID player, long money) {
        requireMinimum(money);
        if (!bank.withdraw(player, money)) {
            throw new ForexException("No te alcanza la plata.");
        }
        long fee = (long) Math.floor(money * emeraldFee(player));
        double quantity = (money - fee) / emerald.price();
        account(player).emeralds += quantity;
        cycleVolume += money;
        return new TradeReceipt(EMERALD_TICKER, true, quantity, money, fee, emerald.price(), 0);
    }

    /** Sells {@code quantity} virtual emeralds; anything above the balance sells everything. */
    public TradeReceipt sellEmeralds(UUID player, double quantity) {
        double held = emeralds(player);
        double sold = Math.min(quantity, held);
        if (sold <= Shitcoin.DUST) {
            throw new ForexException("No tenés esmeraldas virtuales.");
        }
        long gross = (long) Math.floor(sold * emerald.price());
        long fee = (long) Math.floor(gross * emeraldFee(player));
        long payout = gross - fee;
        if (payout <= 0) {
            throw new ForexException("Eso no vale ni una moneda.");
        }
        if (!bank.deposit(player, payout)) {
            throw new ForexException("El banco no pudo acreditar la venta.");
        }
        Account account = account(player);
        account.emeralds = held - sold <= Shitcoin.DUST ? 0 : held - sold;
        cycleVolume += gross;
        return new TradeReceipt(EMERALD_TICKER, false, sold, payout, fee, emerald.price(), 0);
    }

    // ---------------------------------------------------------------- shitcoins

    public static String normalizeTicker(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.startsWith("$")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    public static boolean isEmeraldAlias(String raw) {
        String normalized = normalizeTicker(raw);
        return normalized.equals("VEMR") || normalized.equals("EMR") || normalized.startsWith("ESMERALDA");
    }

    public Shitcoin create(UUID creator, String rawTicker, String rawName) {
        String ticker = normalizeTicker(rawTicker);
        if (!TICKER.matcher(ticker).matches() || ticker.chars().noneMatch(Character::isLetter)) {
            throw new ForexException("El ticker tiene que ser de 2 a 6 letras o números, con al menos una letra.");
        }
        if (RESERVED.contains(ticker) || isEmeraldAlias(ticker)) {
            throw new ForexException("$" + ticker + " está reservado.");
        }
        if (coins.containsKey(ticker)) {
            throw new ForexException("$" + ticker + " ya cotiza en el mercado.");
        }
        String name = rawName == null ? "" : rawName.replaceAll("[§&][0-9a-fk-orA-FK-OR]", "").trim();
        if (name.length() < 3 || name.length() > 24) {
            throw new ForexException("El nombre tiene que tener entre 3 y 24 caracteres.");
        }
        long owned = coins.values().stream().filter(coin -> coin.creator().equals(creator)).count();
        if (owned >= settings.coins().maxPerCreator()) {
            throw new ForexException("Ya tenés el máximo de shitcoins activas (" + settings.coins().maxPerCreator() + ").");
        }
        if (coins.size() >= settings.coins().maxListed()) {
            throw new ForexException("El mercado está lleno. Esperá a que alguna moneda se muera.");
        }
        Account account = accounts.get(creator);
        long cooldown = settings.coins().rugCooldownHours() * 60 * MINUTE;
        if (account != null && account.rugPulls > 0 && now() - account.lastRugAt < cooldown) {
            long left = cooldown - (now() - account.lastRugAt);
            throw new ForexException("Después de tu último rug pull nadie te firma nada. Esperá "
                + ForexFormat.duration(left) + ".");
        }
        long cost = settings.coins().creationCost();
        if (cost > 0 && !bank.withdraw(creator, cost)) {
            throw new ForexException("Lanzar una moneda cuesta " + ForexFormat.money(cost) + ".");
        }
        Shitcoin coin = new Shitcoin(ticker, name, creator, now(), settings.coins().creatorFeeDefault(),
            settings.coins().stakingShareDefault());
        coin.record(price(coin));
        coins.put(ticker, coin);
        return coin;
    }

    public Optional<Shitcoin> coin(String ticker) {
        return Optional.ofNullable(coins.get(normalizeTicker(ticker)));
    }

    public List<Shitcoin> coins() {
        return List.copyOf(coins.values());
    }

    public double price(Shitcoin coin) {
        return curve.price(coin.supply(), coin.hype());
    }

    public double marketCap(Shitcoin coin) {
        return price(coin) * coin.supply();
    }

    /** Money a seller of {@code amount} would receive before fees, capped by what the pool holds. */
    public long quoteSale(Shitcoin coin, double amount) {
        return Math.min(curveValue(coin, amount), coin.pool());
    }

    public TradeReceipt buy(UUID player, String ticker, long money) {
        Shitcoin coin = require(ticker);
        requireMinimum(money);
        if (!bank.withdraw(player, money)) {
            throw new ForexException("No te alcanza la plata.");
        }
        long creatorFee = (long) Math.floor(money * coin.creatorFee());
        long platformFee = (long) Math.floor(money * platformFee(player));
        long net = money - creatorFee - platformFee;
        double tokens = curve.tokensFor(coin.supply(), net, coin.hype());
        coin.supply(coin.supply() + tokens);
        coin.pool(coin.pool() + net);
        coin.credit(player, tokens);
        payCreator(coin, creatorFee);
        coin.addVolume(money);
        cycleVolume += money;
        coin.peakCap(Math.max(coin.peakCap(), marketCap(coin)));
        return new TradeReceipt(coin.ticker(), true, tokens, money, creatorFee + platformFee, price(coin), 0);
    }

    /** Sells free (unstaked) tokens; anything above the free balance sells all of them. */
    public TradeReceipt sell(UUID player, String ticker, double quantity) {
        Shitcoin coin = require(ticker);
        double sold = Math.min(quantity, coin.free(player));
        if (sold <= Shitcoin.DUST) {
            throw new ForexException(coin.staked(player) > Shitcoin.DUST
                ? "Tus $" + coin.ticker() + " están en staking. Sacalos primero con /forex unstake."
                : "No tenés $" + coin.ticker() + ".");
        }
        long value = curveValue(coin, sold);
        long gross = Math.min(value, coin.pool());
        if (gross <= 0) {
            throw new ForexException("El pozo de $" + coin.ticker() + " está vacío: nadie te compra. Rezá.");
        }
        long creatorFee = (long) Math.floor(gross * coin.creatorFee());
        long platformFee = (long) Math.floor(gross * platformFee(player));
        long payout = gross - creatorFee - platformFee;
        if (payout > 0 && !bank.deposit(player, payout)) {
            throw new ForexException("El banco no pudo acreditar la venta.");
        }
        coin.pool(coin.pool() - gross);
        coin.supply(coin.supply() - sold);
        coin.debit(player, sold);
        payCreator(coin, creatorFee);
        coin.addVolume(gross);
        cycleVolume += gross;
        return new TradeReceipt(coin.ticker(), false, sold, payout, creatorFee + platformFee, price(coin),
            Math.max(0, value - gross));
    }

    public double stake(UUID player, String ticker, double quantity) {
        Shitcoin coin = require(ticker);
        double amount = Math.min(quantity, coin.free(player));
        if (amount <= Shitcoin.DUST) {
            throw new ForexException("No tenés $" + coin.ticker() + " libres para bloquear.");
        }
        coin.stake(player, amount, now() + settings.coins().stakeLockMinutes() * MINUTE);
        return amount;
    }

    public double unstake(UUID player, String ticker, double quantity) {
        Shitcoin coin = require(ticker);
        double amount = Math.min(quantity, coin.staked(player));
        if (amount <= Shitcoin.DUST) {
            throw new ForexException("No tenés $" + coin.ticker() + " en staking.");
        }
        long left = coin.lockedUntil(player) - now();
        if (left > 0) {
            throw new ForexException("Tus $" + coin.ticker() + " siguen bloqueados " + ForexFormat.duration(left) + ".");
        }
        coin.unstake(player, amount);
        return amount;
    }

    public void setCreatorFee(UUID player, String ticker, double fee) {
        Shitcoin coin = requireOwned(player, ticker);
        ForexSettings.Coins config = settings.coins();
        if (fee < config.creatorFeeMin() - 1e-9 || fee > config.creatorFeeMax() + 1e-9) {
            throw new ForexException("La comisión tiene que estar entre " + ForexFormat.percent(config.creatorFeeMin())
                + " y " + ForexFormat.percent(config.creatorFeeMax()) + ".");
        }
        coin.creatorFee(fee);
    }

    public void setStakingShare(UUID player, String ticker, double share) {
        Shitcoin coin = requireOwned(player, ticker);
        if (share < 0 || share > 1) {
            throw new ForexException("El reparto a stakers va de 0% a 100% de tu comisión.");
        }
        coin.stakingShare(share);
    }

    /** Makes the coin the membership token of a team, or unlinks it with {@code null}. */
    public void linkTeam(UUID player, String ticker, String teamId) {
        Shitcoin coin = requireOwned(player, ticker);
        if (teamId != null && coins.values().stream()
            .anyMatch(other -> other != coin && other.teamId().filter(teamId::equals).isPresent())) {
            throw new ForexException("Tu clan ya tiene una moneda oficial.");
        }
        coin.teamId(teamId);
    }

    public Optional<Shitcoin> teamCoin(String teamId) {
        return coins.values().stream().filter(coin -> coin.teamId().filter(teamId::equals).isPresent()).findFirst();
    }

    /** The creator takes {@code rugpull-min..max} of the pool; the rest is split among the other holders. */
    public RugPull rugPull(UUID player, String ticker) {
        Shitcoin coin = requireOwned(player, ticker);
        ForexSettings.Coins config = settings.coins();
        double fraction = config.rugPullMin() + random.nextDouble() * (config.rugPullMax() - config.rugPullMin());
        long stolen = (long) Math.floor(coin.pool() * fraction);
        long leftover = coin.pool() - stolen + coin.dividendPot();
        Map<UUID, Double> victims = new LinkedHashMap<>(coin.holdings());
        victims.remove(coin.creator());
        Map<UUID, Long> refunds = split(leftover, victims);
        if (stolen > 0) {
            bank.deposit(coin.creator(), stolen);
        }
        refunds.forEach(bank::deposit);
        Account account = account(player);
        account.rugPulls++;
        account.lastRugAt = now();
        bury(coin, DeadCoin.Fate.RUG_PULL, coin.pool());
        return new RugPull(coin.ticker(), coin.name(), coin.creator(), stolen, fraction, refunds);
    }

    // ---------------------------------------------------------------- perks

    public List<Shitcoin> topCoins() {
        return coins.values().stream()
            .sorted(Comparator.comparingDouble(this::marketCap).reversed())
            .limit(settings.whales().topCoins())
            .toList();
    }

    /** Holds at least {@code whales.min-share} of a coin in the top of market capitalisation. */
    public boolean isWhale(UUID player) {
        double share = settings.whales().minShare();
        return topCoins().stream().anyMatch(coin -> coin.supply() > Shitcoin.DUST && coin.balance(player) >= 1.0
            && coin.balance(player) / coin.supply() >= share);
    }

    /** Every whale at once, for effects that check many players. */
    public Set<UUID> whales() {
        double share = settings.whales().minShare();
        Set<UUID> whales = new LinkedHashSet<>();
        for (Shitcoin coin : topCoins()) {
            if (coin.supply() <= Shitcoin.DUST) {
                continue;
            }
            coin.holdings().forEach((holder, balance) -> {
                if (balance >= 1.0 && balance / coin.supply() >= share) {
                    whales.add(holder);
                }
            });
        }
        return whales;
    }

    /** Holds at least {@code clan-pass-threshold} tokens of the team's official coin. */
    public boolean hasClanPass(UUID player, String teamId) {
        return teamCoin(teamId).map(coin -> coin.balance(player) >= settings.clanPassThreshold()).orElse(false);
    }

    /**
     * Interest multiplier for Don Casares' loans: the best prestige tier reached by a coin the player created,
     * raised by every rug pull on record.
     */
    public double creditMultiplier(UUID player) {
        double best = coins.values().stream()
            .filter(coin -> coin.creator().equals(player))
            .mapToDouble(this::marketCap)
            .max().orElse(0);
        double multiplier = 1.0;
        for (Map.Entry<Long, Double> tier : settings.credit().prestigeTiers().entrySet()) {
            if (best >= tier.getKey()) {
                multiplier = Math.min(multiplier, tier.getValue());
            }
        }
        Account account = accounts.get(player);
        if (account != null) {
            multiplier *= 1.0 + account.rugPulls * settings.credit().rugPullPenalty();
        }
        return Math.min(settings.credit().maxMultiplier(), multiplier);
    }

    public int rugPulls(UUID player) {
        Account account = accounts.get(player);
        return account == null ? 0 : account.rugPulls;
    }

    public boolean auraHidden(UUID player) {
        Account account = accounts.get(player);
        return account != null && account.auraHidden;
    }

    public boolean toggleAura(UUID player) {
        Account account = account(player);
        account.auraHidden = !account.auraHidden;
        return !account.auraHidden;
    }

    public Portfolio portfolio(UUID player) {
        double held = emeralds(player);
        List<Position> positions = new ArrayList<>();
        for (Shitcoin coin : coins.values()) {
            double balance = coin.balance(player);
            if (balance > Shitcoin.DUST) {
                double price = price(coin);
                positions.add(new Position(coin.ticker(), balance, coin.staked(player), price,
                    (long) Math.floor(balance * price)));
            }
        }
        return new Portfolio(held, (long) Math.floor(held * emerald.price()), List.copyOf(positions));
    }

    public List<DeadCoin> graveyard() {
        return List.copyOf(graveyard);
    }

    public long millisToNextCycle() {
        return nextCycleAt == 0 ? settings.cycleMinutes() * MINUTE : Math.max(0, nextCycleAt - now());
    }

    // ---------------------------------------------------------------- tick

    /** Runs whatever is due. Cheap to call often: market cycles only happen every {@code cycle-minutes}. */
    public List<MarketEvent> tick() {
        long now = now();
        List<MarketEvent> events = new ArrayList<>();
        if (nextCycleAt == 0) {
            nextCycleAt = now + settings.cycleMinutes() * MINUTE;
        } else if (now >= nextCycleAt) {
            nextCycleAt = now + settings.cycleMinutes() * MINUTE;
            runCycle(events);
        }
        for (Shitcoin coin : List.copyOf(coins.values())) {
            watchBubble(coin, now, events);
            payDividends(coin, now, events);
        }
        return events;
    }

    /** Forces a market cycle now. */
    public List<MarketEvent> forceCycle() {
        List<MarketEvent> events = new ArrayList<>();
        nextCycleAt = now() + settings.cycleMinutes() * MINUTE;
        runCycle(events);
        return events;
    }

    /** Forces the 50/50 on a coin now, bubble or not. */
    public List<MarketEvent> forceFlip(String ticker) {
        Shitcoin coin = require(ticker);
        List<MarketEvent> events = new ArrayList<>();
        flip(coin, now(), events);
        return events;
    }

    private void runCycle(List<MarketEvent> events) {
        double activity = (double) cycleVolume / settings.emerald().activityReference();
        cycleVolume = 0;
        events.add(new MarketEvent.EmeraldMoved(emerald.step(random, activity)));
        ForexSettings.Coins config = settings.coins();
        for (Shitcoin coin : coins.values()) {
            coin.hype(coin.hype() * Math.exp(config.drift() + config.volatility() * random.nextGaussian()));
            if (random.nextDouble() < config.pumpChance()) {
                double gain = config.pumpMin() + random.nextDouble() * (config.pumpMax() - config.pumpMin());
                coin.hype(coin.hype() * (1.0 + gain));
                events.add(new MarketEvent.Pump(coin.ticker(), gain));
            }
            coin.cycleVolume(0);
            coin.record(price(coin));
            coin.peakCap(Math.max(coin.peakCap(), marketCap(coin)));
        }
    }

    private void watchBubble(Shitcoin coin, long now, List<MarketEvent> events) {
        ForexSettings.Bubble config = settings.bubble();
        boolean critical = coin.holderCount() >= config.minHolders() && coin.pool() >= config.minPool();
        if (critical && !coin.isBubble()) {
            coin.nextFlipAt(now + config.flipIntervalMinutes() * MINUTE);
            events.add(new MarketEvent.BubbleFormed(coin.ticker(), coin.nextFlipAt()));
            return;
        }
        if (!critical && coin.isBubble()) {
            coin.nextFlipAt(0);
            events.add(new MarketEvent.BubbleDeflated(coin.ticker()));
            return;
        }
        if (!coin.isBubble()) {
            return;
        }
        long left = coin.nextFlipAt() - now;
        if (left <= 0) {
            flip(coin, now, events);
            return;
        }
        List<Long> warnings = config.warningMinutes();
        int crossed = coin.warningsSent();
        while (crossed < warnings.size() && left <= warnings.get(crossed) * MINUTE) {
            crossed++;
        }
        if (crossed > coin.warningsSent()) {
            coin.warningsSent(crossed);
            events.add(new MarketEvent.FlipWarning(coin.ticker(), left, Set.copyOf(coin.holdings().keySet())));
        }
    }

    private void flip(Shitcoin coin, long now, List<MarketEvent> events) {
        ForexSettings.Bubble config = settings.bubble();
        if (random.nextBoolean()) {
            double gain = config.survivePumpMin() + random.nextDouble() * (config.survivePumpMax() - config.survivePumpMin());
            coin.hype(coin.hype() * (1.0 + gain));
            coin.flipsSurvived(coin.flipsSurvived() + 1);
            coin.nextFlipAt(now + config.flipIntervalMinutes() * MINUTE);
            coin.record(price(coin));
            coin.peakCap(Math.max(coin.peakCap(), marketCap(coin)));
            events.add(new MarketEvent.FlipSurvived(coin.ticker(), coin.flipsSurvived(), gain, coin.nextFlipAt()));
            return;
        }
        coin.hype(coin.hype() * config.collapseFactor());
        coin.record(price(coin));
        long burned = coin.pool() + coin.dividendPot();
        Set<UUID> victims = new LinkedHashSet<>(coin.holdings().keySet());
        bury(coin, DeadCoin.Fate.COLLAPSE, burned);
        events.add(new MarketEvent.Collapse(coin.ticker(), coin.name(), burned, coin.peakCap(), Set.copyOf(victims)));
    }

    private void payDividends(Shitcoin coin, long now, List<MarketEvent> events) {
        if (now - coin.lastDividendAt() < settings.coins().dividendMinutes() * MINUTE) {
            return;
        }
        coin.lastDividendAt(now);
        if (coin.dividendPot() <= 0 || coin.totalStaked() <= Shitcoin.DUST) {
            return;
        }
        Map<UUID, Long> payouts = split(coin.dividendPot(), coin.stakes());
        long paid = 0;
        for (Map.Entry<UUID, Long> payout : payouts.entrySet()) {
            if (bank.deposit(payout.getKey(), payout.getValue())) {
                paid += payout.getValue();
            }
        }
        coin.dividendPot(coin.dividendPot() - paid);
        if (paid > 0) {
            events.add(new MarketEvent.Dividends(coin.ticker(), paid, payouts));
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Floors the curve value, tolerating the rounding error of a buy followed by a sell. */
    private long curveValue(Shitcoin coin, double amount) {
        return (long) Math.floor(curve.proceeds(coin.supply(), amount, coin.hype()) + 1e-6);
    }

    private void payCreator(Shitcoin coin, long fee) {
        if (fee <= 0) {
            return;
        }
        long toStakers = (long) Math.floor(fee * coin.stakingShare());
        coin.dividendPot(coin.dividendPot() + toStakers);
        if (fee - toStakers > 0) {
            bank.deposit(coin.creator(), fee - toStakers);
        }
    }

    private void bury(Shitcoin coin, DeadCoin.Fate fate, long lostPool) {
        coins.remove(coin.ticker());
        graveyard.addFirst(new DeadCoin(coin.ticker(), coin.name(), coin.creator(), fate,
            Math.max(coin.peakCap(), marketCap(coin)), lostPool, now()));
        while (graveyard.size() > GRAVEYARD) {
            graveyard.removeLast();
        }
    }

    /** Floors each pro-rata share; the remainder stays with the caller. */
    private static Map<UUID, Long> split(long amount, Map<UUID, Double> weights) {
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<UUID, Long> shares = new LinkedHashMap<>();
        if (amount <= 0 || total <= Shitcoin.DUST) {
            return shares;
        }
        weights.forEach((holder, weight) -> {
            long share = (long) Math.floor(amount * (weight / total));
            if (share > 0) {
                shares.put(holder, share);
            }
        });
        return shares;
    }

    private double emeraldFee(UUID player) {
        return settings.emerald().fee() * whaleDiscount(player);
    }

    private double platformFee(UUID player) {
        return settings.platformFee() * whaleDiscount(player);
    }

    private double whaleDiscount(UUID player) {
        return isWhale(player) ? 1.0 - settings.whales().feeDiscount() : 1.0;
    }

    private void requireMinimum(long money) {
        if (money < settings.minTrade()) {
            throw new ForexException("La operación mínima es " + ForexFormat.money(settings.minTrade()) + ".");
        }
    }

    private Shitcoin require(String ticker) {
        return coin(ticker).orElseThrow(() -> new ForexException("No existe $" + normalizeTicker(ticker) + "."));
    }

    private Shitcoin requireOwned(UUID player, String ticker) {
        Shitcoin coin = require(ticker);
        if (!coin.creator().equals(player)) {
            throw new ForexException("$" + coin.ticker() + " no es tuya.");
        }
        return coin;
    }

    private Account account(UUID player) {
        return accounts.computeIfAbsent(player, ignored -> new Account());
    }

    // ---------------------------------------------------------------- persistence hooks

    Map<UUID, Account> accounts() {
        return accounts;
    }

    long nextCycleAt() {
        return nextCycleAt;
    }

    void restoreClock(long restoredNextCycleAt) {
        nextCycleAt = restoredNextCycleAt;
    }

    void restoreCoin(Shitcoin coin) {
        coins.put(coin.ticker(), coin);
    }

    void restoreGrave(DeadCoin dead) {
        if (graveyard.size() < GRAVEYARD) {
            graveyard.addLast(dead);
        }
    }
}
