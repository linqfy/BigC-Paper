package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.List;

/**
 * /tideframe difuminados: what 0.22 adds, on one page. Frosted-glass panels over a photograph (static, and a hidden one
 * shown by a button, which blurs the static canvas it appears over), blurred drop shadows, a blurred glow, an
 * animated GIF ({@link #GIF_FOLDER}, copied from the plugin's own art on first start), a coin button whose hover image
 * is the star, and a page that slides out to the right when it closes.
 */
public final class MediaDemo {
    private MediaDemo() { }
    public static final String ID = "difuminados";
    /** images/{GIF_FOLDER}.gif: the bundled demo GIF (resource {@link #GIF_RESOURCE}). */
    public static final String GIF_FOLDER = "demo_estrella", GIF_RESOURCE = "demo/estrella.gif";

    public static Screen screen() {
        var glass = new Screen.Style(.5, "#0c1824", "#bfe9ff", "#ffffff", true, 0, .14, 1, null, "none", "center", 0).withBlur(0, 6, 4);
        var text = Screen.Style.defaults().withTextCase("original");
        var title = new Screen.Style(1, "#28384b", "#ffffff", "#ffffff", false).withText(2, "#0c1824", false).withTextCase("original");
        var note = new Screen.Style(1, "#28384b", "#ffffff", "#bfe9ff", false).withTextCase("original");
        var glow = new Screen.Style(.85, "#ffb347", "#ffffff", "#ffffff", false).withBlur(5, 0, 0);
        var button = new Screen.Style(1, "#173b48", "#8bdfc9", "#edf8f4", true, 0, .14, 1, "#53dbc1", "glow", "center", 0).withTextCase("original").withBlur(0, 0, 3);
        return new Screen(ID, "Difuminados", List.of(
                box("photo", "rectangle", false, 0, 0, 2, 2, -10, Screen.Style.defaults(), RuntimeDemoCatalog.photo("real_lab"), null),
                box("title_glass", "rounded", true, 0, .74, 1.3, .26, 0, glass, null, null),
                label("title", "Difuminados", 0, .74, 1.2, .2, title, 1, null),
                box("card_glass", "rounded", true, -.45, .08, .9, .62, 0, glass, null, null),
                label("card_title", "Vidrio esmerilado", -.45, .26, .86, .13, text, 1, null),
                label("card_note", "backdropBlur 6", -.45, .08, .86, .13, note, 1, null),
                label("card_shadow", "shadowBlur 4", -.45, -.08, .86, .13, note, 1, null),
                box("glow", "ellipse", true, .5, .12, .42, .62, 1, glow, null, null),
                box("star", "rectangle", false, .5, .12, .3, .5, 2, Screen.Style.defaults(), null, "{\"frames\":{\"folder\":\"" + GIF_FOLDER + "\"}}"),
                label("star_note", "GIF animado", .5, -.27, .8, .13, note, 1, null),
                new Screen.Element("show", "Mostrar panel", -.5, -.58, .7, .16, "button", "", "rounded", true, null, true, null, button, 3, null)
                        .withLogic(ActionParser.element(ScreenCodec.parse("""
                            {"actions":[{"type":"toggle","elements":["popup","popup_text"]}]}"""))),
                box("popup", "rounded", true, -.45, -.05, .9, .5, 5, glass.withBlur(0, 10, 4), null, "{\"hidden\":true}"),
                label("popup_text", "Panel oculto\ncon vidrio", -.45, -.05, .86, .26, text, 6, "{\"hidden\":true}"),
                label("hint", "Shift: cerrar", .5, -.78, .8, .13, note, 1, null),
                // Hover image: the coin becomes the star while the cursor is on it.
                new Screen.Element("coin", "", .5, -.55, .22, .3, "button", "", "rectangle", false, null, false, RuntimeDemoCatalog.photo("coin"),
                        Screen.Style.defaults(), 3, null).withHoverImage(starFrame())),
                RenderQuality.low(), new Screen.Presentation("menu", 1, "arrow", .5, 1))
                .withLogic(ActionParser.screen(ScreenCodec.parse("""
                    {"behavior":{"close-on-sneak":true},
                     "animation":{"open":{"effect":"fade","duration-ms":300},"close":{"effect":"slide","from":"right","distance":96,"duration-ms":350,"easing":"ease-in"}},
                     "on-open":[{"type":"actionbar","value":"<aqua>Difuminados 0.22: tocá Mostrar panel y pasá el cursor por la moneda"}]}""")));
    }
    /** A decoration; {@code glass} = filled, with a border when it blurs its backdrop (the frosted panels and the glow). */
    /** The first frame of the demo GIF, for the coin button's hover image. */
    private static dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset starFrame() {
        try (var in = dev.linqfy.bigCasares.modules.tideframe.engine.TideframeResources.open(GIF_RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing bundled " + GIF_RESOURCE);
            return dev.linqfy.bigCasares.modules.tideframe.engine.core.GifFrames.decode(in.readAllBytes(), 0, 1).frames().get(0);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Could not read " + GIF_RESOURCE, failure); }
    }
    private static Screen.Element box(String id, String shape, boolean glass, double x, double y, double w, double h, int layer, Screen.Style style,
                                      dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset image, String logic) {
        var e = new Screen.Element(id, "", x, y, w, h, "decoration", "", shape, glass && style.backdropBlur() > 0, null, glass, image, style, layer, null);
        return logic == null ? e : e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
    private static Screen.Element label(String id, String text, double x, double y, double w, double h, Screen.Style style, int layer, String logic) {
        var e = new Screen.Element(id, text, x, y, w, h, "label", "", "rectangle", false, null, false, null, style, layer, null);
        return logic == null ? e : e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }
}
