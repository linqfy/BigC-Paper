package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canvases rendered at the native resolution of one display size, instead of magnifying the 1x atlas.
 *
 * At size S the title canvas is 16*q font units wide (q = quarters). A glyph is at most 256 px tall and
 * must satisfy ascent <= height, so rows near the top of the canvas (large ascent) need a coarser
 * pixel density P (px per unit) than rows further down. Rows are split into resolution bands:
 * every row uses the finest density in {Pmax, Pmax/2, Pmax/4...} whose 256-px glyph still reaches
 * the frame. Pmax targets about 1792 px across (1:1 on a 1080p window at the size's intended GUI scale).
 * Positions use exact fractional space advances, so tiles of any width land on their source pixels.
 */
final class NativeCanvas {
    static final char GLYPH_START = '一', GLYPH_END = '鿿', SPACE_START = '㐀', SPACE_END = '䶿';
    private final int quarters, maxDensity, frameAscent;
    private final Map<String, byte[]> entries = new LinkedHashMap<>();
    private final Map<String, Character> glyphByKey = new HashMap<>();
    private final Map<Character, Integer> advances = new HashMap<>();
    private final Map<Double, Character> spaces = new TreeMap<>();
    private final List<String> providers = new ArrayList<>();
    private final Map<String, String> normal = new HashMap<>();
    private final Map<String, Map<String, String>> hover = new HashMap<>();
    private final Map<String, Map<String, String>> layers = new HashMap<>();
    private char nextGlyph = GLYPH_START, nextSpace = SPACE_START;

    NativeCanvas(int quarters) {
        this.quarters = quarters;
        this.maxDensity = density(quarters);
        this.frameAscent = HudAssets.frameAscentQuarters(quarters);
    }
    /** Px per unit for a size: the largest of 16/12/8/6/4 keeping the canvas at most 2176 px wide (2x at 1080p: 2048 px, 2.75x at GUI 3: 2112 px, both 1:1). */
    static int density(int quarters) {
        for (int p : new int[]{16, 12, 8, 6, 4}) if (16 * quarters * p <= 2176) return p;
        return 4;
    }
    int width() { return 16 * quarters * maxDensity; }
    int height() { return width() * 9 / 16; }

    /** A cropped tile, fully encoded: preparing is pure (parallel); committing allocates glyphs in a fixed order. */
    record Tile(byte[] png, String sha, int ascent, int height, int advance, double prefix) { }
    /** Pure, thread-safe: renders one screen (normal + hover variants + runtime layers) into encoded tiles. */
    record Prepared(String screenId, List<Tile> normal, Map<String, List<Tile>> hover, List<String> hoverIds, Map<String, List<Tile>> layers) { }
    /** A runtime layer: {@code screen} holds only the element(s) of the layer, rendered with {@code hover} highlighted. */
    record Layer(String key, Screen screen, String hover, dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Motion.Pose pose) {
        Layer(String key, Screen screen, String hover) { this(key, screen, hover, null); }
    }
    Prepared prepare(Screen screen, List<String> hoverIds, java.util.function.BiFunction<Screen, String, Boolean> needsHover) {
        return prepare(screen, java.util.Set.of(), hoverIds, needsHover, List.of());
    }
    /** {@code exclude}: runtime elements left out of the static canvas; each of {@code runtimeLayers} is baked on its own. */
    Prepared prepare(Screen screen, java.util.Set<String> exclude, List<String> hoverIds, java.util.function.BiFunction<Screen, String, Boolean> needsHover,
                     List<Layer> runtimeLayers) {
        var batch = new HudRaster.Batch(screen, width(), height(), element -> !exclude.contains(element.id()));
        var staticCanvas = batch.render(null);
        var base = tiles(staticCanvas);
        var variants = new LinkedHashMap<String, List<Tile>>();
        for (String id : hoverIds) if (needsHover.apply(screen, id)) variants.put(id, tiles(batch.render(id)));
        var baked = new LinkedHashMap<String, List<Tile>>();
        for (var layer : runtimeLayers) baked.put(layer.key(), tiles(new HudRaster.Batch(layer.screen(), width(), height()).over(staticCanvas).render(layer.hover(), layer.pose())));
        return new Prepared(screen.id(), base, variants, hoverIds, baked);
    }
    /** Single-threaded, in a deterministic order, so the pack bytes (and its hash) never change between runs. */
    void commit(Prepared prepared) {
        String base = commit(prepared.normal());
        normal.put(prepared.screenId(), base);
        var variants = new HashMap<String, String>();
        for (String id : prepared.hoverIds()) variants.put(id, prepared.hover().containsKey(id) ? commit(prepared.hover().get(id)) : base);
        hover.put(prepared.screenId(), variants);
        var runtime = new HashMap<String, String>();
        prepared.layers().forEach((key, tiles) -> runtime.put(key, commit(tiles)));
        layers.put(prepared.screenId(), runtime);
    }
    private String commit(List<Tile> tiles) {
        var out = new StringBuilder();
        for (var tile : tiles) {
            char glyph = glyph(tile);
            out.append(space(tile.prefix())).append(glyph).append(space(-tile.prefix() - tile.advance()));
        }
        return out.toString();
    }
    boolean has(String screenId) { return normal.containsKey(screenId); }
    /** Glyphs of a runtime layer, or null when the screen has no such layer. */
    String layer(String screenId, String key) { var known = layers.get(screenId); return known == null ? null : known.get(key); }
    String frame(String screenId, String hoverId) {
        if (hoverId == null) return normal.get(screenId);
        var variant = hover.get(screenId).get(hoverId);
        if (variant == null) throw new IllegalArgumentException("Unknown HUD button");
        return variant;
    }

    /** Rows: (top unit, density, content units). */
    private record Band(int top, int density, int units) { }
    private List<Band> bands() {
        var bands = new ArrayList<Band>();
        int total = 9 * quarters; // 144 * S / 4 units tall
        int r = 0;
        while (r < total) {
            int p = maxDensity;
            while (p > 1 && frameAscent - r > 256 / p) p /= 2;
            int units = 256 / p;
            if (p < maxDensity) {
                // Stop where the next finer density becomes possible.
                int finer = p * 2, finerReach = frameAscent - 256 / finer;
                units = Math.min(units, Math.max(1, finerReach - r));
            }
            units = Math.min(units, total - r);
            bands.add(new Band(r, p, units));
            r += units;
        }
        return bands;
    }

    private List<Tile> tiles(BufferedImage raster) {
        var out = new ArrayList<Tile>();
        int[] src = ((DataBufferInt) raster.getRaster().getDataBuffer()).getData();
        int rw = raster.getWidth();
        for (var band : bands()) {
            int factor = maxDensity / band.density();
            int bandWidth = rw / factor, contentRows = band.units() * band.density();
            int needRows = Math.max(contentRows, (frameAscent - band.top()) * band.density());
            int imageRows = Math.min(256, ((needRows + band.density() - 1) / band.density()) * band.density());
            int srcTop = band.top() * maxDensity;
            // Area-average down to this band's density.
            int[] px = new int[bandWidth * contentRows];
            for (int y = 0; y < contentRows; y++) for (int x = 0; x < bandWidth; x++) {
                long a = 0, r = 0, g = 0, b = 0;
                for (int dy = 0; dy < factor; dy++) for (int dx = 0; dx < factor; dx++) {
                    int sy = srcTop + y * factor + dy, sx = x * factor + dx;
                    if (sy >= raster.getHeight()) continue;
                    int p = src[sy * rw + sx], al = p >>> 24;
                    a += al; r += ((p >> 16) & 255) * al; g += ((p >> 8) & 255) * al; b += (p & 255) * al;
                }
                int n = factor * factor;
                int alpha = (int) Math.round(a / (double) n);
                px[y * bandWidth + x] = alpha == 0 ? 0 : alpha << 24 | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
            }
            for (int left = 0; left < bandWidth; left += 128) {
                int w = Math.min(128, bandWidth - left);
                int minX = w, maxX = -1, minY = contentRows, maxY = -1;
                for (int y = 0; y < contentRows; y++) for (int x = 0; x < w; x++) if ((px[y * bandWidth + left + x] >>> 24) != 0) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
                if (maxX < 0) continue;
                // Vertical crops in whole units keep the ascent integral; height must still reach the ascent.
                int cropTop = minY / band.density() * band.density();
                int ascent = frameAscent - band.top() - cropTop / band.density();
                int rows = Math.max((maxY + 1) - cropTop, ascent * band.density());
                rows = Math.min(imageRows - cropTop, ((rows + band.density() - 1) / band.density()) * band.density());
                int cw = maxX + 1 - minX;
                var tile = new BufferedImage(cw, rows, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < rows; y++) for (int x = 0; x < cw; x++) {
                    int sy = cropTop + y;
                    tile.setRGB(x, y, sy < contentRows ? px[sy * bandWidth + left + minX + x] : 0);
                }
                int height = rows / band.density();
                if (ascent > height) throw new IllegalStateException("Native band cannot reach the frame");
                byte[] png = HudAssets.encodePng(tile);
                int ink = 0;
                for (int x = 0; x < tile.getWidth(); x++) for (int y = 0; y < tile.getHeight(); y++) if ((tile.getRGB(x, y) >>> 24) != 0) ink = x + 1;
                // Vanilla BitmapProvider: advance = (int)(0.5 + inkWidth * height / imageHeight) + 1.
                int advance = (int) (0.5f + ink * ((float) height / tile.getHeight())) + 1;
                double prefix = -8.0 * quarters + (left + minX) / (double) band.density();
                out.add(new Tile(png, HexFormat.of().formatHex(PackGenerator.digest("SHA-256", png)), ascent, height, advance, prefix));
            }
        }
        return out;
    }

    private char glyph(Tile tile) {
        String key = tile.sha() + ':' + tile.ascent() + ':' + tile.height();
        var known = glyphByKey.get(key);
        if (known != null) return known;
        if (nextGlyph > GLYPH_END) throw new IllegalArgumentException("Native HUD glyph capacity exceeded");
        char c = nextGlyph++;
        String file = "font/hud_native/" + tile.sha() + ".png";
        entries.putIfAbsent("assets/tideframe/textures/" + file, tile.png());
        advances.put(c, tile.advance());
        providers.add("{\"type\":\"bitmap\",\"file\":\"tideframe:" + file + "\",\"ascent\":" + tile.ascent() + ",\"height\":" + tile.height()
                + ",\"chars\":[\"" + HudAssets.escapedChar(c) + "\"]}");
        glyphByKey.put(key, c);
        return c;
    }
    private char space(double units) {
        double rounded = Math.round(units * 4096) / 4096.0;
        var known = spaces.get(rounded);
        if (known != null) return known;
        if (nextSpace > SPACE_END) throw new IllegalArgumentException("Native HUD space capacity exceeded");
        char c = nextSpace++;
        spaces.put(rounded, c);
        return c;
    }
    /** Extra providers for this size's font (spaces + native canvas glyphs). */
    String providersJson() {
        var json = new StringBuilder("{\"type\":\"space\",\"advances\":{");
        boolean first = true;
        for (var e : spaces.entrySet()) {
            if (!first) json.append(','); first = false;
            json.append('"').append(HudAssets.escapedChar(e.getValue())).append("\":").append(String.format(Locale.ROOT, "%.6f", e.getKey()));
        }
        json.append("}}");
        for (String p : providers) json.append(',').append(p);
        return json.toString();
    }
    Map<String, byte[]> entries() { return entries; }
    static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }
}
