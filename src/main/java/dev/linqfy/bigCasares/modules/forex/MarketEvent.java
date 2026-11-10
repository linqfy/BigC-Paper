package dev.linqfy.bigCasares.modules.forex;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Things the market tick wants the server to hear about. */
public sealed interface MarketEvent {

    record EmeraldMoved(EmeraldMarket.Step step) implements MarketEvent {
    }

    /** The 1-in-10 "to the moon" jump. */
    record Pump(String ticker, double gain) implements MarketEvent {
    }

    /** A coin reached critical mass: the coin will be flipped at {@code flipAt}. */
    record BubbleFormed(String ticker, long flipAt) implements MarketEvent {
    }

    /** Holders left or the pool dried up below critical mass; the flip is called off. */
    record BubbleDeflated(String ticker) implements MarketEvent {
    }

    record FlipWarning(String ticker, long millisLeft, Set<UUID> holders) implements MarketEvent {
    }

    /** Heads: the bubble survives and climbs another step. */
    record FlipSurvived(String ticker, int survived, double gain, long nextFlipAt) implements MarketEvent {
    }

    /** Tails: total collapse. The pool burns and every holder is wiped out. */
    record Collapse(String ticker, String name, long burned, double peakCap, Set<UUID> victims)
        implements MarketEvent {
    }

    record Dividends(String ticker, long total, Map<UUID, Long> payouts) implements MarketEvent {
    }
}
