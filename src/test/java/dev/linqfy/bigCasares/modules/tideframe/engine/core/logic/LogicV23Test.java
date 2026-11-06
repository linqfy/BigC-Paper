package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 0.23 phase 1 page format: cooldowns, triggers, confirm and playback, read and written back the same. */
class LogicV23Test {
    @Test void elementCooldownsReadAndWriteBack() {
        var logic = ActionParser.element(ScreenCodec.parse("{\"actions\":[{\"type\":\"close\"}],\"cooldown\":\"1.5s\",\"on-cooldown\":[{\"type\":\"message\",\"value\":\"wait {cooldown}\"}]}"));
        assertEquals(1500, logic.cooldownMs());
        assertEquals(1, logic.onCooldown().size());
        assertEquals(logic, ActionParser.element(ScreenCodec.parse(LogicJson.element(logic))));
        assertEquals(2000, ActionParser.element(ScreenCodec.parse("{\"cooldown\":40}")).cooldownMs(), "a plain number counts ticks, like a delay");
        assertEquals(250, ActionParser.element(ScreenCodec.parse("{\"cooldown\":\"250ms\"}")).cooldownMs());
        assertFalse(LogicJson.element(ElementLogic.EMPTY).contains("cooldown"), "pages without a cooldown keep their JSON");
        assertThrows(IllegalArgumentException.class, () -> ActionParser.element(ScreenCodec.parse("{\"on-cooldown\":[{\"type\":\"close\"}]}")));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.element(ScreenCodec.parse("{\"cooldown\":\"2h\"}")));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.element(ScreenCodec.parse("{\"cooldown\":\"soon\"}")));
    }
    @Test void screenTriggersReadAndWriteBack() {
        var logic = ActionParser.screen(ScreenCodec.parse("{\"timers\":[{\"every\":\"5s\",\"actions\":[{\"type\":\"message\",\"value\":\"hi\"}]}],"
                + "\"on-damage\":[{\"type\":\"close\"}],\"on-world-change\":[{\"type\":\"message\",\"value\":\"{world}\"}],"
                + "\"on-change\":{\"*coins\":[{\"type\":\"message\",\"value\":\"{new}\"}]}}"));
        assertEquals(100, logic.timers().get(0).everyTicks());
        assertEquals(List.of("coins"), List.copyOf(logic.onChange().keySet()), "on-change names drop the global marker");
        assertEquals(logic, ActionParser.screen(ScreenCodec.parse(LogicJson.screen(logic))));
        assertEquals(4, logic.actionLists().stream().filter(list -> !list.isEmpty()).count());
        assertFalse(LogicJson.screen(ScreenLogic.EMPTY).contains("timers"));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.screen(ScreenCodec.parse("{\"timers\":[{\"every\":\"100ms\",\"actions\":[{\"type\":\"close\"}]}]}")), "faster than 0.25 s");
        assertThrows(IllegalArgumentException.class, () -> ActionParser.screen(ScreenCodec.parse("{\"timers\":[{\"actions\":[{\"type\":\"close\"}]}]}")), "every is required");
        assertThrows(IllegalArgumentException.class, () -> ActionParser.screen(ScreenCodec.parse("{\"timers\":[{\"every\":\"1s\",\"actions\":[]}]}")));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.screen(ScreenCodec.parse("{\"on-change\":{\"coins\":[],\"*coins\":[]}}")), "one variable listed twice");
        assertThrows(IllegalArgumentException.class, () -> ActionParser.screen(ScreenCodec.parse("{\"on-change\":{\"Bad Name\":[]}}")));
    }
    @Test void confirmAndPlaybackActionsReadAndWriteBack() {
        var actions = ActionParser.actions(ScreenCodec.parse("[{\"type\":\"confirm\",\"title\":\"Sure?\",\"value\":\"Pay {price}\",\"yes\":\"Pay\",\"no\":\"Back\","
                + "\"then\":[{\"type\":\"pay\",\"value\":\"5\"}],\"else\":[]},{\"type\":\"pause\",\"element\":\"intro\"},{\"type\":\"resume\",\"value\":\"intro\"},"
                + "{\"type\":\"frame\",\"element\":\"intro\",\"value\":\"{step}\"}]"));
        assertEquals(new UiAction.Confirm("Sure?", "Pay {price}", "Pay", "Back", List.of(new UiAction.Pay("5")), List.of()), actions.get(0));
        assertEquals(new UiAction.Playback("intro", "resume", ""), actions.get(2));
        assertEquals(actions, ActionParser.actions(ScreenCodec.parse(LogicJson.actions(actions))));
        var bare = (UiAction.Confirm) ActionParser.action(ScreenCodec.parse("{\"type\":\"confirm\",\"then\":[{\"type\":\"close\"}]}"));
        assertEquals("Are you sure?", bare.title());
        assertEquals("Yes", bare.yes());
        assertThrows(IllegalArgumentException.class, () -> ActionParser.action(ScreenCodec.parse("{\"type\":\"frame\",\"value\":\"2\"}")), "frame needs its element");
    }
    @Test void hudAndToastActionsReadWriteAndRun() {
        var actions = ActionParser.actions(ScreenCodec.parse("[{\"type\":\"hud\",\"value\":\"hud_vida\"},{\"type\":\"hud\",\"value\":\"hud_vida\",\"mode\":\"remove\"},"
                + "{\"type\":\"toast\",\"value\":\"aviso\",\"seconds\":\"{t}\",\"values\":{\"titulo\":\"Hola {player}\"}}]"));
        assertEquals(new UiAction.Hud("hud_vida", true), actions.get(0));
        assertEquals(new UiAction.Hud("hud_vida", false), actions.get(1));
        assertEquals(actions, ActionParser.actions(ScreenCodec.parse(LogicJson.actions(actions))));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.action(ScreenCodec.parse("{\"type\":\"hud\",\"value\":\"x\",\"mode\":\"toggle\"}")));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.action(ScreenCodec.parse("{\"type\":\"toast\",\"value\":\"x\",\"values\":{\"Bad Name\":\"1\"}}")));
        var effects = new ActionRunnerTest.Recorder();
        var variables = new Variables();
        variables.declare(Map.of("t", "120"));
        new ActionRunner(Map.of(), variables, text -> text.replace("{player}", "Steve"), effects).run(actions);
        assertEquals(List.of("hud add hud_vida", "hud remove hud_vida", "toast aviso 60.0 {titulo=Hola Steve}"), effects.log, "seconds are kept to 1-60, values resolved");
    }
    @Test void stackedHudPagesTravelInSchemaNine() {
        var label = new dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen.Element("box", "", 0, .6, .4, .2, "decoration", "", "rectangle", true, null, true, null);
        var hud = new dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen("hud_vida", "VIDA", List.of(label), dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality.low())
                .withPresentation(new dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen.Presentation("overlay", 1, "text").withStack(true));
        String json = ScreenCodec.encode(hud);
        assertTrue(json.contains("\"stack\":true") && json.contains("\"version\":9"), json);
        assertEquals(hud, ScreenCodec.decode(json));
        assertThrows(IllegalArgumentException.class, () -> new dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen.Presentation("menu", 1, "arrow").withStack(true), "only HUD pages stack");
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json.replace("\"stack\":true", "\"stack\":\"yes\"")));
        assertFalse(ScreenCodec.encode(hud.withPresentation(new dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen.Presentation("overlay", 1, "text"))).contains("stack"));
    }
    @Test void actionsComputeCalculatedText() {
        var effects = new ActionRunnerTest.Recorder();
        var variables = new Variables();
        variables.declare(Map.of("coins", "40", "player", "shadowed"));
        UnaryOperator<String> placeholders = text -> text.replace("{player}", "Steve").replace("%health%", "17.5");
        var runner = new ActionRunner(Map.of(), variables, placeholders, effects);
        runner.run(ActionParser.actions(ScreenCodec.parse("[{\"type\":\"message\",\"value\":\"{coins * 2} {%health% * 2} {player + \\\"!\\\"} {coins / 0}\"},"
                + "{\"type\":\"condition\",\"value\":\"{coins * 3} > 100\",\"then\":[{\"type\":\"message\",\"value\":\"rich\"}]}]")));
        assertEquals(List.of("message 80 35 Steve! {coins / 0}", "message rich"), effects.log, "built-in values win over variables, as in plain tokens");
    }
}
