package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.List;

/**
 * /tideframe logic_demo: every schema-9 feature on one page. Variables and live text (coins), a bar filled by a
 * variable, conditions, a hidden panel toggled by a button, a visible-if label, a scrolling data list, a text
 * input dialog, keybinds and right click. Written in the page JSON format so it doubles as an example.
 */
public final class LogicDemo {
    private LogicDemo() { }
    public static final String ID = "logic_demo";

    public static Screen screen() {
        var style = new Screen.Style(1, "#1d3346", "#8bdfc9", "#edf8f4", false);
        return new Screen(ID, "LOGIC DEMO", List.of(
                label("title", "SCREEN LOGIC", 0, .8, 1.2, .14, null),
                label("coins", "COINS {coins}", -.5, .55, .8, .12, null),
                new Screen.Element("coin_bar", "{bar:coins/200:0}", .45, .55, .7, .08, "label", "", "rounded", true, null, true, null,
                        new Screen.Style(1, "#0d1a24", "#8bdfc9", "#ffd166", false), 0, null),
                button("plus", "+10", -.6, .3, .45, .14, style, """
                    {"actions":[{"type":"variable","variable":"coins","operation":"add","value":"10"},{"type":"sound","value":"BLOCK_NOTE_BLOCK_PLING 1 2"}],
                     "right-actions":[{"type":"variable","variable":"coins","operation":"subtract","value":"10"}]}"""),
                button("buy", "BUY 50", 0, .3, .45, .14, style, """
                    {"actions":[{"type":"function","function":"buy","arguments":{"item":"apple","price":"50"}}]}"""),
                button("toggle", "PANEL", .6, .3, .45, .14, style, """
                    {"actions":[{"type":"toggle","elements":["panel"]}]}"""),
                label("panel", "HIDDEN PANEL", .6, .08, .55, .14, "{\"hidden\":true}"),
                label("rich", "RICH!", -.6, .08, .5, .12, "{\"visible-if\":\"{coins} >= 100\"}"),
                button("item", "{index}. {item}", -.45, -.2, .7, .13, style, """
                    {"list":{"source":"items","rows":3,"spacing":14,
                             "on-select":[{"type":"message","value":"<aqua>You picked {item} ({index} of the list)"}]}}"""),
                button("name", "NAME", .45, -.2, .6, .13, style, """
                    {"actions":[{"type":"input","variable":"name","title":"Your name","label":"Name","max-length":16,
                                 "on-submit":[{"type":"message","value":"<green>Hello {name}!"}],
                                 "on-cancel":[{"type":"message","value":"<gray>Nothing changed."}]}]}"""),
                label("hello", "HI {name}", .45, -.42, .8, .12, null),
                label("hint", "WHEEL: LIST  Q: KEY  1: RESET", 0, -.86, 1.9, .1, null)),
                RenderQuality.low(), new Screen.Presentation("menu", 1, "arrow"))
                .withLogic(ActionParser.screen(ScreenCodec.parse("""
                    {"behavior":{"close-on-sneak":true},
                     "variables":{"coins":"40","name":"PLAYER","items":"Sword,Bow,Apple,Shield,Potion,Torch,Map"},
                     "functions":{"buy":{"params":{"item":"","price":"0"},"actions":[
                        {"type":"condition","value":"{coins} >= {price}",
                         "then":[{"type":"variable","variable":"coins","operation":"subtract","value":"{price}"},
                                 {"type":"message","value":"<green>Bought one {item} for {price} coins."}],
                         "else":[{"type":"message","value":"<red>You need {price} coins."},{"type":"tint","element":"panel","value":"#ff8080"}]}]}},
                     "keybinds":[{"key":"Q","actions":[{"type":"actionbar","value":"<yellow>Q pressed on the logic demo"}]},
                                 {"key":"1","actions":[{"type":"variable","variable":"coins","value":"0"}]}],
                     "on-open":[{"type":"actionbar","value":"<aqua>Logic demo: try the buttons, the wheel and Q"}]}""")));
    }
    private static Screen.Element label(String id, String text, double x, double y, double w, double h, String logic) {
        var e = new Screen.Element(id, text, x, y, w, h, "label", "", "rectangle", false, null, false, null,
                Screen.Style.defaults(), 0, null);
        return logic == null ? e : e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
    private static Screen.Element button(String id, String text, double x, double y, double w, double h, Screen.Style style, String logic) {
        var e = new Screen.Element(id, text, x, y, w, h, "button", "", "rounded", true, null, true, null, style, 0, null);
        return e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
}
