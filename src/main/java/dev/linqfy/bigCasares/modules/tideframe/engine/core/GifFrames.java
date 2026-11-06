package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Animated GIF to the PNG frames an animated element plays. Each GIF frame is composited over the previous ones the
 * way browsers do (frame offsets, transparency and the none / keep / restore-to-background / restore-to-previous
 * disposal modes), then the GIF's own timeline is sampled at a fixed interval, because a runtime layer advances once
 * per interval. Delays of 0 or 10 ms play as 100 ms, like browsers. CPU work: call on a worker.
 */
public final class GifFrames {
    /** Largest GIF file accepted. */
    public static final int MAX_GIF_BYTES = 33_554_432;
    /** GIF frames read at most; later frames are ignored (the result says so). */
    public static final int MAX_SOURCE_FRAMES = 1024;
    /** Shortest interval a runtime layer can show (one server tick). */
    public static final int TICK_MS = 50;
    private GifFrames() { }

    /**
     * @param frames     composited frames, one per {@code intervalMs}, at most {@code maxFrames}; consecutive samples of the
     *                   same GIF frame share one asset (and so one texture in the pack)
     * @param intervalMs interval the frames were sampled at
     * @param sourceFrames GIF frames read; {@code truncated} when the file had more than {@link #MAX_SOURCE_FRAMES}
     * @param durationMs length of one loop of the GIF
     */
    public record Result(List<ImageAsset> frames, int intervalMs, int sourceFrames, boolean truncated, long durationMs) {
        public Result { frames = List.copyOf(frames); }
        /** Distinct images among the frames (what the pack stores). */
        public long distinct() { return frames.stream().map(ImageAsset::sha256).distinct().count(); }
    }

    /** True when the bytes start with a GIF signature. */
    public static boolean isGif(byte[] bytes) {
        return bytes != null && bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
    }

    /**
     * @param requestedIntervalMs sampling interval (multiple of {@link #TICK_MS} works best); 0 picks it from the GIF:
     *                            its shortest delay rounded to whole ticks
     * @param maxFrames           most frames to return; a GIF longer than {@code maxFrames} intervals is sampled
     *                            at a longer interval instead of being cut short
     */
    public static Result decode(byte[] gif, int requestedIntervalMs, int maxFrames) {
        if (gif == null || gif.length > MAX_GIF_BYTES) throw invalid("GIF exceeds " + MAX_GIF_BYTES / 1_048_576 + " MiB or is absent");
        if (!isGif(gif)) throw invalid("Not a GIF file");
        if (requestedIntervalMs != 0 && (requestedIntervalMs < TICK_MS || requestedIntervalMs > 60_000)) throw invalid("Frame interval must be 50 to 60000 ms");
        if (maxFrames < 1) throw invalid("At least one frame is needed");
        var readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IllegalStateException("GIF decoder unavailable");
        ImageReader reader = readers.next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(gif))) {
            reader.setInput(input, false, false);
            var screen = child(root(reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0")), "LogicalScreenDescriptor");
            int width = screen == null ? 0 : integer(screen, "logicalScreenWidth"), height = screen == null ? 0 : integer(screen, "logicalScreenHeight");
            // Pass 1, metadata only (no pixels decoded): each frame's placement, disposal and delay.
            var frameInfo = new ArrayList<Placement>();
            boolean truncated = false;
            for (int index = 0; ; index++) {
                if (index == MAX_SOURCE_FRAMES) { truncated = hasFrame(reader, index); break; }
                javax.imageio.metadata.IIOMetadata meta;
                try { meta = reader.getImageMetadata(index); } catch (IndexOutOfBoundsException end) { break; }
                var metadata = root(meta.getAsTree("javax_imageio_gif_image_1.0"));
                var descriptor = child(metadata, "ImageDescriptor");
                if (descriptor == null) throw invalid("GIF frame " + index + " has no image descriptor");
                var control = child(metadata, "GraphicControlExtension");
                int delay = control == null ? 0 : integer(control, "delayTime") * 10;
                var placement = new Placement(integer(descriptor, "imageLeftPosition"), integer(descriptor, "imageTopPosition"),
                        integer(descriptor, "imageWidth"), integer(descriptor, "imageHeight"),
                        control == null ? "none" : control.getAttribute("disposalMethod"), delay <= 10 ? 100 : delay);
                // Refuse a frame that claims a huge area before any of its pixels are decoded.
                if ((long) placement.width() * placement.height() > ImageAsset.MAX_PIXELS) throw invalid("GIF frame " + index + " is larger than " + ImageAsset.MAX_PIXELS + " pixels");
                frameInfo.add(placement);
            }
            if (frameInfo.isEmpty()) throw invalid("GIF has no frames");
            // A GIF without a usable logical screen takes the first frame's extent.
            if (width < 1 || height < 1) { width = frameInfo.get(0).left() + frameInfo.get(0).width(); height = frameInfo.get(0).top() + frameInfo.get(0).height(); }
            if (width < 1 || height < 1 || width > ImageAsset.MAX_SIDE || height > ImageAsset.MAX_SIDE || (long) width * height > ImageAsset.MAX_PIXELS)
                throw invalid("GIF must be 1 to " + ImageAsset.MAX_SIDE + " px per side and at most " + ImageAsset.MAX_PIXELS + " pixels");
            var delays = frameInfo.stream().map(Placement::delayMs).toList();
            long duration = delays.stream().mapToLong(Integer::longValue).sum();
            int interval = requestedIntervalMs != 0 ? requestedIntervalMs
                    : Math.max(TICK_MS, (int) Math.round(delays.stream().mapToInt(Integer::intValue).min().orElse(100) / (double) TICK_MS) * TICK_MS);
            if (delays.size() > 1 && (duration + interval - 1) / interval > maxFrames)
                interval = (int) Math.min(60_000, ((duration + maxFrames - 1) / maxFrames + TICK_MS - 1) / TICK_MS * TICK_MS);
            int count = delays.size() == 1 ? 1 : (int) Math.max(1, Math.min(maxFrames, (duration + interval - 1) / interval));
            int[] sources = new int[count];
            for (int step = 0; step < count; step++) sources[step] = frameAt(delays, step * (long) interval);
            var needed = new java.util.BitSet();
            for (int source : sources) needed.set(source);
            // Pass 2: composite in order up to the last frame shown, keeping only the shown frames (as PNG).
            var assets = new ImageAsset[frameInfo.size()];
            int[] canvas = new int[width * height], previous = null;
            for (int index = 0; index <= needed.length() - 1; index++) {
                var placement = frameInfo.get(index);
                BufferedImage frame = reader.read(index);
                int left = placement.left(), top = placement.top();
                if (placement.disposal().equals("restoreToPrevious")) previous = canvas.clone();
                int[] pixels = frame.getRGB(0, 0, frame.getWidth(), frame.getHeight(), null, 0, frame.getWidth());
                for (int y = 0; y < frame.getHeight(); y++) for (int x = 0; x < frame.getWidth(); x++) {
                    int cx = left + x, cy = top + y, color = pixels[y * frame.getWidth() + x];
                    // GIF transparency is all or nothing: a transparent pixel leaves what is underneath.
                    if (cx < width && cy < height && (color >>> 24) != 0) canvas[cy * width + cx] = color | 0xff000000;
                }
                if (needed.get(index)) assets[index] = asset(canvas, width, height);
                if (placement.disposal().equals("restoreToBackgroundColor")) {
                    // Browsers clear to transparent rather than to the background colour.
                    for (int y = Math.max(0, top); y < Math.min(height, top + frame.getHeight()); y++)
                        Arrays.fill(canvas, y * width + Math.max(0, left), y * width + Math.min(width, left + frame.getWidth()), 0);
                } else if (placement.disposal().equals("restoreToPrevious") && previous != null) canvas = previous;
            }
            var frames = new ArrayList<ImageAsset>(count);
            for (int source : sources) frames.add(assets[source]);
            return new Result(frames, interval, frameInfo.size(), truncated, duration);
        } catch (IOException | RuntimeException failure) {
            if (failure instanceof IllegalArgumentException invalid) throw invalid;
            throw invalid("GIF could not be decoded: " + failure.getMessage());
        } finally { reader.dispose(); }
    }

    /** Where a GIF frame goes, what happens to it afterwards and how long it shows. */
    private record Placement(int left, int top, int width, int height, String disposal, int delayMs) { }

    /** GIF frame showing at {@code timeMs} into one loop. */
    static int frameAt(List<Integer> delays, long timeMs) {
        long elapsed = 0;
        for (int i = 0; i < delays.size(); i++) { elapsed += delays.get(i); if (timeMs < elapsed) return i; }
        return delays.size() - 1;
    }
    private static boolean hasFrame(ImageReader reader, int index) {
        try { reader.getImageMetadata(index); return true; } catch (IndexOutOfBoundsException | IOException end) { return false; }
    }
    private static ImageAsset asset(int[] pixels, int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        var out = new ByteArrayOutputStream();
        try { if (!ImageIO.write(image, "png", out)) throw new IllegalStateException("PNG encoder unavailable"); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return ImageAsset.fromPng(out.toByteArray());
    }
    private static IIOMetadataNode root(org.w3c.dom.Node node) { return (IIOMetadataNode) node; }
    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node.getNodeName().equals(name)) return (IIOMetadataNode) node;
        return null;
    }
    private static int integer(IIOMetadataNode node, String attribute) {
        try { return Integer.parseInt(node.getAttribute(attribute)); }
        catch (NumberFormatException malformed) { throw invalid("GIF metadata is malformed"); }
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
