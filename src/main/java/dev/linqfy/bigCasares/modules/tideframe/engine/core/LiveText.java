package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.regex.Pattern;

/**
 * Labels and buttons whose text contains {placeholders} or %placeholderapi% tokens are "live":
 * the baked canvas leaves their text out and the runtime draws it with per-row live fonts,
 * at exactly the logical position the raster would have used (single line, 6-pixel advance).
 */
public final class LiveText {
    private LiveText() { }
    /** {token}, ${variable}, {item.field} (list rows) or %placeholder% (with optional ,MIN=..,MAX=..,DEFAULT=.. clamp). */
    static final Pattern TOKEN = Pattern.compile("\\$?\\{[a-z_][a-z0-9_.]{0,47}}|%[A-Za-z0-9_:.-]{1,64}(?:,(?:MIN|MAX|DEFAULT)=[^,%]{1,32}){0,3}%");
    /** Printable ASCII the project font can draw (space is a font advance, not a glyph). */
    public static final String CHARSET = "!\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
    /** Accented capitals: Spanish first, then grave, circumflex, diaeresis, tilde and cedilla. */
    public static final String LATIN_UPPER = "ÁÉÍÓÚÜÑÀÈÌÒÙÂÊÎÔÛÄËÏÖÃÕÇ";
    /** The same letters in lower case, in the same order. */
    public static final String LATIN_LOWER = "áéíóúüñàèìòùâêîôûäëïöãõç";
    /** Punctuation and game symbols besides ASCII. */
    public static final String SYMBOLS = "¡¿°ºª«»·€×÷±²³•…←↑→↓★❤✔✖";
    /** Every non-ASCII character element text may use (schema 9). */
    public static final String EXTRA = LATIN_UPPER + LATIN_LOWER + SYMBOLS;
    /** Live-font grid for text drawn as written: ASCII, both cases of the accented letters, symbols. */
    public static final String MIXED_GRID = CHARSET + EXTRA;
    /** Live-font grid for upper-case text: no lower-case cells, accented capitals and symbols instead. */
    public static final String UPPER_GRID = CHARSET.replaceAll("[a-z]", "") + LATIN_UPPER + SYMBOLS;
    /** Values of a style's {@code textCase}: the font draws text in capitals (default) or as written. */
    public static final java.util.Set<String> TEXT_CASES = java.util.Set.of("upper", "original");
    private static final Pattern TEXT = Pattern.compile("[ -~\n" + EXTRA + "]*");
    /** True when every character of an element text is drawable (printable ASCII, line feeds, {@link #EXTRA}). */
    public static boolean drawable(String text) { return text != null && TEXT.matcher(text).matches(); }
    /** True when the text needs schema 9 (it has characters beyond printable ASCII). */
    public static boolean extended(String text) {
        for (int i = 0; i < text.length(); i++) { char c = text.charAt(i); if ((c < ' ' || c > '~') && c != '\n') return true; }
        return false;
    }
    /**
     * Common look-alikes become characters the font has: typographic quotes and dashes, ♥ ✓ ✗ ⭐ and friends.
     * Emoji presentation selectors (U+FE00..FE0F) and zero-width joiners are dropped.
     */
    public static String normalize(String value) {
        var out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= '︀' && c <= '️' || c == '‍' || c == '​') continue;
            out.append(switch (c) {
                case '‘', '’', '‚', '′', '´' -> '\'';
                case '“', '”', '„', '″' -> '"';
                case '–', '—', '−' -> '-';
                case ' ' -> ' ';
                case '♥', '♡', '❣' -> '❤';
                case '✓', '☑' -> '✔';
                case '✕', '✗', '✘', '❌' -> '✖';
                case '⭐', '☆', '✩', '✪', '✰' -> '★';
                case '∙', '●' -> '•';
                default -> c;
            });
        }
        return out.toString();
    }
    /** Upper case for the font: ASCII and the accented letters; everything else is returned unchanged. */
    public static char upper(char c) {
        if (c >= 'a' && c <= 'z') return (char) (c - 32);
        int accented = LATIN_LOWER.indexOf(c);
        return accented >= 0 ? LATIN_UPPER.charAt(accented) : c;
    }
    public static String upper(String text) {
        var out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) out.append(upper(text.charAt(i)));
        return out.toString();
    }

    /**
     * {bar:source}, {bar:source/max} or either with :N for N notches (0 = smooth, default 10, like a boss bar).
     * A source is a built-in (health, food, xp, air, armor, boss, hp, timer), a variable name or a %placeholder%;
     * max is a number, a variable or a placeholder (default 100 for variables and placeholders).
     */
    private static final String BAR_VALUE = "%[A-Za-z0-9_:.-]{1,64}%|[a-z_][a-z0-9_]{0,23}";
    private static final Pattern BAR = Pattern.compile("\\{bar:(" + BAR_VALUE + ")(?:/([0-9]{1,9}(?:\\.[0-9]{1,4})?|" + BAR_VALUE + "))?(?::(\\d{1,2}))?}");
    public static final java.util.List<String> BAR_SOURCES = java.util.List.of("health","food","xp","air","armor","boss","hp","timer");
    public static final int DEFAULT_BAR_SEGMENTS = 10;

    public static boolean isLive(Screen.Element element) {
        return !element.kind().equals("decoration") && !isBar(element) && isLive(element.text());
    }
    /** Live text or progress bar: anything the runtime draws on top of the baked canvas. */
    public static boolean isDynamic(Screen.Element element) { return isLive(element) || isBar(element); }
    /** A label whose whole text is {bar:source} is a progress bar: the box is the track, the fill is drawn live. */
    public static boolean isBar(Screen.Element element) { return barSource(element) != null; }
    public static String barSource(Screen.Element element) {
        if (element.kind().equals("decoration")) return null;
        var m = BAR.matcher(element.text());
        return m.matches() ? m.group(1) : null;
    }
    /** The bar's maximum as written ("1000", "max_coins", "%placeholder%"), or null for built-in sources' own maximum. */
    public static String barMax(Screen.Element element) {
        var m = BAR.matcher(element.text());
        return m.matches() ? m.group(2) : null;
    }
    /** True when a built-in game value fills the bar (no "/max", a known source). */
    public static boolean builtInBar(Screen.Element element) {
        String source = barSource(element);
        return source != null && barMax(element) == null && BAR_SOURCES.contains(source);
    }
    /** Notches requested by the token (0 = smooth). */
    public static int barSegments(Screen.Element element) {
        var m = BAR.matcher(element.text());
        if (!m.matches()) return 0;
        return m.group(3) == null ? DEFAULT_BAR_SEGMENTS : Math.min(40, Integer.parseInt(m.group(3)));
    }
    /** Logical width of one notched cell, or 0 when the bar is smooth (too few pixels per notch or wider than a glyph). */
    public static int barCellWidth(Screen.Element element) {
        int segments = barSegments(element);
        if (segments <= 1) return 0;
        int cell = barBox(element)[2] / segments;
        return cell >= 3 && cell <= 64 ? cell : 0;
    }
    /** Trail (recent damage) pixels for "fraction|trail"; never less than the fill. */
    public static int barTrailPixels(String value, int width) {
        int fill = barPixels(value, width), bar = value.indexOf('|');
        return bar < 0 ? fill : Math.max(fill, barPixels(value.substring(bar + 1), width));
    }
    /** Filled logical pixels for a fraction string ("0.73"); malformed values draw nothing. */
    public static int barPixels(String fraction, int width) {
        int bar = fraction.indexOf('|');
        if (bar >= 0) fraction = fraction.substring(0, bar);
        try {
            double value = Double.parseDouble(fraction);
            if (!Double.isFinite(value)) return 0;
            return (int) Math.round(Math.max(0, Math.min(1, value)) * width);
        } catch (NumberFormatException malformed) { return 0; }
    }
    /** Inner fill rectangle of a bar in logical pixels: {left, top, width, height}. */
    public static int[] barBox(Screen.Element element) {
        int width = Math.max(1, (int) Math.round(element.width() * 128));
        int height = Math.max(1, (int) Math.round(element.height() * 72));
        int centerX = (int) Math.round((element.x() + 1) * 128), centerY = (int) Math.round((1 - element.y()) * 72);
        int inset = element.border() ? element.style().outlineWidth() : 0;
        int left = centerX - width / 2 + inset, top = centerY - height / 2 + inset;
        return new int[]{left, top, Math.max(1, width - 2 * inset), Math.max(1, Math.min(48, height - 2 * inset))};
    }
    public static boolean isLive(String text) {
        return text != null && !text.contains("\n") && (TOKEN.matcher(text).find() || dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Expressions.contains(text));
    }

    /** Top logical row of the single text line (HudRaster: centre - blockHeight/2 - 1, blockHeight 7). */
    public static int lineTop(Screen.Element element) {
        int centerY = (int) Math.round((1 - element.y()) * 72), scale = element.style().textScale();
        return centerY - (7 * scale) / 2 - 1;
    }
    /** Most characters that fit inside the element box. */
    public static int maxChars(Screen.Element element) {
        int width = Math.max(1, (int) Math.round(element.width() * 128));
        return Math.max(0, (width + element.style().textScale()) / (6 * element.style().textScale()));
    }
    /** Left logical x of a rendered line of {@code length} characters. */
    public static int lineLeft(Screen.Element element, int length) {
        int width = Math.max(1, (int) Math.round(element.width() * 128));
        int centerX = (int) Math.round((element.x() + 1) * 128);
        int scale = element.style().textScale();
        int left = centerX - width / 2, textWidth = Math.max(0, (length * 6 - 1) * scale);
        return switch (element.style().textAlign()) {
            case "left" -> left;
            case "right" -> left + width - textWidth;
            default -> centerX - textWidth / 2;
        };
    }
    /** Upper-cases (the default text case), replaces characters the font lacks with '?' and fits the box. */
    public static String sanitize(String value, int maxChars) { return sanitize(value, maxChars, true); }
    /** As {@link #sanitize(String, int)}; {@code upper=false} keeps the case as written (style textCase "original"). */
    public static String sanitize(String value, int maxChars, boolean upper) {
        String grid = upper ? UPPER_GRID : MIXED_GRID;
        String clean = normalize(value);
        var out = new StringBuilder();
        for (int i = 0; i < clean.length() && out.length() < maxChars; i++) {
            char c = upper ? upper(clean.charAt(i)) : clean.charAt(i);
            // One '?' per code point: an emoji outside the font is a surrogate pair, not two characters.
            if (Character.isHighSurrogate(c) && i + 1 < clean.length() && Character.isLowSurrogate(clean.charAt(i + 1))) i++;
            out.append(c == ' ' || grid.indexOf(c) >= 0 ? c : '?');
        }
        return out.toString();
    }
    /** Live text of an element: its own text case. */
    public static String sanitize(String value, Screen.Element element) {
        return sanitize(value, maxChars(element), element.style().upperCase());
    }
}
