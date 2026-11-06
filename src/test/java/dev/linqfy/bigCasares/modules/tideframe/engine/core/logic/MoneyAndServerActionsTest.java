package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** The charge, pay and server actions (0.23): parsing, branches, amounts and round trips. */
class MoneyAndServerActionsTest {
    private static List<UiAction> actions(String json) { return ActionParser.actions(Json.parse(json)); }
    private static ActionRunner runner(ActionRunnerTest.Recorder effects, Variables variables) {
        return new ActionRunner(Map.of(), variables, text -> text, effects);
    }

    @Test void chargeTakesTheMoneyOnlyWhenTheBalanceCoversIt() {
        var effects = new ActionRunnerTest.Recorder(); effects.balance = new BigDecimal("100");
        var variables = new Variables(); variables.declare(Map.of("price", "60"));
        var buy = actions("""
            [{"type":"charge","amount":"{price}","then":[{"type":"give","value":"DIAMOND 1"},{"type":"message","value":"ok"}],
              "else":[{"type":"message","value":"falta plata"}]}]""");
        runner(effects, variables).run(buy);
        runner(effects, variables).run(buy);
        assertEquals(List.of("charged 60.00", "give DIAMOND 1", "message ok", "refused 60.00", "message falta plata"), effects.log);
        assertEquals(new BigDecimal("40.00"), effects.balance, "the second purchase took nothing");
    }

    @Test void payAddsAndBadAmountsMoveNothing() {
        var effects = new ActionRunnerTest.Recorder();
        runner(effects, new Variables()).run(actions("""
            [{"type":"pay","value":"12.345"},{"type":"pay","amount":"-5"},{"type":"pay","amount":"abc"},
             {"type":"charge","value":"0","then":[{"type":"message","value":"never"}],"else":[{"type":"message","value":"free is not a price"}]},
             {"type":"deposit","amount":"2000000000000"}]"""));
        assertEquals("paid 12.34", effects.log.get(0), "two decimals kept, rounded down");
        assertTrue(effects.log.get(1).startsWith("warn pay: '-5'"));
        assertTrue(effects.log.get(2).startsWith("warn pay: 'abc'"));
        assertTrue(effects.log.get(3).startsWith("warn charge: '0'"));
        assertEquals("message free is not a price", effects.log.get(4));
        assertTrue(effects.log.get(5).startsWith("warn pay: '2000000000000'"), "more than a trillion is refused");
        assertEquals(new BigDecimal("12.34"), effects.balance);
    }

    @Test void serverEndsTheRunAndOnlyAcceptsProxyNames() {
        var effects = new ActionRunnerTest.Recorder(); var variables = new Variables(); variables.declare(Map.of("target", "lobby-2"));
        runner(effects, variables).run(actions("[{\"type\":\"server\",\"value\":\"{target}\"},{\"type\":\"message\",\"value\":\"not reached\"}]"));
        assertEquals(List.of("connect lobby-2"), effects.log);
        var bad = new ActionRunnerTest.Recorder();
        runner(bad, new Variables()).run(actions("[{\"type\":\"connect\",\"server\":\"lobby 2\"},{\"type\":\"message\",\"value\":\"continues\"}]"));
        assertTrue(bad.log.get(0).startsWith("warn server: 'lobby 2'"));
        assertEquals("message continues", bad.log.get(1));
    }

    @Test void theNewActionsRoundTripAndTheirBranchesAreIndexed() {
        var parsed = actions("""
            [{"type":"charge","amount":"5","then":[{"type":"show","elements":["ok"]}],"else":[{"type":"function","function":"poor"}]},
             {"type":"pay","amount":"3"},{"type":"server","server":"survival"}]""");
        assertEquals(parsed, actions(LogicJson.actions(parsed)), "written and read back the same");
        assertEquals(java.util.Set.of("ok"), LogicIndex.targets(List.of(parsed)));
        assertEquals(java.util.Set.of("poor"), LogicIndex.calls(List.of(parsed)));
    }
}
