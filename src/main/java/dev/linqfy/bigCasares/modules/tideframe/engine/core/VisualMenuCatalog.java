package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.pack.PackGenerator;
import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The default menu ("/tideframe"). Original MIT art painted in code.
 *
 * Authored on the 256x144 GUI-pixel canvas at exactly 4 source pixels per GUI pixel
 * (1024x576, the HUD atlas size): every box and image lands on whole pixels, so the
 * client shows it 1:1 at GUI scale 4 with no resampling. Call screen() on the pack worker.
 */
public final class VisualMenuCatalog {
    private VisualMenuCatalog() { }
    private static final class Holder { static final Screen SCREEN = create(); }
    public static Screen screen() { return Holder.SCREEN; }

    static final int PX = 4; // source pixels per GUI pixel
    private static final String BG = "#0a1620", PANEL_EDGE = "#23404b", INK = "#e9f4f0", MUTED = "#7d9fa5", GOLD = "#f2cb8a";
    private static final String CARD = "#10232c", CARD_EDGE = "#22404b";
    private static final String[] ACCENTS = {"#78e2cf", "#f3a58f", "#aeb8f6"};

    /**
     * Full-screen layout: the panel runs edge to edge (the 2x size covers a 1080p screen and a few pixels of every
     * edge fall off), so every text and button stays inside the safe area x 12..244, y 8..133 (ViewportFit.SAFE_*).
     */
    private static Screen create() {
        var e = new ArrayList<Screen.Element>();
        e.add(new Screen.Element("panel", "", 0, 0, 2, 2, "decoration", "", "rectangle", false, null, true, null,
                new Screen.Style(1, BG, PANEL_EDGE, INK, false, 0, 0, 1, null, "none", "center", 0).withGradient("#050b11", "vertical"), -30, null));
        var brand = styled(text("brand", "TIDEFRAME", 14, 9, 94, 17, GOLD, "left"), 1, null, true);
        e.add(new Screen.Element(brand.id(), brand.text(), brand.x(), brand.y(), brand.width(), brand.height(), brand.kind(), brand.action(), brand.shape(),
                brand.border(), null, false, null, brand.style().withTextGradient("#e0903a"), brand.layer(), null));
        e.add(text("tagline", "{player}", 140, 9, 224, 17, MUTED, "right")); // live: the viewer's name
        e.add(box("close_x", "X", 228, 8, 242, 20, "button", "close", true, true, null, style("#152d37", "#2c4d59", INK, "#b9504c", .25, 1).withHoverEffect("glow"), 8));
        e.add(box("hero", 12, 23, 244, 77, "decoration", "", false, false, dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.RuntimeDemoCatalog.photo("pixel_hero"), Screen.Style.defaults(), -10));
        // A soft dark plate behind the banner text keeps it readable over the busy pixel art.
        e.add(box("hero_plate", 16, 27, 152, 76, "decoration", "", true, false, null,
                new Screen.Style(.5, "#000000", "#000000", INK, false, 0, .12, 0, null, "none", "center", 0).withGradient("#06121a", "horizontal"), -8));
        var heroTitle = text("hero_title", "TIDAL VAULT", 22, 30, 202, 48, GOLD, "left");
        e.add(new Screen.Element(heroTitle.id(), heroTitle.text(), heroTitle.x(), heroTitle.y(), heroTitle.width(), heroTitle.height(), "label", "", "rectangle",
                false, null, false, null, heroTitle.style().withText(2, "#1a0e04", true).withTextGradient("#ff8a2a"), 6, null));
        e.add(styled(text("hero_subtitle", "CHOOSE YOUR CHALLENGE", 22, 52, 160, 60, "#cfe8e4", "left"), 1, "#06121a", false));
        // The headline game sits in the banner: a gold, glowing call to action.
        e.add(box("challenge", "BOSS FIGHT", 22, 63, 106, 74, "button", "open:challenge_demo", true, true, null,
                style("#5a3a12", "#f2cb8a", "#fff3c4", "#8a5a1c", .3, 1).withHoverEffect("glow").withGradient("#2e1a06", "vertical"), 8));
        String[] ids = {"sequence", "roulette", "hud"};
        String[] titles = {"SEQUENCE", "ROULETTE", "COIN HUD"};
        String[] subtitles = {"MEMORY TEST", "SPIN TO WIN", "YOUR MONEY"};
        String[] actions = {"open:sequence_demo", "open:roulette_demo", "open:overlay_demo"};
        for (int i = 0; i < 3; i++) {
            int x0 = 12 + i * 79, x1 = x0 + 74;
            final int card = i;
            e.add(box(ids[i] + "_card", x0, 80, x1, 124, "decoration", "", true, true, null, style(CARD, CARD_EDGE, INK, null, .1, 1).withGradient("#0a181f", "vertical"), -5));
            e.add(box(ids[i] + "_art", x0 + 3, 83, x1 - 3, 104, "decoration", "", false, false, dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.RuntimeDemoCatalog.photo("pixel_card_" + ids[card]), Screen.Style.defaults(), 1));
            e.add(styled(text(ids[i] + "_title", titles[i], x0 + 2, 105, x1 - 2, 113, INK, "center"), 1, null, true));
            e.add(text(ids[i] + "_subtitle", subtitles[i], x0 + 2, 114, x1 - 2, 122, ACCENTS[i], "center"));
            // The clickable ring sits above the art; hovering recolours only the ring.
            e.add(box(ids[i], x0, 80, x1, 124, "button", actions[i], false, true, null, style(CARD, CARD_EDGE, INK, ACCENTS[i], .1, 1).withHoverEffect("glow"), 6));
        }
        e.add(text("footer", "LEFT CLICK TO PLAY   SNEAK TO CLOSE", 12, 125, 244, 133, MUTED, "center"));
        var screen = new Screen("visual_menu09", "THE TIDAL VAULT", e, RenderQuality.custom(1024, 576), new Screen.Presentation("menu", 1, "arrow"));
        screen.validateActionAreas();
        return screen;
    }

    /** Element from GUI-pixel edges on the 256x144 canvas. */
    private static Screen.Element box(String id, int x0, int y0, int x1, int y1, String kind, String action,
                                      boolean background, boolean border, ImageAsset art, Screen.Style style, int layer) {
        return box(id, "", x0, y0, x1, y1, kind, action, background, border, art, style, layer);
    }
    private static Screen.Element box(String id, String text, int x0, int y0, int x1, int y1, String kind, String action,
                                      boolean background, boolean border, ImageAsset art, Screen.Style style, int layer) {
        double x = (x0 + x1) / 256.0 - 1, y = 1 - (y0 + y1) / 144.0, w = (x1 - x0) / 128.0, h = (y1 - y0) / 72.0;
        return new Screen.Element(id, text, x, y, w, h, kind, action, "rounded", border, null, background, art, style, layer, null);
    }
    private static Screen.Element text(String id, String text, int x0, int y0, int x1, int y1, String color, String align) {
        return box(id, text, x0, y0, x1, y1, "label", "", false, false, null,
                new Screen.Style(1, BG, BG, color, false, 0, 0, 1, null, "none", align, 0), 5);
    }
    private static Screen.Element styled(Screen.Element e, int scale, String outline, boolean shadow) {
        return new Screen.Element(e.id(), e.text(), e.x(), e.y(), e.width(), e.height(), e.kind(), e.action(), e.shape(), e.border(),
                e.messageCondition(), e.background(), e.image(), e.style().withText(scale, outline, shadow), e.layer(), e.actionArea());
    }
    private static Screen.Style style(String fill, String border, String text, String hover, double radius, int outline) {
        return new Screen.Style(1, fill, border, text, false, 0, radius, outline, hover, "none", "center", 0);
    }

    @FunctionalInterface private interface Paint { void draw(Graphics2D g, int width, int height); }
    /** Paints at the exact on-atlas size of a box of guiWidth x guiHeight. */
    /** Art is painted at DETAIL x the 1x atlas so native large-size canvases (up to ~1920 px) stay sharp. */
    static final int DETAIL = 2;
    private static ImageAsset image(int guiWidth, int guiHeight, Paint painter) {
        int w = guiWidth * PX, h = guiHeight * PX;
        var image = new BufferedImage(w * DETAIL, h * DETAIL, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g.scale(DETAIL, DETAIL);
            painter.draw(g, w, h);
        } finally { g.dispose(); }
        try {
            var out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) throw new IOException("PNG encoder unavailable");
            return ImageAsset.fromPng(out.toByteArray());
        } catch (IOException failure) { throw new IllegalStateException("Cannot bake original menu art", failure); }
    }

    private static Color c(int rgb) { return new Color(rgb); }
    private static Color c(int rgb, int alpha) { return new Color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha); }

    /** Night sea, aurora, moon and a glowing vault; the left third stays calm for the title. */
    private static void hero(Graphics2D g, int w, int h) {
        var clip = new RoundRectangle2D.Double(0, 0, w, h, 20, 20);
        g.setClip(clip);
        g.setPaint(new LinearGradientPaint(0, 0, 0, h * .72f, new float[]{0, .55f, 1},
                new Color[]{c(0x081a28), c(0x123447), c(0x2a5f68)}));
        g.fillRect(0, 0, w, h);
        // Horizon warmth behind the vault.
        g.setPaint(new RadialGradientPaint(w * .74f, h * .7f, w * .32f, new float[]{0, 1}, new Color[]{c(0xf0a978, 70), c(0xf0a978, 0)}));
        g.fillRect(0, 0, w, h);
        var random = new Random(0x71DEF2AL);
        for (int i = 0; i < 70; i++) {
            float x = random.nextFloat() * w, y = random.nextFloat() * h * .55f, r = random.nextFloat() < .15f ? 2.2f : 1.3f;
            g.setColor(c(0xeaf6ff, 60 + random.nextInt(150)));
            g.fill(new Ellipse2D.Float(x, y, r, r));
        }
        // Aurora ribbons.
        g.setStroke(new BasicStroke(14, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int[][] ribbons = {{0x6fe3c8, 38}, {0x9fb0ff, 30}, {0x6fe3c8, 22}};
        for (int i = 0; i < ribbons.length; i++) {
            g.setColor(c(ribbons[i][0], ribbons[i][1]));
            float y = 26 + i * 16;
            g.draw(new QuadCurve2D.Float(w * .28f, y + 40, w * .58f, y - 34, w * 1.02f, y + 14));
        }
        // Moon with halo.
        float mx = w * .87f, my = h * .25f;
        g.setPaint(new RadialGradientPaint(mx, my, 60, new float[]{0, .35f, 1}, new Color[]{c(0xdaf5e8, 120), c(0xdaf5e8, 40), c(0xdaf5e8, 0)}));
        g.fill(new Ellipse2D.Float(mx - 60, my - 60, 120, 120));
        g.setColor(c(0xe4f7ec)); g.fill(new Ellipse2D.Float(mx - 17, my - 17, 34, 34));
        g.setColor(c(0xc8e6da)); g.fill(new Ellipse2D.Float(mx - 6, my - 9, 9, 7)); g.fill(new Ellipse2D.Float(mx + 4, my + 3, 6, 5));
        // Mountain layers.
        g.setPaint(new GradientPaint(0, h * .45f, c(0x3b7a80), 0, h * .8f, c(0x1f4d57)));
        poly(g, 0, .74, .1, .62, .2, .68, .34, .52, .46, .66, .58, .55, .7, .64, .84, .5, 1, .62, 1, 1, 0, 1);
        g.setPaint(new GradientPaint(0, h * .55f, c(0x235560), 0, h, c(0x10343e)));
        poly(g, 0, .82, .14, .74, .28, .8, .42, .7, .55, .79, .66, .72, .8, .8, .92, .73, 1, .78, 1, 1, 0, 1);
        // Sea with moon reflection.
        g.setPaint(new GradientPaint(0, h * .8f, c(0x0f3440), 0, h, c(0x07202a)));
        g.fillRect(0, (int) (h * .8), w, h);
        g.setStroke(new BasicStroke(2));
        for (int i = 0; i < 9; i++) {
            float y = h * .83f + i * 4.2f, half = 46 - i * 4;
            g.setColor(c(0xd9f4e6, 120 - i * 11));
            g.draw(new java.awt.geom.Line2D.Float(mx - half + (i % 2) * 8, y, mx + half - (i % 3) * 6, y));
        }
        var transform = g.getTransform();
        g.translate(w * .66f, h * .8f); g.scale(1.25, 1.25);
        vault(g, 0, 0);
        g.setTransform(transform);
        // Calm left side for the title.
        g.setPaint(new GradientPaint(0, 0, c(0x06121b, 200), w * .55f, 0, c(0x06121b, 0)));
        g.fillRect(0, 0, w, h);
        bigText(g, "TIDAL VAULT", 12 * PX, 11 * PX, 2 * PX);
        g.setClip(null);
        g.setStroke(new BasicStroke(PX / 2f));
        g.setColor(c(0x4d8a92, 150));
        g.draw(new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 18, 18));
    }

    /** Angular shrine on a rock, with a glowing core. Base centre at (x, y). */
    private static void vault(Graphics2D g, float x, float y) {
        g.setPaint(new RadialGradientPaint(x, y - 52, 70, new float[]{0, 1}, new Color[]{c(0x7af0d4, 90), c(0x7af0d4, 0)}));
        g.fill(new Ellipse2D.Float(x - 70, y - 122, 140, 140));
        g.setColor(c(0x0f2a33)); polyAbs(g, x - 62, y, x - 34, y - 26, x + 30, y - 30, x + 66, y);
        g.setColor(c(0x3d7c7c)); polyAbs(g, x - 34, y - 26, x - 26, y - 82, x + 24, y - 86, x + 34, y - 28);
        g.setColor(c(0x5aa49d)); polyAbs(g, x - 26, y - 82, x + 24, y - 86, x + 18, y - 96, x - 18, y - 94);
        g.setColor(c(0x2a5e62)); polyAbs(g, x + 24, y - 86, x + 34, y - 28, x + 20, y - 30, x + 14, y - 80);
        g.setColor(c(0x071c25)); polyAbs(g, x - 16, y - 30, x - 12, y - 70, x + 12, y - 72, x + 16, y - 30);
        g.setPaint(new RadialGradientPaint(x, y - 50, 22, new float[]{0, .5f, 1}, new Color[]{c(0xf4fff8), c(0x8ff3da, 220), c(0x8ff3da, 0)}));
        g.fill(new Ellipse2D.Float(x - 22, y - 72, 44, 44));
        g.setColor(c(0xeafff6)); polyAbs(g, x, y - 62, x + 8, y - 50, x, y - 38, x - 8, y - 50);
    }

    /** The project's own 5x7 glyphs drawn at an integer scale, gold gradient with a soft drop shadow. */
    private static void bigText(Graphics2D g, String text, int left, int top, int cell) {
        var old = g.getComposite();
        for (int pass = 0; pass < 2; pass++) {
            for (int index = 0; index < text.length(); index++) {
                String[] rows = PackGenerator.pattern(text.charAt(index)).split("/");
                for (int ry = 0; ry < rows.length; ry++) for (int rx = 0; rx < rows[ry].length(); rx++) {
                    if (rows[ry].charAt(rx) != '1') continue;
                    int px = left + (index * 6 + rx) * cell, py = top + ry * cell;
                    if (pass == 0) {
                        g.setComposite(AlphaComposite.SrcOver.derive(.55f));
                        g.setColor(c(0x02080c)); g.fillRect(px + cell / 2, py + cell / 2, cell, cell);
                        g.setComposite(old);
                    } else {
                        float t = ry / 6f;
                        g.setColor(blend(0xfff1cf, 0xe8a85c, t));
                        g.fillRect(px, py, cell, cell);
                    }
                }
            }
        }
    }
    private static Color blend(int a, int b, float t) {
        int r = Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), gg = Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t),
                bb = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return new Color(r, gg, bb);
    }

    private static void card(Graphics2D g, int w, int h, int index) {
        int[][] gradients = {{0x0f3a40, 0x1e6b66}, {0x3a2433, 0x7a4a45}, {0x1d2747, 0x40508a}};
        var clip = new RoundRectangle2D.Double(0, 0, w, h, 14, 14);
        g.setClip(clip);
        g.setPaint(new GradientPaint(0, 0, c(gradients[index][0]), w, h, c(gradients[index][1])));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(w / 2f, h / 2f, w * .45f, new float[]{0, 1}, new Color[]{c(0xffffff, 34), c(0xffffff, 0)}));
        g.fillRect(0, 0, w, h);
        float cx = w / 2f, cy = h / 2f;
        if (index == 0) {
            int[] glow = {0x78e2cf, 0xf2cb8a, 0x78e2cf, 0xaeb8f6};
            float[] xs = {cx - 84, cx - 28, cx + 28, cx + 84};
            float[] ys = {cy + 10, cy - 12, cy + 12, cy - 10};
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{2, 7}, 0));
            g.setColor(c(0xe9f4f0, 120));
            var path = new Path2D.Float(); path.moveTo(xs[0], ys[0]);
            for (int i = 1; i < 4; i++) path.lineTo(xs[i], ys[i]);
            g.draw(path);
            for (int i = 0; i < 4; i++) {
                g.setPaint(new RadialGradientPaint(xs[i], ys[i], 26, new float[]{0, 1}, new Color[]{c(glow[i], 150), c(glow[i], 0)}));
                g.fill(new Ellipse2D.Float(xs[i] - 26, ys[i] - 26, 52, 52));
                g.setColor(c(0x0a1d24)); polyAbs(g, xs[i], ys[i] - 17, xs[i] + 15, ys[i], xs[i], ys[i] + 17, xs[i] - 15, ys[i]);
                g.setColor(c(glow[i])); polyAbs(g, xs[i], ys[i] - 13, xs[i] + 11, ys[i], xs[i], ys[i] + 13, xs[i] - 11, ys[i]);
                g.setColor(c(0xffffff, 200)); polyAbs(g, xs[i], ys[i] - 13, xs[i] + 5, ys[i] - 6, xs[i], ys[i], xs[i] - 5, ys[i] - 6);
            }
        } else if (index == 1) {
            float r = 31;
            g.setPaint(new RadialGradientPaint(cx, cy, 60, new float[]{0, 1}, new Color[]{c(0xf3a58f, 110), c(0xf3a58f, 0)}));
            g.fill(new Ellipse2D.Float(cx - 60, cy - 60, 120, 120));
            int[] colors = {0xe76f6f, 0x6fd9a8, 0x7fa8f5, 0xf2cb8a};
            for (int i = 0; i < 8; i++) {
                g.setColor(c(colors[i % 4]));
                g.fill(new java.awt.geom.Arc2D.Float(cx - r, cy - r, 2 * r, 2 * r, i * 45 + 10, 45, java.awt.geom.Arc2D.PIE));
            }
            g.setStroke(new BasicStroke(4)); g.setColor(c(0xf6e7c8)); g.draw(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
            g.setColor(c(0x2a1c26)); g.fill(new Ellipse2D.Float(cx - 9, cy - 9, 18, 18));
            g.setColor(c(0xf6e7c8)); g.fill(new Ellipse2D.Float(cx - 4, cy - 4, 8, 8));
            g.setColor(c(0xffffff)); polyAbs(g, cx - 9, cy - r - 10, cx + 9, cy - r - 10, cx, cy - r + 8);
        } else {
            float x = cx - 92;
            // Heart.
            g.setColor(c(0xe76f6f));
            g.fill(new Ellipse2D.Float(x, cy - 34, 22, 22)); g.fill(new Ellipse2D.Float(x + 18, cy - 34, 22, 22));
            polyAbs(g, x + 1, cy - 20, x + 39, cy - 20, x + 20, cy + 2);
            // Coin.
            g.setColor(c(0xf2cb8a)); g.fill(new Ellipse2D.Float(x + 5, cy + 6, 30, 30));
            g.setColor(c(0xc28a45)); g.setStroke(new BasicStroke(3)); g.draw(new Ellipse2D.Float(x + 11, cy + 12, 18, 18));
            // Bars.
            int[] fills = {0xe76f6f, 0xf2cb8a};
            float[] amount = {.78f, .55f};
            for (int i = 0; i < 2; i++) {
                float by = cy - 30 + i * 36;
                g.setColor(c(0x0c1530, 200)); g.fill(new RoundRectangle2D.Float(x + 52, by, 130, 18, 10, 10));
                g.setPaint(new GradientPaint(x + 52, 0, c(fills[i]), x + 182, 0, blend(fills[i], 0xffffff, .35f)));
                g.fill(new RoundRectangle2D.Float(x + 52, by, 130 * amount[i], 18, 10, 10));
            }
        }
        g.setClip(null);
    }

    /** Polygon in fractions of the current clip bounds (0..1). */
    private static void poly(Graphics2D g, double... points) {
        var b = g.getClipBounds();
        var path = new Path2D.Double(); path.moveTo(points[0] * b.width, points[1] * b.height);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i] * b.width, points[i + 1] * b.height);
        path.closePath(); g.fill(path);
    }
    private static void polyAbs(Graphics2D g, float... points) {
        var path = new Path2D.Float(); path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) path.lineTo(points[i], points[i + 1]);
        path.closePath(); g.fill(path);
    }
    static List<String> buttonIds() { return List.of("close_x", "sequence", "roulette", "hud"); }
}
