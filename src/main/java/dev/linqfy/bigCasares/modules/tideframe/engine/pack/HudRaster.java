package dev.linqfy.bigCasares.modules.tideframe.engine.pack;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.ImageAsset;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Original coverage rasterizer for the vanilla title HUD. No installed fonts, shaders,
 * graphics contexts or world entities are used. All work belongs on a worker.
 */
public final class HudRaster {
    public static final int LOGICAL_WIDTH = 256;
    public static final int LOGICAL_HEIGHT = 144;
    public static final int RESOLUTION = 4;
    public static final int WIDTH = LOGICAL_WIDTH * RESOLUTION;
    public static final int HEIGHT = LOGICAL_HEIGHT * RESOLUTION;
    private static final int COVERAGE_SAMPLES = 4;
    private HudRaster() { }

    /** Step 8 is final; steps 1..7 combine fade, slide and scale; step 0 is transparent. */
    public static BufferedImage render(Screen screen, String hover, int animationStep) {
        Objects.requireNonNull(screen, "Screen must be specified");
        if (animationStep < 0 || animationStep > 8) throw new IllegalArgumentException("Invalid HUD animation step");
        if (animationStep == 0) return new BufferedImage(screen.quality().width(), screen.quality().height(), BufferedImage.TYPE_INT_ARGB);
        return animate(new Batch(screen).render(hover), animationStep);
    }

    /** One screen's bake shares bounded source/filter caches across hover variants. */
    static final class Batch {
        private final Screen screen;
        private final List<Screen.Element> elements;
        private final PixelCache decoded = new PixelCache(), filtered = new PixelCache();
        private final OpacityScratch opacityScratch = new OpacityScratch();
        private final int width, height;
        /** What a runtime layer is drawn over (the screen's static canvas, same size), for backdrop blurs; null = only this canvas. */
        private BufferedImage under;
        Batch(Screen screen) { this(screen, screen.quality().width(), screen.quality().height()); }
        /** Only the elements {@code include} accepts (runtime layers are baked separately). */
        Batch(Screen screen, java.util.function.Predicate<Screen.Element> include) { this(screen, screen.quality().width(), screen.quality().height(), include); }
        /** Render at an explicit 16:9 resolution (native per-size canvases); legacy geometry only at its own size. */
        Batch(Screen screen, int width, int height) { this(screen, width, height, element -> true); }
        Batch(Screen screen, int width, int height, java.util.function.Predicate<Screen.Element> include) {
            this.screen = Objects.requireNonNull(screen); this.width = width; this.height = height;
            elements = screen.elements().stream().filter(include).sorted(Comparator.comparingInt(Screen.Element::layer)).toList();
        }
        BufferedImage render(String hover) { return render(hover, null); }
        /** Backdrop blurs of this batch also soften {@code canvas} (a runtime layer's static canvas, same size). */
        Batch over(BufferedImage canvas) {
            if (canvas != null && (canvas.getWidth() != width || canvas.getHeight() != height)) throw new IllegalArgumentException("Backdrop canvas size differs");
            under = canvas; return this;
        }
        /** {@code pose}: a keyframe's offset, scale, opacity and rotation applied to every element drawn (runtime layers). */
        BufferedImage render(String hover, dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Motion.Pose pose) {
            var canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            boolean legacy = screen.quality().preset().equals("legacy") && width == screen.quality().width();
            boolean fit = dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.allowed(screen);
            for (var element : elements) draw(canvas, element, element.kind().equals("button") && element.id().equals(hover),
                    legacy, screen.legacyText(), fit, decoded, filtered, opacityScratch, pose == null || pose.identity() ? null : pose, under);
            return canvas;
        }
    }

    /** Reuse one bounded area backup, rather than allocating a full-resolution layer for every decoration. */
    private static final class OpacityScratch {
        private int[] previous = new int[0];
        OpacityGroup begin(BufferedImage canvas, int left, int top, int width, int height, int margin) {
            int x = Math.max(0, left - margin), y = Math.max(0, top - margin);
            int right = Math.min(canvas.getWidth(), left + width + margin), bottom = Math.min(canvas.getHeight(), top + height + margin);
            int areaWidth = Math.max(0, right - x), areaHeight = Math.max(0, bottom - y), count = areaWidth * areaHeight;
            if (previous.length < count) previous = new int[count];
            int[] target = pixels(canvas);
            for (int row = 0; row < areaHeight; row++) {
                int index = (y + row) * canvas.getWidth() + x;
                System.arraycopy(target, index, previous, row * areaWidth, areaWidth);
                Arrays.fill(target, index, index + areaWidth, 0);
            }
            return new OpacityGroup(x, y, areaWidth, areaHeight, previous);
        }
    }

    private record OpacityGroup(int x, int y, int width, int height, int[] previous) {
        /** {@code blur}: standard deviation in canvas pixels applied to the element alone before it is composited (0 = none). */
        void finish(BufferedImage canvas, double opacity, double brightness, double blur) {
            int[] target = pixels(canvas);
            if (blur > 0 && width > 0 && height > 0) {
                int[] region = new int[width * height];
                for (int row = 0; row < height; row++) System.arraycopy(target, (y + row) * canvas.getWidth() + x, region, row * width, width);
                Blur.gaussian(region, width, height, blur);
                for (int row = 0; row < height; row++) System.arraycopy(region, row * width, target, (y + row) * canvas.getWidth() + x, width);
            }
            for (int row = 0; row < height; row++) for (int column = 0; column < width; column++) {
                int targetX = x + column, targetY = y + row, index = targetY * canvas.getWidth() + targetX;
                int element = HudRaster.opacity(brightness(target[index], brightness), opacity);
                target[index] = previous[row * width + column];
                blend(canvas, targetX, targetY, element);
            }
        }
    }

    /** Each cache retains at most64MiB of decoded ARGB; a4K source fits without unbounded screen growth. */
    private static final class PixelCache {
        private final LinkedHashMap<String, BufferedImage> images = new LinkedHashMap<>(16, .75f, true);
        private long pixels;
        BufferedImage get(String key) { return images.get(key); }
        void put(String key, BufferedImage image) {
            var previous = images.put(key, image);
            if (previous != null) pixels -= (long)previous.getWidth() * previous.getHeight();
            pixels += (long)image.getWidth() * image.getHeight();
            var iterator = images.entrySet().iterator();
            while (pixels > 16L * 1024 * 1024 && images.size() > 1) {
                var entry = iterator.next(); pixels -= (long)entry.getValue().getWidth() * entry.getValue().getHeight(); iterator.remove();
            }
        }
    }

    /** Reuse the final canvas: entrance frames never re-decode or re-filter photographs. */
    static BufferedImage animate(BufferedImage canvas, int animationStep) {
        if (animationStep < 0 || animationStep > 8) throw new IllegalArgumentException("Invalid HUD animation step");
        if (animationStep == 8) return canvas;
        int width = canvas.getWidth(), height = canvas.getHeight();
        var animated = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (animationStep == 0) return animated;
        int[] source = pixels(canvas), target = pixels(animated);
        double progress = animationStep / 8.0, scale = .85 + .15 * progress;
        double slide = 10 * (width / (double)LOGICAL_WIDTH) * (1 - progress);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int sourceX = (int)Math.floor((x + .5 - width / 2.0) / scale + width / 2.0);
            int sourceY = (int)Math.floor((y + .5 - height / 2.0 - slide) / scale + height / 2.0);
            if (sourceX < 0 || sourceX >= width || sourceY < 0 || sourceY >= height) continue;
            int color = source[sourceY * width + sourceX];
            int alpha = (int)Math.round((color >>> 24) * progress);
            if (alpha != 0) target[y * width + x] = (color & 0xffffff) | (alpha << 24);
        }
        return animated;
    }

    /**
     * {@code literal}: the page is schema 1-8 (Screen.legacyText), so "<gold>" in a text is drawn as written.
     * {@code fitText}: a line wider than its box is drawn smaller, then cut with "…" (TextFit.allowed).
     */
    private static void draw(BufferedImage canvas, Screen.Element element, boolean hover, boolean legacy, boolean literal, boolean fitText,
                             PixelCache decoded, PixelCache filtered, OpacityScratch opacityScratch, dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Motion.Pose pose,
                             BufferedImage under) {
        if (element.style().opacity() == 0 || pose != null && pose.opacity() == 0) return;
        if (element.style().backdropBlur() > 0) backdrop(canvas, under, element, hover, legacy, pose);
        boolean transform = pose != null || element.style().rotation() != 0 || hover && !element.style().hoverEffect().equals("none") && !element.style().hoverEffect().equals("glow");
        if (transform) drawTransformed(canvas, element, hover, legacy, literal, fitText, decoded, filtered, opacityScratch, pose == null ? dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Motion.Pose.IDENTITY : pose);
        else drawPlain(canvas, element, hover, legacy, literal, fitText, decoded, filtered, opacityScratch, canvas.getWidth(), canvas.getHeight(), 0, 0);
    }

    private record Geometry(int width, int height, int centerX, int centerY, int left, int top) { }
    private static Geometry geometry(Screen.Element element, boolean legacy, int width, int height) {
        int logicalWidth = Math.max(1, (int)Math.round(element.width() * LOGICAL_WIDTH / 2.0));
        int logicalHeight = Math.max(1, (int)Math.round(element.height() * LOGICAL_HEIGHT / 2.0));
        int logicalCenterX = (int)Math.round((element.x() + 1) * LOGICAL_WIDTH / 2.0);
        int logicalCenterY = (int)Math.round((1 - element.y()) * LOGICAL_HEIGHT / 2.0);
        int boxWidth = legacy ? logicalWidth * RESOLUTION : Math.max(1, (int)Math.round(element.width() * width / 2));
        int boxHeight = legacy ? logicalHeight * RESOLUTION : Math.max(1, (int)Math.round(element.height() * height / 2));
        int centerX = legacy ? logicalCenterX * RESOLUTION : (int)Math.round((element.x() + 1) * width / 2);
        int centerY = legacy ? logicalCenterY * RESOLUTION : (int)Math.round((1 - element.y()) * height / 2);
        return new Geometry(boxWidth, boxHeight, centerX, centerY,
                legacy ? (logicalCenterX - logicalWidth / 2) * RESOLUTION : centerX - boxWidth / 2,
                legacy ? (logicalCenterY - logicalHeight / 2) * RESOLUTION : centerY - boxHeight / 2);
    }

    /**
     * Paint a complete bounded element plane before transforming, so offscreen source pixels can rotate into view.
     * {@code pose} adds a keyframe's offset (logical px), scale, rotation and opacity on top of the element's own.
     */
    private static void drawTransformed(BufferedImage canvas, Screen.Element element, boolean hover, boolean legacy, boolean literal, boolean fitText,
                                        PixelCache decoded, PixelCache filtered, OpacityScratch opacityScratch, dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Motion.Pose pose) {
        var box = geometry(element, legacy, canvas.getWidth(), canvas.getHeight());
        double resolution = canvas.getWidth() / (double)LOGICAL_WIDTH;
        int margin = (int)Math.ceil(resolution * margin(element, hover));
        int sourceLeft = box.left() - margin, sourceTop = box.top() - margin;
        var plane = new BufferedImage(box.width() + margin * 2, box.height() + margin * 2, BufferedImage.TYPE_INT_ARGB);
        drawPlain(plane, element, hover, legacy, literal, fitText, decoded, filtered, opacityScratch, canvas.getWidth(), canvas.getHeight(), -sourceLeft, -sourceTop);
        double angle = Math.toRadians(element.style().rotation() + pose.rotation()), cos = Math.cos(angle), sin = Math.sin(angle);
        double scale = (hover && element.style().hoverEffect().equals("scale") ? 1.05 : 1) * pose.scale();
        double shiftX = pose.x() * resolution;
        double shiftY = (hover && element.style().hoverEffect().equals("translate") ? -2 * resolution : 0) + pose.y() * resolution;
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (int y : new int[]{sourceTop, sourceTop + plane.getHeight()}) for (int x : new int[]{sourceLeft, sourceLeft + plane.getWidth()}) {
            double dx = x - box.centerX(), dy = y - box.centerY();
            double px = box.centerX() + (cos * dx - sin * dy) * scale + shiftX;
            double py = box.centerY() + (sin * dx + cos * dy) * scale + shiftY;
            minX = Math.min(minX, px); maxX = Math.max(maxX, px); minY = Math.min(minY, py); maxY = Math.max(maxY, py);
        }
        double alpha = pose.opacity();
        for (int y = Math.max(0, (int)Math.floor(minY)); y < Math.min(canvas.getHeight(), (int)Math.ceil(maxY)); y++)
            for (int x = Math.max(0, (int)Math.floor(minX)); x < Math.min(canvas.getWidth(), (int)Math.ceil(maxX)); x++) {
                double dx = (x + .5 - box.centerX() - shiftX) / scale, dy = (y + .5 - box.centerY() - shiftY) / scale;
                double sx = box.centerX() + cos * dx + sin * dy - sourceLeft - .5;
                double sy = box.centerY() - sin * dx + cos * dy - sourceTop - .5;
                if (sx < -.5 || sy < -.5 || sx >= plane.getWidth() - .5 || sy >= plane.getHeight() - .5) continue;
                int color = bilinear(plane, sx, sy);
                blend(canvas, x, y, alpha >= 1 ? color : opacity(color, alpha));
            }
    }

    private static void drawPlain(BufferedImage canvas, Screen.Element element, boolean hover, boolean legacy, boolean literal, boolean fitText,
                                  PixelCache decoded, PixelCache filtered, OpacityScratch opacityScratch,
                                  int renderWidth, int renderHeight, int offsetX, int offsetY) {
        if (element.style().opacity() == 0) return;
        int canvasWidth = canvas.getWidth(), canvasHeight = canvas.getHeight();
        double resolution = renderWidth / (double)LOGICAL_WIDTH;
        // Keep the authored layout and 5x7 strokes on their original logical grid.
        int logicalWidth = Math.max(1, (int)Math.round(element.width() * LOGICAL_WIDTH / 2.0));
        int logicalHeight = Math.max(1, (int)Math.round(element.height() * LOGICAL_HEIGHT / 2.0));
        int logicalCenterX = (int)Math.round((element.x() + 1) * LOGICAL_WIDTH / 2.0);
        int logicalCenterY = (int)Math.round((1 - element.y()) * LOGICAL_HEIGHT / 2.0);
        var box = geometry(element, legacy, renderWidth, renderHeight);
        int width = box.width(), height = box.height(), centerX = box.centerX() + offsetX, centerY = box.centerY() + offsetY;
        int left = box.left() + offsetX, top = box.top() + offsetY;
        var opacityGroup = element.style().opacity() == 1 && element.style().brightness() == 1 && element.style().blur() == 0 ? null
                : opacityScratch.begin(canvas, left, top, width, height, (int)Math.ceil(resolution * margin(element, hover)));
        int fill = color(hover ? Objects.requireNonNullElse(element.style().hoverColor(), "#275b64") : element.style().fillColor(), legacy ? 224 : 255, 1);
        int outline = color(hover ? Objects.requireNonNullElse(element.style().hoverColor(), "#61dfd1") : element.style().borderColor(), 255, 1);
        if (element.style().shadow() && element.style().shadowBlur() > 0)
            blurredShadow(canvas, element.shape(), left, top, width, height, resolution, element.style().radius(), element.style().shadowBlur());
        else if (element.style().shadow()) shadow(canvas, element.shape(), left, top, width, height, resolution, 1, element.style().radius());
        if (hover && element.style().hoverEffect().equals("glow"))
            glow(canvas, element.shape(), left, top, width, height, resolution, outline, element.style().radius());
        String gradientEnd = element.style().gradientColor();
        int fillEnd = gradientEnd == null ? fill : color(gradientEnd, legacy ? 224 : 255, 1);
        if (element.background()) for (int py = Math.max(0, top); py < Math.min(canvasHeight, top + height); py++)
            for (int px = Math.max(0, left); px < Math.min(canvasWidth, left + width); px++) {
                int localX = px - left, localY = py - top;
                int base = gradientEnd == null ? fill : mix(fill, fillEnd, gradientT(element.style().gradientDirection(), localX, localY, width, height));
                blend(canvas, px, py, covered(base, coverage(element.shape(), localX, localY, width, height, false,
                        resolution * element.style().outlineWidth(), element.style().radius())));
            }
        // A hovered button with a hover image draws that image instead (same box, same fit).
        var source = hover && element.hoverImage() != null ? element.hoverImage() : element.image();
        if (source != null) {
            var image = decoded.get(source.sha256());
            if (image == null) { image = decode(source); decoded.put(source.sha256(), image); }
            // A 9-slice picture also takes the element's shape with style.imageClip, as the editor's preview does.
            if (element.style().slice() > 0) sliced(canvas, image, left, top, width, height, element.style().slice(), element.style().sliceScale(), resolution,
                    element.style().imageClip() && !element.shape().equals("rectangle") ? element.shape() : null, element.style().radius());
            else {
            double fit = Math.min((double)width / image.getWidth(), (double)height / image.getHeight());
            int imageWidth = Math.max(1, (int)Math.floor(image.getWidth() * fit));
            int imageHeight = Math.max(1, (int)Math.floor(image.getHeight() * fit));
            int imageLeft = centerX - imageWidth / 2, imageTop = centerY - imageHeight / 2;
            String filterKey = source.sha256() + ':' + imageWidth + ':' + imageHeight;
            var photo = filtered.get(filterKey);
            if (photo == null) {
                if (imageWidth == image.getWidth() && imageHeight == image.getHeight()) photo = image;
                else {
                    photo = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
                    int[] photoPixels = pixels(photo);
                    for (int y = 0; y < imageHeight; y++) for (int x = 0; x < imageWidth; x++)
                        photoPixels[y * imageWidth + x] = bilinear(image, (x + .5) * image.getWidth() / imageWidth - .5,
                                (y + .5) * image.getHeight() / imageHeight - .5);
                }
                filtered.put(filterKey, photo);
            }
            int[] photoPixels = pixels(photo);
            // style.imageClip: the picture takes the element's shape (rounded corners, ellipse), antialiased like its fill.
            boolean clip = element.style().imageClip() && !element.shape().equals("rectangle");
            for (int y = 0; y < imageHeight; y++) for (int x = 0; x < imageWidth; x++) {
                int targetX = imageLeft + x, targetY = imageTop + y;
                if (targetX < 0 || targetX >= canvasWidth || targetY < 0 || targetY >= canvasHeight) continue;
                int pixel = photoPixels[y * imageWidth + x];
                if (clip) pixel = covered(pixel, coverage(element.shape(), targetX - left, targetY - top, width, height, false, 0, element.style().radius()));
                blend(canvas, targetX, targetY, pixel);
            }
            }
        }
        int textScale = element.style().textScale();
        // Words in their own colour (schema 9 tags): lines are cut from the text as drawn; each character keeps its colour.
        String drawn = element.text();
        int[] charColors = null;
        if (!literal && drawn.indexOf('<') >= 0) {
            var runs = dev.linqfy.bigCasares.modules.tideframe.engine.core.RichText.runs(drawn);
            var plain = new StringBuilder();
            for (var run : runs) plain.append(run.text());
            charColors = new int[plain.length()];
            int at = 0;
            for (var run : runs) {
                int argb = run.color() == null ? 0 : 0xff000000 | Integer.parseInt(run.color().substring(1), 16);
                for (int k = 0; k < run.text().length(); k++) charColors[at++] = argb;
            }
            drawn = plain.toString();
        }
        var lines = textLines(drawn, element.style().wrapWidth(), textScale);
        int[] starts = lineStarts(drawn, element.style().wrapWidth(), textScale);
        int blockHeight = lines.size() * 8 * textScale - textScale;
        int textTop = (int)Math.round((logicalCenterY - blockHeight / 2 - 1) * resolution) + offsetY;
        int textColor = color(hover && !element.background() && element.image() == null && element.style().hoverColor() != null
                ? element.style().hoverColor() : element.style().textColor(), 255, 1);
        // Live text and bars are drawn at runtime with their own fonts (see LiveText); the canvas leaves them out.
        boolean live = dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.isLive(element) || dev.linqfy.bigCasares.modules.tideframe.engine.core.LiveText.isBar(element);
        int logicalLeft = logicalCenterX - logicalWidth / 2, logicalTop = logicalCenterY - logicalHeight / 2;
        int clipLeft = (int)Math.round(logicalLeft * resolution) + offsetX, clipRight = (int)Math.round((logicalLeft + logicalWidth) * resolution) + offsetX;
        int clipTop = (int)Math.round(logicalTop * resolution) + offsetY, clipBottom = (int)Math.round((logicalTop + logicalHeight) * resolution) + offsetY;
        // Decorated text may spill its outline/shadow a little past the box, never past the canvas.
        int spill = (int)Math.ceil((element.style().outlineThickness() + (element.style().textShadow() ? textScale : 0)) * resolution);
        if (element.style().isStyledText()) { clipLeft -= spill; clipTop -= spill; clipRight += spill; clipBottom += spill; }
        boolean textual = !live && !element.kind().equals("decoration");
        // Schema 9: lines wider than the box are drawn smaller, past half size cut with "…" (TextFit), as live text is.
        int size = dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.FULL;
        if (textual && fitText) {
            var fitting = dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.lines(element, drawn);
            int widest = dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.widest(fitting);
            if (!fitting.isEmpty() && widest > dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.maxChars(element, size)) {
                size = dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.size(element, widest);
                int max = dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.maxChars(element, size);
                lines = lines.stream().map(line -> dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.cut(line, max)).toList();
            }
        }
        if (textual && size != dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.FULL) {
            // Shrunk lines keep the block's centre; each font pixel is size / 4 logical px, on the live text's grid.
            int pad = element.style().outlineThickness(), topQuarters = 4 * (logicalCenterY - blockHeight / 2 - 1) + 2 * blockHeight
                    - (lines.size() * 8 - 1) * textScale * size / 2;
            for (int line = 0; line < lines.size(); line++) {
                String drawnLine = lines.get(line);
                int cellLeft = dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.cellLeft(element, drawnLine.length(), size, pad), cellTop = topQuarters + line * 8 * textScale * size - pad * size;
                var mask = TextMask.of(drawnLine, element.style());
                int[] lineColors = charColors == null ? null : java.util.Arrays.copyOfRange(charColors, starts[line], starts[line] + drawnLine.length());
                for (int y = 0; y < mask.height(); y++) for (int x = 0; x < mask.width(); x++) {
                    int argb = mask.color(x, y, textColor, element.style(), lineColors);
                    if (argb == 0) continue;
                    int fromX = (int)Math.round((cellLeft + x * size / 4.0) * resolution) + offsetX, toX = (int)Math.round((cellLeft + (x + 1) * size / 4.0) * resolution) + offsetX;
                    int fromY = (int)Math.round((cellTop + y * size) / 4.0 * resolution) + offsetY, toY = (int)Math.round((cellTop + (y + 1) * size) / 4.0 * resolution) + offsetY;
                    for (int targetY = fromY; targetY < toY; targetY++) for (int targetX = fromX; targetX < toX; targetX++)
                        if (targetX >= clipLeft && targetX < clipRight && targetY >= clipTop && targetY < clipBottom
                                && targetX >= 0 && targetX < canvasWidth && targetY >= 0 && targetY < canvasHeight)
                            blend(canvas, targetX, targetY, argb);
                }
            }
        }
        for (int line = 0; textual && size == dev.linqfy.bigCasares.modules.tideframe.engine.core.TextFit.FULL && line < lines.size(); line++) {
            String text = lines.get(line); int textWidth = Math.max(0, (text.length() * 6 - 1) * textScale);
            // Every alignment snaps to the logical pixel grid (crisp strokes; live text uses the same grid).
            int logicalTextLeft = switch (element.style().textAlign()) {
                case "left" -> logicalLeft;
                case "right" -> logicalLeft + logicalWidth - textWidth;
                default -> logicalCenterX - textWidth / 2;
            };
            int textLeft = (int)Math.round(logicalTextLeft * resolution) + offsetX;
            int lineTop = textTop + (int)Math.round(line * 8 * textScale * resolution);
            var mask = TextMask.of(text, element.style());
            int[] lineColors = charColors == null ? null : java.util.Arrays.copyOfRange(charColors, starts[line], starts[line] + text.length());
            for (int y = 0; y < mask.height(); y++) for (int x = 0; x < mask.width(); x++) {
                int argb = mask.color(x, y, textColor, element.style(), lineColors);
                if (argb == 0) continue;
                int lx = x - mask.pad(), ly = y - mask.pad();
                for (int targetY = lineTop + (int)Math.round(ly * resolution); targetY < lineTop + (int)Math.round((ly + 1) * resolution); targetY++)
                    for (int targetX = textLeft + (int)Math.round(lx * resolution); targetX < textLeft + (int)Math.round((lx + 1) * resolution); targetX++)
                        if (targetX >= clipLeft && targetX < clipRight && targetY >= clipTop && targetY < clipBottom
                                && targetX >= 0 && targetX < canvasWidth && targetY >= 0 && targetY < canvasHeight)
                            blend(canvas, targetX, targetY, argb);
            }
        }
        // An explicit outline belongs above all content, including opaque custom image pixels.
        if (element.border() && element.style().outlineWidth() > 0) for (int py = Math.max(0, top); py < Math.min(canvasHeight, top + height); py++)
            for (int px = Math.max(0, left); px < Math.min(canvasWidth, left + width); px++) {
                int localX = px - left, localY = py - top;
                blend(canvas, px, py, covered(outline, coverage(element.shape(), localX, localY, width, height, true,
                        resolution * element.style().outlineWidth(), element.style().radius())));
            }
        if (opacityGroup != null) opacityGroup.finish(canvas, element.style().opacity(), element.style().brightness(), element.style().blur() * resolution);
    }

    /** Character wrapping is deterministic on the authored6logical-pixel advance; explicit empty lines are retained. */
    private static List<String> textLines(String text, int wrapWidth, int textScale) {
        var lines = new ArrayList<String>(); int columns = wrapWidth == 0 ? Integer.MAX_VALUE : Math.max(1, (wrapWidth + textScale) / (6 * textScale));
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) lines.add("");
            else for (int start = 0; start < line.length(); start += Math.min(columns, line.length()))
                lines.add(line.substring(start, Math.min(line.length(), start + columns)));
        }
        return lines;
    }

    /** Where each line of {@link #textLines} starts inside {@code text}. */
    private static int[] lineStarts(String text, int wrapWidth, int textScale) {
        var starts = new ArrayList<Integer>(); int columns = wrapWidth == 0 ? Integer.MAX_VALUE : Math.max(1, (wrapWidth + textScale) / (6 * textScale));
        int offset = 0;
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) starts.add(offset);
            else for (int start = 0; start < line.length(); start += Math.min(columns, line.length())) starts.add(offset + start);
            offset += line.length() + 1;
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Own 4x4 subpixel area coverage; no native graphics driver or font rasterizer is involved. */
    private static int coverage(String shape, int x, int y, int width, int height, boolean border, double inset, double radiusFraction) {
        if (shape.equals("rectangle"))
            return !border || x < inset || y < inset || x >= width - inset || y >= height - inset ? 255 : 0;
        double radius = Math.min(width, height) * radiusFraction;
        int count = 0;
        for (int sy = 0; sy < COVERAGE_SAMPLES; sy++) for (int sx = 0; sx < COVERAGE_SAMPLES; sx++) {
            double px = x + (sx + .5) / COVERAGE_SAMPLES;
            double py = y + (sy + .5) / COVERAGE_SAMPLES;
            if (inside(shape, px, py, width, height, radius)
                    && (!border || !inside(shape, px - inset, py - inset, width - 2 * inset,
                    height - 2 * inset, Math.max(0, radius - inset)))) count++;
        }
        return (count * 255 + COVERAGE_SAMPLES * COVERAGE_SAMPLES / 2) / (COVERAGE_SAMPLES * COVERAGE_SAMPLES);
    }

    /** Position along a gradient (0 = fillColor, 1 = gradientColor) for a pixel of a width x height box. */
    static double gradientT(String direction, int x, int y, int width, int height) {
        double u = width <= 1 ? 0 : x / (double)(width - 1), v = height <= 1 ? 0 : y / (double)(height - 1);
        return switch (direction) {
            case "horizontal" -> u;
            case "diagonal" -> (u + v) / 2;
            case "radial" -> Math.min(1, Math.hypot(u - .5, v - .5) / Math.sqrt(.5));
            default -> v;
        };
    }
    /** Channel-wise blend of two ARGB colours (alpha too). */
    static int mix(int from, int to, double t) {
        int a = (int)Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = (int)Math.round((from >>> 16 & 255) + ((to >>> 16 & 255) - (from >>> 16 & 255)) * t);
        int g = (int)Math.round((from >>> 8 & 255) + ((to >>> 8 & 255) - (from >>> 8 & 255)) * t);
        int b = (int)Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int color(String hex, int alpha, double opacity) {
        return Integer.parseInt(hex.substring(1), 16) | (int)Math.round(alpha * opacity) << 24;
    }
    /** Authored brightness changes this element only; alpha and underlying layers are preserved. */
    private static int brightness(int color, double brightness) {
        if (brightness == 1) return color;
        int red = Math.min(255, (int)Math.round(((color >>> 16) & 255) * brightness));
        int green = Math.min(255, (int)Math.round(((color >>> 8) & 255) * brightness));
        int blue = Math.min(255, (int)Math.round((color & 255) * brightness));
        return (color & 0xff000000) | red << 16 | green << 8 | blue;
    }
    private static int opacity(int color, double opacity) {
        return (color & 0xffffff) | (int)Math.round((color >>> 24) * opacity) << 24;
    }

    /** Logical pixels an element may paint outside its box (shadow, hover glow, decorated text). */
    private static int margin(Screen.Element element, boolean hover) {
        var style = element.style();
        int margin = style.shadow() ? style.shadowBlur() > 0 ? 3 * style.shadowBlur() + SHADOW_DROP + 1 : 6 : 1;
        if (hover && style.hoverEffect().equals("glow")) margin = Math.max(margin, GLOW_SPREAD + 1);
        if (style.isStyledText()) margin = Math.max(margin, style.outlineThickness() + (style.textShadow() ? style.textScale() : 0) + 1);
        // A blurred element spreads its own content (shadow and glow included) three standard deviations further.
        return margin + (style.blur() > 0 ? 3 * style.blur() + 1 : 0);
    }

    static final int GLOW_SPREAD = 6;
    /** Soft exterior halo in the hover colour; quadratic falloff over {@link #GLOW_SPREAD} logical pixels. */
    private static void glow(BufferedImage canvas, String shape, int left, int top, int width, int height,
                             double resolution, int color, double radiusFraction) {
        double spread = GLOW_SPREAD * resolution, halfWidth = width / 2.0, halfHeight = height / 2.0;
        double centerX = left + halfWidth, centerY = top + halfHeight;
        double radius = shape.equals("rounded") ? Math.min(width, height) * radiusFraction : 0;
        int reach = (int)Math.ceil(spread);
        for (int y = Math.max(0, top - reach); y < Math.min(canvas.getHeight(), top + height + reach); y++)
            for (int x = Math.max(0, left - reach); x < Math.min(canvas.getWidth(), left + width + reach); x++) {
                double px = Math.abs(x + .5 - centerX), py = Math.abs(y + .5 - centerY), distance;
                if (shape.equals("ellipse")) {
                    double nx = px / halfWidth, ny = py / halfHeight;
                    distance = (Math.sqrt(nx * nx + ny * ny) - 1) * Math.min(halfWidth, halfHeight);
                } else {
                    double qx = px - (halfWidth - radius), qy = py - (halfHeight - radius);
                    distance = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - radius;
                }
                if (distance <= 0 || distance >= spread) continue;
                double falloff = 1 - distance / spread;
                int alpha = (int)Math.round(150 * falloff * falloff);
                if (alpha > 0) blend(canvas, x, y, (color & 0xffffff) | alpha << 24);
            }
    }

    /** Own exterior drop shadow: five shifted silhouettes give a soft edge without a native graphics context. */
    private static void shadow(BufferedImage canvas, String shape, int left, int top, int width, int height,
                               double resolution, double opacity, double radiusFraction) {
        int spread = Math.max(1, (int)Math.ceil(3 * resolution));
        double radius = Math.min(width, height) * radiusFraction;
        for (int y = Math.max(0, top - spread); y < Math.min(canvas.getHeight(), top + height + 2 * spread); y++)
            for (int x = Math.max(0, left - spread); x < Math.min(canvas.getWidth(), left + width + 2 * spread); x++) {
                if (inside(shape, x - left + .5, y - top + .5, width, height, radius)) continue;
                int samples = 0;
                for (int offset = -2; offset <= 2; offset++) {
                    double dx = resolution * offset, dy = resolution * (2 + offset);
                    if (inside(shape, x - left + .5 - dx, y - top + .5 - dy, width, height, radius)) samples++;
                }
                if (samples > 0) blend(canvas, x, y, (int)Math.round(48 * opacity * samples / 5.0) << 24);
            }
    }

    /** Logical pixels a blurred shadow is dropped below its element. */
    static final int SHADOW_DROP = 2;
    /** Opacity of a blurred shadow's silhouette before blurring (about half of it remains at the element's edge). */
    static final int SHADOW_ALPHA = 96;
    /**
     * Drop shadow with a Gaussian edge: the shape's silhouette, dropped {@link #SHADOW_DROP} logical pixels, blurred by
     * {@code blur} logical pixels and drawn only outside the element (a translucent fill never darkens itself).
     */
    private static void blurredShadow(BufferedImage canvas, String shape, int left, int top, int width, int height,
                                      double resolution, double radiusFraction, int blur) {
        double sigma = blur * resolution, radius = Math.min(width, height) * radiusFraction;
        int drop = (int)Math.round(SHADOW_DROP * resolution), reach = (int)Math.ceil(3 * sigma) + 1;
        int x0 = Math.max(0, left - reach), y0 = Math.max(0, top + drop - reach);
        int x1 = Math.min(canvas.getWidth(), left + width + reach), y1 = Math.min(canvas.getHeight(), top + height + drop + reach);
        if (x1 <= x0 || y1 <= y0) return;
        int areaWidth = x1 - x0, areaHeight = y1 - y0;
        int[] mask = new int[areaWidth * areaHeight];
        for (int y = 0; y < areaHeight; y++) for (int x = 0; x < areaWidth; x++)
            if (inside(shape, x0 + x - left + .5, y0 + y - top - drop + .5, width, height, radius)) mask[y * areaWidth + x] = SHADOW_ALPHA << 24;
        Blur.gaussian(mask, areaWidth, areaHeight, sigma);
        for (int y = 0; y < areaHeight; y++) for (int x = 0; x < areaWidth; x++) {
            if (inside(shape, x0 + x - left + .5, y0 + y - top + .5, width, height, radius)) continue;
            blend(canvas, x0 + x, y0 + y, mask[y * areaWidth + x]);
        }
    }

    /**
     * Frosted glass: inside the element's shape, what is already drawn underneath (plus {@code under}, the static canvas
     * a runtime layer is shown over) is replaced by a blurred copy of itself. Uses the element's box moved and scaled
     * like the element (hover effect, keyframe pose); rotation is not applied.
     */
    private static void backdrop(BufferedImage canvas, BufferedImage under, Screen.Element element, boolean hover, boolean legacy,
                                 dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.Motion.Pose pose) {
        var box = geometry(element, legacy, canvas.getWidth(), canvas.getHeight());
        double resolution = canvas.getWidth() / (double)LOGICAL_WIDTH;
        double scale = (hover && element.style().hoverEffect().equals("scale") ? 1.05 : 1) * (pose == null ? 1 : pose.scale());
        double shiftX = pose == null ? 0 : pose.x() * resolution;
        double shiftY = (hover && element.style().hoverEffect().equals("translate") ? -2 * resolution : 0) + (pose == null ? 0 : pose.y() * resolution);
        int width = Math.max(1, (int)Math.round(box.width() * scale)), height = Math.max(1, (int)Math.round(box.height() * scale));
        int left = (int)Math.round(box.centerX() + shiftX - width / 2.0), top = (int)Math.round(box.centerY() + shiftY - height / 2.0);
        double sigma = element.style().backdropBlur() * resolution;
        int reach = (int)Math.ceil(3 * sigma) + 1, canvasWidth = canvas.getWidth();
        int x0 = Math.max(0, left - reach), y0 = Math.max(0, top - reach);
        int x1 = Math.min(canvasWidth, left + width + reach), y1 = Math.min(canvas.getHeight(), top + height + reach);
        if (x1 <= x0 || y1 <= y0) return;
        int areaWidth = x1 - x0, areaHeight = y1 - y0;
        int[] target = pixels(canvas), below = under == null ? null : pixels(under), area = new int[areaWidth * areaHeight];
        for (int y = 0; y < areaHeight; y++) for (int x = 0; x < areaWidth; x++) {
            int index = (y0 + y) * canvasWidth + x0 + x;
            area[y * areaWidth + x] = below == null ? target[index] : over(target[index], below[index]);
        }
        Blur.gaussian(area, areaWidth, areaHeight, sigma);
        for (int py = Math.max(0, top); py < Math.min(canvas.getHeight(), top + height); py++)
            for (int px = Math.max(0, left); px < Math.min(canvasWidth, left + width); px++) {
                int coverage = coverage(element.shape(), px - left, py - top, width, height, false, 0, element.style().radius());
                if (coverage == 0) continue;
                int index = py * canvasWidth + px;
                target[index] = replace(target[index], area[(py - y0) * areaWidth + px - x0], coverage);
            }
    }
    /** {@code under} replaced by {@code over} on coverage/255 of the pixel, in premultiplied alpha (no dark fringes). */
    static int replace(int under, int over, int coverage) {
        if (coverage >= 255) return over;
        double t = coverage / 255.0, underAlpha = under >>> 24, overAlpha = over >>> 24;
        double alpha = underAlpha * (1 - t) + overAlpha * t;
        if (alpha < .5) return 0;
        int out = (int)Math.round(alpha) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double value = (((under >>> shift) & 255) * underAlpha * (1 - t) + ((over >>> shift) & 255) * overAlpha * t) / alpha;
            out |= Math.min(255, (int)Math.round(value)) << shift;
        }
        return out;
    }

    private static int covered(int color, int coverage) {
        return (color & 0xffffff) | ((((color >>> 24) * coverage + 127) / 255) << 24);
    }

    /** Bilinear pixel-center sampling in premultiplied RGBA prevents transparent RGB from producing halos. */
    private static int bilinear(BufferedImage image, double x, double y) {
        x = Math.max(0, Math.min(image.getWidth() - 1, x));
        y = Math.max(0, Math.min(image.getHeight() - 1, y));
        int left = (int)Math.floor(x), top = (int)Math.floor(y);
        int right = Math.min(image.getWidth() - 1, left + 1), bottom = Math.min(image.getHeight() - 1, top + 1);
        double fx = x - left, fy = y - top;
        int[] pixels = pixels(image);
        int c00 = pixels[top * image.getWidth() + left], c10 = pixels[top * image.getWidth() + right];
        int c01 = pixels[bottom * image.getWidth() + left], c11 = pixels[bottom * image.getWidth() + right];
        double a00 = (c00 >>> 24) * (1 - fx) * (1 - fy), a10 = (c10 >>> 24) * fx * (1 - fy);
        double a01 = (c01 >>> 24) * (1 - fx) * fy, a11 = (c11 >>> 24) * fx * fy;
        double total = a00 + a10 + a01 + a11;
        int alpha = (int)Math.round(total);
        if (alpha == 0) return 0;
        return alpha << 24
                | filteredChannel(c00, c10, c01, c11, 16, a00, a10, a01, a11, total) << 16
                | filteredChannel(c00, c10, c01, c11, 8, a00, a10, a01, a11, total) << 8
                | filteredChannel(c00, c10, c01, c11, 0, a00, a10, a01, a11, total);
    }

    private static int filteredChannel(int c00, int c10, int c01, int c11, int shift,
                                       double a00, double a10, double a01, double a11, double total) {
        return (int)Math.round((((c00 >>> shift) & 255) * a00 + ((c10 >>> shift) & 255) * a10
                + ((c01 >>> shift) & 255) * a01 + ((c11 >>> shift) & 255) * a11) / total);
    }

    private static boolean inside(String shape, double px, double py, double width, double height, double radius) {
        if (px < 0 || py < 0 || px >= width || py >= height || width <= 0 || height <= 0) return false;
        return switch (shape) {
            case "rectangle" -> true;
            case "ellipse" -> {
                double dx = (px - width / 2.0) / (width / 2.0), dy = (py - height / 2.0) / (height / 2.0);
                yield dx * dx + dy * dy <= 1;
            }
            case "rounded" -> {
                double dx = Math.max(radius - px, Math.max(0, px - (width - radius)));
                double dy = Math.max(radius - py, Math.max(0, py - (height - radius)));
                yield dx * dx + dy * dy <= radius * radius;
            }
            default -> throw new IllegalArgumentException("Unsupported HUD shape");
        };
    }

    /** Straight-alpha source-over; user image RGB is preserved exactly over transparent pixels. */
    private static void blend(BufferedImage image, int x, int y, int source) {
        if (source >>> 24 == 0) return;
        int[] pixels = pixels(image); int index = y * image.getWidth() + x;
        pixels[index] = over(source, pixels[index]);
    }
    /** {@code source} over {@code target}, straight alpha (the same arithmetic {@link #blend} has always used). */
    static int over(int source, int target) {
        int sourceAlpha = source >>> 24, targetAlpha = target >>> 24;
        if (sourceAlpha == 0) return target;
        if (sourceAlpha == 255 || targetAlpha == 0) return source;
        int alphaNumerator = sourceAlpha * 255 + targetAlpha * (255 - sourceAlpha);
        int alpha = (alphaNumerator + 127) / 255;
        int color = alpha << 24;
        color |= channel(source, target, 16, sourceAlpha, targetAlpha, alphaNumerator) << 16;
        color |= channel(source, target, 8, sourceAlpha, targetAlpha, alphaNumerator) << 8;
        color |= channel(source, target, 0, sourceAlpha, targetAlpha, alphaNumerator);
        return color;
    }

    private static int[] pixels(BufferedImage image) { return ((DataBufferInt)image.getRaster().getDataBuffer()).getData(); }

    /**
     * 9-slice (style "slice"): the image fills the box; its outer {@code slice} pixels keep their size ({@code scale} image
     * pixels per logical pixel), the edges stretch along the box and the centre both ways. Nearest pixels, like a pixel-art
     * frame. When the corners do not fit, all four shrink by the same factor (as CSS border-image does in the editor).
     */
    static void sliced(BufferedImage canvas, BufferedImage image, int left, int top, int width, int height, int slice, int scale, double resolution) {
        sliced(canvas, image, left, top, width, height, slice, scale, resolution, null, 0);
    }
    /** {@code clipShape}: the element's shape the picture is cut to (antialiased like the fill), or null for none. */
    static void sliced(BufferedImage canvas, BufferedImage image, int left, int top, int width, int height, int slice, int scale, double resolution,
                       String clipShape, double radius) {
        double corner = slice / (double) scale * resolution;
        corner *= Math.min(1, Math.min(width / (2 * corner), height / (2 * corner)));
        int[] source = pixels(image);
        int imageWidth = image.getWidth(), imageHeight = image.getHeight();
        for (int y = 0; y < height; y++) {
            int ty = top + y;
            if (ty < 0 || ty >= canvas.getHeight()) continue;
            int sy = slicedSource(y, height, corner, slice, imageHeight);
            for (int x = 0; x < width; x++) {
                int tx = left + x;
                if (tx < 0 || tx >= canvas.getWidth()) continue;
                int pixel = source[sy * imageWidth + slicedSource(x, width, corner, slice, imageWidth)];
                if (clipShape != null) pixel = covered(pixel, coverage(clipShape, x, y, width, height, false, 0, radius));
                blend(canvas, tx, ty, pixel);
            }
        }
    }
    /** Image row or column for box pixel {@code at} of {@code size}: corner, stretched middle, or far corner. */
    static int slicedSource(int at, int size, double corner, int slice, int imageSize) {
        double centre = at + .5;
        if (centre < corner) return Math.min(slice - 1, (int) (centre * slice / corner));
        if (centre >= size - corner) return Math.min(imageSize - 1, imageSize - slice + (int) ((centre - (size - corner)) * slice / corner));
        double middle = size - 2 * corner, stretched = imageSize - 2 * slice;
        return Math.min(imageSize - slice - 1, slice + (int) ((centre - corner) * stretched / middle));
    }

    private static int channel(int source, int target, int shift, int sourceAlpha, int targetAlpha, int alphaNumerator) {
        return (((source >>> shift) & 255) * sourceAlpha * 255
                + ((target >>> shift) & 255) * targetAlpha * (255 - sourceAlpha) + alphaNumerator / 2) / alphaNumerator;
    }

    private static BufferedImage decode(ImageAsset image) {
        var readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) throw new IllegalStateException("PNG decoder unavailable");
        var reader = readers.next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(image.png()))) {
            reader.setInput(input, true, true);
            var decoded = reader.read(0);
            var rgba = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_ARGB);
            rgba.setRGB(0, 0, rgba.getWidth(), rgba.getHeight(), decoded.getRGB(0, 0, rgba.getWidth(), rgba.getHeight(), null, 0, rgba.getWidth()),
                    0, rgba.getWidth());
            return rgba;
        } catch (IOException failure) { throw new IllegalArgumentException("Validated PNG could not be decoded", failure); }
        finally { reader.dispose(); }
    }
}
