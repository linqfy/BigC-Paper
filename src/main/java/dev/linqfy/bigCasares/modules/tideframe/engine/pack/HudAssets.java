package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Immutable pre-baked vanilla title HUD. Construct on a worker before delivering
 * the pack. Region owners only select precompiled zero-advance tile sequences.
 */
public final class HudAssets {
    /** Native per-size canvases trade pack size for sharpness; still well under the client's server-pack limit. */
    public static final int MAX_PACK_BYTES = 200 * 1024 * 1024;
    private static final int SPACE_START = 0xe800, SPACE_MIN_QUARTER = -1024, SPACE_MAX_QUARTER = 1023;
    private static final int VISUAL_START = 0xf000, VISUAL_END = 0xf8ff;
    private static final int EXTRA_VISUAL_START = 0xe000, EXTRA_VISUAL_END = 0xe7ff;
    private static final HudAssets EMPTY = new HudAssets(Map.of(), Map.of(), Map.of(), List.of(), Map.of(),Map.of(),List.of(4),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),false);
    private final Map<String, Screen> screens;
    private final Map<String, Frames> frames;
    private final Map<String, byte[]> entries;
    private final List<Sprite> cursor;
    private final Map<CursorProfile,List<Sprite>> cursors;
    private final Map<Character,Map<Integer,Integer>> displayCorrections;
    private final List<Integer> sizes;
    /**
     * Live-text fonts by (presentation scale, line top, text style). Plain text keeps a white grid tinted by the
     * component colour; decorated text (scale/outline/shadow) bakes its colours into the grid and is drawn white.
     */
    private record LiveKey(int scale, int top, int size, int textScale, String color, String outline, boolean shadow, String gradient, boolean mixed) {
        static LiveKey of(Screen screen, Screen.Element element) { return of(screen, element, null); }
        /** {@code color}: a word colour of the text (null: the element's own); a coloured run is solid, without the gradient. */
        static LiveKey of(Screen screen, Screen.Element element, String color) { return of(screen, element, color, dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.FULL); }
        /**
         * {@code size}: quarters of the text size (TextFit: 4, or 3 and 2 for text that shrinks to fit its box);
         * {@code top}: the glyphs' top row in quarter logical pixels at that size.
         */
        static LiveKey of(Screen screen, Screen.Element element, String color, int size) {
            var style = element.style();
            boolean styled = style.isStyledText();
            return new LiveKey(screen.presentation().scale(), dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.lineTopQuarters(element, size), size, style.textScale(),
                    styled ? color == null ? style.textColor() : color : null, styled ? style.textOutline() : null, styled && style.textShadow(),
                    styled && color == null ? style.textGradient() : null, !style.upperCase());
        }
        Screen.Style style() {
            return Screen.Style.defaults().withText(textScale, outline, shadow).withTextGradient(gradient).withTextCase(mixed ? "original" : "upper");
        }
        /** Characters of the grid: capitals only, or both cases when the text is drawn as written. */
        String charset() { return mixed ? dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.MIXED_GRID : dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.UPPER_GRID; }
        int pad() { return style().outlineThickness(); }
        /** Logical width of one grid cell: at least the 6-pixel advance, wide enough for outline and shadow ink. */
        int cellWidth() { var style = style(); return Math.max(6 * textScale, 2 * style.outlineThickness() + 5 * textScale + (shadow ? textScale : 0)); }
    }
    private final Map<LiveKey,Integer> liveFonts;
    /** Progress-bar fill fonts by (presentation scale, fill rectangle rows, colour). */
    private record BarKey(int scale, int top, int height, String color, int cellWidth) {
        static BarKey of(Screen screen, Screen.Element element) {
            int[] box = dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.barBox(element);
            return new BarKey(screen.presentation().scale(), box[1], box[3], element.style().textColor(), dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.barCellWidth(element));
        }
    }
    private final Map<BarKey,Integer> barFonts;
    private final Map<Integer,NativeCanvas> natives;
    /** Runtime layers of the 1x atlas by screen id, then key ("id", "id@hover", "id@3", "id#2", "id#2@hover"). */
    private final Map<String, Map<String, Sprite>> layers;
    /** Elements baked as runtime layers, by screen id (see Screen#runtimeIds). */
    private final Map<String, Set<String>> runtime;
    /** Item icons of the static elements by screen id, drawn over the canvas (see ItemIcons). */
    private final Map<String, Sprite> icons;
    /** Item icons of runtime elements by screen id, then element id: drawn with that element's layer. */
    private final Map<String, Map<String, Sprite>> elementIcons;
    /** {@code size} is the page's own cursor size, or -1 for the configured one. */
    private record CursorProfile(int scale,String shape,double size) {
        CursorProfile(int scale,String shape) { this(scale,shape,-1); }
    }
    private static CursorProfile cursorProfile(Screen screen) {
        var presentation=screen.presentation();
        return new CursorProfile(presentation.scale(),presentation.cursor(),presentation.cursorSize()==null?-1:presentation.cursorSize());
    }

    private record Glyph(char character, int advanceQuarter) { }
    private record Placement(Glyph glyph, int offsetQuarter) { }
    private record Sprite(List<Placement> tiles) { }
    private record Frames(Sprite normal, Map<String, Sprite> hover, List<Sprite> entrance) { }

    /**
     * Vanilla draws a title at the screen centre, scaled 4x, with the text origin 10 units above
     * the centre. A bitmap glyph's top sits at {@code 7 - ascent} units from that origin and the
     * client rejects {@code ascent > height}; font sheets are 256x256, so one glyph is at most 256
     * source pixels tall. Every HUD canvas (and its cursor) shares this single vertical frame:
     * the top tile row of a canvas drawn at total scale {@code S} uses {@link #frameAscent(int)},
     * which centres the 144*S GUI-pixel canvas exactly at S=1 and within 12 GUI pixels above.
     */
    static int frameAscent(int totalScale) { return frameAscentQuarters(totalScale * 4); }
    /** Same frame for a total scale given in quarters (4 = 1x, 7 = 1.75x). */
    static int frameAscentQuarters(int totalQuarters) {
        if (totalQuarters < 4) throw new IllegalArgumentException("Invalid HUD scale");
        if (totalQuarters == 4) return 15;
        double scale = totalQuarters / 4.0;
        return (int) Math.min(Math.round(18 * scale - 3), 4L * totalQuarters);
    }
    /** GUI-pixel offset of the canvas top edge from the screen centre (negative is upwards). */
    public static int canvasTopGui(int totalScale) { return canvasTopGuiQuarters(totalScale * 4); }
    public static int canvasTopGuiQuarters(int totalQuarters) { return -4 * (3 + frameAscentQuarters(totalQuarters)); }

    /** Display sizes (in quarters) that get their own font when none are configured: 1x, 1.5x, 1.75x, 2.25x, 3.5x. */
    /** 1x plus the sizes that fill the common screens edge to edge: 1.75 (720p/1440p/4K at GUI Auto), 2 (1080p Auto), 2.75 (1080p GUI 3, 1440p GUI 4). */
    public static final List<Integer> DEFAULT_SIZES = List.of(4, 7, 8, 11);
    /** Font id for a display size in quarters. */
    public static String fontName(int quarters) { return quarters == 4 ? "tideframe:hud" : "tideframe:hud_q" + quarters; }
    private static final int CORRECTION_START = 0xd100, CORRECTION_ZERO = 128;

    /** Widest atlas whose 256-pixel glyph rows still reach the centred frame (16 units per row). */
    static final int ATLAS_WIDTH = 1024, ATLAS_HEIGHT = 576;

    /** Source-to-font ratio 64/width preserves the 256 logical-pixel title footprint exactly. */
    private record TileProfile(int tileWidth, int imageHeight, int fontHeight, int canvasWidth, int scale) {
        static TileProfile forWidth(int width) {
            if (width > ATLAS_WIDTH || width % 32 != 0) throw new IllegalArgumentException("Unsupported HUD atlas width " + width);
            int g = gcd(width, 64), denominator = width / g, multiplier = 256 / denominator;
            // Fractional display sizes are whole quarters, so font heights stay integers only if divisible by 4.
            while (multiplier > 0 && (64 / g) * multiplier % 4 != 0) multiplier--;
            int imageHeight = denominator * multiplier, fontHeight = (64 / g) * multiplier;
            int horizontalStep = width / gcd(width, 256), tileWidth = 128 / horizontalStep * horizontalStep;
            if (fontHeight < 16) throw new IllegalStateException("HUD tile rows cannot reach the centred frame");
            return new TileProfile(tileWidth, imageHeight, fontHeight, width, 1);
        }
        static TileProfile forScreen(Screen screen) {
            return forWidth(atlasWidth(screen)).scaled(screen.presentation().scale());
        }
        static TileProfile cursor() { return forWidth(HudRaster.WIDTH); }
        TileProfile scaled(int scale) { return new TileProfile(tileWidth,imageHeight,fontHeight*scale,canvasWidth,scale); }
        /** Font units between the canvas top and the tile row starting at source row {@code top}. */
        int rowUnits(int top) {
            if (top * 64 % canvasWidth != 0) throw new IllegalStateException("Unaligned HUD tile row");
            return top * 64 / canvasWidth * scale;
        }
        private static int gcd(int a, int b) { while (b != 0) { int next = a % b; a = b; b = next; } return a; }
    }
    private static int gcd(int a, int b) { return TileProfile.gcd(a, b); }
    /** Rasters wider than the atlas are area-filtered down; the GPU samples font sheets nearest-neighbour. */
    static int atlasWidth(Screen screen) { return Math.min(ATLAS_WIDTH, screen.quality().width()); }

    private HudAssets(Map<String, Screen> screens, Map<String, Frames> frames, Map<String, byte[]> entries,
                      List<Sprite> cursor, Map<CursorProfile,List<Sprite>> cursors,Map<Character,Map<Integer,Integer>> displayCorrections,List<Integer> sizes,Map<LiveKey,Integer> liveFonts,Map<BarKey,Integer> barFonts,Map<Integer,NativeCanvas> natives,
                      Map<String, Map<String, Sprite>> layers, Map<String, Set<String>> runtime, Map<String, Integer> frameIntervals,
                      Map<String, Sprite> icons, Map<String, Map<String, Sprite>> elementIcons, boolean autoFit) {
        this.screens = Map.copyOf(screens); this.frames = Map.copyOf(frames); this.frameIntervals = Map.copyOf(frameIntervals); this.autoFit = autoFit;
        // The builder owns every value and never publishes it. Public access always clones bytes.
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries)); this.cursor = List.copyOf(cursor);
        this.cursors=Map.copyOf(cursors);
        this.displayCorrections=Map.copyOf(displayCorrections);
        this.sizes=List.copyOf(sizes);
        this.liveFonts=Map.copyOf(liveFonts);
        this.barFonts=Map.copyOf(barFonts);
        this.natives=Map.copyOf(natives);
        var copiedLayers = new HashMap<String, Map<String, Sprite>>();
        layers.forEach((id, known) -> copiedLayers.put(id, Map.copyOf(known)));
        this.layers = Map.copyOf(copiedLayers);
        var copiedRuntime = new HashMap<String, Set<String>>();
        runtime.forEach((id, known) -> copiedRuntime.put(id, Set.copyOf(known)));
        this.runtime = Map.copyOf(copiedRuntime);
        this.icons = Map.copyOf(icons);
        var copiedIcons = new HashMap<String, Map<String, Sprite>>();
        elementIcons.forEach((id, known) -> copiedIcons.put(id, Map.copyOf(known)));
        this.elementIcons = Map.copyOf(copiedIcons);
    }

    public static HudAssets empty() { return EMPTY; }

    public static HudAssets create(Collection<Screen> supplied) { return create(supplied, DEFAULT_SIZES); }
    /** @param displaySizes display sizes in quarters (4..16); 4 (1x) is always included. */
    public static HudAssets create(Collection<Screen> supplied, Collection<Integer> displaySizes) {
        return create(supplied, displaySizes, DEFAULT_CURSOR_SCALE);
    }
    /** Cursor sprites are drawn at {@code cursorScale} of their original 9-pixel / 15-pixel design (config display.cursor-size). */
    public static final double DEFAULT_CURSOR_SCALE = .4;
    public static HudAssets create(Collection<Screen> supplied, Collection<Integer> displaySizes, double cursorScale) {
        return create(supplied, displaySizes, cursorScale, Map.of());
    }
    /**
     * A runtime layer to bake: {@code screen} holds just the layer's element, drawn with {@code hover} highlighted and
     * {@code pose} (a keyframe) applied, or null.
     */
    private record LayerJob(String key, Screen screen, String hover, dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Motion.Pose pose) {
        LayerJob(String key, Screen screen, String hover) { this(key, screen, hover, null); }
    }
    private static boolean needsHover(Screen.Element e) {
        return e.kind().equals("button") && !(e.style().opacity() == 0 || !e.border() && !e.background() && e.style().hoverColor() == null
                && e.style().hoverEffect().equals("none") && e.hoverImage() == null);
    }
    /**
     * Runtime layers of a screen: one per element that can change (plus its hover look), one per animation frame,
     * one per list row (plus hover). {@code frameImages}: PNG frames by folder (plugins/BigCasares/tideframe/engine/images/folder).
     */
    private static List<LayerJob> layerJobs(Screen screen, Set<String> runtimeIds, Map<String, List<dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset>> frameImages) {
        var jobs = new ArrayList<LayerJob>();
        for (String id : runtimeIds) {
            var e = screen.element(id).orElseThrow();
            var logic = e.logic();
            if (logic.list() != null) {
                for (int i = 0; i < logic.list().rows(); i++) {
                    var row = dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows.row(e, i);
                    var single = screen.withElements(List.of(row));
                    String key = dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows.rowId(id, i);
                    jobs.add(new LayerJob(key, single, null));
                    if (needsHover(row)) jobs.add(new LayerJob(key + "@hover", single, id));
                }
                continue;
            }
            var images = logic.frames() == null ? null : frameImages.get(logic.frames().folder());
            if (images != null && !images.isEmpty()) {
                if (images.size() > MAX_FRAMES) throw new IllegalArgumentException("Animation " + logic.frames().folder() + " has more than " + MAX_FRAMES + " frames");
                for (int k = 0; k < images.size(); k++) jobs.add(new LayerJob(id + "@" + k, screen.withElements(List.of(e.withImage(images.get(k)))), null));
                continue;
            }
            var single = screen.withElements(List.of(e));
            var motion = logic.motion();
            if (motion != null) {
                // Keyframes: one layer per baked frame; with a hover trigger the frames are the hover look.
                for (int k = 0; k < motion.frames(); k++) jobs.add(new LayerJob(id + "@" + k, single, null, motion.frame(k)));
                continue;
            }
            jobs.add(new LayerJob(id, single, null));
            if (needsHover(e)) jobs.add(new LayerJob(id + "@hover", single, id));
        }
        return jobs;
    }
    /** Frames one animated element may have (each frame is its own layer at every display size). */
    public static final int MAX_FRAMES = 64;
    public static HudAssets create(Collection<Screen> supplied, Collection<Integer> displaySizes, double cursorScale,
                                   Map<String, List<dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset>> frameImages) {
        return create(supplied, displaySizes, cursorScale, frameImages, Map.of());
    }
    /**
     * {@code frameIntervals}: by folder, the pace (ms) a GIF's frames were sampled at; it overrides "interval-ms".
     * Folders not listed (PNG frames) play at their elements' "interval-ms", or 50 ms without one.
     */
    public static HudAssets create(Collection<Screen> supplied, Collection<Integer> displaySizes, double cursorScale,
                                   Map<String, List<dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset>> frameImages, Map<String, Integer> frameIntervals) {
        return create(supplied, displaySizes, cursorScale, frameImages, frameIntervals, false);
    }
    /**
     * {@code autoFit}: the pack ships the auto-fit shader (see {@link FitShader}) and menus are drawn with the colour
     * marker at the reference size; {@link #REFERENCE_QUARTERS} is then added to the display sizes.
     */
    public static HudAssets create(Collection<Screen> supplied, Collection<Integer> displaySizes, double cursorScale,
                                   Map<String, List<dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset>> frameImages, Map<String, Integer> frameIntervals, boolean autoFit) {
        if (autoFit) { var withReference = new TreeSet<Integer>(displaySizes); withReference.add(REFERENCE_QUARTERS); displaySizes = withReference; }
        if (supplied == null) throw new IllegalArgumentException("Screens must be specified");
        Objects.requireNonNull(frameIntervals, "Frame intervals must be specified");
        for (int interval : frameIntervals.values()) if (interval < 50 || interval > 60_000) throw new IllegalArgumentException("Frame interval must be 50 to 60000 ms");
        Objects.requireNonNull(frameImages, "Frame images must be specified");
        if (!Double.isFinite(cursorScale) || cursorScale < .25 || cursorScale > 1.5) throw new IllegalArgumentException("Cursor size must be 0.25 to 1.5");
        var sizeSet = new TreeSet<Integer>(displaySizes); sizeSet.add(4);
        for (int q : sizeSet) if (q < 4 || q > 16) throw new IllegalArgumentException("Display sizes must be 1x to 4x in quarter steps");
        var screens = new TreeMap<String, Screen>();
        for (var screen : supplied) {
            if (screen == null) throw new IllegalArgumentException("Screen must be specified");
            var previous = screens.putIfAbsent(screen.id(), screen);
            if (previous != null && !previous.equals(screen)) throw new IllegalArgumentException("Duplicate HUD screen id");
            if (screens.size() > 96) throw new IllegalArgumentException("At most 96 HUD screens are supported");
        }
        if (screens.isEmpty()) return EMPTY;
        var builder = new Builder(List.copyOf(sizeSet)); var frames = new LinkedHashMap<String, Frames>();
        var layers = new LinkedHashMap<String, Map<String, Sprite>>();
        var runtime = new LinkedHashMap<String, Set<String>>();
        var jobsByScreen = new LinkedHashMap<String, List<LayerJob>>();
        var icons = new LinkedHashMap<String, Sprite>();
        var elementIcons = new LinkedHashMap<String, Map<String, Sprite>>();
        int layerCount = 0;
        for (var screen : screens.values()) {
            var runtimeIds = screen.runtimeIds();
            var batch = runtimeIds.isEmpty() ? new HudRaster.Batch(screen) : new HudRaster.Batch(screen, element -> !runtimeIds.contains(element.id()));
            var staticCanvas = batch.render(null);
            var raster = fit(staticCanvas);
            var profile = TileProfile.forScreen(screen);
            var normal = builder.addSprite(raster, "canvas", profile);
            var hover = new TreeMap<String, Sprite>();
            if (!runtimeIds.isEmpty()) {
                var jobs = layerJobs(screen, runtimeIds, frameImages);
                layerCount += jobs.size();
                if (layerCount > MAX_LAYERS) throw new IllegalArgumentException("Too many runtime layers (at most " + MAX_LAYERS + "): fewer list rows, frames or changing elements");
                var baked = new LinkedHashMap<String, Sprite>();
                // A layer's backdrop blur also softens the static canvas it is shown over.
                for (var job : jobs) baked.put(job.key(), builder.addSprite(fit(new HudRaster.Batch(job.screen()).over(staticCanvas).render(job.hover(), job.pose())), "layer", profile));
                layers.put(screen.id(), baked); runtime.put(screen.id(), runtimeIds); jobsByScreen.put(screen.id(), jobs);
            }
            for (var element : screen.elements()) if (element.kind().equals("button") && !runtimeIds.contains(element.id()))
                hover.put(element.id(), element.style().opacity() == 0 || !element.border() && !element.background()
                        && element.style().hoverColor() == null && element.style().hoverEffect().equals("none") && element.hoverImage() == null ? normal
                        : builder.addSprite(fit(batch.render(element.id())), "canvas", profile));
            // Item icons: glyphs naming the client's own textures, over the canvas (static) or with their element's layer.
            var staticIcons = new ArrayList<Placement>();
            for (var element : screen.elements()) {
                // Opacity 0 hides the element, so its icon too.
                if (element.item() == null || element.style().opacity() == 0) continue;
                var icon = dev.linqfy.bigCasares.modules.tideframe.engine.core.ItemIcons.find(element.item()).orElseThrow();
                int iconScale = screen.presentation().scale();
                var glyph = builder.addIcon(icon, dev.linqfy.bigCasares.modules.tideframe.engine.core.ItemIcons.top(element), dev.linqfy.bigCasares.modules.tideframe.engine.core.ItemIcons.size(element.width(), element.height()), iconScale);
                var placement = new Placement(glyph, (int) Math.round(dev.linqfy.bigCasares.modules.tideframe.engine.core.ItemIcons.left(element) * iconScale));
                if (runtimeIds.contains(element.id())) elementIcons.computeIfAbsent(screen.id(), id -> new LinkedHashMap<>()).put(element.id(), new Sprite(List.of(placement)));
                else staticIcons.add(placement);
            }
            if (!staticIcons.isEmpty()) icons.put(screen.id(), new Sprite(List.copyOf(staticIcons)));
            // The entrance is the title's own native fade-in (UiSession), not baked frames: those were
            // 8 extra full canvases per menu, each loaded by the client once per display size.
            frames.put(screen.id(), new Frames(normal, Map.copyOf(hover), List.of()));
        }
        var cursors = new LinkedHashMap<CursorProfile,List<Sprite>>();
        var requested = new LinkedHashSet<CursorProfile>();
        // Passive overlays never show a cursor, so they request no cursor sprites.
        for(var screen:screens.values()) if(!screen.presentation().mode().equals("overlay")) requested.add(cursorProfile(screen));
        for(var family:requested) {
            var rows=new ArrayList<Sprite>();
            // One sprite per logical pixel row (144), not per atlas row (576): same 1-GUI-pixel steps as horizontal movement.
            double size=family.size()<0?cursorScale:family.size();
            for(int row=0;row<HudRaster.LOGICAL_HEIGHT;row++) rows.add(builder.addSprite(cursorImage(family.shape(),row*HudRaster.RESOLUTION,size),
                    family.shape().equals("cross")?"cursor":"cursor_"+family.shape(),TileProfile.cursor().scaled(family.scale())));
            cursors.put(family,List.copyOf(rows));
        }
        var cursor=cursors.getOrDefault(new CursorProfile(1,"cross"),List.of());
        var liveFonts = new LinkedHashMap<LiveKey,Integer>();
        var barFonts = new LinkedHashMap<BarKey,Integer>();
        var colourKeys = new ArrayList<LiveKey>();
        for (var screen : screens.values()) for (var element : dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows.expand(screen.elements())) {
            if (dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.isLive(element)) {
                var key = LiveKey.of(screen, element);
                if (!liveFonts.containsKey(key)) {
                    if (liveFonts.size() >= 64) throw new IllegalArgumentException("At most 64 distinct live-text rows/styles are supported");
                    liveFonts.put(key, liveFonts.size());
                    builder.addLiveFont(liveFonts.get(key), key);
                }
                // Styled live text bakes its colours: each word colour needs a font of its own (added below).
                if (liveColorsBaked(element)) for (String color : dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.colors(element.text())) colourKeys.add(LiveKey.of(screen, element, color));
            } else if (dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.isBar(element)) {
                var key = BarKey.of(screen, element);
                if (!barFonts.containsKey(key)) {
                    if (barFonts.size() >= 32) throw new IllegalArgumentException("At most 32 distinct progress bars are supported");
                    barFonts.put(key, barFonts.size());
                    builder.addBarFont(barFonts.get(key), key);
                }
            }
        }
        // Word colours come after every element's own font and only while the budget lasts: past it, those words keep
        // the element's colour (LiveLayer falls back) instead of the whole pack failing.
        for (var key : colourKeys) if (!liveFonts.containsKey(key) && liveFonts.size() < 64) {
            liveFonts.put(key, liveFonts.size());
            builder.addLiveFont(liveFonts.get(key), key);
        }
        // Text wider than its box shrinks (TextFit): the smaller sizes a live text may need, on current pages only, with a
        // budget of their own after every other font. Past it, such text is cut with an ellipsis at the sizes it has.
        int shrinkFonts = 0;
        for (var screen : screens.values()) {
            if (!dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.allowed(screen)) continue;
            for (var element : dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows.expand(screen.elements())) {
                if (!dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.isLive(element)) continue;
                for (int size : dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.liveSizes(element)) {
                    var keys = new ArrayList<LiveKey>(List.of(LiveKey.of(screen, element, null, size)));
                    if (liveColorsBaked(element)) for (String color : dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.colors(element.text())) keys.add(LiveKey.of(screen, element, color, size));
                    for (var key : keys) if (!liveFonts.containsKey(key) && shrinkFonts < SHRINK_FONTS) {
                        liveFonts.put(key, liveFonts.size());
                        builder.addLiveFont(liveFonts.get(key), key);
                        shrinkFonts++;
                    }
                }
            }
        }
        // Every larger size gets canvases rendered at its own native resolution (baked in parallel).
        var natives = new TreeMap<Integer,NativeCanvas>();
        for (int q : builder.sizes) if (q != 4) natives.put(q, new NativeCanvas(q));
        record Job(int quarters, Screen screen) { }
        var jobs = new ArrayList<Job>();
        for (int q : natives.keySet()) for (var screen : screens.values())
            if (!screen.quality().preset().equals("legacy")) jobs.add(new Job(q, screen));
        java.util.function.BiFunction<Screen, String, Boolean> needsHover = (sc, id) -> sc.elements().stream().filter(e -> e.id().equals(id)).findFirst()
                .map(e -> !(e.style().opacity() == 0 || !e.border() && !e.background() && e.style().hoverColor() == null
                        && e.style().hoverEffect().equals("none") && e.hoverImage() == null)).orElse(false);
        // Rendering and PNG encoding run on every core; glyphs are then committed in a fixed order.
        // Parallel, but bounded by heap: each worker holds full-resolution canvases and decoded photographs (~0.75 GiB).
        int workers = (int) Math.max(1, Math.min(java.lang.Runtime.getRuntime().availableProcessors(),
                java.lang.Runtime.getRuntime().maxMemory() / (768L * 1024 * 1024)));
        List<NativeCanvas.Prepared> prepared;
        var pool = new java.util.concurrent.ForkJoinPool(workers);
        try {
            prepared = pool.submit(() -> jobs.parallelStream().map(job -> {
                var runtimeIds = runtime.getOrDefault(job.screen().id(), Set.of());
                var runtimeLayers = jobsByScreen.getOrDefault(job.screen().id(), List.of()).stream()
                        .map(layer -> new NativeCanvas.Layer(layer.key(), layer.screen(), layer.hover(), layer.pose())).toList();
                return natives.get(job.quarters()).prepare(job.screen(), runtimeIds,
                        job.screen().elements().stream().filter(e -> e.kind().equals("button") && !runtimeIds.contains(e.id())).map(Screen.Element::id).toList(),
                        needsHover, runtimeLayers);
            }).toList()).get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("HUD bake interrupted", interrupted);
        } catch (java.util.concurrent.ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException unchecked) throw unchecked;
            if (failed.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(failed.getCause());
        } finally { pool.shutdown(); }
        for (int i = 0; i < jobs.size(); i++) natives.get(jobs.get(i).quarters()).commit(prepared.get(i));
        builder.finish(natives);
        return new HudAssets(screens, frames, builder.entries, cursor, cursors,builder.displayCorrections,builder.sizes,liveFonts,barFonts,natives,layers,runtime,frameIntervals,icons,elementIcons,autoFit);
    }
    /** Runtime layers across all screens: each is a set of glyphs at every display size. */
    public static final int MAX_LAYERS = 768;

    /** Elements of {@code screen} that this pack baked as runtime layers. */
    public Set<String> runtimeIds(Screen screen) { return runtime.getOrDefault(screen.id(), Set.of()); }
    /**
     * Glyphs (zero net advance) of a runtime layer at a display size: "id", "id@hover", "id@3" (animation frame 3),
     * "id#2" (list row 2), "id#2@hover". Null when the pack has no such layer.
     */
    public String layer(Screen screen, String key, int quarters) {
        requireKnown(screen);
        var canvas = natives.get(quarters);
        String glyphs;
        if (canvas != null && canvas.has(screen.id())) glyphs = canvas.layer(screen.id(), key);
        else {
            var sprite = layers.getOrDefault(screen.id(), Map.of()).get(key);
            glyphs = sprite == null ? null : sequence(sprite, -128 * screen.presentation().scale());
        }
        if (glyphs == null) return null;
        // An element's item icon goes with its layer: shown, hidden, shifted and tinted together.
        var icon = elementIcons.getOrDefault(screen.id(), Map.of()).get(key.endsWith("@hover") ? key.substring(0, key.length() - 6) : key);
        return icon == null ? glyphs : glyphs + sequence(icon, -128 * screen.presentation().scale());
    }
    private final Map<String, Integer> frameIntervals;
    /**
     * Milliseconds between frames of an element animated with "frames": the pace its folder's GIF was sampled at
     * (so a long GIF sampled more sparsely still plays at its real speed), else its own "interval-ms", else 50.
     */
    public int frameInterval(Screen.Element element) {
        var frames = element.logic().frames();
        if (frames == null) throw new IllegalArgumentException("Element " + element.id() + " has no frames");
        var sampled = frameIntervals.get(frames.folder());
        if (sampled != null) return sampled;
        return frames.intervalMs() != dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ElementLogic.Frames.AUTO ? frames.intervalMs() : dev.linqfy.bigCasares.modules.tideframe.engine.core.GifFrames.TICK_MS;
    }
    /** Animation frames baked for an element (0 when it has none). */
    public int frameCount(Screen screen, String id) {
        var known = layers.getOrDefault(screen.id(), Map.of());
        int count = 0;
        while (known.containsKey(id + "@" + count)) count++;
        return count;
    }
    /** The static canvas never shows a runtime element's hover look (its layer does). */
    private String staticHover(Screen screen, String hover) {
        if (hover == null) return null;
        String base = dev.linqfy.bigCasares.modules.tideframe.engine.core.ListRows.template(hover);
        return runtime.getOrDefault(screen.id(), Set.of()).contains(base) ? null : hover;
    }

    /** Font of a live-text element at display size {@code quarters}, or null when the element is not live. */
    public String liveFont(Screen screen, Screen.Element element, int quarters) { return liveFont(screen, element, quarters, null); }
    /** Font of a run of live text in word colour {@code color} (null: the element's own colour); null when there is none. */
    public String liveFont(Screen screen, Screen.Element element, int quarters, String color) {
        return liveFont(screen, element, quarters, color, dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.FULL);
    }
    /** The same at text size {@code size} (TextFit quarters); null when the pack has no font for it. */
    public String liveFont(Screen screen, Screen.Element element, int quarters, String color, int size) {
        if (!dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.isLive(element)) return null;
        var index = liveFonts.get(LiveKey.of(screen, element, color == null || !liveColorsBaked(element) ? null : color, size));
        if (index == null || !sizes.contains(quarters)) return null;
        return quarters == 4 ? "tideframe:live_" + index : "tideframe:live_" + index + "_q" + quarters;
    }
    /** True when the live font bakes the element's colours (draw it white); false when the component colour tints it. */
    public static boolean liveColorsBaked(Screen.Element element) { return element.style().isStyledText(); }
    /** Logical pixels the live glyph ink starts before the text origin (outline thickness). */
    public static int livePad(Screen.Element element) { return element.style().outlineThickness(); }
    /** Characters for a live font: each glyph is followed by its advance correction so it moves exactly 6 x textScale logical px. */
    public static String liveGlyphs(String sanitized, int scale, int quarters) { return liveGlyphs(sanitized, scale, quarters, 1, 6); }
    public static String liveGlyphs(Screen.Element element, String sanitized, int scale, int quarters) {
        return liveGlyphs(element, sanitized, scale, quarters, dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.FULL);
    }
    /** The same at text size {@code size} (TextFit quarters): each glyph moves 6 x textScale x size / 4 logical px. */
    public static String liveGlyphs(Screen.Element element, String sanitized, int scale, int quarters, int size) {
        var style = element.style();
        int cell = Math.max(6 * style.textScale(), 2 * style.outlineThickness() + 5 * style.textScale() + (style.textShadow() ? style.textScale() : 0));
        return liveGlyphs(sanitized, scale, quarters, style.textScale(), cell, size);
    }
    private static String liveGlyphs(String sanitized, int scale, int quarters, int textScale, int cellWidth) {
        return liveGlyphs(sanitized, scale, quarters, textScale, cellWidth, dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.FULL);
    }
    private static String liveGlyphs(String sanitized, int scale, int quarters, int textScale, int cellWidth, int size) {
        boolean corrected = liveCorrection(scale, quarters, textScale, cellWidth, size) != 0;
        var out = new StringBuilder(sanitized.length() * 2);
        for (char c : sanitized.toCharArray()) { out.append(c); if (c != ' ' && corrected) out.append(LIVE_CORRECTION); }
        return out.toString();
    }
    /** Notched-cell width the bar font was built with (0 = smooth). */
    public static int barCellWidth(Screen.Element element) { return dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.barCellWidth(element); }
    /** Font of a progress bar's fill, or null when the element is not a bar. */
    public String barFont(Screen screen, Screen.Element element, int quarters) {
        if (!dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.isBar(element)) return null;
        var index = barFonts.get(BarKey.of(screen, element));
        if (index == null || !sizes.contains(quarters)) return null;
        return quarters == 4 ? "tideframe:bar_" + index : "tideframe:bar_" + index + "_q" + quarters;
    }
    static final char BAR_SEGMENT = '\ue100', BAR_TRAIL = '\ue108', BAR_CORRECTION = '\ue110', BAR_CELL = '\ue120', BAR_CELL_CORRECTION = '\ue121';
    static final int BAR_SEGMENTS = 7; // widths 1,2,4..64 logical px (256 atlas px: the client font sheet limit)
    /** Fill glyphs for {@code logicalWidth} pixels: binary segments, each followed by its exact-advance correction. */
    public static String barGlyphs(int logicalWidth) { return barGlyphs(logicalWidth, logicalWidth, 0); }
    /**
     * Boss-style fill: whole notched cells from the bar start, the remainder as smooth segments, then the
     * recent-damage trail (lighter) up to {@code trailWidth}. Advances exactly max(fill, trail) logical px.
     */
    public static String barGlyphs(int fillWidth, int trailWidth, int cellWidth) {
        var out = new StringBuilder();
        int rest = fillWidth;
        if (cellWidth > 0) while (rest >= cellWidth) { out.append(BAR_CELL).append(BAR_CELL_CORRECTION); rest -= cellWidth; }
        segments(out, rest, BAR_SEGMENT);
        segments(out, Math.max(0, trailWidth - fillWidth), BAR_TRAIL);
        return out.toString();
    }
    private static void segments(StringBuilder out, int width, char base) {
        for (int k = BAR_SEGMENTS - 1; k >= 0; k--) {
            int size = 1 << k;
            while (width >= size) { out.append((char)(base + k)).append((char)(BAR_CORRECTION + k)); width -= size; }
        }
    }
    static final char LIVE_CORRECTION = '\ue000';
    /**
     * In the fonts of shrunk text (TextFit sizes 3 and 2): moves half a logical pixel back. At three quarters a character
     * advances 4.5 px, so an odd count leaves half a pixel that the HUD spaces (whole pixels) cannot return.
     */
    public static final char LIVE_HALF_BACK = '\ue001';
    /** Live fonts for shrunk text, on top of the 64 of live text as authored and its word colours. */
    static final int SHRINK_FONTS = 96;
    private static final int LIVE_CELL_HEIGHT = 256;
    /** Units one character should advance (6 logical px) minus what vanilla gives its 24-px-wide cell. */
    static double liveCorrection(int scale, int quarters) { return liveCorrection(scale, quarters, 1, 6); }
    static double liveCorrection(int scale, int quarters, int textScale, int cellWidth) {
        return liveCorrection(scale, quarters, textScale, cellWidth, dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.FULL);
    }
    /** The same at text size {@code size}: the cell is size x cellWidth px wide and should advance 6 x textScale x size / 4 px. */
    static double liveCorrection(int scale, int quarters, int textScale, int cellWidth, int size) {
        int height = 4 * scale * quarters; // 16 units per 256 px at 1x
        int actual = (int) (0.5f + size * cellWidth * (float) height / LIVE_CELL_HEIGHT) + 1;
        return .375 * textScale * scale * quarters * size / 4 - actual;
    }
    static double barCorrection(int scale, int quarters, int width) {
        int height = 4 * scale * quarters;
        int actual = (int) (0.5f + 4 * width * (float) height / LIVE_CELL_HEIGHT) + 1;
        return width / 16.0 * scale * quarters - actual;
    }

    /** HUD-font space that advances {@code logicalPixels} * presentation scale (the frame's horizontal unit). */
    public static String hudSpace(int quarters) {
        var out = new StringBuilder();
        while (quarters < SPACE_MIN_QUARTER) { out.append(space(SPACE_MIN_QUARTER)); quarters -= SPACE_MIN_QUARTER; }
        while (quarters > SPACE_MAX_QUARTER) { out.append(space(SPACE_MAX_QUARTER)); quarters -= SPACE_MAX_QUARTER; }
        return out.append(space(quarters)).toString();
    }

    /** Display sizes (quarters) that have a font in this pack, ascending; always contains 4 (1x). */
    public List<Integer> sizes() { return sizes; }

    /** Size (quarters) menus are drawn at in auto-fit mode: the shader scales it to the window. */
    public static final int REFERENCE_QUARTERS = FitShader.REFERENCE_QUARTERS;
    private final boolean autoFit;
    /** True when this pack carries the auto-fit shader. */
    public boolean autoFit() { return autoFit; }
    /**
     * True when {@code screen} is drawn with the colour marker at the reference size so the shader fits it to the
     * window: menus and HUD pages at presentation scale 1 (both fill the same canvas, and with auto-fit the player has
     * no size to choose, so a HUD page left at a fixed size could fall off a smaller window). Enlarged layouts stay as
     * authored.
     */
    public boolean fitted(Screen screen) {
        return autoFit && screen.presentation().scale() == 1;
    }

    /** Keep each tile zero-advance when vanilla's integer glyph advance is scaled (quarters, 4 = 1x). */
    public String displayText(String text,int quarters) {
        if(quarters==4)return text;
        if(!sizes.contains(quarters))throw new IllegalArgumentException("Display size "+quarters/4.0+"x is not in this pack");
        var result=new StringBuilder(text.length()*2);
        for(int i=0;i<text.length();i++) {
            char ch=text.charAt(i);result.append(ch);
            var corrections=displayCorrections.get(ch);
            if(corrections!=null)result.append((char)(CORRECTION_START+CORRECTION_ZERO+corrections.get(quarters)));
        }
        return result.toString();
    }

    public void requireKnown(Screen screen) {
        if (screen == null || !screen.equals(screens.get(screen.id())))
            throw new IllegalArgumentException("HUD layout changes require a server restart.");
    }

    /** Hover is enabled after entry animation; 0..7 select the seven intermediate frames plus empty start. */
    /** Canvas glyphs for a display size: native-resolution canvases where available, else the 1x atlas. */
    public String frame(Screen screen, String hover, int animationStep, int quarters) {
        var canvas = natives.get(quarters);
        if (canvas == null || !canvas.has(screen.id())) return frame(screen, hover, animationStep);
        requireKnown(screen);
        return canvas.frame(screen.id(), animationStep < 8 ? null : staticHover(screen, hover)) + icons(screen);
    }
    public String frame(Screen screen, String hover, int animationStep) {
        requireKnown(screen);
        if (animationStep < 0 || animationStep > 8) throw new IllegalArgumentException("Invalid HUD animation step");
        var known = frames.get(screen.id());
        hover = staticHover(screen, hover);
        if (hover != null && !known.hover().containsKey(hover)) throw new IllegalArgumentException("Unknown HUD button");
        var glyph = hover == null || animationStep < 8 ? known.normal() : known.hover().get(hover);
        return sequence(glyph, -128*screen.presentation().scale()) + icons(screen);
    }
    /** The static elements' item icons (zero net advance), drawn over the canvas. */
    private String icons(Screen screen) {
        var sprite = icons.get(screen.id());
        return sprite == null ? "" : sequence(sprite, -128 * screen.presentation().scale());
    }

    /** Normalized x/y match Screen coordinates; each glyph's net advance is exactly zero. */
    public String cursor(double x, double y) {
        return cursor(cursor, x, y, 1);
    }

    /** Every quality profile shares the centred frame of its presentation scale. */
    public String cursor(Screen screen, double x, double y) {
        requireKnown(screen);
        return cursor(cursors.getOrDefault(cursorProfile(screen),List.of()), x, y, screen.presentation().scale());
    }

    private static String cursor(List<Sprite> cursor, double x, double y, int scale) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Invalid HUD cursor position");
        if (cursor.isEmpty()) throw new IllegalStateException("HUD assets are unavailable");
        int leftX = (int)Math.round((Math.max(-1, Math.min(1, x)) + 1) * 128) - 4;
        int centerY = Math.max(0, Math.min(cursor.size() - 1,
                (int)Math.round((1 - Math.max(-1, Math.min(1, y))) * cursor.size() / 2.0)));
        return sequence(cursor.get(centerY), (leftX - 128)*scale);
    }

    /** Own9logical-pixel sprites: cross, solid5x9block and5x9textcaret. */
    private static BufferedImage cursorImage(String shape,int row,double scale) {
        if(shape.equals("arrow")) return arrowCursor(row,scale);
        // Cells of u atlas pixels (4 = one logical pixel at scale 1); the hot spot stays at column 4.
        int u=Math.max(1,(int)Math.round(HudRaster.RESOLUTION*scale)), hot=4*HudRaster.RESOLUTION;
        var image=new BufferedImage(9*HudRaster.RESOLUTION,HudRaster.HEIGHT,BufferedImage.TYPE_INT_ARGB);
        switch(shape) {
            case "cross" -> {
                for(int offset=-3;offset<=3;offset++) {
                    cursorSquare(image,hot,row+offset*u,u,0xffffffff);
                    cursorSquare(image,hot+offset*u,row,u,0xffffffff);
                }
                cursorSquare(image,hot,row,u,0xff61dfd1);
            }
            case "block" -> {
                for(int y=-4;y<=4;y++)for(int x=-2;x<=2;x++)cursorSquare(image,hot+x*u,row+y*u,u,
                        Math.abs(y)==4 || Math.abs(x)==2?0xffffffff:0xff61dfd1);
            }
            case "text" -> {
                for(int y=-4;y<=4;y++)cursorSquare(image,hot,row+y*u,u,0xff61dfd1);
                for(int x=-2;x<=2;x++)for(int y:new int[]{-4,4})cursorSquare(image,hot+x*u,row+y*u,u,0xffffffff);
            }
            default -> throw new IllegalArgumentException("Unknown cursor shape");
        }
        return image;
    }

    /** Pointer arrow, tip on the selected point (sprite column 4, given row): white fill, dark outline, soft shadow. */
    private static BufferedImage arrowCursor(int row, double scale) {
        double r = HudRaster.RESOLUTION * scale;
        var image = new BufferedImage(18 * HudRaster.RESOLUTION, HudRaster.HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL, java.awt.RenderingHints.VALUE_STROKE_PURE);
            g.translate(4 * HudRaster.RESOLUTION, row); g.scale(r, r);
            var arrow = new java.awt.geom.Path2D.Double();
            double[][] points = {{0, 0}, {0, 12.5}, {3, 9.8}, {5.2, 14.6}, {7.3, 13.7}, {5.2, 9.2}, {9, 9.2}};
            arrow.moveTo(points[0][0], points[0][1]);
            for (int i = 1; i < points.length; i++) arrow.lineTo(points[i][0], points[i][1]);
            arrow.closePath();
            var shadow = java.awt.geom.AffineTransform.getTranslateInstance(.9, 1.1).createTransformedShape(arrow);
            g.setColor(new java.awt.Color(0, 0, 0, 70)); g.fill(shadow);
            g.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.setColor(new java.awt.Color(0x0b141a)); g.draw(arrow);
            g.setPaint(new java.awt.GradientPaint(0, 0, java.awt.Color.WHITE, 0, 14, new java.awt.Color(0xd8ece8)));
            g.fill(arrow);
        } finally { g.dispose(); }
        return image;
    }

    private static void cursorSquare(BufferedImage image, int left, int top, int size, int color) {
        int[] pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        for (int dy = 0; dy < size; dy++) for (int dx = 0; dx < size; dx++) {
            int y = top + dy, x = left + dx;
            if (y >= 0 && y < image.getHeight() && x >= 0 && x < image.getWidth()) pixels[y * image.getWidth() + x] = color;
        }
    }

    public Map<String, byte[]> entries() {
        var copied = new LinkedHashMap<String, byte[]>();
        entries.forEach((path, bytes) -> copied.put(path, bytes.clone()));
        return Collections.unmodifiableMap(copied);
    }

    private static String sequence(Sprite sprite, int prefixQuarter) {
        var text = new StringBuilder(sprite.tiles().size() * 3);
        for (var tile : sprite.tiles()) {
            int prefix = prefixQuarter + tile.offsetQuarter();
            text.append(space(prefix)).append(tile.glyph().character())
                    .append(space(-prefix - tile.glyph().advanceQuarter()));
        }
        return text.toString();
    }

    private static char space(int quarter) {
        if (quarter < SPACE_MIN_QUARTER || quarter > SPACE_MAX_QUARTER) throw new IllegalArgumentException("HUD spacing is out of range");
        return (char)(SPACE_START + quarter - SPACE_MIN_QUARTER);
    }

    private static final class Builder {
        private final Map<String, byte[]> entries = new LinkedHashMap<>();
        private final Map<String, Glyph> identical = new HashMap<>();
        /** One bitmap glyph; its ascent/height are derived per display size so every size keeps the centred frame. */
        private record Provider(String file, int rowUnits, int height, int scale, char character) {
            int ascent(int quarters) { return frameAscentQuarters(scale * quarters) - rowUnits * quarters / 4; }
            String json(int quarters) {
                if (rowUnits * quarters % 4 != 0 || height * quarters % 4 != 0) throw new IllegalStateException("HUD size is not pixel aligned");
                int ascent = ascent(quarters), scaledHeight = height * quarters / 4;
                if (ascent > scaledHeight) throw new IllegalStateException("HUD glyph ascent exceeds its height");
                return "{\"type\":\"bitmap\",\"file\":\"tideframe:" + file + "\",\"ascent\":" + ascent
                        + ",\"height\":" + scaledHeight + ",\"chars\":[\"" + escaped(character) + "\"]}";
            }
        }
        /**
         * A game item's own texture (minecraft:&lt;texture&gt;.png, never copied): ascent and height per display size, the
         * top rounded to whole font units; frames stacked in the texture are grid rows and only the first is named.
         */
        private record IconProvider(String texture, int rows, double topPx, int sizePx, int scale, char character) {
            int height(int quarters) { return sizePx * scale * quarters / 16; }
            int ascent(int quarters) { return frameAscentQuarters(scale * quarters) - (int) Math.round(topPx * scale * quarters / 16.0); }
            String json(int quarters) {
                int ascent = ascent(quarters), height = height(quarters);
                if (ascent > height) throw new IllegalStateException("Item icon above the part of the menu the game can draw");
                var chars = new StringBuilder("[\"").append(escaped(character)).append('"');
                for (int row = 1; row < rows; row++) chars.append(",\"\\u0000\"");
                return "{\"type\":\"bitmap\",\"file\":\"minecraft:" + texture + ".png\",\"ascent\":" + ascent + ",\"height\":" + height
                        + ",\"chars\":" + chars + "]}";
            }
        }
        private final List<IconProvider> iconProviders = new ArrayList<>();
        private final List<Integer> sizes;
        Builder(List<Integer> sizes) { this.sizes = sizes; }

        /** One item icon glyph; its advance comes from the measured painted columns, corrected per display size like any tile. */
        private Glyph addIcon(dev.linqfy.bigCasares.modules.tideframe.engine.core.ItemIcons.Icon icon, double topPx, int sizePx, int scale) {
            String key = "item:" + icon.texture() + ':' + topPx + ':' + sizePx + ':' + scale;
            var previous = identical.get(key);
            if (previous != null) return previous;
            if (next == VISUAL_END + 1) next = EXTRA_VISUAL_START;
            if (next > EXTRA_VISUAL_END && next < VISUAL_START) throw new IllegalArgumentException("HUD glyph capacity exceeded (4352 providers)");
            var provider = new IconProvider(icon.texture(), icon.rows(), topPx, sizePx, scale, (char) next);
            for (int q : sizes) provider.json(q); // Validates every size before anything is emitted.
            next++;
            var glyph = new Glyph(provider.character(), icon.advance(provider.height(4)) * 4);
            identical.put(key, glyph);
            var correction = new TreeMap<Integer, Integer>();
            for (int q : sizes) {
                if (q == 4) continue;
                int deltaQuarters = glyph.advanceQuarter() / 4 * q - icon.advance(provider.height(q)) * 4;
                if (Math.abs(deltaQuarters) >= CORRECTION_ZERO) throw new IllegalArgumentException("Display compensation outside budget");
                correction.put(q, deltaQuarters);
            }
            displayCorrections.put(glyph.character(), Map.copyOf(correction));
            iconProviders.add(provider);
            return glyph;
        }
        private final List<Provider> providers = new ArrayList<>();
        private final Map<Character,Map<Integer,Integer>> displayCorrections=new LinkedHashMap<>();
        private long bytes = 22; // Stored ZIP end-of-central-directory.
        private int next = VISUAL_START;

        /** Vanilla's font atlas rejects glyphs wider or taller than 256 source pixels. */
        private Sprite addSprite(BufferedImage image, String name, TileProfile profile) {
            var placements = new ArrayList<Placement>();
            int[] pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
            for (int top = 0; top < image.getHeight(); top += profile.imageHeight())
                for (int left = 0; left < image.getWidth(); left += profile.tileWidth()) {
                    int width = Math.min(profile.tileWidth(), image.getWidth() - left);
                    int height = Math.min(profile.imageHeight(), image.getHeight() - top);
                    var tile = new BufferedImage(width, profile.imageHeight(), BufferedImage.TYPE_INT_ARGB);
                    int[] tilePixels = ((DataBufferInt)tile.getRaster().getDataBuffer()).getData();
                    boolean visible = false;
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        int pixel = pixels[(top + y) * image.getWidth() + left + x];
                        tilePixels[y * width + x] = pixel;
                        visible |= (pixel >>> 24) != 0;
                    }
                    if (!visible) continue;
                    // Keep only the painted part: client memory is the sum of these PNG areas, per display size.
                    int minX = width, maxX = -1, minY = profile.imageHeight(), maxY = -1;
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if ((tilePixels[y * width + x] >>> 24) != 0) {
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    }
                    int hStep = Math.max(1, profile.canvasWidth() / gcd(profile.canvasWidth(), 256));
                    int cropLeft = minX / hStep * hStep, cropRight = maxX + 1;
                    // Vertical crops move the ascent / change the height: whole multiples of 4 font units keep every size integral.
                    int unitsPx4 = 4 * profile.imageHeight(), vStep = unitsPx4 % profile.fontHeight() == 0 ? unitsPx4 / profile.fontHeight() : 0;
                    int cropTop = 0, cropBottom = profile.imageHeight();
                    if (vStep > 0) {
                        cropTop = minY / vStep * vStep;
                        cropBottom = Math.min(profile.imageHeight(), (maxY / vStep + 1) * vStep);
                        while (!validCrop(profile, top, cropTop, cropBottom) && cropBottom < profile.imageHeight()) cropBottom = Math.min(profile.imageHeight(), cropBottom + vStep);
                        if (!validCrop(profile, top, cropTop, cropBottom)) { cropTop = 0; cropBottom = profile.imageHeight(); }
                    }
                    // Raw pixel copy: drawImage would re-blend alpha and round semi-transparent colours.
                    int cw = cropRight - cropLeft, ch = cropBottom - cropTop;
                    var copy = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
                    copy.setRGB(0, 0, cw, ch, tile.getRGB(cropLeft, cropTop, cw, ch, null, 0, cw), 0, cw);
                    int unitsTop = cropTop * profile.fontHeight() / profile.imageHeight();
                    int unitsHeight = (cropBottom - cropTop) * profile.fontHeight() / profile.imageHeight();
                    var glyph = add(copy, name, profile.rowUnits(top) + unitsTop, unitsHeight, profile.scale());
                    placements.add(new Placement(glyph, (left + cropLeft) * 256 / profile.canvasWidth()*profile.scale()));
                }
            return new Sprite(List.copyOf(placements));
        }

        private boolean validCrop(TileProfile profile, int top, int cropTop, int cropBottom) {
            int unitsTop = cropTop * profile.fontHeight() / profile.imageHeight();
            int unitsHeight = (cropBottom - cropTop) * profile.fontHeight() / profile.imageHeight();
            var provider = new Provider("", profile.rowUnits(top) + unitsTop, unitsHeight, profile.scale(), ' ');
            try { for (int q : sizes) provider.json(q); return unitsHeight > 0; }
            catch (IllegalStateException invalid) { return false; }
        }
        private Glyph add(BufferedImage image, String name, int rowUnits, int fontHeight, int scale) {
            var provider = new Provider("", rowUnits, fontHeight, scale, ' ');
            for (int q : sizes) provider.json(q); // Validates every size before anything is emitted.
            byte[] png = png(image); String sha = HexFormat.of().formatHex(PackGenerator.digest("SHA-256", png));
            // Equal image bytes at different vertical offsets need distinct font providers.
            String providerKey = sha + ':' + rowUnits + ':' + fontHeight + ':' + scale;
            var previous = identical.get(providerKey);
            if (previous != null) return previous;
            if (next == VISUAL_END + 1) next = EXTRA_VISUAL_START;
            if (next > EXTRA_VISUAL_END && next < VISUAL_START) throw new IllegalArgumentException("HUD glyph capacity exceeded (4352 providers)");
            String file = "font/hud/" + name + '_' + sha + ".png";
            String path = "assets/tideframe/textures/" + file;
            if (!entries.containsKey(path)) { reserve(path, png.length); entries.put(path, png); }
            int inkWidth = 0;
            int[] pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
            for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++)
                if ((pixels[y * image.getWidth() + x] >>> 24) != 0) inkWidth = Math.max(inkWidth, x + 1);
            // Vanilla BitmapProvider derives integer advance from the last nonzero-alpha column.
            var glyph = new Glyph((char)next++, (Math.round(inkWidth * ((float)fontHeight / image.getHeight())) + 1) * 4);
            identical.put(providerKey, glyph);
            var correction=new TreeMap<Integer,Integer>();
            for(int q:sizes) {
                if(q==4)continue;
                // Vanilla: advance = (int)(0.5 + inkWidth * height / imageHeight) + 1, at the scaled height.
                int scaledAdvance=(int)(0.5f+inkWidth*((float)(fontHeight*q/4)/image.getHeight()))+1;
                int deltaQuarters=glyph.advanceQuarter()/4*q-scaledAdvance*4; // expected - actual, in quarter units
                if(Math.abs(deltaQuarters)>=CORRECTION_ZERO)throw new IllegalArgumentException("Display compensation outside budget");
                correction.put(q,deltaQuarters);
            }
            displayCorrections.put(glyph.character(),Map.copyOf(correction));
            providers.add(new Provider(file, rowUnits, fontHeight, scale, glyph.character()));
            return glyph;
        }

        /**
         * Smallest live cell (64, 128 or 256 px) that holds the ink ({@code inkBottom} px from the cell top) and whose
         * height in font units still reaches the row's ascent at every display size (the client rejects ascent > height).
         */
        private int liveCellHeight(int scale, int rowUnits, int inkBottom) {
            for (int cell : new int[]{64, 128}) {
                if (inkBottom > cell - 1) continue;
                boolean fits = true;
                for (int q : sizes) if (frameAscentQuarters(scale * q) - rowUnits * scale * q / 4 > cell * scale * q / 64) { fits = false; break; }
                if (fits) return cell;
            }
            return LIVE_CELL_HEIGHT;
        }

        /**
         * One 16-column grid per live row/style (capitals, or both cases); every cell has the same width so all glyphs
         * share one advance. A cell is 256 px tall (16 font units at 1x) unless the row allows a 128 or 64 px cell
         * with the same units-per-pixel: identical on screen, a half or a quarter of the client memory.
         * The grid has 4 px per logical pixel; shrunk text (TextFit size 3 or 2) draws each font pixel 3 or 2 px wide in
         * narrower cells, so it stays on whole grid pixels and keeps the same units-per-pixel.
         */
        private void addLiveFont(int index, LiveKey key) {
            int size = key.size(), scale = key.scale(), pad = key.pad(), cellPx = size * key.cellWidth();
            int glyphTop = key.top() - pad * size; // quarter logical px: grid pixels
            int rowUnits = Math.max(0, glyphTop) / 64 * 4, offset = glyphTop - rowUnits * 16;
            var style = key.style();
            int textArgb = key.color() == null ? 0xffffffff : 0xff000000 | Integer.parseInt(key.color().substring(1), 16);
            String chars = key.charset();
            int gridRows = (chars.length() + 15) / 16, cell = liveCellHeight(scale, rowUnits, Math.max(0, offset) + size * TextMask.of("A", style).height());
            var grid = new BufferedImage(16 * cellPx, gridRows * cell, BufferedImage.TYPE_INT_ARGB);
            var rows = new ArrayList<String>();
            for (int row = 0; row < gridRows; row++) {
                var line = new StringBuilder();
                for (int column = 0; column < 16; column++) {
                    int i = row * 16 + column;
                    if (i >= chars.length()) { line.append(escaped('\u0000')); continue; }
                    char c = chars.charAt(i); line.append(escaped(c));
                    var mask = TextMask.of(String.valueOf(c), style);
                    int ox = column * cellPx, oy = row * cell + Math.max(0, offset);
                    for (int y = 0; y < mask.height(); y++) for (int x = 0; x < mask.width(); x++) {
                        int argb = mask.color(x, y, textArgb, style);
                        if (argb == 0 || x * size + size - 1 >= cellPx || oy - row * cell + y * size + size - 1 >= cell - 1) continue;
                        for (int dy = 0; dy < size; dy++) for (int dx = 0; dx < size; dx++) grid.setRGB(ox + x * size + dx, oy + y * size + dy, argb);
                    }
                    // Nearly invisible marker: vanilla measures advance up to the last non-transparent column.
                    grid.setRGB(ox + cellPx - 1, row * cell + cell - 1, 0x01ffffff);
                }
                rows.add(line.toString());
            }
            byte[] png = png(grid);
            String file = "font/live/grid_" + HexFormat.of().formatHex(PackGenerator.digest("SHA-256", png)).substring(0, 16) + ".png";
            String path = "assets/tideframe/textures/" + file;
            if (!entries.containsKey(path)) { reserve(path, png.length); entries.put(path, png); }
            for (int q : sizes) {
                int height = cell * scale * q / 64, ascent = frameAscentQuarters(scale * q) - rowUnits * scale * q / 4;
                if (ascent > height || rowUnits * scale * q % 4 != 0) throw new IllegalStateException("Live text row is not representable");
                double desired = .375 * key.textScale() * scale * q * size / 4, correction = liveCorrection(scale, q, key.textScale(), key.cellWidth(), size);
                // Shrunk text can end half a logical pixel (q / 32 units) past a whole one: LIVE_HALF_BACK returns it.
                String halfBack = size == dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.FULL ? "" : ",\"" + escaped(LIVE_HALF_BACK) + "\":" + (-q / 32.0);
                String font = "{\"providers\":[{\"type\":\"space\",\"advances\":{\" \":" + desired + ",\"" + escaped(LIVE_CORRECTION) + "\":" + correction + halfBack
                        + "}},{\"type\":\"bitmap\",\"file\":\"tideframe:" + file + "\",\"ascent\":" + ascent + ",\"height\":" + height
                        + ",\"chars\":[\"" + String.join("\",\"", rows) + "\"]}]}\n";
                String fontPath = "assets/tideframe/font/live_" + index + (q == 4 ? "" : "_q" + q) + ".json";
                byte[] bytes = font.getBytes(StandardCharsets.UTF_8);
                reserve(fontPath, bytes.length); entries.put(fontPath, bytes);
            }
        }

        /**
         * Boss-style bar fill: glossy segments (1..64 logical px), the same in a lighter "recent damage" colour,
         * and one notched cell whose last column is a dark groove. One bitmap per glyph.
         */
        private void addBarFont(int index, BarKey key) {
            int scale = key.scale(), rowUnits = Math.max(0, key.top()) / 16 * 4, offset = Math.max(0, (key.top() - rowUnits * 4) * 4);
            int rgb = Integer.parseInt(key.color().substring(1), 16), pixelHeight = Math.min(LIVE_CELL_HEIGHT - 1 - offset, key.height() * 4);
            int trail = mix(rgb, 0xffffff, .55);
            var glyphs = new ArrayList<String[]>(); // {file, char}
            for (int k = 0; k < BAR_SEGMENTS; k++) {
                glyphs.add(new String[]{barTexture(4 << k, offset, pixelHeight, rgb, false), escaped((char)(BAR_SEGMENT + k))});
                glyphs.add(new String[]{barTexture(4 << k, offset, pixelHeight, trail, false), escaped((char)(BAR_TRAIL + k))});
            }
            if (key.cellWidth() > 0) glyphs.add(new String[]{barTexture(4 * key.cellWidth(), offset, pixelHeight, rgb, true), escaped(BAR_CELL)});
            for (int q : sizes) {
                int height = 4 * scale * q, ascent = frameAscentQuarters(scale * q) - rowUnits * scale * q / 4;
                if (ascent > height || rowUnits * scale * q % 4 != 0) throw new IllegalStateException("Progress bar row is not representable");
                var font = new StringBuilder("{\"providers\":[{\"type\":\"space\",\"advances\":{");
                for (int k = 0; k < BAR_SEGMENTS; k++) {
                    if (k > 0) font.append(',');
                    font.append('"').append(escaped((char)(BAR_CORRECTION + k))).append("\":").append(barCorrection(scale, q, 1 << k));
                }
                if (key.cellWidth() > 0) font.append(",\"").append(escaped(BAR_CELL_CORRECTION)).append("\":").append(barCorrection(scale, q, key.cellWidth()));
                font.append("}}");
                for (var glyph : glyphs)
                    font.append(",{\"type\":\"bitmap\",\"file\":\"tideframe:").append(glyph[0]).append("\",\"ascent\":").append(ascent)
                        .append(",\"height\":").append(height).append(",\"chars\":[\"").append(glyph[1]).append("\"]}");
                font.append("]}\n");
                String fontPath = "assets/tideframe/font/bar_" + index + (q == 4 ? "" : "_q" + q) + ".json";
                byte[] bytes = font.toString().getBytes(StandardCharsets.UTF_8);
                reserve(fontPath, bytes.length); entries.put(fontPath, bytes);
            }
        }
        /** One glossy bar piece: bright top edge, soft gloss, darker base; a notched cell ends in a dark groove. */
        private String barTexture(int width, int offset, int pixelHeight, int rgb, boolean notched) {
            var image = new BufferedImage(width, LIVE_CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < pixelHeight; y++) {
                double t = pixelHeight <= 1 ? 0 : y / (double)(pixelHeight - 1);
                double light = y < 4 ? 1.55 : t < .45 ? 1.28 - .5 * t : y >= pixelHeight - 4 ? .62 : 1;
                for (int x = 0; x < width; x++) {
                    double l = notched && x >= width - 4 ? light * .42 : light;
                    image.setRGB(x, offset + y, 0xff000000 | shade(rgb >>> 16 & 255, l) << 16 | shade(rgb >>> 8 & 255, l) << 8 | shade(rgb & 255, l));
                }
            }
            byte[] png = png(image);
            String file = "font/bar/seg_" + HexFormat.of().formatHex(PackGenerator.digest("SHA-256", png)).substring(0, 16) + ".png";
            String path = "assets/tideframe/textures/" + file;
            if (!entries.containsKey(path)) { reserve(path, png.length); entries.put(path, png); }
            return file;
        }
        private static int mix(int rgb, int other, double amount) {
            int r = (int)Math.round((rgb >>> 16 & 255) * (1 - amount) + (other >>> 16 & 255) * amount);
            int g = (int)Math.round((rgb >>> 8 & 255) * (1 - amount) + (other >>> 8 & 255) * amount);
            int b = (int)Math.round((rgb & 255) * (1 - amount) + (other & 255) * amount);
            return r << 16 | g << 8 | b;
        }
        private static int shade(int channel, double light) { return Math.max(0, Math.min(255, (int)Math.round(channel * light + (light > 1 ? 20 * (light - 1) : 0)))); }

        private void finish() { finish(Map.of()); }
        private void finish(Map<Integer,NativeCanvas> natives) {
            var spaces = new StringBuilder("{\"type\":\"space\",\"advances\":{");
            for (int quarter = SPACE_MIN_QUARTER; quarter <= SPACE_MAX_QUARTER; quarter++) {
                if (quarter != SPACE_MIN_QUARTER) spaces.append(',');
                spaces.append('"').append(escaped(space(quarter))).append("\":").append(quarter / 4.0);
            }
            spaces.append("}}");
            var baseProviders = new ArrayList<>(providers.stream().map(p -> p.json(4)).toList());
            iconProviders.forEach(p -> baseProviders.add(p.json(4)));
            String font = "{\"providers\":[" + spaces + (baseProviders.isEmpty() ? "" : "," + String.join(",", baseProviders)) + "]}\n";
            byte[] encoded = font.getBytes(StandardCharsets.UTF_8);
            String path = "assets/tideframe/font/hud.json";
            reserve(path, encoded.length); entries.put(path, encoded);
            for(int q:sizes) {
                if(q==4)continue;
                var scaledSpaces=new StringBuilder("{\"type\":\"space\",\"advances\":{");
                for(int quarter=SPACE_MIN_QUARTER;quarter<=SPACE_MAX_QUARTER;quarter++) {
                    if(quarter!=SPACE_MIN_QUARTER)scaledSpaces.append(',');
                    scaledSpaces.append('"').append(escaped(space(quarter))).append("\":").append(quarter*q/16.0);
                }
                for(int delta=-CORRECTION_ZERO+1;delta<CORRECTION_ZERO;delta++)scaledSpaces.append(",\"")
                    .append(escaped((char)(CORRECTION_START+CORRECTION_ZERO+delta))).append("\":").append(delta/4.0);
                scaledSpaces.append("}}");
                var scaledProviders=new ArrayList<>(providers.stream().map(p -> p.json(q)).toList());
                for(var icon:iconProviders)scaledProviders.add(icon.json(q));
                var nativeCanvas=natives.get(q);
                if(nativeCanvas!=null) {
                    scaledProviders.add(nativeCanvas.providersJson());
                    for(var entry:nativeCanvas.entries().entrySet()) if(!entries.containsKey(entry.getKey())) {reserve(entry.getKey(),entry.getValue().length);entries.put(entry.getKey(),entry.getValue());}
                }
                String scaledPath="assets/"+fontName(q).replace(":","/font/")+".json";
                byte[] scaled=("{\"providers\":["+scaledSpaces+","+String.join(",",scaledProviders)+"]}\n").getBytes(StandardCharsets.UTF_8);
                reserve(scaledPath,scaled.length);entries.put(scaledPath,scaled);
            }
        }

        private void reserve(String path, int length) {
            // Local + central headers include the fixed timestamp extra in each directory.
            bytes += length + 94L + path.getBytes(StandardCharsets.UTF_8).length * 2L;
            if (bytes > MAX_PACK_BYTES) throw new IllegalArgumentException("HUD resource pack exceeds the 200 MiB limit");
        }
    }

    /** Area-averages (premultiplied) any raster wider than the atlas, keeping the 16:9 canvas. */
    static BufferedImage fit(BufferedImage source) {
        if (source.getWidth() <= ATLAS_WIDTH) return source;
        int sw = source.getWidth(), sh = source.getHeight(), tw = ATLAS_WIDTH, th = ATLAS_HEIGHT;
        int[] in = source.getRGB(0, 0, sw, sh, null, 0, sw);
        // Horizontal pass, then vertical pass; exact box coverage per destination pixel.
        double[] mid = new double[tw * sh * 4];
        double fx = sw / (double) tw, fy = sh / (double) th;
        for (int y = 0; y < sh; y++) for (int x = 0; x < tw; x++) {
            double start = x * fx, end = start + fx, a = 0, r = 0, g = 0, b = 0;
            for (int sx = (int) start; sx < Math.min(sw, (int) Math.ceil(end)); sx++) {
                double w = Math.min(end, sx + 1) - Math.max(start, sx);
                if (w <= 0) continue;
                int p = in[y * sw + sx]; double alpha = (p >>> 24) / 255.0 * w;
                a += alpha; r += ((p >> 16) & 255) * alpha; g += ((p >> 8) & 255) * alpha; b += (p & 255) * alpha;
            }
            int o = (y * tw + x) * 4; mid[o] = a; mid[o + 1] = r; mid[o + 2] = g; mid[o + 3] = b;
        }
        var target = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        int[] out = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < th; y++) for (int x = 0; x < tw; x++) {
            double start = y * fy, end = start + fy, a = 0, r = 0, g = 0, b = 0;
            for (int sy = (int) start; sy < Math.min(sh, (int) Math.ceil(end)); sy++) {
                double w = Math.min(end, sy + 1) - Math.max(start, sy);
                if (w <= 0) continue;
                int o = (sy * tw + x) * 4;
                a += mid[o] * w; r += mid[o + 1] * w; g += mid[o + 2] * w; b += mid[o + 3] * w;
            }
            double area = fx * fy, alpha = a / area;
            int ia = (int) Math.round(Math.min(1, alpha) * 255);
            // Fully transparent results stay exactly zero so empty tiles are still skipped.
            out[y * tw + x] = ia == 0 ? 0 : ia << 24 | clamp(r / a) << 16 | clamp(g / a) << 8 | clamp(b / a);
        }
        return target;
    }
    private static int clamp(double value) { return (int) Math.max(0, Math.min(255, Math.round(value))); }

    static byte[] encodePng(BufferedImage image) { return png(image); }
    static String escapedChar(char character) { return escaped(character); }
    private static String escaped(char character) { return String.format(Locale.ROOT, "\\u%04x", (int)character); }

    private static byte[] png(BufferedImage image) {
        var writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) throw new IllegalStateException("PNG encoder unavailable");
        var writer = writers.next(); var bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            // Strongest deflate: about 4 % smaller packs for the same pixels (the client decodes either equally fast).
            var param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) { param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT); param.setCompressionQuality(0f); }
            writer.write(null, new IIOImage(image, null, null), param); output.flush();
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("HUD PNG encoding failed", failure); }
        finally { writer.dispose(); }
    }
}
