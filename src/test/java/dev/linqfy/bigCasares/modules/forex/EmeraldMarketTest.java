package dev.linqfy.bigCasares.modules.forex;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmeraldMarketTest {

    private final ForexSettings settings = ForexSettings.defaults();

    @Test
    void aFullDayWithoutCorrectionsGrowsInsideTheDailyRange() {
        EmeraldMarket slow = new EmeraldMarket(settings);
        EmeraldMarket busy = new EmeraldMarket(settings);
        int cycles = (int) (settings.dayMinutes() / settings.cycleMinutes());
        for (int i = 0; i < cycles; i++) {
            slow.step(new ScriptedRandom().queue(0.99, 0.0), 0.0);
            busy.step(new ScriptedRandom().queue(0.99, 1.0), 1.0);
        }
        assertEquals(1.005, slow.price() / 100.0, 1e-6, "idle market: minimum daily growth");
        assertEquals(1.02, busy.price() / 100.0, 1e-6, "busy market: maximum daily growth");
    }

    @Test
    void correctionsDropTheConfiguredAmountButNeverBreakTheReserveFloor() {
        EmeraldMarket market = new EmeraldMarket(settings);
        market.restore(200, 200, List.of());
        EmeraldMarket.Step step = market.step(new ScriptedRandom().queue(0.0, 1.0), 0);
        assertTrue(step.correction());
        assertEquals(-0.06, step.change(), 1e-9);
        for (int i = 0; i < 10; i++) {
            market.step(new ScriptedRandom().queue(0.0, 1.0), 0);
        }
        assertEquals(200 * (1 - settings.emerald().maxDrawdown()), market.price(), 1e-9);
        assertFalse(market.price() < market.floor());
    }

    @Test
    void neverFallsBelowTheStartingPrice() {
        EmeraldMarket market = new EmeraldMarket(settings);
        market.step(new ScriptedRandom().queue(0.0, 1.0), 0);
        assertEquals(settings.emerald().startPrice(), market.price(), 1e-9);
    }
}
