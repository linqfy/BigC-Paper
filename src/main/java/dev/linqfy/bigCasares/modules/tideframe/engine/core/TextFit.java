package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Schema 9: a line of text wider than its box is drawn smaller instead of being cut, at 3/4 and then 1/2 of its size;
 * past half it is cut with an ellipsis. Sizes are quarters of the element's text size (4 = as authored). Only the width
 * counts: wrapped text already breaks its lines, and a text taller than its box is clipped as before. Schema 1-8 pages
 * keep cutting (Screen.legacyText), as do pages of the internal legacy quality. The editor (textfit.mjs) decides with
 * these same numbers; the shared cases are in src/test/resources/textfit.json.
 */
public final class TextFit {
    private TextFit() { }
    /** Sizes in quarters of the text size: as authored, three quarters and half. */
    public static final int FULL = 4, THREE_QUARTERS = 3, HALF = 2;
    public static final List<Integer> SIZES = List.of(FULL, THREE_QUARTERS, HALF);
    /** Marks a line cut even at half size (the project font draws it). */
    public static final char ELLIPSIS = '…';
    /** What {@link #longest(String)} counts for a token it cannot bound. */
    public static final int UNBOUNDED = 256;

    /** True when this page's text may shrink: a current page (schema 9, or built in code), not an old one. */
    public static boolean allowed(Screen screen) {
        return !screen.legacyText() && !screen.quality().preset().equals("legacy");
    }
    /** Box width in logical pixels, as LiveText and HudRaster measure it. */
    public static int boxWidth(Screen.Element element) { return Math.max(1, (int) Math.round(element.width() * 128)); }
    /**
     * Characters of one line that fit in {@code width} logical pixels at text scale {@code textScale}, drawn at
     * {@code size}: the line takes (characters x 6 - 1) x scale x size / 4 pixels. At size 4 this is LiveText.maxChars.
     */
    public static int maxChars(int width, int textScale, int size) {
        return Math.max(0, (4 * width + textScale * size) / (6 * textScale * size));
    }
    public static int maxChars(Screen.Element element, int size) { return maxChars(boxWidth(element), element.style().textScale(), size); }
    /** The largest of {@code sizes} at which a line of {@code length} characters fits; the smallest when none does. */
    public static int size(Screen.Element element, int length, Collection<Integer> sizes) {
        int smallest = FULL;
        for (int size : SIZES) {
            if (!sizes.contains(size)) continue;
            if (length <= maxChars(element, size)) return size;
            smallest = size;
        }
        return smallest;
    }
    public static int size(Screen.Element element, int length) { return size(element, length, SIZES); }
    /** {@code line} cut to {@code max} characters, the last one an ellipsis, when it is longer. */
    public static String cut(String line, int max) {
        if (line.length() <= max) return line;
        return max <= 0 ? "" : line.substring(0, max - 1) + ELLIPSIS;
    }
    /** The lines of a static text that may shrink (none when it wraps: wrapping already fits its width). */
    public static List<String> lines(Screen.Element element, String drawn) {
        return element.style().wrapWidth() > 0 ? List.of() : List.of(drawn.split("\n", -1));
    }
    /** The widest of {@code lines}, in characters. */
    public static int widest(List<String> lines) { return lines.stream().mapToInt(String::length).max().orElse(0); }
    /**
     * True when the element's text could be drawn smaller: live text (its values change), or a static line wider than
     * the box. {@code text} is the text as drawn.
     */
    public static boolean mayShrink(Screen.Element element, String text) {
        if (element.kind().equals("decoration") || LiveText.isBar(element)) return false;
        if (LiveText.isLive(element)) return true;
        return widest(lines(element, text)) > maxChars(element, FULL);
    }

    /**
     * Longest each built-in {token} can be (LivePlaceholders.builtIn): a name has 16 letters, block coordinates stay
     * inside the world border (-29999984) and build height (-2032); any other token is unbounded. A value longer than
     * this is still drawn: cut with an ellipsis at the smallest size the pack has.
     */
    private static final Map<String, Integer> TOKEN_LENGTHS = Map.ofEntries(Map.entry("player", 16), Map.entry("health", 4),
            Map.entry("max_health", 4), Map.entry("food", 2), Map.entry("level", 10), Map.entry("xp", 4), Map.entry("ping", 10),
            Map.entry("online", 10), Map.entry("max_players", 10), Map.entry("x", 9), Map.entry("y", 5), Map.entry("z", 9),
            Map.entry("time", 5), Map.entry("balance_short", 8));
    /**
     * Most characters a live text can show: its fixed text plus the longest each token can be ({@link #UNBOUNDED} for a
     * variable, a %placeholder%, a calculation or any token the plugin does not fill itself). Tags are not counted.
     */
    public static int longest(String template) {
        var text = new StringBuilder();
        for (var run : RichText.runs(template)) text.append(run.text());
        if (dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Expressions.contains(text.toString())) return UNBOUNDED;
        var matcher = LiveText.TOKEN.matcher(text);
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            total += matcher.start() - end;
            String token = matcher.group();
            Integer bound = token.startsWith("{") ? TOKEN_LENGTHS.get(token.substring(1, token.length() - 1)) : null;
            total += bound == null ? UNBOUNDED : bound;
            end = matcher.end();
        }
        total += text.length() - end;
        return (int) Math.min(UNBOUNDED, total);
    }
    /** The smaller sizes a live text may need in its box, judged by {@link #longest(String)}. */
    public static List<Integer> liveSizes(Screen.Element element) {
        int longest = longest(element.text());
        if (longest <= maxChars(element, FULL)) return List.of();
        return longest <= maxChars(element, THREE_QUARTERS) ? List.of(THREE_QUARTERS) : List.of(THREE_QUARTERS, HALF);
    }

    /**
     * Top of a single line's glyphs at {@code size}, in quarter logical pixels: the line keeps the centre it has at full
     * size (LiveText.lineTop) and shrinks around it.
     */
    public static int lineTopQuarters(Screen.Element element, int size) {
        int full = 4 * LiveText.lineTop(element), scale = element.style().textScale();
        return full + 14 * scale - 7 * scale * size / 2;
    }
    /**
     * Whole logical pixel where a shrunk line's glyph cell starts (its ink begins {@code pad} font pixels later): the
     * nearest to the line's alignment, moved one pixel in when rounding would push the ink past the box. The plugin's
     * live text and the baked text use this same position. At size 4 it is LiveText.lineLeft - pad, as always.
     */
    public static int cellLeft(Screen.Element element, int length, int size, int pad) {
        if (size == FULL) return LiveText.lineLeft(element, length) - pad;
        int width = boxWidth(element), boxLeft = 4 * ((int) Math.round((element.x() + 1) * 128) - width / 2), boxRight = boxLeft + 4 * width;
        int ink = Math.max(0, (length * 6 - 1) * element.style().textScale() * size);
        int cell = Math.floorDiv(lineLeftQuarters(element, length, size) - pad * size + 2, 4);
        if (4 * cell + pad * size + ink > boxRight) cell--;
        else if (4 * cell + pad * size < boxLeft) cell++;
        return cell;
    }
    /**
     * Left of a single line of {@code length} characters at {@code size}, in quarter logical pixels, by the element's
     * alignment (at size 4, four times LiveText.lineLeft).
     */
    public static int lineLeftQuarters(Screen.Element element, int length, int size) {
        if (size == FULL) return 4 * LiveText.lineLeft(element, length);
        int width = boxWidth(element), centerX = (int) Math.round((element.x() + 1) * 128), scale = element.style().textScale();
        int left = centerX - width / 2, textWidth = Math.max(0, (length * 6 - 1) * scale * size);
        return switch (element.style().textAlign()) {
            case "left" -> 4 * left;
            case "right" -> 4 * (left + width) - textWidth;
            default -> 4 * centerX - textWidth / 2;
        };
    }
}
