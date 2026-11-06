package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 0.23 phase 1 in a running screen: cooldowns, confirm, triggers, on-change and GIF playback. */
class TriggersAndCooldownTest {
    private static final long SECOND = 1_000_000_000L;
    private static Screen.Element button(String id, String logic) {
        var e = new Screen.Element(id, id.toUpperCase(Locale.ROOT), 0, -.5, .5, .12, "button", "");
        return logic == null ? e : e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
    private static Screen screen(String screenLogic, Screen.Element... elements) {
        var screen = new Screen("shop", "SHOP", List.of(elements), RenderQuality.low());
        return screenLogic == null ? screen : screen.withLogic(ActionParser.screen(ScreenCodec.parse(screenLogic)));
    }
    private static ScreenRuntime open(Screen screen, ScreenRuntimeTest.FakeHost host) {
        var runtime = new ScreenRuntime(screen, new HashMap<>(), host);
        runtime.open();
        return runtime;
    }

    @Test void aCooldownIgnoresEarlyClicksAndTellsHowLongIsLeft() {
        var claim = button("claim", "{\"actions\":[{\"type\":\"message\",\"value\":\"reward\"}],\"cooldown\":\"2s\","
                + "\"on-cooldown\":[{\"type\":\"message\",\"value\":\"wait {cooldown}s\"}]}");
        var screen = screen(null, claim);
        var host = new ScreenRuntimeTest.FakeHost(screen);
        var runtime = open(screen, host);
        runtime.click("claim", UiSession.Click.LEFT, "");
        host.clock += SECOND / 2;
        runtime.click("claim", UiSession.Click.LEFT, "");
        host.clock += SECOND + SECOND * 4 / 10 + 1;
        runtime.click("claim", UiSession.Click.LEFT, "");
        host.clock += SECOND / 10;
        runtime.click("claim", UiSession.Click.LEFT, "");
        assertEquals(List.of("message reward", "message wait 1.5s", "message wait 0.1s", "message reward"), host.log);
        // The wait belongs to the player, not to this open menu: reopening does not reset it.
        var again = open(screen, host);
        again.click("claim", UiSession.Click.LEFT, "");
        assertEquals("message wait 2s", host.log.get(host.log.size() - 1));
    }
    @Test void aCooldownWithoutWordsJustIgnoresAndOnlyRealClicksStartIt() {
        var buy = button("buy", "{\"actions\":[{\"type\":\"message\",\"value\":\"bought\"}],\"cooldown\":\"500ms\"}");
        var screen = screen(null, buy);
        var host = new ScreenRuntimeTest.FakeHost(screen);
        var runtime = open(screen, host);
        runtime.click("buy", UiSession.Click.RIGHT, "");
        runtime.click("buy", UiSession.Click.LEFT, "");
        runtime.click("buy", UiSession.Click.LEFT, "");
        assertEquals(List.of("message bought"), host.log, "a right click with nothing to run does not start the wait");
        host.clock += SECOND / 2;
        runtime.click("buy", UiSession.Click.LEFT, "");
        assertEquals(List.of("message bought", "message bought"), host.log);
    }
    @Test void confirmRunsThenOnYesAndElseOnNo() {
        var sell = button("sell", "{\"actions\":[{\"type\":\"confirm\",\"title\":\"Sure?\",\"value\":\"Sell for {price}?\",\"then\":[{\"type\":\"message\",\"value\":\"sold\"}],"
                + "\"else\":[{\"type\":\"message\",\"value\":\"kept\"}]},{\"type\":\"message\",\"value\":\"after\"}]}");
        var screen = screen("{\"variables\":{\"price\":\"40\"}}", sell);
        var host = new ScreenRuntimeTest.FakeHost(screen);
        var runtime = open(screen, host);
        runtime.click("sell", UiSession.Click.LEFT, "");
        assertEquals(List.of("confirm Sell for 40?"), host.log, "nothing runs until the player answers");
        host.yes.run();
        host.yes.run();
        runtime.click("sell", UiSession.Click.LEFT, "");
        host.no.run();
        assertEquals(List.of("confirm Sell for 40?", "message sold", "message after", "confirm Sell for 40?", "message kept", "message after"), host.log);
    }
    @Test void timersRepeatWhileOpenAndStopWhenClosed() {
        var screen = screen("{\"variables\":{\"n\":\"0\"},\"timers\":[{\"every\":\"1s\",\"actions\":[{\"type\":\"variable\",\"variable\":\"n\",\"operation\":\"add\",\"value\":\"1\"},"
                + "{\"type\":\"message\",\"value\":\"tick {n}\"}]}]}");
        var host = new ScreenRuntimeTest.FakeHost(screen);
        var runtime = open(screen, host);
        assertEquals(1, host.later.size(), "the first run waits one period");
        host.later.poll().run();
        host.later.poll().run();
        runtime.shutdown();
        host.later.poll().run();
        assertTrue(host.later.isEmpty(), "a closed screen schedules nothing more");
        assertEquals(List.of("message tick 1", "message tick 2"), host.log);
    }
    @Test void damageAndWorldChangeBringTheirValues() {
        var screen = screen("{\"on-damage\":[{\"type\":\"message\",\"value\":\"ouch {damage} {cause}\"}],"
                + "\"on-world-change\":[{\"type\":\"message\",\"value\":\"{from} -> {world}\"}]}");
        var host = new ScreenRuntimeTest.FakeHost(screen);
        var runtime = open(screen, host);
        runtime.damaged(3.456, "fall");
        runtime.worldChanged("world_nether", "world");
        assertEquals(List.of("message ouch 3.46 fall", "message world -> world_nether"), host.log);
    }
    @Test void onChangeSeesOldAndNewAndNeverLoopsForever() {
        var add = button("add", "{\"actions\":[{\"type\":\"variable\",\"variable\":\"coins\",\"operation\":\"add\",\"value\":\"5\"}]}");
        var screen = screen("{\"variables\":{\"coins\":\"10\",\"spent\":\"0\"},\"on-change\":{"
                + "\"coins\":[{\"type\":\"message\",\"value\":\"{old} -> {new}\"},{\"type\":\"variable\",\"variable\":\"coins\",\"operation\":\"add\",\"value\":\"1\"}],"
                + "\"spent\":[{\"type\":\"variable\",\"variable\":\"spent\",\"operation\":\"add\",\"value\":\"1\"}]}}", add);
        var host = new ScreenRuntimeTest.FakeHost(screen);
        var runtime = open(screen, host);
        runtime.click("add", UiSession.Click.LEFT, "");
        assertEquals(List.of("message 10 -> 15"), host.log, "the list does not set itself off again");
        assertEquals("16", runtime.variables().get("coins"));
        assertEquals(List.of("coins 10 -> 15", "coins 15 -> 16"), host.changes, "every real change is reported once");
    }
    @Test void onChangeChainsAreCut() {
        // a0 sets a1, a1 sets a2... ten in a row: the chain stops at the depth limit.
        var variables = new StringBuilder();
        var changes = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            variables.append(i == 0 ? "" : ",").append("\"a").append(i).append("\":\"0\"");
            changes.append(i == 0 ? "" : ",").append("\"a").append(i).append("\":[{\"type\":\"variable\",\"variable\":\"a").append(i + 1).append("\",\"value\":\"1\"}]");
        }
        var go = button("go", "{\"actions\":[{\"type\":\"variable\",\"variable\":\"a0\",\"value\":\"1\"}]}");
        var screen = screen("{\"variables\":{" + variables + "},\"on-change\":{" + changes + "}}", go);
        var host = new ScreenRuntimeTest.FakeHost(screen);
        var runtime = open(screen, host);
        runtime.click("go", UiSession.Click.LEFT, "");
        assertTrue(host.log.stream().anyMatch(line -> line.startsWith("warn") && line.contains("on-change stopped")), host.log.toString());
        assertEquals("1", runtime.variables().get("a" + ScreenRuntime.MAX_CHANGE_DEPTH));
        assertEquals("0", runtime.variables().get("a" + (ScreenRuntime.MAX_CHANGE_DEPTH + 1)));
    }
    @Test void playbackPausesResumesAndJumps() {
        var intro = new Screen.Element("intro", "", 0, 0, .5, .5, "decoration", "")
                .withLogic(ActionParser.element(ScreenCodec.parse("{\"frames\":{\"folder\":\"intro\",\"interval-ms\":100}}")));
        var screen = screen(null, intro);
        var host = new ScreenRuntimeTest.FakeHost(screen);
        host.frames = 10;
        var runtime = open(screen, host);
        host.clock = host.view.started("intro") + 350_000_000L;
        runtime.run(ActionParser.actions(ScreenCodec.parse("[{\"type\":\"pause\",\"element\":\"intro\"}]")), java.util.Map.of());
        assertEquals(3, host.view.frozen("intro"), "paused on the frame showing");
        host.clock += 5 * SECOND;
        runtime.run(ActionParser.actions(ScreenCodec.parse("[{\"type\":\"frame\",\"element\":\"intro\",\"value\":\"8\"}]")), java.util.Map.of());
        assertEquals(7, host.view.frozen("intro"), "a jump while paused stays paused");
        runtime.run(ActionParser.actions(ScreenCodec.parse("[{\"type\":\"resume\",\"element\":\"intro\"}]")), java.util.Map.of());
        assertNull(host.view.frozen("intro"));
        assertEquals(7, DynamicLayer.frameIndex(host.clock - host.view.started("intro"), 100, 10, true), "plays on from where it was");
        runtime.run(ActionParser.actions(ScreenCodec.parse("[{\"type\":\"frame\",\"element\":\"intro\",\"value\":\"99\"}]")), java.util.Map.of());
        assertEquals(9, DynamicLayer.frameIndex(host.clock - host.view.started("intro"), 100, 10, true), "a jump past the end lands on the last frame");
        host.frames = 0;
        runtime.run(ActionParser.actions(ScreenCodec.parse("[{\"type\":\"pause\",\"element\":\"intro\"}]")), java.util.Map.of());
        assertTrue(host.log.get(host.log.size() - 1).contains("not a PNG or GIF animation"));
    }
    @Test void listRowsShareTheirListsCooldown() {
        var rows = button("rows", "{\"list\":{\"source\":\"kits\",\"rows\":3,\"spacing\":16,\"on-select\":[{\"type\":\"message\",\"value\":\"kit {item}\"}]},\"cooldown\":\"1s\"}");
        var screen = screen("{\"variables\":{\"kits\":\"a,b,c\"}}", rows);
        var host = new ScreenRuntimeTest.FakeHost(screen);
        var runtime = open(screen, host);
        runtime.click("rows#0", UiSession.Click.LEFT, "");
        runtime.click("rows#1", UiSession.Click.LEFT, "");
        assertEquals(new ArrayList<>(List.of("message kit a")), host.log);
    }
}
