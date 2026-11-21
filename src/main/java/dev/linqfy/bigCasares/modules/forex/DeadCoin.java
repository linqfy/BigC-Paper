package dev.linqfy.bigCasares.modules.forex;

import java.util.List;
import java.util.UUID;

/** A dead coin in the graveyard ("el cementerio"), with its last candles so its chart can still be shown. */
public record DeadCoin(String ticker, String name, UUID creator, Fate fate, double peakCap, long lostPool,
                       long diedAt, List<Candle> candles) {

    public DeadCoin {
        candles = List.copyOf(candles);
    }

    public enum Fate {
        RUG_PULL,
        COLLAPSE
    }
}
