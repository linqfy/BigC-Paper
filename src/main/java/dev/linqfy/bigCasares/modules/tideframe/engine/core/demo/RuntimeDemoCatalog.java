package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Original MIT demonstration art and finite, pre-baked screens. Initialize screens()
 * on a worker before pack generation: procedural PNG creation performs CPU work.
 * Runtime controllers subsequently select immutable existing screens without I/O.
 */
public final class RuntimeDemoCatalog {
    private RuntimeDemoCatalog() { }
    private static final String[] PALETTE = {"#ef6676", "#71d6ad", "#78aaf6", "#f6d478"};
    private static final class Holder { static final Map<String, Screen> SCREENS = create(); }

    public static Map<String, Screen> screens() { return Holder.SCREENS; }

    private static Map<String, Screen> create() {
        // Realistic full-screen photographs (the owner's renders), one per demo family.
        ImageAsset art = photo("real_lab"), sequenceArt = photo("real_sequence"), wheelArt = photo("real_wheel");
        // Spinning frames each bake their own tiles: a soft, darker copy keeps the pack small and the wheel in focus.
        ImageAsset spinArt = photo("real_wheel_soft");
        var screens = new LinkedHashMap<String, Screen>();
        var home = base(art, "IMAGE LAB");
        home.add(label("intro", "Choose a working example", 0, .44, 1.7, .16, "#b5c9df"));
        home.add(button("sequence", "LETTER SEQUENCE", -.43, .08, .75, .22, "open:sequence_demo", "#315574"));
        home.add(button("roulette", "COLOR CHALLENGE", .43, .08, .75, .22, "open:roulette_demo", "#495176"));
        home.add(button("overlay", "PASSIVE HUD", 0, -.26, .85, .22, "open:overlay_demo", "#285a58"));
        home.add(label("hint", "PICK A DEMO TO TRY", 0, -.76, 1.8, .13, "#b5c9df"));
        put(screens, "image_demo08", "IMAGE DEMO", home);
        for (int complete = 0; complete <= 4; complete++) {
            put(screens, SequenceGame.stableFrameId(complete), "LETTER SEQUENCE", sequence(sequenceArt, complete, 0));
            if (complete < 4) for (int phase = 1; phase <= 3; phase++)
                put(screens, "seq_f" + complete + "_" + phase, "LETTER SEQUENCE", sequence(sequenceArt, complete, phase));
        }
        var select = base(wheelArt, "SPIN THE WHEEL");
        select.add(label("intro", "PICK THE COLOR TO LAND ON", 0, .44, 1.8, .16, "#b5c9df"));
        for (var color : RouletteSpin.Color.values()) {
            int i = color.ordinal();
            select.add(button("choose_" + lower(color), color.name(), -.66 + i * .44, .02, .37, .25,
                    "message:roulette:" + color.name(), PALETTE[i]));
        }
        select.add(label("disclosure", "THE WHEEL STOPS ON YOUR PICK", 0, -.36, 1.8, .15, "#93afc4"));
        select.add(button("back", "BACK", 0, -.7, .55, .18, "open:visual_menu09", "#27455e"));
        put(screens, "roulette_demo", "COLOR CHALLENGE", select);
        for (int orientation = 0; orientation < RouletteSpin.ORIENTATIONS; orientation++) {
            var frame = wheel(spinArt, orientation * 15, "SPINNING...");
            put(screens, RouletteSpin.orientationId(orientation), "COLOR CHALLENGE", frame);
        }
        for (var color : RouletteSpin.Color.values()) {
            var frame = wheel(spinArt, color.ordinal() * 90, "IT LANDED ON " + color.name() + "!");
            frame.add(button("again", "SPIN AGAIN", 0, -.74, .9, .16, "open:roulette_demo", "#27455e"));
            put(screens, "win_" + lower(color), "COLOR RESULT", frame);
        }
        // Passive HUD: only the money. A dark pill with a pixel coin and the live balance in big gold digits.
        var overlay = new ArrayList<Screen.Element>();
        overlay.add(new Screen.Element("money_pill", "", -.48, .74, .76, .26, "decoration", "", "rounded", true, null, true, null,
                new Screen.Style(.82, "#141a22", "#f2cb8a", "#ffffff", true, 0, .5, 1, null, "none", "center", 0).withGradient("#06090d", "vertical"), 0, null));
        overlay.add(element("coin", "", -.775, .74, .125, .2, "decoration", "", false, false, photo("coin"), Screen.Style.defaults(), 1));
        // {balance_short}: 1250, 25.3K, 1.25M... always fits the pill (never cut digits off a money amount).
        overlay.add(element("balance", "{balance_short}", -.385, .74, .59, .2, "label", "", false, false, null,
                new Screen.Style(1, "#000000", "#000000", "#fff3b0", false, 0, .08, 1, null, "none", "left", 0)
                        .withText(2, "#2a1a04", true).withTextGradient("#f2a83a"), 2));
        putOverlay(screens, "overlay_demo", "PASSIVE DEMO HUD", overlay);
        // Boss-fight key prompts: transparent overlays drawn over the world (no menu, no fixed camera).
        BossFightScreens.build(BossFightSettings.defaults()).forEach((id, screen) -> {
            if (screens.put(id, screen) != null) throw new IllegalStateException("Duplicate demo screen");
        });
        return Collections.unmodifiableMap(screens);
    }

    private static Screen.Element text(String id, String text, double x, double y, double w, double h, String color, String align,
                                       int scale, String outline, boolean shadow) {
        return element(id, text, x, y, w, h, "label", "", false, false, null,
                new Screen.Style(1, "#132b3d", "#ffffff", color, false, 0, .08, 1, null, "none", align, 0).withText(scale, outline, shadow), 4);
    }
    private static Screen.Element bar(String id, String source, double x, double y, double w, double h, String fill, String track, String frame) {
        return new Screen.Element(id, "{bar:" + source + "}", x, y, w, h, "label", "", "rounded", true, null, true, null,
                new Screen.Style(1, track, frame, fill, true, 0, .5, 1, null, "none", "center", 0), 2, null);
    }

    private static List<Screen.Element> sequence(ImageAsset art, int completed, int phase) {
        var elements = base(art, "LETTER SEQUENCE");
        elements.add(label("order", completed == 4 ? "SEQUENCE COMPLETE" : "CLICK A - C - B - D", 0, .4, 1.8, .17, "#b5c9df"));
        for (int index = 0; index < 4; index++) {
            String letter = String.valueOf((char)('A' + index));
            int sequenceIndex = SequenceGame.ORDER.indexOf(letter);
            if (sequenceIndex < completed) continue;
            boolean fading = sequenceIndex == completed && phase != 0;
            double opacity = fading ? new double[]{1, .67, .34, .08}[phase] : 1;
            var style = new Screen.Style(opacity, "#315574", "#a0deed", "#ffffff", false, 0, .2, 1, "#4a7aa3", "glow", "center", 0).withText(2, "#14283a", true);
            elements.add(element("letter_" + letter.toLowerCase(Locale.ROOT), letter, -.66 + index * .44, -.03, .36, .4,
                    fading ? "decoration" : "button", fading ? "" : "message:sequence:" + letter, true, true, null, style, 2));
        }
        elements.add(label("hint", "CORRECT LETTERS FADE AWAY", 0, -.38, 1.8, .15, "#93afc4"));
        elements.add(button("reset", "RESET", -.39, -.72, .58, .18, "message:sequence:RESET", "#27455e"));
        elements.add(button("back", "BACK", .39, -.72, .58, .18, "open:visual_menu09", "#27455e"));
        return elements;
    }

    private static ArrayList<Screen.Element> wheel(ImageAsset art, int angle, String status) {
        var elements = base(art, "COLOR CHALLENGE");
        elements.add(element("wheel", "", 0, .04, .66, 1.17, "decoration", "", false, false, wheelImage(angle),
                Screen.Style.defaults(), 1));
        elements.add(label("status", status, 0, -.605, 1.8, .1, "#e8f3ff"));
        return elements;
    }

    private static ArrayList<Screen.Element> base(ImageAsset art, String heading) {
        var elements = new ArrayList<Screen.Element>();
        elements.add(element("art", "", 0, 0, 2, 2, "decoration", "", false, false, art, Screen.Style.defaults(), -10));
        // A soft dark veil keeps text and buttons readable over bright photographs.
        elements.add(element("veil", "", 0, 0, 2, 2, "decoration", "", false, true, null,
                new Screen.Style(.38, "#000000", "#000000", "#ffffff", false, 0, 0, 1, null, "none", "center", 0)
                        .withGradient("#000000", "vertical"), -9));
        // Short headings get the big outlined title; long ones keep the 1x font so they never clip.
        var title = new Screen.Style(1, "#132b3d", "#ffffff", "#f2cb8a", false, 0, .08, 1, null, "none", "center", 0)
                .withText(heading.length() <= 16 ? 2 : 1, "#3a2408", true).withTextGradient("#e8902a");
        elements.add(element("heading", heading, 0, .74, 1.65, .24, "label", "", false, false, null, title, 4));
        // Inside the full-screen safe area (|x| <= .9, |y| <= .88).
        elements.add(button("close_x", "X", .82, .79, .14, .16, "close", "#533b54"));
        return elements;
    }

    private static Screen.Element button(String id, String text, double x, double y, double w, double h, String action, String fill) {
        boolean close = id.equals("close_x");
        String border = close ? "#2c4d59" : lighten(fill, .35), hover = close ? "#b9504c" : lighten(fill, .22);
        // Rounded, a lighter edge of its own colour, and a hover that brightens the button instead of recolouring it.
        return element(id, text, x, y, w, h, "button", action, true, true, null,
                new Screen.Style(1, close ? "#152d37" : fill, border, id.startsWith("choose_") ? "#142431" : "#f3faf7", false,
                        0, close ? .25 : .22, 1, hover, "glow", "center", 0).withGradient(close ? null : darken(fill, .35), "vertical"), 3);
    }
    private static String darken(String hex, double amount) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return String.format(Locale.ROOT, "#%02x%02x%02x", (int) Math.round((rgb >> 16 & 255) * (1 - amount)),
                (int) Math.round((rgb >> 8 & 255) * (1 - amount)), (int) Math.round((rgb & 255) * (1 - amount)));
    }
    private static String lighten(String hex, double amount) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        int r = (int) Math.round(((rgb >> 16) & 255) + (255 - ((rgb >> 16) & 255)) * amount);
        int g = (int) Math.round(((rgb >> 8) & 255) + (255 - ((rgb >> 8) & 255)) * amount);
        int b = (int) Math.round((rgb & 255) + (255 - (rgb & 255)) * amount);
        return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b);
    }

    private static Screen.Element label(String id, String text, double x, double y, double w, double h, String color) {
        return label(id, text, x, y, w, h, color, true);
    }
    /** Labels over the photograph get a drop shadow so they stay readable on bright sky. */
    private static Screen.Element label(String id, String text, double x, double y, double w, double h, String color, boolean shadow) {
        return element(id, text, x, y, w, h, "label", "", false, false, null,
                new Screen.Style(1, "#132b3d", "#ffffff", color, false).withText(1, null, shadow), 4);
    }

    private static Screen.Element element(String id, String text, double x, double y, double w, double h, String kind,
                                          String action, boolean border, boolean background, ImageAsset image, Screen.Style style, int layer) {
        // Buttons and panels use rounded corners; the full-canvas art stays rectangular.
        String shape = "button".equals(kind) || id.equals("hud_panel") || id.startsWith("letter_") || id.startsWith("cap_") ? "rounded" : "rectangle";
        return new Screen.Element(id, text, x, y, w, h, kind, action, shape, border, null, background, image, style, layer, null);
    }

    private static void putOverlay(Map<String, Screen> screens, String id, String title, List<Screen.Element> elements) {
        Screen screen = new Screen(id, title, elements, RenderQuality.low(), new Screen.Presentation("overlay", 1, "text"));
        screen.validateActionAreas();
        if (screens.put(id, screen) != null) throw new IllegalStateException("Duplicate demo screen");
    }
    private static void put(Map<String, Screen> screens, String id, String title, List<Screen.Element> elements) {
        Screen screen = new Screen(id, title, elements, RenderQuality.low());
        screen.validateActionAreas();
        if (screens.put(id, screen) != null) throw new IllegalStateException("Duplicate demo screen");
    }

    private static String lower(RouletteSpin.Color color) { return color.name().toLowerCase(Locale.ROOT); }

    /** Own geometric landscape; no copied textures, fonts, logos or stock assets. */
    /** Bundled art from /art/NAME.png. */
    public static ImageAsset photo(String name) {
        try (var in = dev.linqfy.bigCasares.modules.tideframe.engine.TideframeResources.open("art/" + name + ".png")) {
            if (in == null) throw new IllegalStateException("Missing bundled art " + name);
            return ImageAsset.fromPng(in.readAllBytes());
        } catch (IOException failure) { throw new IllegalStateException("Could not read bundled art " + name, failure); }
    }
    private static ImageAsset landscape() {
        // Painted at 4x (2048x1152) so native large-size canvases stay sharp; drawing coordinates stay 512x288.
        var image = new BufferedImage(2048, 1152, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.scale(4, 4);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setPaint(new GradientPaint(0, 0, new Color(17, 24, 46), 512, 288, new Color(11, 48, 57)));
            g.fillRect(0, 0, 512, 288);
            for (int i = 0; i < 74; i++) {
                int x = (i * 137 + 37) % 512, y = (i * 67 + 13) % 210;
                g.setColor(new Color(140, 197, 218, 40 + (i * 19) % 65)); g.fillRect(x, y, i % 9 == 0 ? 2 : 1, 1);
            }
            // Smooth moon glow (a radial gradient, not stacked translucent rings that band).
            g.setPaint(new java.awt.RadialGradientPaint(408f, 100f, 100f, new float[]{0f, .18f, 1f},
                    new Color[]{new Color(214, 244, 236, 170), new Color(98, 187, 186, 60), new Color(98, 187, 186, 0)}));
            g.fillOval(308, 0, 200, 200);
            g.setColor(new Color(226, 247, 238)); g.fillOval(398, 90, 20, 20);
            g.setStroke(new BasicStroke(1));
            for (int ridge = 0; ridge < 4; ridge++) {
                var p = new Path2D.Double(); p.moveTo(0, 288);
                for (int x = 0; x <= 512; x += 16) {
                    double y = 205 + ridge * 16 + Math.sin(x * .023 + ridge * 1.8) * (21 - ridge * 3)
                            + Math.sin(x * .071 + ridge) * 7;
                    p.lineTo(x, y);
                }
                p.lineTo(512, 288); p.closePath();
                g.setColor(new Color(18 + ridge * 3, 51 - ridge * 4, 70 - ridge * 7)); g.fill(p);
                g.setColor(new Color(110, 177, 184, 45)); g.draw(p);
            }
        } finally { g.dispose(); }
        return png(image);
    }

    /** Four original sectors and a fixed pointer; angle rotates the wheel, not the pointer. */
    private static ImageAsset wheelImage(int angle) {
        var image = new BufferedImage(720, 720, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.scale(3, 3); // 3x detail; drawing coordinates stay 240x240.
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(new Color(7, 18, 31, 220)); g.fillOval(8, 8, 224, 224);
            for (int i = 0; i < 4; i++) {
                g.setColor(Color.decode(PALETTE[i]));
                g.fill(new Arc2D.Double(14, 14, 212, 212, 45 - i * 90 + angle, 90, Arc2D.PIE));
            }
            g.setStroke(new BasicStroke(5)); g.setColor(new Color(221, 239, 246)); g.drawOval(11, 11, 218, 218);
            g.setColor(new Color(16, 38, 56)); g.fillOval(94, 94, 52, 52);
            g.setColor(new Color(220, 245, 246)); g.setStroke(new BasicStroke(2)); g.drawOval(94, 94, 52, 52);
            g.setColor(new Color(8, 18, 31)); g.fillPolygon(new int[]{104, 136, 120}, new int[]{1, 1, 32}, 3);
            g.setColor(new Color(247, 251, 255)); g.fillPolygon(new int[]{109, 131, 120}, new int[]{3, 3, 25}, 3);
        } finally { g.dispose(); }
        return png(image);
    }

    private static ImageAsset png(BufferedImage image) {
        try {
            var bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", bytes)) throw new IOException("PNG writer unavailable");
            return ImageAsset.fromPng(bytes.toByteArray());
        } catch (IOException failure) { throw new IllegalStateException("Could not generate original demo art", failure); }
    }
}
