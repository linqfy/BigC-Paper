package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.List;

/**
 * /tideframe modeltest (experimental.mannequin-test): a frame around a transparent hole in the middle of the screen,
 * where the client-only Mannequin stands ({@link dev.linqfy.bigCasares.modules.tideframe.engine.core.ModelPlacement}). The hole is drawn for the game's
 * default FOV (70), where the model fills 43 % of the screen height; the hole leaves room up to 60 %.
 */
public final class ModelDemo {
    private ModelDemo() { }
    public static final String ID = "vista_3d";
    /** The hole, in canvas units (the canvas spans -1..1 on both axes): 34 % of the width, 60 % of the height. */
    public static final double HOLE_HALF_WIDTH = .34, HOLE_HALF_HEIGHT = .6;

    public static Screen screen() {
        // Opaque, so the bands and the sides can overlap at the corners without a seam or a darker square.
        var panel = new Screen.Style(1, "#101a26", "#3b5168", "#ffffff", false);
        var edge = new Screen.Style(1, "#101a26", "#8bdfc9", "#ffffff", false);
        var title = new Screen.Style(1, "#101a26", "#ffffff", "#ffffff", false).withText(2, "#0c1824", false).withTextCase("original");
        var note = new Screen.Style(1, "#101a26", "#ffffff", "#bfe9ff", false).withTextCase("original");
        var button = new Screen.Style(1, "#173b48", "#8bdfc9", "#edf8f4", true, 0, .14, 1, "#53dbc1", "glow", "center", 0).withTextCase("original");
        double side = 1 - HOLE_HALF_WIDTH, band = 1 - HOLE_HALF_HEIGHT;
        return new Screen(ID, "Vista 3D", List.of(
                // Four panels around the hole: the middle stays transparent and shows the world (and the model).
                box("left", -1 + side / 2, 0, side, 2, panel),
                box("right", 1 - side / 2, 0, side, 2, panel),
                box("top", 0, 1 - band / 2, 2, band, panel),
                box("bottom", 0, -1 + band / 2, 2, band, panel),
                new Screen.Element("hole_edge", "", 0, 0, HOLE_HALF_WIDTH * 2, HOLE_HALF_HEIGHT * 2, "decoration", "", "rounded", true, null, false, null, edge, 1, null),
                // Texts live in the full-width bands: the side panels are too narrow for them.
                label("title", "Vista 3D", 0, .87, 1.2, .2, title),
                label("subtitle", "Modelo real del juego", 0, .71, 1.4, .1, note),
                label("fov", "Hecho para FOV 70\nShift: cerrar", -.5, -.8, .9, .22, note),
                new Screen.Element("close", "Cerrar", .5, -.8, .4, .16, "button", "close", "rounded", true, null, true, null, button, 3, null)),
                RenderQuality.low(), new Screen.Presentation("menu", 1, "arrow", .5, 1))
                .withLogic(ActionParser.screen(ScreenCodec.parse("""
                    {"behavior":{"close-on-sneak":true},
                     "on-open":[{"type":"actionbar","value":"<aqua>Prueba del Mannequin: ¿se ve centrado en el hueco?"}]}""")));
    }
    private static Screen.Element box(String id, double x, double y, double w, double h, Screen.Style style) {
        return new Screen.Element(id, "", x, y, w, h, "decoration", "", "rectangle", false, null, true, null, style, 0, null);
    }
    private static Screen.Element label(String id, String text, double x, double y, double w, double h, Screen.Style style) {
        return new Screen.Element(id, text, x, y, w, h, "label", "", "rectangle", false, null, false, null, style, 2, null);
    }
}
