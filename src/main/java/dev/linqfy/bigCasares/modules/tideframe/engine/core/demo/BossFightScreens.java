package dev.linqfy.bigCasares.modules.tideframe.engine.core.demo;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the five boss-prompt overlays from {@link BossFightSettings}: transparent screens over the world with the
 * boss name on top (no health bar), the key as a keycap, a timer bar under it and short feedback text. Every colour,
 * position, size and text comes from the settings. A screen JSON with the same id in plugins/BigCasares/tideframe/engine/screens wins.
 */
public final class BossFightScreens {
    private BossFightScreens() { }

    public static Map<String, Screen> build(BossFightSettings settings) {
        var out = new LinkedHashMap<String, Screen>();
        put(out, KeyChallenge.SCREEN, prompt(settings, null));
        put(out, KeyChallenge.KEY_SCREEN, prompt(settings, "key"));
        put(out, KeyChallenge.WIDE_SCREEN, prompt(settings, "wide"));
        put(out, KeyChallenge.WIN_SCREEN, result(settings, true));
        put(out, KeyChallenge.LOSE_SCREEN, result(settings, false));
        return java.util.Collections.unmodifiableMap(out);
    }

    /** Logical sizes of the keycaps for a key size: the letter and word scales, and cap width/height in screen units. */
    record Caps(int letterScale, int wordScale, double side, double wideWidth, double height) {
        static Caps of(double keySize) {
            int letter = Math.max(2, Math.min(4, (int) Math.round(4 * keySize))), word = Math.max(2, Math.min(4, (int) Math.round(3 * keySize)));
            // The cap grows with key-size even after the letters reach the 4x maximum.
            int sidePx = Math.max(7 * Math.max(letter, word) + 8, (int) Math.round(41 * keySize));
            int widePx = Math.max(29 * word + 8, (int) Math.round(103 * keySize));
            return new Caps(letter, word, sidePx / 128.0, widePx / 128.0, sidePx / 72.0);
        }
    }

    private static List<Screen.Element> prompt(BossFightSettings s, String cap) {
        var e = new ArrayList<Screen.Element>();
        double bossY = s.layout("boss-y"), keyY = s.layout("key-y");
        boolean shadow = s.show("text-shadow");
        String outline = s.color("outline");
        e.add(text("boss_name", "{boss_name}", 0, bossY, 1.6, .2, s.color("boss-name"), 2, outline, true));
        // No boss health bar or numbers here: the key prompts show only the name, the key, the timer and feedback.
        var caps = Caps.of(s.layout("key-size"));
        double capTop = keyY + caps.height() / 2, capBottom = keyY - caps.height() / 2;
        e.add(text("feedback", "{feedback}", 0, Math.min(.95, capTop + .16), 1.9, .2, s.color("text"), 2, outline, shadow));
        if (cap != null) {
            boolean wide = cap.equals("wide");
            double w = wide ? caps.wideWidth() : caps.side(), h = caps.height();
            // A real keycap: a darker base peeking out under the face, both with a hard edge.
            e.add(box("cap_base", 0, clamp(keyY - .045), w, h, s.color("key-depth"), null, s.color("key-edge"), 1));
            e.add(box("cap_face", 0, keyY, w, h, s.color("key"), s.color("key-gradient"), s.color("key-edge"), 2));
            if (wide) e.add(text("key_word", "{key_word}", 0, keyY, w, h, s.color("key-letter"), caps.wordScale(), null, false));
            else e.add(text("key", "{key}", 0, keyY, w, h, s.color("key-letter"), caps.letterScale(), null, false));
        }
        // The timer sits right under the key; combo and lives flank the key, so nothing reaches the hotbar.
        double timerY = clamp(capBottom - .075), side = caps.wideWidth() / 2 + .27;
        e.add(new Screen.Element("timer", "{bar:timer:0}", 0, timerY, Math.max(.6, caps.wideWidth()), .06, "label", "", "rounded", true, null, true, null,
                new Screen.Style(.85, s.color("timer-track"), s.color("timer-track"), s.color("timer"), false, 0, .5, 1, null, "none", "center", 0), 2, null));
        if (s.show("combo")) e.add(text("combo", "{combo}", -side, keyY, .46, .1, s.color("text"), 1, outline, shadow));
        if (s.show("lives")) e.add(text("lives", s.text("lives") + " {lives}", side, keyY, .46, .1, s.color("text"), 1, outline, shadow));
        return e;
    }

    private static List<Screen.Element> result(BossFightSettings s, boolean won) {
        var e = new ArrayList<Screen.Element>();
        String outline = s.color("outline");
        boolean shadow = s.show("text-shadow");
        var title = new Screen.Style(1, "#000000", "#000000", s.color(won ? "victory" : "defeat"), false, 0, .08, 1, null, "none", "center", 0)
                .withText(4, outline, true).withTextGradient(s.color(won ? "victory-gradient" : "defeat-gradient"));
        e.add(new Screen.Element("result", s.text(won ? "victory" : "defeated"), 0, .4, 1.9, .34, "label", "", "rectangle", false, null, false, null, title, 4, null));
        e.add(text("result_line", s.text(won ? "victory-line" : "defeat-line"), 0, 0, 1.9, .12, s.color("text"), 1, outline, shadow));
        e.add(text("result_score", s.text("score") + " {score}", 0, -.17, 1.2, .16, s.color("text"), 2, outline, shadow));
        e.add(text("result_stats", s.text("best-combo") + " {best_combo}   " + s.text("hits") + " {hits}   " + s.text("misses") + " {misses}",
                0, -.38, 1.9, .1, s.color("text"), 1, outline, shadow));
        return e;
    }

    /** Element centres must stay on the canvas whatever positions the owner configured. */
    private static double clamp(double y) { return Math.max(-.97, Math.min(.97, y)); }
    private static Screen.Element text(String id, String text, double x, double y, double w, double h, String color, int scale, String outline, boolean shadow) {
        return new Screen.Element(id, text, x, y, w, h, "label", "", "rectangle", false, null, false, null,
                new Screen.Style(1, "#000000", "#000000", color, false, 0, .08, 1, null, "none", "center", 0).withText(scale, outline, shadow), 4, null);
    }
    private static Screen.Element box(String id, double x, double y, double w, double h, String fill, String gradient, String edge, int layer) {
        var style = new Screen.Style(1, fill, edge, "#000000", false, 0, .2, 2, null, "none", "center", 0).withGradient(gradient, "vertical");
        return new Screen.Element(id, "", x, y, w, h, "decoration", "", "rounded", true, null, true, null, style, layer, null);
    }
    private static void put(Map<String, Screen> screens, String id, List<Screen.Element> elements) {
        var screen = new Screen(id, "BOSS FIGHT", elements, RenderQuality.low(), new Screen.Presentation("overlay", 1, "text"));
        screen.validateActionAreas();
        screens.put(id, screen);
    }
}
