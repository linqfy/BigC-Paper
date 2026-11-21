package dev.linqfy.bigCasares.modules.forex;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.random.RandomGenerator;

/** Returns queued values, then fixed defaults: no pumps, no corrections, no volatility, heads. */
final class ScriptedRandom implements RandomGenerator {

    private final Deque<Double> doubles = new ArrayDeque<>();
    private final Deque<Boolean> booleans = new ArrayDeque<>();
    private double defaultDouble = 0.999;

    ScriptedRandom queue(double... values) {
        for (double value : values) {
            doubles.addLast(value);
        }
        return this;
    }

    ScriptedRandom flips(boolean... values) {
        for (boolean value : values) {
            booleans.addLast(value);
        }
        return this;
    }

    ScriptedRandom defaultDouble(double value) {
        defaultDouble = value;
        return this;
    }

    @Override
    public double nextDouble() {
        return doubles.isEmpty() ? defaultDouble : doubles.removeFirst();
    }

    @Override
    public boolean nextBoolean() {
        return booleans.isEmpty() || booleans.removeFirst();
    }

    @Override
    public double nextGaussian() {
        return 0;
    }

    @Override
    public long nextLong() {
        return 0;
    }
}
