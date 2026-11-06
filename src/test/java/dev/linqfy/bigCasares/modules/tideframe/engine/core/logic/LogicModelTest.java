package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LogicModelTest {
    @Test void variablesFollowLocalAndGlobalRules() {
        var shared = new LinkedHashMap<String, String>();
        var first = new Variables(shared);
        first.declare(Map.of("page", "1", "*coins", "100"));
        assertEquals("1", first.get("page")); assertEquals("100", first.get("coins")); assertEquals("100", first.get("*coins"));
        assertTrue(first.apply("coins", "add", "5"));
        assertEquals("105", shared.get("coins"));
        assertFalse(first.apply("coins", "add", "0"));
        var second = new Variables(shared);
        second.declare(Map.of("*coins", "100", "page", "7"));
        assertEquals("105", second.get("coins"), "declared global keeps the player's value");
        assertEquals("7", second.get("page"));
        assertEquals("Coins 105 page 7 {unknown} 105", second.substitute("Coins {coins} page ${page} {unknown} ${coins}"));
        assertTrue(second.apply("ratio", "divide", "3"));
        assertEquals("0", second.get("ratio"));
        second.apply("ratio", "set", "10"); second.apply("ratio", "divide", "3");
        assertEquals("3.333333", second.get("ratio"));
        assertFalse(second.apply("ratio", "divide", "0"));
        assertThrows(IllegalArgumentException.class, () -> Variables.validName("Bad-Name"));
        assertThrows(IllegalArgumentException.class, () -> Variables.validName("**x"));
        assertEquals("*ok", Variables.validName("*ok"));
    }
    @Test void conditionsCompareNumbersTextAndTruthiness() {
        assertTrue(Conditions.test("10 >= 9.5")); assertFalse(Conditions.test("10 < 9.5"));
        assertTrue(Conditions.test("abc == abc")); assertTrue(Conditions.test("&aVIP = VIP")); assertTrue(Conditions.test("'a b' != \"a c\""));
        assertFalse(Conditions.test("abc > abd"), "ordering needs numbers");
        assertTrue(Conditions.test("1 = 2 || 3 = 3")); assertFalse(Conditions.test("1 = 1 && 2 = 3"));
        assertTrue(Conditions.test("yes")); assertFalse(Conditions.test("false")); assertFalse(Conditions.test("0")); assertFalse(Conditions.test(""));
        assertTrue(Conditions.test("-5 < -4.5")); assertTrue(Conditions.test("1e2 = 100"));
    }
    @Test void conditionStructureCannotBeInjectedByValues() {
        Map<String, String> values = Map.of("{name}", "x || 1", "{rank}", "admin");
        assertFalse(Conditions.test("{name} == admin", text -> values.getOrDefault(text, text)));
        assertTrue(Conditions.test("{rank} == admin", text -> values.getOrDefault(text, text)));
        assertTrue(Conditions.test("{name} == 'x || 1'", text -> values.getOrDefault(text, text)));
        assertTrue(Conditions.test("%player_health,MIN=0,MAX=20,DEFAULT=20% >= 20", text -> text.equals("%player_health,MIN=0,MAX=20,DEFAULT=20%") ? "20" : text));
    }
    @Test void clampsBoundPlaceholderValues() {
        assertEquals("20", Conditions.clamp("%player_health,MIN=0,MAX=20%", t -> "35"));
        assertEquals("1", Conditions.clamp("%player_health,MIN=1,MAX=20%", t -> "-3"));
        assertEquals("20", Conditions.clamp("%player_health,MIN=0,MAX=20,DEFAULT=20%", t -> t));
        assertEquals("HP 7.5 / 20", Conditions.clamp("HP %health,MIN=0% / 20", t -> "7.5"));
        assertEquals("no clamp %x%", Conditions.clamp("no clamp %x%", t -> "1"));
    }
    @Test void parserAcceptsUltimateUiShapes() {
        var parsed = ActionParser.actions(Json.parse("""
            [{"type":"sound","value":"ENTITY_PLAYER_LEVELUP 1 1"},{"type":"teleport","value":"world:example x:1 y:64 z:-3"},
             {"type":"delay","value":"20t"},{"type":"delay","value":"1.5s"},{"type":"delay","ticks":5},{"type":"give","value":"DIAMOND"},
             {"type":"command","value":"/spawn"},{"type":"call","name":"buy","args":{"price":5}},{"type":"visibility","element":"a","value":"hide"},
             {"type":"input","id":"pet_name","maxLength":16,"actions":[{"type":"close"}]}]"""));
        assertEquals(new UiAction.Sound("ENTITY_PLAYER_LEVELUP", 1, 1), parsed.get(0));
        assertEquals(new UiAction.Teleport("example", "1", "64", "-3", null, null), parsed.get(1));
        assertEquals(new UiAction.Delay(20), parsed.get(2)); assertEquals(new UiAction.Delay(30), parsed.get(3)); assertEquals(new UiAction.Delay(5), parsed.get(4));
        assertEquals(new UiAction.Give("DIAMOND", "1"), parsed.get(5)); assertEquals(new UiAction.Command("spawn"), parsed.get(6));
        assertEquals(new UiAction.Call("buy", Map.of("price", "5")), parsed.get(7));
        assertEquals(new UiAction.Visibility(List.of(), List.of("a"), List.of()), parsed.get(8));
        var input = (UiAction.Input) parsed.get(9);
        assertEquals("pet_name", input.variable()); assertEquals(16, input.maxLength()); assertEquals(List.of(new UiAction.Close()), input.onSubmit());
        assertThrows(IllegalArgumentException.class, () -> ActionParser.actions(Json.parse("[{\"type\":\"mesage\",\"value\":\"typo\"}]")));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.actions(Json.parse("[{\"value\":\"no type\"}]")));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.actions(Json.parse("[{\"type\":\"delay\",\"value\":\"20000\"}]")));
    }
    @Test void screenAndElementLogicRoundTripThroughJson() {
        var screenLogic = ActionParser.screen(Json.parse("""
            {"behavior":{"close-on-sneak":false,"close-on-damage":true,"close-on-f":true,"reopen":true,"keep-open":true},
             "variables":{"page":"1","*coins":100},"command":"shop","permission":"shop.use","open-on-join":true,
             "functions":{"buy":{"params":{"item":"DIAMOND","price":100},"actions":[{"type":"variable","variable":"coins","operation":"subtract","value":"{price}"}]}},
             "keybinds":[{"key":"Q","actions":[{"type":"close"}]},{"key":"shift+l","actions":[{"type":"redirect","value":"menu"}]}],
             "on-open":[{"type":"sound","sound":"ui.button.click","volume":0.5,"pitch":1.2}],"on-close":[{"type":"message","value":"bye"}]}"""));
        assertEquals(new ScreenLogic.Behavior(false, true, true, false, true), screenLogic.behavior());
        assertFalse(LogicJson.screen(screenLogic).contains("keyboard-navigation"), "off: the JSON stays as it was");
        var keyboard = ActionParser.screen(Json.parse("{\"behavior\":{\"keyboard-navigation\":true}}"));
        assertTrue(keyboard.behavior().keyboardNavigation());
        assertTrue(keyboard.behavior().closeOnSneak(), "the other rules keep their defaults");
        assertEquals(keyboard, ActionParser.screen(Json.parse(LogicJson.screen(keyboard))));
        assertEquals(Set.of("Q", "SHIFT_L"), screenLogic.keybinds().keySet());
        assertEquals("100", screenLogic.variables().get("*coins"));
        assertEquals(screenLogic, ActionParser.screen(Json.parse(LogicJson.screen(screenLogic))));
        var elementLogic = ActionParser.element(Json.parse("""
            {"actions":[{"type":"function","function":"buy","arguments":{"item":"GOLD"}}],"right-actions":[{"type":"message","value":"info"}],
             "hidden":true,"visible-if":"{coins} >= 10","frames":{"folder":"coin/spin","interval-ms":100,"loop":false},
             "list":{"source":"servers","rows":4,"spacing":18,"filter":"search","on-select":[{"type":"message","value":"{item}"}]},
             "draggable":true,"on-drop":[{"type":"message","value":"{dragged}"}]}"""));
        assertEquals(elementLogic, ActionParser.element(Json.parse(LogicJson.element(elementLogic))));
        assertTrue(elementLogic.changesAtRuntime()); assertTrue(elementLogic.interactive());
        var everyAction = ActionParser.actions(Json.parse("""
            [{"type":"command","value":"a"},{"type":"console","value":"b"},{"type":"message","value":"c\\nd"},{"type":"actionbar","value":"e"},
             {"type":"sound","value":"s 1 1"},{"type":"teleport","value":"w 1 2 3 4 5"},{"type":"delay","value":"3t"},{"type":"give","value":"X 2"},
             {"type":"redirect","value":"p"},{"type":"back"},{"type":"close"},{"type":"toggle","show":["a"],"hide":["b"],"elements":["c"]},
             {"type":"variable","variable":"*v","operation":"add","value":"1"},{"type":"condition","value":"1=1","then":[{"type":"close"}]},
             {"type":"permission","permission":"x.y","else":[{"type":"close"}]},{"type":"function","function":"f"},
             {"type":"input","variable":"v","filter":"decimal","allow-empty":false},{"type":"offset","element":"a","dx":"-4"},
             {"type":"tint","element":"a","value":"#00ff00"},{"type":"animation","element":"a"},{"type":"scroll","element":"a","rows":"3","absolute":true}]"""));
        assertEquals(everyAction, ActionParser.actions(Json.parse(LogicJson.actions(everyAction))));
    }
    @Test void screenLogicRejectsBadCommandsKeysAndFunctions() {
        assertThrows(IllegalArgumentException.class, () -> ActionParser.screen(Json.parse("{\"command\":\"op\"}")));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.screen(Json.parse("{\"keybinds\":[{\"key\":\"X\",\"actions\":[]}]}")));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.screen(Json.parse("{\"functions\":{\"empty\":{\"actions\":[]}}}")));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.screen(Json.parse("{\"unknown\":true}")));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.element(Json.parse("{\"frames\":{\"folder\":\"../x\"}}")));
    }
    private static Screen logicScreen() {
        var button = new Screen.Element("buy", "BUY {coins}", 0, 0, .4, .2, "button", "").withLogic(ActionParser.element(Json.parse("""
            {"actions":[{"type":"variable","variable":"coins","operation":"subtract","value":"10"},{"type":"show","element":"done"}]}""")));
        var done = new Screen.Element("done", "DONE", 0, -.5, .4, .2, "label", "").withLogic(ActionParser.element(Json.parse("{\"hidden\":true}")));
        return new Screen("shop", "SHOP", List.of(button, done), RenderQuality.low())
                .withLogic(ActionParser.screen(Json.parse("{\"variables\":{\"coins\":\"50\"},\"command\":\"shop\"}")));
    }
    @Test void schemaNineKeepsLogicAndOlderScreensStayUnchanged() {
        var screen = logicScreen();
        String json = ScreenCodec.encode(screen);
        assertTrue(json.startsWith("{\"version\":9,"), json);
        var decoded = ScreenCodec.decode(json);
        assertEquals(screen, decoded);
        assertEquals(Set.of("done"), decoded.runtimeIds());
        assertEquals(screen, decoded.move("buy", .1, .1).move("buy", 0, 0));
        assertEquals(screen.logic(), decoded.delete("done").logic());
        var plain = new Screen("plain", "PLAIN", List.of(new Screen.Element("a", "A", 0, 0, .2, .2, "button", "close")), RenderQuality.low());
        assertFalse(ScreenCodec.encode(plain).contains("\"version\":9"));
        assertFalse(plain.hasLogic());
        var legacy = new Screen("legacy", "LEGACY", List.of(new Screen.Element("a", "A", 0, 0, .2, .2, "button", "close").withLogic(
                ActionParser.element(Json.parse("{\"actions\":[{\"type\":\"close\"}]}")))));
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.encode(legacy));
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json.replace("\"version\":9", "\"version\":8")));
        assertThrows(IllegalArgumentException.class, () -> ScreenCodec.decode(json.replace("\"logic\":{\"behavior\"", "\"logics\":{\"behavior\"")));
    }
    @Test void elementLogicNeedsAButtonForInteraction() {
        assertThrows(IllegalArgumentException.class, () -> new Screen.Element("a", "A", 0, 0, .2, .2, "label", "")
                .withLogic(ActionParser.element(Json.parse("{\"actions\":[{\"type\":\"close\"}]}"))));
        assertDoesNotThrow(() -> new Screen.Element("a", "A", 0, 0, .2, .2, "label", "")
                .withLogic(ActionParser.element(Json.parse("{\"hidden\":true,\"visible-if\":\"1=1\"}"))));
    }
    @Test void problemsNameUnknownTargetsAndFunctions() {
        var screen = logicScreen().withLogic(ActionParser.screen(Json.parse("""
            {"keybinds":[{"key":"Q","actions":[{"type":"hide","element":"ghost"},{"type":"function","function":"nope"},{"type":"scroll","element":"buy","rows":"1"}]}]}""")));
        var problems = screen.logicProblems();
        assertTrue(problems.stream().anyMatch(p -> p.contains("'ghost'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("'nope'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("not a list")), problems.toString());
        assertEquals(Set.of("buy", "done"), screen.runtimeIds());
    }
}
