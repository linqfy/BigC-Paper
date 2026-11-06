package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GifFramesTest {
    private static final int RED = 0xffff0000, GREEN = 0xff00ff00, BLUE = 0xff0000ff, CLEAR = -1;

    /** One GIF frame: a block of {@code colors} (CLEAR = transparent) at an offset, with its delay and disposal. */
    private record Frame(int left, int top, int width, int height, int delayCs, String disposal, int... colors) {
        static Frame solid(int width, int height, int color, int delayCs) { return new Frame(0, 0, width, height, delayCs, "none", fill(width * height, color)); }
        static int[] fill(int n, int color) { int[] c = new int[n]; java.util.Arrays.fill(c, color); return c; }
    }

    /** Writes a real animated GIF with javax.imageio (palette: transparent, red, green, blue). */
    private static byte[] gif(int width, int height, Frame... frames) throws Exception {
        byte[] r = {0, (byte) 255, 0, 0}, g = {0, 0, (byte) 255, 0}, b = {0, 0, 0, (byte) 255};
        var palette = new IndexColorModel(2, 4, r, g, b, 0);
        var writer = ImageIO.getImageWritersByFormatName("gif").next();
        var out = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            var streamMeta = writer.getDefaultStreamMetadata(null);
            var streamTree = (IIOMetadataNode) streamMeta.getAsTree("javax_imageio_gif_stream_1.0");
            IIOMetadataNode screen = null;
            for (var node = streamTree.getFirstChild(); node != null; node = node.getNextSibling())
                if (node.getNodeName().equals("LogicalScreenDescriptor")) screen = (IIOMetadataNode) node;
            if (screen == null) { screen = new IIOMetadataNode("LogicalScreenDescriptor"); streamTree.appendChild(screen); }
            screen.setAttribute("logicalScreenWidth", "" + width); screen.setAttribute("logicalScreenHeight", "" + height);
            screen.setAttribute("colorResolution", "8"); screen.setAttribute("pixelAspectRatio", "0");
            streamMeta.setFromTree("javax_imageio_gif_stream_1.0", streamTree);
            writer.prepareWriteSequence(streamMeta);
            for (var frame : frames) {
                var image = new BufferedImage(frame.width(), frame.height(), BufferedImage.TYPE_BYTE_INDEXED, palette);
                for (int y = 0; y < frame.height(); y++) for (int x = 0; x < frame.width(); x++) {
                    int color = frame.colors()[y * frame.width() + x];
                    image.getRaster().setSample(x, y, 0, color == RED ? 1 : color == GREEN ? 2 : color == BLUE ? 3 : 0);
                }
                var meta = writer.getDefaultImageMetadata(new ImageTypeSpecifier(image), null);
                var tree = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
                for (var node = tree.getFirstChild(); node != null; node = node.getNextSibling()) {
                    var element = (IIOMetadataNode) node;
                    if (element.getNodeName().equals("ImageDescriptor")) {
                        element.setAttribute("imageLeftPosition", "" + frame.left()); element.setAttribute("imageTopPosition", "" + frame.top());
                    } else if (element.getNodeName().equals("GraphicControlExtension")) {
                        element.setAttribute("disposalMethod", frame.disposal()); element.setAttribute("delayTime", "" + frame.delayCs());
                        element.setAttribute("transparentColorFlag", "TRUE"); element.setAttribute("transparentColorIndex", "0");
                    }
                }
                meta.setFromTree("javax_imageio_gif_image_1.0", tree);
                writer.writeToSequence(new IIOImage(image, null, meta), null);
            }
            writer.endWriteSequence();
        } finally { writer.dispose(); }
        return out.toByteArray();
    }

    private static int pixel(ImageAsset asset, int x, int y) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(asset.png())).getRGB(x, y);
    }

    @Test void theGifTimelineIsSampledAtItsShortestDelay() throws Exception {
        byte[] bytes = gif(4, 4, Frame.solid(4, 4, RED, 10), Frame.solid(4, 4, GREEN, 10), Frame.solid(4, 4, BLUE, 20));
        var auto = GifFrames.decode(bytes, 0, 64);
        assertEquals(100, auto.intervalMs()); assertEquals(400, auto.durationMs()); assertEquals(3, auto.sourceFrames());
        var colours = new ArrayList<Integer>();
        for (var frame : auto.frames()) colours.add(pixel(frame, 1, 1));
        assertEquals(List.of(RED, GREEN, BLUE, BLUE), colours, "the 200 ms frame holds for two intervals");
        assertEquals(3, auto.distinct(), "a held frame reuses its image");
        assertSame(auto.frames().get(2), auto.frames().get(3));
        var everyTick = GifFrames.decode(bytes, 50, 64);
        assertEquals(50, everyTick.intervalMs()); assertEquals(8, everyTick.frames().size()); assertEquals(3, everyTick.distinct());
    }

    @Test void framesAreCompositedWithOffsetsTransparencyAndRestoreToBackground() throws Exception {
        int[] patch = {GREEN, CLEAR, GREEN, GREEN};
        byte[] bytes = gif(4, 4, Frame.solid(4, 4, RED, 10),
                new Frame(2, 2, 2, 2, 10, "restoreToBackgroundColor", patch),
                new Frame(0, 0, 1, 1, 10, "none", BLUE));
        var frames = GifFrames.decode(bytes, 100, 64).frames();
        assertEquals(3, frames.size());
        assertEquals(GREEN, pixel(frames.get(1), 2, 2), "the patch lands at its offset");
        assertEquals(RED, pixel(frames.get(1), 3, 2), "a transparent GIF pixel keeps what is underneath");
        assertEquals(RED, pixel(frames.get(1), 1, 1));
        assertEquals(0, pixel(frames.get(2), 2, 2) >>> 24, "restore to background clears the patch area to transparent");
        assertEquals(0, pixel(frames.get(2), 3, 2) >>> 24);
        assertEquals(BLUE, pixel(frames.get(2), 0, 0));
        assertEquals(RED, pixel(frames.get(2), 1, 1), "outside the disposed area nothing changes");
    }

    @Test void restoreToPreviousUndoesOnlyThatFrame() throws Exception {
        byte[] bytes = gif(4, 4, Frame.solid(4, 4, RED, 10),
                new Frame(0, 0, 2, 2, 10, "restoreToPrevious", Frame.fill(4, GREEN)),
                new Frame(3, 3, 1, 1, 10, "none", BLUE));
        var frames = GifFrames.decode(bytes, 100, 64).frames();
        assertEquals(GREEN, pixel(frames.get(1), 0, 0));
        assertEquals(RED, pixel(frames.get(2), 0, 0), "the green block is undone");
        assertEquals(BLUE, pixel(frames.get(2), 3, 3));
    }

    @Test void aLongGifIsSampledMoreSparselyInsteadOfBeingCut() throws Exception {
        var many = new Frame[100];
        for (int i = 0; i < many.length; i++) many[i] = Frame.solid(2, 2, i % 2 == 0 ? RED : BLUE, 10);
        var result = GifFrames.decode(gif(2, 2, many), 0, 64);
        assertEquals(10_000, result.durationMs());
        assertTrue(result.frames().size() <= 64, "frames " + result.frames().size());
        assertEquals(0, result.intervalMs() % 50, "whole ticks");
        assertTrue(result.frames().size() * (long) result.intervalMs() >= result.durationMs(), "the whole loop is still covered");
        assertEquals(200, result.intervalMs());
    }

    @Test void zeroDelaysPlayLikeBrowsersAndOneFrameStaysOneFrame() throws Exception {
        var zero = GifFrames.decode(gif(2, 2, Frame.solid(2, 2, RED, 0), Frame.solid(2, 2, GREEN, 1)), 0, 64);
        assertEquals(100, zero.intervalMs()); assertEquals(2, zero.frames().size());
        var still = GifFrames.decode(gif(3, 2, Frame.solid(3, 2, BLUE, 50)), 0, 64);
        assertEquals(1, still.frames().size()); assertEquals(3, still.frames().get(0).width()); assertEquals(2, still.frames().get(0).height());
    }

    @Test void refusesWhatIsNotAUsableGif() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> GifFrames.decode(new byte[]{1, 2, 3, 4, 5, 6, 7}, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> GifFrames.decode(null, 0, 64));
        byte[] truncated = java.util.Arrays.copyOf(gif(2, 2, Frame.solid(2, 2, RED, 10)), 14);
        assertThrows(IllegalArgumentException.class, () -> GifFrames.decode(truncated, 0, 64));
        byte[] fine = gif(2, 2, Frame.solid(2, 2, RED, 10));
        assertThrows(IllegalArgumentException.class, () -> GifFrames.decode(fine, 30, 64), "intervals under one tick cannot play");
        assertThrows(IllegalArgumentException.class, () -> GifFrames.decode(new byte[GifFrames.MAX_GIF_BYTES + 1], 0, 64));
        assertTrue(GifFrames.isGif(fine)); assertFalse(GifFrames.isGif(new byte[]{'G', 'I', 'F'}));
    }

    @Test void aFrameClaimingAHugeAreaIsRefusedBeforeItsPixelsAreDecoded() throws Exception {
        // 2900 x 2900 = 8.41 million pixels, over the 8.29 million limit, inside a tiny logical screen.
        byte[] huge = gif(4, 4, Frame.solid(4, 4, RED, 10), new Frame(0, 0, 2900, 2900, 10, "none", Frame.fill(2900 * 2900, CLEAR)));
        var refused = assertThrows(IllegalArgumentException.class, () -> GifFrames.decode(huge, 0, 64));
        assertTrue(refused.getMessage().contains("frame 1"), refused.getMessage());
    }

    @Test void onlyTheFramesShownAreKeptAndLaterFramesAreNeverDecoded() throws Exception {
        // 64 frames of 50 ms (a 3.2 s loop) sampled every 3 s: only the frames showing at 0 ms and 3000 ms are kept.
        var many = new Frame[64];
        for (int i = 0; i < many.length; i++) many[i] = Frame.solid(2, 2, i < 60 ? RED : BLUE, 5);
        var result = GifFrames.decode(gif(2, 2, many), 3000, 64);
        assertEquals(2, result.frames().size()); assertEquals(RED, pixel(result.frames().get(0), 0, 0)); assertEquals(BLUE, pixel(result.frames().get(1), 0, 0));
        assertEquals(64, result.sourceFrames());
    }
}
