package dev.linqfy.bigCasares.modules.forex;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A player-launched token. {@code pool} is the real Vault money backing it; {@code dividendPot} is the creator
 * fee share waiting to be paid to stakers. Staked tokens are part of the holder's balance but cannot be sold.
 */
public final class Shitcoin {

    /** Tokens below this are dust: they do not count as a holder and are cleared on sale. */
    public static final double DUST = 1e-6;
    static final int HISTORY = 48;

    private final String ticker;
    private final String name;
    private final UUID creator;
    private final long createdAt;
    private final Map<UUID, Double> holdings = new LinkedHashMap<>();
    private final Map<UUID, Double> staked = new LinkedHashMap<>();
    private final Map<UUID, Long> lockedUntil = new LinkedHashMap<>();
    private final Deque<Double> history = new ArrayDeque<>();

    private double creatorFee;
    private double stakingShare;
    private double hype = 1.0;
    private double supply;
    private long pool;
    private long dividendPot;
    private String teamId;
    private long nextFlipAt;
    private int warningsSent;
    private int flipsSurvived;
    private long lastDividendAt;
    private double peakCap;
    private long cycleVolume;
    private long lifetimeVolume;

    public Shitcoin(String ticker, String name, UUID creator, long createdAt, double creatorFee, double stakingShare) {
        this.ticker = ticker;
        this.name = name;
        this.creator = creator;
        this.createdAt = createdAt;
        this.creatorFee = creatorFee;
        this.stakingShare = stakingShare;
        this.lastDividendAt = createdAt;
    }

    public String ticker() {
        return ticker;
    }

    public String name() {
        return name;
    }

    public UUID creator() {
        return creator;
    }

    public long createdAt() {
        return createdAt;
    }

    public double creatorFee() {
        return creatorFee;
    }

    void creatorFee(double value) {
        creatorFee = value;
    }

    public double stakingShare() {
        return stakingShare;
    }

    void stakingShare(double value) {
        stakingShare = value;
    }

    public double hype() {
        return hype;
    }

    void hype(double value) {
        hype = Math.max(1e-12, value);
    }

    public double supply() {
        return supply;
    }

    void supply(double value) {
        supply = Math.max(0, value);
    }

    public long pool() {
        return pool;
    }

    void pool(long value) {
        pool = Math.max(0, value);
    }

    public long dividendPot() {
        return dividendPot;
    }

    void dividendPot(long value) {
        dividendPot = Math.max(0, value);
    }

    public Optional<String> teamId() {
        return Optional.ofNullable(teamId);
    }

    void teamId(String value) {
        teamId = value;
    }

    public long nextFlipAt() {
        return nextFlipAt;
    }

    void nextFlipAt(long value) {
        nextFlipAt = value;
        warningsSent = 0;
    }

    int warningsSent() {
        return warningsSent;
    }

    void warningsSent(int value) {
        warningsSent = value;
    }

    public int flipsSurvived() {
        return flipsSurvived;
    }

    void flipsSurvived(int value) {
        flipsSurvived = value;
    }

    long lastDividendAt() {
        return lastDividendAt;
    }

    void lastDividendAt(long value) {
        lastDividendAt = value;
    }

    public double peakCap() {
        return peakCap;
    }

    void peakCap(double value) {
        peakCap = value;
    }

    long cycleVolume() {
        return cycleVolume;
    }

    void cycleVolume(long value) {
        cycleVolume = value;
    }

    public long lifetimeVolume() {
        return lifetimeVolume;
    }

    void lifetimeVolume(long value) {
        lifetimeVolume = value;
    }

    void addVolume(long amount) {
        cycleVolume += amount;
        lifetimeVolume += amount;
    }

    public boolean isBubble() {
        return nextFlipAt > 0;
    }

    /** Total tokens held, staked ones included. */
    public double balance(UUID holder) {
        return holdings.getOrDefault(holder, 0.0);
    }

    public double staked(UUID holder) {
        return staked.getOrDefault(holder, 0.0);
    }

    public double free(UUID holder) {
        return Math.max(0, balance(holder) - staked(holder));
    }

    public long lockedUntil(UUID holder) {
        return lockedUntil.getOrDefault(holder, 0L);
    }

    public double totalStaked() {
        return staked.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public Map<UUID, Double> holdings() {
        return Map.copyOf(holdings);
    }

    public Map<UUID, Double> stakes() {
        return Map.copyOf(staked);
    }

    Map<UUID, Long> locks() {
        return Map.copyOf(lockedUntil);
    }

    public int holderCount() {
        return (int) holdings.values().stream().filter(amount -> amount >= 1.0).count();
    }

    void credit(UUID holder, double amount) {
        holdings.merge(holder, amount, Double::sum);
    }

    void debit(UUID holder, double amount) {
        double left = balance(holder) - amount;
        if (left <= DUST) {
            holdings.remove(holder);
            staked.remove(holder);
            lockedUntil.remove(holder);
        } else {
            holdings.put(holder, left);
        }
    }

    void stake(UUID holder, double amount, long lockUntil) {
        staked.merge(holder, amount, Double::sum);
        lockedUntil.put(holder, lockUntil);
    }

    void unstake(UUID holder, double amount) {
        double left = staked(holder) - amount;
        if (left <= DUST) {
            staked.remove(holder);
            lockedUntil.remove(holder);
        } else {
            staked.put(holder, left);
        }
    }

    void restoreHolding(UUID holder, double amount, double stakedAmount, long lockUntil) {
        if (amount > DUST) {
            holdings.put(holder, amount);
        }
        if (stakedAmount > DUST) {
            staked.put(holder, Math.min(stakedAmount, amount));
            lockedUntil.put(holder, lockUntil);
        }
    }

    public List<Double> history() {
        return List.copyOf(history);
    }

    void record(double price) {
        history.addLast(price);
        while (history.size() > HISTORY) {
            history.removeFirst();
        }
    }
}
