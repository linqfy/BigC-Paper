package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.List;

/**
 * /tideframe novedades: what 0.21 adds, on one page. Lower case, accents and symbols in the pixel font (as written
 * and in capitals), live text as written, a tooltip and hover keyframes, a keyframe animation started by a button,
 * a looping one, a sideways opening slide with a fade-out close, and a smaller cursor for this page only.
 */
public final class ShowcaseDemo {
    private ShowcaseDemo() { }
    public static final String ID = "novedades";

    public static Screen screen() {
        var plain = Screen.Style.defaults().withTextCase("original");
        var gold = new Screen.Style(1, "#28384b", "#ffffff", "#ffd966", false).withTextCase("original");
        var button = new Screen.Style(1, "#173b48", "#8bdfc9", "#edf8f4", false, 0, .12, 1, "#53dbc1", "glow", "center", 0).withTextCase("original");
        var badge = new Screen.Style(1, "#3b2a10", "#ffd966", "#ffd966", false, 0, .3, 1, null, "none", "center", 0).withText(2, null, false).withTextCase("original");
        return new Screen(ID, "Novedades 0.21", List.of(
                label("title", "Novedades 0.21", 0, .8, 1.6, .24, gold.withText(2, "#3b2a10", false), null),
                label("heart", "❤", .82, .8, .2, .2, new Screen.Style(1, "#28384b", "#ffffff", "#ff5d7a", false).withText(2, null, false), """
                    {"keyframes":{"trigger":"loop","easing":"ease-in-out","steps":[{"at":0,"scale":1},{"at":400,"scale":1.3},{"at":800,"scale":1}]}}"""),
                label("mixed", "¿Cómo estás? Año, pingüino, ñandú", 0, .5, 1.8, .13, plain, null),
                label("caps", "Mayúsculas: canción, menú, árbol", 0, .35, 1.8, .13, Screen.Style.defaults(), null),
                label("symbols", "★ ❤ ✔ ✖ ← ↑ → ↓ • 1º 2ª 20°C", 0, .2, 1.8, .13, plain, null),
                label("live", "Hola, {player} ★", 0, .05, 1.2, .13, plain, null),
                element("hover", "Pasá el cursor", -.5, -.25, .8, .16, button, """
                    {"tooltip":"tip","on-hover":[{"type":"sound","value":"UI_BUTTON_CLICK 0.4 1.6"}],
                     "keyframes":{"trigger":"hover","easing":"ease-out","steps":[{"at":0},{"at":150,"scale":1.08}]}}"""),
                element("play", "Animar ★", .5, -.25, .8, .16, button, """
                    {"actions":[{"type":"animation","element":"star"},{"type":"actionbar","value":"<gold>★ ¡Wiii!"}]}"""),
                label("tip", "¡Hola! Soy un tooltip", -.45, -.5, 1, .14, gold, null),
                badge("star", "★", .5, -.56, .22, .3, badge, """
                    {"keyframes":{"trigger":"action","easing":"ease-in-out","steps":[{"at":0},{"at":300,"rotation":180,"scale":1.4},{"at":600,"rotation":360,"scale":1}]}}"""),
                label("hint", "Shift: cerrar", 0, -.86, 1, .13, plain, null)),
                RenderQuality.low(), new Screen.Presentation("menu", 1, "arrow", .5, 1))
                .withLogic(ActionParser.screen(ScreenCodec.parse("""
                    {"behavior":{"close-on-sneak":true},
                     "animation":{"open":{"effect":"slide","from":"left","distance":48,"duration-ms":350,"easing":"ease-out"},
                                  "close":{"effect":"fade","duration-ms":250}},
                     "on-open":[{"type":"actionbar","value":"<aqua>Novedades 0.21: pasá el cursor por los botones"}]}""")));
    }
    private static Screen.Element label(String id, String text, double x, double y, double w, double h, Screen.Style style, String logic) {
        var e = new Screen.Element(id, text, x, y, w, h, "label", "", "rectangle", false, null, false, null, style, 0, null);
        return logic == null ? e : e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
    private static Screen.Element badge(String id, String text, double x, double y, double w, double h, Screen.Style style, String logic) {
        var e = new Screen.Element(id, text, x, y, w, h, "label", "", "rounded", true, null, true, null, style, 2, null);
        return e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
    private static Screen.Element element(String id, String text, double x, double y, double w, double h, Screen.Style style, String logic) {
        var e = new Screen.Element(id, text, x, y, w, h, "button", "", "rounded", true, null, true, null, style, 1, null);
        return e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
}
