package dev.linqfy.bigCasares.modules.forex;

import java.util.UUID;

/** A dead coin in the graveyard ("el cementerio"). */
public record DeadCoin(String ticker, String name, UUID creator, Fate fate, double peakCap, long lostPool,
                       long diedAt) {

    public enum Fate {
        RUG_PULL,
        COLLAPSE
    }
}
