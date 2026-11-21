package dev.linqfy.bigCasares.modules.forex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Candles of an asset: the closed ones, oldest first, plus the one being built during the current cycle.
 * {@link #version()} changes on every update, so charts know when to redraw.
 */
public final class PriceSeries {

    static final int CAPACITY = 48;

    private final Deque<Candle> closed = new ArrayDeque<>();
    private Candle current;
    private long version;

    public PriceSeries(double openingPrice) {
        this.current = Candle.flat(openingPrice);
    }

    public Candle current() {
        return current;
    }

    public long version() {
        return version;
    }

    /** Closed candles followed by the current one. */
    public List<Candle> candles() {
        List<Candle> all = new ArrayList<>(closed);
        all.add(current);
        return all;
    }

    /** Close prices, oldest first, the current one last. */
    public List<Double> closes() {
        return candles().stream().map(Candle::close).toList();
    }

    void observe(double price) {
        current = current.observe(price);
        version++;
    }

    void addVolume(long amount) {
        current = current.withVolume(amount);
        version++;
    }

    /** Ends the cycle: the next candle opens at the last close. */
    void close() {
        closed.addLast(current);
        while (closed.size() > CAPACITY) {
            closed.removeFirst();
        }
        current = Candle.flat(current.close());
        version++;
    }

    /** Starts over with a single flat candle. */
    void reset(double price) {
        closed.clear();
        current = Candle.flat(price);
        version++;
    }

    List<String> encodeClosed() {
        return closed.stream().map(Candle::encode).toList();
    }

    void restore(List<Candle> restoredClosed, Candle restoredCurrent) {
        closed.clear();
        restoredClosed.forEach(closed::addLast);
        while (closed.size() > CAPACITY) {
            closed.removeFirst();
        }
        current = restoredCurrent;
        version++;
    }
}
