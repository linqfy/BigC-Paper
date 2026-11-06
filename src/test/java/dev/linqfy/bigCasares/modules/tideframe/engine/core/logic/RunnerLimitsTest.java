package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Limits that keep a page's actions from crashing the owner thread or freezing a run (Codex review, 0.23). */
class RunnerLimitsTest {
    /** Dialogs answered at once with "no", as the closing rules do. */
    static final class AnswersNo extends ActionRunnerTest.Recorder {
        @Override public void confirm(UiAction.Confirm confirm, Runnable yes, Runnable no) { super.confirm(confirm, yes, no); no.run(); }
        @Override public void input(UiAction.Input input, Consumer<String> submit, Runnable cancel) { super.input(input, submit, cancel); cancel.run(); }
    }
    private static List<UiAction> actions(String json) { return ActionParser.actions(ScreenCodec.parse(json)); }
    private static ActionRunner runner(ActionRunnerTest.Recorder effects, Variables variables, Map<String, ScreenLogic.Function> functions) {
        UnaryOperator<String> placeholders = text -> text;
        return new ActionRunner(functions, variables, placeholders, effects);
    }

    @Test void answersGivenAtOnceStayWithinTheStepBudget() {
        var functions = ActionParser.screen(ScreenCodec.parse("{\"functions\":{\"f\":{\"actions\":[{\"type\":\"confirm\",\"value\":\"again?\",\"else\":[{\"type\":\"function\",\"function\":\"f\"}]}]}}}")).functions();
        var effects = new AnswersNo();
        assertDoesNotThrow(() -> runner(effects, new Variables(), functions).run(actions("[{\"type\":\"function\",\"function\":\"f\"}]")),
                "a function that asks again from its own \"no\" used to recurse until the stack overflowed");
        assertTrue(effects.log.stream().anyMatch(line -> line.startsWith("warn Stopped an action list after " + ActionRunner.MAX_STEPS)), effects.log.toString());
    }
    @Test void functionArgumentsCannotGrowForever() {
        var functions = ActionParser.screen(ScreenCodec.parse("{\"functions\":{\"f\":{\"params\":{\"x\":\"a\"},\"actions\":[{\"type\":\"message\",\"value\":\"{x}\"},"
                + "{\"type\":\"function\",\"function\":\"f\",\"arguments\":{\"x\":\"{x}{x}\"}}]}}}")).functions();
        var effects = new ActionRunnerTest.Recorder();
        runner(effects, new Variables(), functions).run(actions("[{\"type\":\"function\",\"function\":\"f\"}]"));
        assertTrue(effects.log.stream().filter(line -> line.startsWith("message ")).allMatch(line -> line.length() <= "message ".length() + Variables.MAX_VALUE), "kept to a variable's length");
        assertTrue(effects.log.getLast().startsWith("warn Stopped an action list"), "and the endless call still stops at the step budget");
    }
    @Test void aLongValueIsCutInTheDialogAndTheBranchesRunFromTheAction() {
        var variables = new Variables();
        variables.declare(Map.of("v", "x".repeat(200), "w", "tab\there"));
        var effects = new ActionRunnerTest.Recorder();
        runner(effects, variables, Map.of()).run(actions("[{\"type\":\"confirm\",\"title\":\"{w}\",\"value\":\"{v}{v}{v}\",\"then\":[{\"type\":\"message\",\"value\":\"{v}{v}{v}\"}]}]"));
        assertEquals(256, effects.asked.message().length(), "the dialog shows the first 256 characters instead of failing");
        assertEquals("tab here", effects.asked.title(), "control characters become spaces");
        assertTrue(effects.asked.then().isEmpty(), "the shown dialog carries no branches");
        effects.yes.run();
        assertEquals("message " + "x".repeat(600), effects.log.getLast(), "the chosen branch runs from the action, with its own text");
    }
    @Test void anAnswerAfterThePageClosedChangesNothing() {
        var variables = new Variables();
        variables.declare(Map.of("*answer", "old"));
        var effects = new ActionRunnerTest.Recorder();
        runner(effects, variables, Map.of()).run(actions("[{\"type\":\"input\",\"variable\":\"*answer\",\"title\":\"Name\",\"on-submit\":[{\"type\":\"message\",\"value\":\"got {answer}\"}]}]"));
        effects.alive = false;
        effects.submit.accept("new");
        assertEquals("old", variables.get("answer"), "the page that asked is gone: its answer is dropped");
        assertFalse(effects.log.stream().anyMatch(line -> line.startsWith("message got")));
    }
    @Test void theRemainderOfAHugeNumberIsExact() {
        assertEquals("1", Expressions.render("{100000000000000000 % 3}", part -> part, name -> null));
        assertEquals("-2", Expressions.render("{-17 % 5}", part -> part, name -> null), "the sign follows the dividend, as in the editor");
    }
}
