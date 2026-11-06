package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Colours for words inside a text (idea 28, schema 9), as in MiniMessage: {@code Saldo: <gold>{balance}</gold>} or
 * {@code <#ffcc00>word</#ffcc00>}. Only the 16 game colour names (plus grey and dark_grey) and #rrggbb are tags;
 * anything else, such as {@code <3} or {@code a<b}, stays text. A closing tag ends the latest tag spelled the same way
 * (and any opened after it); a closing tag with nothing to close is text, and an unclosed tag runs to the end.
 * {@code \<} draws a "<" a tag would otherwise take. editor/richtext.mjs follows exactly the same rules.
 */
public final class RichText {
    private RichText() { }
    /** Distinct colours one text may use (each one is a font of its own for styled live text). */
    public static final int MAX_COLORS = 8;
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("black", "#000000"), Map.entry("dark_blue", "#0000aa"), Map.entry("dark_green", "#00aa00"), Map.entry("dark_aqua", "#00aaaa"),
            Map.entry("dark_red", "#aa0000"), Map.entry("dark_purple", "#aa00aa"), Map.entry("gold", "#ffaa00"), Map.entry("gray", "#aaaaaa"),
            Map.entry("grey", "#aaaaaa"), Map.entry("dark_gray", "#555555"), Map.entry("dark_grey", "#555555"), Map.entry("blue", "#5555ff"),
            Map.entry("green", "#55ff55"), Map.entry("aqua", "#55ffff"), Map.entry("red", "#ff5555"), Map.entry("light_purple", "#ff55ff"),
            Map.entry("yellow", "#ffff55"), Map.entry("white", "#ffffff"));
    private static final Pattern TAG = Pattern.compile("<(/?)(#[0-9a-fA-F]{6}|[a-z_]{3,12})>");

    /** A run of text in one colour; {@code color} (#rrggbb, lower case) is null for the element's own text colour. */
    public record Run(String text, String color) { }

    /** The colour a tag name stands for (#rrggbb), or null when it is not a colour tag. */
    static String color(String name) {
        return name.startsWith("#") ? name.toLowerCase(Locale.ROOT) : NAMES.get(name);
    }

    /** The runs of {@code text}: tags taken out and {@code \<} resolved; neighbours of one colour are merged. */
    public static List<Run> runs(String text) {
        var out = new ArrayList<Run>();
        var open = new ArrayList<String[]>(); // {spelling, colour}
        var current = new StringBuilder();
        var matcher = TAG.matcher(text);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '<') { current.append('<'); i += 2; continue; }
            if (c == '<' && matcher.region(i, text.length()).lookingAt()) {
                String spelling = matcher.group(2).toLowerCase(Locale.ROOT), colour = color(spelling);
                if (colour != null) {
                    int at = -1;
                    if (!matcher.group(1).isEmpty()) for (int k = open.size() - 1; k >= 0 && at < 0; k--) if (open.get(k)[0].equals(spelling)) at = k;
                    if (matcher.group(1).isEmpty() || at >= 0) {
                        add(out, current, open);
                        if (matcher.group(1).isEmpty()) open.add(new String[]{spelling, colour});
                        else open.subList(at, open.size()).clear();
                        i = matcher.end();
                        continue;
                    }
                }
            }
            current.append(c); i++;
        }
        add(out, current, open);
        return List.copyOf(out);
    }
    private static void add(List<Run> out, StringBuilder text, List<String[]> open) {
        if (text.isEmpty()) return;
        String colour = open.isEmpty() ? null : open.get(open.size() - 1)[1];
        var last = out.isEmpty() ? null : out.get(out.size() - 1);
        if (last != null && java.util.Objects.equals(last.color(), colour)) out.set(out.size() - 1, new Run(last.text() + text, colour));
        else out.add(new Run(text.toString(), colour));
        text.setLength(0);
    }
    /** The text as drawn, without tags. */
    public static String plain(String text) {
        if (text.indexOf('<') < 0) return text;
        var out = new StringBuilder();
        for (var run : runs(text)) out.append(run.text());
        return out.toString();
    }
    /** True when a tag or a {@code \<} changes how {@code text} is drawn (schema 9 rules). */
    public static boolean hasTags(String text) { return text != null && text.indexOf('<') >= 0 && !plain(text).equals(text); }
    /** The distinct colours {@code text} uses, in order of appearance. */
    public static List<String> colors(String text) {
        if (text == null || text.indexOf('<') < 0) return List.of();
        var out = new LinkedHashSet<String>();
        for (var run : runs(text)) if (run.color() != null) out.add(run.color());
        return List.copyOf(out);
    }
    /**
     * {@code text} with every "<" that would be read as a tag or an escape written {@code \<}: drawn under the new
     * rules it looks exactly as before (schema 1-8 pages, and values put into a coloured text).
     */
    public static String escape(String text) {
        if (text.indexOf('<') < 0) return text;
        var out = new StringBuilder(text.length() + 8);
        var matcher = TAG.matcher(text);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<' && ((i > 0 && text.charAt(i - 1) == '\\') || matcher.region(i, text.length()).lookingAt() && color(matcher.group(2).toLowerCase(Locale.ROOT)) != null)) out.append('\\');
            out.append(c);
        }
        return out.toString();
    }
    private static final char RUN = '\u0001', TEXT = '\u0002';
    /**
     * Runs whose texts were filled in (live tokens resolved), packed into one value for the live layer: each run is
     * RUN + colour + TEXT + text. The two separators are control characters no game text can draw, and they are taken
     * out of the texts, so a filled-in value (a backslash, a "<gold>") can never change a colour.
     */
    public static String compose(List<Run> runs) {
        var out = new StringBuilder();
        for (var run : runs) {
            if (run.text().isEmpty()) continue;
            out.append(RUN).append(run.color() == null ? "" : run.color()).append(TEXT);
            for (int i = 0; i < run.text().length(); i++) { char c = run.text().charAt(i); if (c != RUN && c != TEXT) out.append(c); }
        }
        return out.toString();
    }
    /** The runs {@link #compose} packed (a value without the frame is one run in the element's own colour). */
    public static List<Run> decompose(String value) {
        if (value.indexOf(RUN) < 0) return value.isEmpty() ? List.of() : List.of(new Run(value, null));
        var out = new ArrayList<Run>();
        for (String part : value.substring(value.indexOf(RUN) + 1).split(String.valueOf(RUN), -1)) {
            int split = part.indexOf(TEXT);
            if (split < 0) continue;
            String color = part.substring(0, split), text = part.substring(split + 1);
            if (!text.isEmpty()) out.add(new Run(text, color.isEmpty() ? null : color));
        }
        return List.copyOf(out);
    }
}
