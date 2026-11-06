package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.UiAction;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

class ScreenRuntimeTest {
    /** A host that records effects and keeps the view the way a session would. */
    static final class FakeHost implements ScreenRuntime.Host {
        final List<String> log = new ArrayList<>();
        final Deque<Runnable> later = new ArrayDeque<>();
        final Map<String, List<Map<String, String>>> lists = new HashMap<>();
        ViewState view; boolean refuseDrop; Consumer<String> submit; Runnable cancel;
        FakeHost(Screen screen) { view = ViewState.initial(screen, 0); }
        public void command(String line) { log.add("command " + line); }
        public void console(String line) { log.add("console " + line); }
        public void message(String text) { log.add("message " + text); }
        public void actionBar(String text) { log.add("actionbar " + text); }
        public void sound(String sound, float volume, float pitch) { log.add("sound " + sound); }
        public void teleport(String world, String x, String y, String z, String yaw, String pitch) { log.add("teleport"); }
        public void give(String item, int amount) { log.add("give " + item + " " + amount); }
        public void open(String screen) { log.add("open " + screen); }
        public void back() { log.add("back"); }
        public void close() { log.add("close"); }
        public void legacy(String action) { log.add("legacy " + action); }
        public boolean hasPermission(String node) { return true; }
        java.math.BigDecimal balance = java.math.BigDecimal.ZERO;
        public boolean charge(java.math.BigDecimal amount) { if (balance.compareTo(amount) < 0) return false; balance = balance.subtract(amount); log.add("charge " + amount); return true; }
        public boolean pay(java.math.BigDecimal amount) { balance = balance.add(amount); log.add("pay " + amount); return true; }
        public void connect(String server) { log.add("connect " + server); }
        public void input(UiAction.Input input, Consumer<String> submit, Runnable cancel) { this.submit = submit; this.cancel = cancel; log.add("input"); }
        public void later(int ticks, Runnable task) { later.add(task); }
        public void warn(String message) { log.add("warn " + message); }
        public String placeholders(String text) { return text.replace("{player}", "Alex"); }
        public void view(UnaryOperator<ViewState> change) { view = change.apply(view); }
        public ViewState view() { return view; }
        public void refreshLive() { }
        public List<Map<String, String>> list(String source) { return lists.get(source); }
        public boolean dropped(String dragged, String target) { log.add("dropped " + dragged + " " + target); return !refuseDrop; }
        long clock = 42;
        public long now() { return clock; }
        final Map<String, Long> cooldowns = new HashMap<>();
        public Map<String, Long> cooldowns() { return cooldowns; }
        Runnable yes, no;
        public void confirm(UiAction.Confirm confirm, Runnable yes, Runnable no) { this.yes = yes; this.no = no; log.add("confirm " + confirm.message()); }
        int frames, interval = 100;
        public int frameCount(String element) { return frames; }
        public int frameInterval(String element) { return interval; }
        final List<String> changes = new ArrayList<>();
        public void variableChanged(String name, String before, String after) { changes.add(name + " " + before + " -> " + after); }
        public void hud(String screen, boolean add) { log.add("hud " + (add ? "add " : "remove ") + screen); }
        public void toast(String screen, double seconds, Map<String, String> values) { log.add("toast " + screen + " " + seconds + " " + values); }
    }
    private static Screen.Element button(String id, double y, String logic) {
        var e = new Screen.Element(id, id.toUpperCase(Locale.ROOT), 0, y, .5, .12, "button", "");
        return logic == null ? e : e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
    private static Screen screen(String screenLogic, Screen.Element... elements) {
        var screen = new Screen("shop", "SHOP", List.of(elements), RenderQuality.low());
        return screenLogic == null ? screen : screen.withLogic(ActionParser.screen(ScreenCodec.parse(screenLogic)));
    }

    @Test void onOpenDeclaresVariablesAndClicksRunActionsWithFallbackToLegacy() {
        var buy = button("buy", .5, "{\"actions\":[{\"type\":\"variable\",\"variable\":\"coins\",\"operation\":\"subtract\",\"value\":\"30\"},{\"type\":\"message\",\"value\":\"{player} has {coins}\"}]}");
        var old = new Screen.Element("old", "OLD", 0, 0, .5, .12, "button", "message:hi");
        var screen = screen("{\"variables\":{\"coins\":\"100\"},\"on-open\":[{\"type\":\"sound\",\"value\":\"ui.button.click\"}]}", buy, old);
        var host = new FakeHost(screen);
        var runtime = new ScreenRuntime(screen, new HashMap<>(), host);
        runtime.open();
        runtime.click("buy", UiSession.Click.LEFT, "");
        runtime.click("old", UiSession.Click.LEFT, "message:hi");
        runtime.click("old", UiSession.Click.RIGHT, "message:hi");
        assertEquals(List.of("sound ui.button.click", "message Alex has 70", "legacy message:hi"), host.log);
    }
    @Test void valuesGivenOnOpeningAreThereForOnOpen() {
        var screen = screen("{\"variables\":{\"titulo\":\"Aviso\",\"texto\":\"-\"},\"on-open\":[{\"type\":\"message\",\"value\":\"{titulo}: {texto}\"}]}");
        var host = new FakeHost(screen);
        new ScreenRuntime(screen, new HashMap<>(), host).open(Map.of("titulo", "Logro"));
        new ScreenRuntime(screen, new HashMap<>(), host).open();
        assertEquals(List.of("message Logro: -", "message Aviso: -"), host.log, "a toast's values replace the page's own before on-open; plain opening keeps them");
    }
    @Test void keybindsVisibilityAndConditionsDriveTheView() {
        var panel = new Screen.Element("panel", "PANEL", 0, -.5, .5, .2, "label", "")
                .withLogic(ActionParser.element(ScreenCodec.parse("{\"hidden\":true}")));
        var vip = new Screen.Element("vip", "VIP", 0, .7, .5, .1, "label", "")
                .withLogic(ActionParser.element(ScreenCodec.parse("{\"visible-if\":\"{coins} >= 50\"}")));
        var screen = screen("""
            {"variables":{"coins":"10"},"keybinds":[{"key":"Q","actions":[{"type":"toggle","elements":["panel"]}]},
             {"key":"1","actions":[{"type":"variable","variable":"coins","value":"80"}]}]}""", panel, vip);
        var host = new FakeHost(screen);
        var runtime = new ScreenRuntime(screen, new HashMap<>(), host);
        runtime.open();
        assertFalse(host.view.shown("panel")); assertFalse(host.view.shown("vip"));
        assertTrue(runtime.key("Q")); assertTrue(host.view.shown("panel"));
        assertFalse(runtime.key("W"));
        runtime.key("1");
        assertTrue(host.view.shown("vip"), "visible-if re-checked when variables change");
    }
    @Test void listsFillFromDataScrollAndFilter() {
        var rows = button("server", .6, """
            {"list":{"source":"servers","rows":3,"spacing":20,"filter":"search","on-select":[{"type":"message","value":"join {item} #{index} ({item.players})"}]}}""");
        var screen = screen("{\"variables\":{\"search\":\"\"}}", rows);
        var host = new FakeHost(screen);
        host.lists.put("servers", List.of(Map.of("name", "Survival", "players", "12"), Map.of("name", "Skyblock", "players", "4"),
                Map.of("name", "Bedwars", "players", "30"), Map.of("name", "Creative", "players", "1")));
        var runtime = new ScreenRuntime(screen, new HashMap<>(), host);
        runtime.open();
        assertEquals(3, host.view.rows("server"));
        runtime.click(ListRows.rowId("server", 1), UiSession.Click.LEFT, "");
        assertEquals("message join Skyblock #2 (4)", host.log.getLast());
        runtime.scrollList("server", 5, false);
        assertEquals(1, runtime.scrolled("server")); assertEquals(3, host.view.rows("server"));
        runtime.click(ListRows.rowId("server", 2), UiSession.Click.LEFT, "");
        assertEquals("message join Creative #4 (1)", host.log.getLast());
        runtime.variables().apply("search", "set", "sky");
        runtime.variablesChanged();
        assertEquals(0, runtime.scrolled("server")); assertEquals(1, host.view.rows("server"));
        assertEquals("Skyblock", runtime.rowScope(screen.element("server").orElseThrow(), 0).get("item"));
        int before = host.log.size();
        runtime.click(ListRows.rowId("server", 2), UiSession.Click.LEFT, "");
        assertEquals(before, host.log.size(), "rows without data do nothing");
    }
    @Test void variableListsSplitOnCommas() {
        var rows = button("item", .6, "{\"list\":{\"source\":\"items\",\"rows\":4,\"spacing\":20}}");
        var screen = screen("{\"variables\":{\"items\":\"Sword, Bow ,Apple\"}}", rows);
        var host = new FakeHost(screen);
        var runtime = new ScreenRuntime(screen, new HashMap<>(), host);
        runtime.open();
        assertEquals(3, host.view.rows("item"));
        assertEquals(List.of("Sword", "Bow", "Apple"), runtime.visibleRows(screen.element("item").orElseThrow()).stream().map(r -> r.get("item")).toList());
    }
    @Test void dragPicksUpAndDropRunsTargetActions() {
        var sword = button("sword", .5, "{\"draggable\":true}");
        var slot = button("slot", -.5, "{\"on-drop\":[{\"type\":\"message\",\"value\":\"{dragged} -> {target}\"}]}");
        var screen = screen(null, sword, slot);
        var host = new FakeHost(screen);
        var runtime = new ScreenRuntime(screen, new HashMap<>(), host);
        runtime.open();
        runtime.click("sword", UiSession.Click.LEFT, "");
        assertEquals("sword", host.view.carried()); assertEquals(ViewState.CARRY_TINT, host.view.tint("sword"));
        runtime.click("slot", UiSession.Click.LEFT, "");
        assertNull(host.view.carried());
        assertEquals(List.of("dropped sword slot", "message sword -> slot"), host.log);
        runtime.click("sword", UiSession.Click.LEFT, ""); host.refuseDrop = true; runtime.click("slot", UiSession.Click.LEFT, "");
        assertEquals("dropped sword slot", host.log.getLast(), "a cancelled drop runs nothing");
        runtime.click("sword", UiSession.Click.LEFT, ""); runtime.click(null, UiSession.Click.LEFT, "");
        assertNull(host.view.carried(), "clicking empty space puts it back");
    }
    @Test void shutdownRunsOnCloseOnceAndStopsPendingDelays() {
        var button = button("go", 0, "{\"actions\":[{\"type\":\"delay\",\"value\":\"1s\"},{\"type\":\"message\",\"value\":\"late\"}]}");
        var screen = screen("{\"on-close\":[{\"type\":\"message\",\"value\":\"bye\"},{\"type\":\"redirect\",\"value\":\"x\"},{\"type\":\"delay\",\"value\":\"1t\"},{\"type\":\"message\",\"value\":\"never\"}]}", button);
        var host = new FakeHost(screen);
        var runtime = new ScreenRuntime(screen, new HashMap<>(), host);
        runtime.open();
        runtime.click("go", UiSession.Click.LEFT, "");
        runtime.shutdown(); runtime.shutdown();
        host.later.forEach(Runnable::run);
        assertEquals(List.of("message bye"), host.log);
    }
    @Test void globalsSurviveAcrossScreensOfTheSession() {
        var globals = new HashMap<String, String>();
        var first = screen("{\"variables\":{\"*coins\":\"100\"}}", button("a", 0, null));
        var host = new FakeHost(first);
        var one = new ScreenRuntime(first, globals, host); one.open();
        one.variables().apply("coins", "subtract", "25");
        var second = screen("{\"variables\":{\"*coins\":\"100\",\"page\":\"2\"}}", button("b", 0, null));
        var two = new ScreenRuntime(second, globals, new FakeHost(second)); two.open();
        assertEquals("75", two.variables().get("coins")); assertEquals("2", two.variables().get("page")); assertNull(one.variables().get("page"));
    }
    @Test void viewStateTargetsFollowVisibilityOffsetsAndRows() {
        var fixed = button("fixed", .8, null);
        var moving = button("moving", .4, "{\"actions\":[{\"type\":\"offset\",\"element\":\"moving\",\"dx\":\"10\"}]}");
        var rows = button("row", 0, "{\"list\":{\"source\":\"r\",\"rows\":2,\"spacing\":18}}");
        var hidden = button("ghost", -.8, "{\"hidden\":true}");
        var screen = screen(null, fixed, moving, rows, hidden);
        var view = ViewState.initial(screen, 0);
        assertEquals(Set.of("moving", "row", "ghost"), view.runtime());
        assertEquals(List.of("fixed", "moving"), view.targets(screen).stream().map(r -> r.id()).toList());
        view = view.rows("row", 2).offset("moving", 64).visibility(List.of("ghost"), List.of(), List.of());
        var targets = view.targets(screen);
        assertEquals(List.of("fixed", "moving", "row#0", "row#1", "ghost"), targets.stream().map(r -> r.id()).toList());
        assertEquals(.5, targets.get(1).x(), 1e-9);
        assertEquals(-18 / 72.0, targets.get(3).y(), 1e-9);
        assertSame(view, view.offset("fixed", 3), "static elements never move");
        assertThrows(IllegalArgumentException.class, () -> ViewState.initial(screen, 0).tint("moving", "red"));
    }
    @Test void listsMustFitOnTheScreen() {
        assertThrows(IllegalArgumentException.class, () -> screen(null, button("row", -.8, "{\"list\":{\"source\":\"r\",\"rows\":8,\"spacing\":20}}")));
        assertEquals(4, ListRows.expand(List.of(button("row", .9, "{\"list\":{\"source\":\"r\",\"rows\":4,\"spacing\":20}}"))).size());
        assertEquals("row", ListRows.template("row#3")); assertEquals(3, ListRows.index("row#3")); assertEquals(-1, ListRows.index("row"));
    }
    @Test void helpersSanitizeCommandsCoordinatesFramesAndColours() {
        assertEquals("give Steve diamond", LogicController.sanitize("/give Steve\ndiamond"));
        assertEquals(12.5, LogicController.coordinate("~2.5", 10), 1e-9);
        assertEquals(-3, LogicController.coordinate("-3", 10), 1e-9);
        assertThrows(NumberFormatException.class, () -> LogicController.coordinate("1e9", 0));
        assertEquals(0, DynamicLayer.frameIndex(0, 50, 10, true));
        assertEquals(3, DynamicLayer.frameIndex(3 * 50_000_000L + 1, 50, 10, true));
        assertEquals(2, DynamicLayer.frameIndex(12 * 50_000_000L, 50, 10, true));
        assertEquals(9, DynamicLayer.frameIndex(12 * 50_000_000L, 50, 10, false));
        assertEquals("#800000", DynamicLayer.multiply("#ff0000", "#808080"));
        assertEquals("#ffffff", DynamicLayer.multiply("#ffffff", null));
        assertEquals("<green>Hi <bold>you <#33ff3d>x<reset>", TextFormat.toMiniMessage("&aHi &lyou &#33ff3dx&r"));
        assertTrue(TideframeEngine.naturalKey("frame10.png").compareTo(TideframeEngine.naturalKey("frame9.png")) > 0);
    }
    @Test void logicDemoIsAValidRoundTrippingScreen() {
        var demo = dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.LogicDemo.screen();
        assertEquals(demo, ScreenCodec.decode(ScreenCodec.encode(demo)));
        assertEquals(List.of(), demo.logicProblems());
        assertDoesNotThrow(demo::validateActionAreas);
        assertDoesNotThrow(() -> dev.linqfy.bigCasares.modules.tideframe.engine.core.CloseControl.prepare(demo));
        var host = new FakeHost(demo);
        var runtime = new ScreenRuntime(demo, new HashMap<>(), host);
        runtime.open();
        assertEquals(3, host.view.rows("item"));
        runtime.click("buy", UiSession.Click.LEFT, "");
        assertEquals("message <red>You need 50 coins.", host.log.get(1));
        runtime.click("plus", UiSession.Click.LEFT, ""); runtime.click("buy", UiSession.Click.LEFT, "");
        assertEquals("message <green>Bought one apple for 50 coins.", host.log.getLast());
        assertEquals("0", runtime.variables().get("coins"));
    }
}
