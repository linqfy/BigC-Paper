package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Item icons (element "item", schema 9): the client draws its own texture and the pack only names it, so nothing of the
 * game's art is copied. item-icons.tsv (measured from the 26.2 client by tools/item-icons-table.mjs) gives each item's
 * texture and how many columns its first frame paints, from which the client derives the glyph's advance.
 * <p>
 * A title glyph's bottom edge can never sit higher than 12 GUI pixels above the screen centre (the client rejects a
 * bitmap ascent above its height), so icons fit only the lower part of a menu: their bottom edge at y 64 or lower of
 * the 144-pixel canvas, at every display size.
 */
public final class ItemIcons {
    private ItemIcons() { }

    /** {@code rows}: animation frames stacked vertically (the first is drawn); {@code ink}: painted columns of that frame. */
    public record Icon(String item, String texture, int width, int height, int rows, int ink, boolean block, boolean tinted) {
        public int cellHeight() { return height / rows; }
        /** Vanilla BitmapProvider: a glyph {@code fontHeight} units tall advances (int)(0.5 + ink * height / cell) + 1 units. */
        public int advance(int fontHeight) { return (int) (0.5 + ink * ((float) fontHeight / cellHeight())) + 1; }
    }
    /** Canvas row (logical px from the top of 144) an icon's bottom edge may not rise above. */
    public static final int MIN_BOTTOM = 64;
    public static final int MIN_SIZE = 16, MAX_SIZE = 64;
    private static volatile Map<String, Icon> table;

    public static Optional<Icon> find(String item) { return item == null ? Optional.empty() : Optional.ofNullable(all().get(item)); }
    public static Map<String, Icon> all() {
        var known = table;
        if (known == null) { known = load(); table = known; }
        return known;
    }
    /** An element's icon: its box's smaller side in logical px, in whole steps of 16 (16 to 64), so every display size draws it whole. */
    public static int size(double width, double height) {
        double side = Math.min(width * 128, height * 72);
        return (int) Math.max(MIN_SIZE, Math.min(MAX_SIZE, Math.floor(side / 16 + 1e-9) * 16));
    }
    /** Top edge (logical px from the canvas top) of the icon centred in an element's box. */
    public static double top(Screen.Element e) { return (1 - e.y()) * 72 - size(e.width(), e.height()) / 2.0; }
    /** Left edge (logical px from the canvas left). */
    public static double left(Screen.Element e) { return (e.x() + 1) * 128 - size(e.width(), e.height()) / 2.0; }
    /**
     * True when the element's look is one the icon can share: the title draws a client texture as it is, so it cannot be
     * faded (opacity 0 hides element and icon alike), turned, blurred, brightened, shadowed or moved by the hover look.
     */
    public static boolean followsStyle(Screen.Style style) {
        return (style.opacity() == 0 || style.opacity() == 1) && style.rotation() == 0 && style.blur() == 0 && style.brightness() == 1
                && !style.shadow() && (style.hoverEffect().equals("none") || style.hoverEffect().equals("glow"));
    }
    /** True when the icon's bottom edge is low enough for the game to draw it (see the class comment). */
    public static boolean drawable(double y, double width, double height) {
        return (1 - y) * 72 + size(width, height) / 2.0 >= MIN_BOTTOM - 1e-9;
    }

    private static Map<String, Icon> load() {
        var icons = new LinkedHashMap<String, Icon>();
        try (var stream = dev.linqfy.bigCasares.modules.tideframe.engine.TideframeResources.open("item-icons.tsv")) {
            if (stream == null) throw new IllegalStateException("item-icons.tsv is missing from the plugin");
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                var f = line.split("\t", -1);
                if (f.length != 7) throw new IllegalStateException("item-icons.tsv: bad line " + line);
                var icon = new Icon(f[0], f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                        f[6].contains("b"), f[6].contains("t"));
                if (!icon.item().matches("[a-z0-9_]{1,64}") || !icon.texture().matches("[a-z0-9_/]{1,128}") || icon.rows() < 1
                        || icon.height() % icon.rows() != 0 || icon.ink() < 1 || icon.ink() > icon.width())
                    throw new IllegalStateException("item-icons.tsv: bad entry " + line);
                icons.put(icon.item(), icon);
            }
        } catch (IOException unreadable) { throw new UncheckedIOException(unreadable); }
        return Collections.unmodifiableMap(icons);
    }
}
