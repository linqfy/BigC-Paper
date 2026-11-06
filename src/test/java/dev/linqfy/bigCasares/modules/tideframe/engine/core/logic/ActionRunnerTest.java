package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

class ActionRunnerTest {
    /** Records every effect; delays and dialogs wait until the test releases them. */
    static class Recorder implements ActionRunner.Effects {
        final List<String> log = new ArrayList<>();
        final Deque<Runnable> later = new ArrayDeque<>();
        final List<Integer> delays = new ArrayList<>();
        Consumer<String> submit; Runnable cancel; UiAction.Input shown;
        boolean alive = true; final Set<String> permissions = new HashSet<>();
        public void command(String line) { log.add("command " + line); }
        public void console(String line) { log.add("console " + line); }
        public void message(String text) { log.add("message " + text); }
        public void actionBar(String text) { log.add("actionbar " + text); }
        public void sound(String sound, float volume, float pitch) { log.add("sound " + sound + " " + volume + " " + pitch); }
        public void teleport(String world, String x, String y, String z, String yaw, String pitch) { log.add("teleport " + world + " " + x + " " + y + " " + z + " " + yaw + " " + pitch); }
        public void give(String item, int amount) { log.add("give " + item + " " + amount); }
        public void open(String screen) { log.add("open " + screen); }
        public void back() { log.add("back"); }
        public void close() { log.add("close"); }
        public void visibility(List<String> show, List<String> hide, List<String> toggle) { log.add("visibility " + show + hide + toggle); }
        public void offset(String element, int dx) { log.add("offset " + element + " " + dx); }
        public void tint(String element, String color) { log.add("tint " + element + " " + color); }
        public void animate(String element) { log.add("animate " + element); }
        public void scroll(String element, int rows, boolean absolute) { log.add("scroll " + element + " " + rows + " " + absolute); }
        public boolean hasPermission(String node) { return permissions.contains(node); }
        java.math.BigDecimal balance = java.math.BigDecimal.ZERO;
        public boolean charge(java.math.BigDecimal amount) {
            if (balance.compareTo(amount) < 0) { log.add("refused " + amount.toPlainString()); return false; }
            balance = balance.subtract(amount); log.add("charged " + amount.toPlainString()); return true;
        }
        public void pay(java.math.BigDecimal amount) { balance = balance.add(amount); log.add("paid " + amount.toPlainString()); }
        public void connect(String server) { log.add("connect " + server); }
        public void input(UiAction.Input input, Consumer<String> submit, Runnable cancel) { shown = input; this.submit = submit; this.cancel = cancel; log.add("input " + input.title()); }
        Runnable yes, no; UiAction.Confirm asked;
        public void confirm(UiAction.Confirm confirm, Runnable yes, Runnable no) { asked = confirm; this.yes = yes; this.no = no; log.add("confirm " + confirm.title() + " | " + confirm.message()); }
        public void playback(String element, String command, int frame) { log.add(command + " " + element + (command.equals("frame") ? " " + frame : "")); }
        public void hud(String screen, boolean add) { log.add("hud " + (add ? "add " : "remove ") + screen); }
        public void toast(String screen, double seconds, Map<String, String> values) { log.add("toast " + screen + " " + seconds + " " + values); }
        public void variablesChanged() { log.add("changed"); }
        public void later(int ticks, Runnable resume) { delays.add(ticks); later.add(resume); }
        public boolean alive() { return alive; }
        public void warn(String message) { log.add("warn " + message); }
        void tick() { var queued = new ArrayList<>(later); later.clear(); queued.forEach(Runnable::run); }
    }
    private static List<UiAction> actions(String json) { return ActionParser.actions(Json.parse(json)); }
    private static ActionRunner runner(Recorder effects, Variables variables, Map<String, ScreenLogic.Function> functions) {
        UnaryOperator<String> placeholders = text -> text.replace("%player_name%", "Steve").replace("{player}", "Steve").replace("%health%", "17.5");
        return new ActionRunner(functions, variables, placeholders, effects);
    }

    @Test void runsInOrderAndResolvesPlaceholdersThenVariables() {
        var effects = new Recorder(); var variables = new Variables();
        variables.declare(Map.of("coins", "100"));
        runner(effects, variables, Map.of()).run(actions("""
            [{"type":"message","value":"<green>Hi %player_name%, you have {coins} coins"},
             {"type":"console","value":"/give {player} diamond {coins}"},
             {"type":"sound","value":"entity.player.levelup 1,2"},
             {"type":"teleport","value":"world_nether 0 64 {coins}"},
             {"type":"actionbar","value":"HP %health,MIN=0,MAX=10%"}]"""));
        assertEquals(List.of("message <green>Hi Steve, you have 100 coins", "console give Steve diamond 100",
                "sound entity.player.levelup 1.0 2.0", "teleport world_nether 0 64 100 null null", "actionbar HP 10"), effects.log);
    }
    @Test void typedValuesAreNeverResolvedAgain() {
        var effects = new Recorder(); var variables = new Variables();
        variables.apply("name", "set", "%player_name% {coins}");
        variables.apply("coins", "set", "5");
        runner(effects, variables, Map.of()).run(actions("[{\"type\":\"message\",\"value\":\"{name}\"}]"));
        assertEquals(List.of("message %player_name% {coins}"), effects.log);
    }
    @Test void delaysPauseAndResumeAndStopWhenTheScreenCloses() {
        var effects = new Recorder();
        runner(effects, new Variables(), Map.of()).run(actions("""
            [{"type":"message","value":"a"},{"type":"delay","value":"1s"},{"type":"message","value":"b"},
             {"type":"delay","value":"500ms"},{"type":"message","value":"c"}]"""));
        assertEquals(List.of("message a"), effects.log); assertEquals(List.of(20), effects.delays);
        effects.tick(); assertEquals(List.of("message a", "message b"), effects.log); assertEquals(List.of(20, 10), effects.delays);
        effects.alive = false; effects.tick();
        assertEquals(List.of("message a", "message b"), effects.log);
    }
    @Test void redirectBackAndCloseEndTheChain() {
        for (String type : List.of("redirect\",\"value\":\"shop", "back", "close")) {
            var effects = new Recorder();
            runner(effects, new Variables(), Map.of()).run(actions("[{\"type\":\"" + type + "\"},{\"type\":\"message\",\"value\":\"never\"}]"));
            assertEquals(1, effects.log.size(), type); assertFalse(effects.log.get(0).startsWith("message"));
        }
    }
    @Test void conditionsAndPermissionsPickTheirBranch() {
        var effects = new Recorder(); var variables = new Variables();
        variables.declare(Map.of("coins", "40", "price", "50"));
        effects.permissions.add("shop.vip");
        runner(effects, variables, Map.of()).run(actions("""
            [{"type":"condition","value":"{coins} >= {price}","then":{"actions":[{"type":"message","value":"buy"}]},
              "else":[{"type":"message","value":"poor"}]},
             {"type":"permission","permission":"shop.vip","then":[{"type":"message","value":"vip"}],"else":[{"type":"message","value":"no"}]},
             {"type":"condition","value":"%health% > 10 && {coins} = 40","then":[{"type":"message","value":"both"}]}]"""));
        assertEquals(List.of("message poor", "message vip", "message both"), effects.log);
    }
    @Test void variableOperationsReportChangesOnly() {
        var effects = new Recorder(); var variables = new Variables();
        variables.declare(Map.of("coins", "100"));
        runner(effects, variables, Map.of()).run(actions("""
            [{"type":"variable","variable":"coins","operation":"subtract","value":"25.5"},
             {"type":"variable","variable":"coins","operation":"multiply","value":"2"},
             {"type":"variable","variable":"coins","operation":"divide","value":"0"},
             {"type":"variable","variable":"coins","operation":"add","value":"abc"},
             {"type":"variable","variable":"*total","value":"{coins}"}]"""));
        assertEquals("149", variables.get("coins")); assertEquals("149", variables.get("total"));
        assertEquals("149", variables.globals().get("total"));
        assertEquals(3, effects.log.stream().filter("changed"::equals).count());
    }
    @Test void functionsTakeDefaultsAndArgumentsAsScope() {
        var effects = new Recorder(); var variables = new Variables();
        variables.declare(Map.of("item", "VARIABLE"));
        var buy = new ScreenLogic.Function(new LinkedHashMap<>(Map.of("item", "DIAMOND", "price", "100")),
                actions("[{\"type\":\"console\",\"value\":\"give {player} {item} 1\"},{\"type\":\"message\",\"value\":\"{item} for {price} (${item})\"}]"));
        runner(effects, variables, Map.of("buy", buy)).run(actions("""
            [{"type":"function","function":"buy"},{"type":"function","function":"buy","arguments":{"item":"NETHERITE_INGOT","price":500}},
             {"type":"function","function":"missing"},{"type":"message","value":"after {item}"}]"""));
        assertEquals("console give Steve DIAMOND 1", effects.log.get(0));
        assertEquals("message DIAMOND for 100 (VARIABLE)", effects.log.get(1));
        assertEquals("message NETHERITE_INGOT for 500 (VARIABLE)", effects.log.get(3));
        assertTrue(effects.log.get(4).startsWith("warn Unknown function 'missing'"));
        assertEquals("message after VARIABLE", effects.log.get(5));
    }
    @Test void recursiveCountdownWithDelayRunsInConstantDepth() {
        var effects = new Recorder(); var variables = new Variables();
        variables.declare(Map.of("left", "100"));
        var countdown = new ScreenLogic.Function(Map.of(), actions("""
            [{"type":"variable","variable":"left","operation":"subtract","value":"1"},
             {"type":"condition","value":"{left} > 0","then":[{"type":"delay","value":"1t"},{"type":"function","function":"countdown"}]}]"""));
        runner(effects, variables, Map.of("countdown", countdown)).run(actions("[{\"type\":\"function\",\"function\":\"countdown\"}]"));
        for (int i = 0; i < 200 && !effects.later.isEmpty(); i++) effects.tick();
        assertEquals("0", variables.get("left"));
        assertTrue(effects.log.stream().noneMatch(line -> line.startsWith("warn")), effects.log.toString());
    }
    @Test void endlessLoopWithoutDelayIsStopped() {
        var effects = new Recorder();
        var loop = new ScreenLogic.Function(Map.of(), actions("[{\"type\":\"message\",\"value\":\"x\"},{\"type\":\"function\",\"function\":\"loop\"}]"));
        runner(effects, new Variables(), Map.of("loop", loop)).run(actions("[{\"type\":\"function\",\"function\":\"loop\"}]"));
        assertTrue(effects.log.getLast().startsWith("warn Stopped an action list"));
        assertTrue(effects.log.size() < ActionRunner.MAX_STEPS + 2);
        var deep = new ScreenLogic.Function(Map.of(), actions("[{\"type\":\"function\",\"function\":\"deep\"},{\"type\":\"message\",\"value\":\"x\"}]"));
        var nested = new Recorder();
        runner(nested, new Variables(), Map.of("deep", deep)).run(actions("[{\"type\":\"function\",\"function\":\"deep\"}]"));
        assertTrue(nested.log.getLast().startsWith("warn Actions nested deeper"), nested.log.toString());
    }
    @Test void inputSavesAcceptedTextAndRunsTheMatchingBranchOnce() {
        var effects = new Recorder(); var variables = new Variables();
        var list = actions("""
            [{"type":"input","variable":"amount","title":"Amount for {player}","max-length":4,"filter":"digits",
              "on-submit":[{"type":"message","value":"ok {amount}"}],"on-cancel":[{"type":"message","value":"cancelled"}]},
             {"type":"message","value":"done"}]""");
        var runner = runner(effects, variables, Map.of());
        runner.run(list);
        assertEquals("Amount for Steve", effects.shown.title());
        effects.submit.accept("12"); effects.submit.accept("99"); effects.cancel.run();
        assertEquals(List.of("input Amount for Steve", "changed", "message ok 12", "message done"), effects.log);
        effects.log.clear(); runner.run(list); effects.submit.accept("12345");
        assertEquals(List.of("input Amount for Steve", "message cancelled", "message done"), effects.log);
        assertEquals("12", variables.get("amount"));
    }
    @Test void scopeTokensFeedListRows() {
        var effects = new Recorder();
        runner(effects, new Variables(), Map.of()).run(actions("[{\"type\":\"message\",\"value\":\"{index}: {item.name} {item}\"}]"),
                Map.of("index", "2", "item.name", "Sword", "item", "sword"));
        assertEquals(List.of("message 2: Sword sword"), effects.log);
    }
    @Test void visualActionsResolveNumbers() {
        var effects = new Recorder(); var variables = new Variables();
        variables.declare(Map.of("dx", "12.6"));
        runner(effects, variables, Map.of()).run(actions("""
            [{"type":"offset","element":"panel","dx":"{dx}"},{"type":"tint","element":"panel","value":"#ff0000"},{"type":"tint","element":"panel"},
             {"type":"scroll","element":"rows","rows":"-2"},{"type":"show","elements":["a","b"]},{"type":"toggle","show":["a"],"hide":["b"],"elements":["c"]},
             {"type":"give","value":"DIAMOND {dx}"},{"type":"animation","element":"panel"}]"""));
        assertEquals(List.of("offset panel 13", "tint panel #ff0000", "tint panel null", "scroll rows -2 false", "visibility [a, b][][]",
                "visibility [a][b][c]", "give DIAMOND 13", "animate panel"), effects.log);
    }
}
