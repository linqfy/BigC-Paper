package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import dev.linqfy.bigCasares.modules.tideframe.engine.testing.PngFixtures;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class ImageAssetTest {
    @Test void grayscaleSamplesKeepTheirSrgbShadeInsteadOfImageIoLinearGrayConversion() throws Exception {
        assertPixels(PngFixtures.sampled(1, 1, 8, 0, new byte[]{0, (byte) 128}), 0xff808080);
        assertPixels(PngFixtures.sampled(1, 1, 16, 0, new byte[]{0, (byte) 128, (byte) 128}), 0xff808080);
        assertPixels(PngFixtures.sampled(2, 1, 1, 0, new byte[]{0, 0x40}), 0xff000000, 0xffffffff);
        assertPixels(PngFixtures.sampled(4, 1, 2, 0, new byte[]{0, 0x1b}), 0xff000000, 0xff555555, 0xffaaaaaa, 0xffffffff);
        assertPixels(PngFixtures.sampled(2, 1, 4, 0, new byte[]{0, 0x18}), 0xff111111, 0xff888888);
    }

    @Test void grayscaleAlphaAndTransparencyKeepOriginalShadeAndAlpha() throws Exception {
        assertPixels(PngFixtures.sampled(1, 1, 8, 4, new byte[]{0, (byte) 128, 127}), 0x7f808080);
        assertPixels(PngFixtures.sampled(1, 1, 16, 4, new byte[]{0, (byte) 128, (byte) 128, 127, 127}), 0x7f808080);
        assertPixels(PngFixtures.sampled(1, 1, 8, 0, new byte[]{0, (byte) 128},
                PngFixtures.chunk("tRNS", new byte[]{0, (byte) 128})), 0x00808080);
        assertPixels(PngFixtures.sampled(1, 1, 16, 0, new byte[]{0, (byte) 128, (byte) 128},
                PngFixtures.chunk("tRNS", new byte[]{(byte) 128, (byte) 128})), 0x00808080);
        assertPixels(PngFixtures.sampled(1, 1, 1, 0, new byte[]{0, (byte) 128},
                PngFixtures.chunk("tRNS", new byte[]{0, 1})), 0x00ffffff);
    }

    private static void assertPixels(byte[] png, int... expected) throws Exception {
        var image = ImageAsset.fromPng(png);
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(image.png()))) {
            var reader = ImageIO.getImageReadersByFormatName("png").next();
            try {
                reader.setInput(input, true, true); var decoded = reader.read(0);
                assertArrayEquals(expected, decoded.getRGB(0, 0, decoded.getWidth(), decoded.getHeight(), null, 0, decoded.getWidth()));
            } finally { reader.dispose(); }
        }
    }

    @Test void canonicalEncodingPreservesEveryRgbaPixelAndStripsMetadata() throws Exception {
        int[] pixels = {0x00abcdef, 0x7f123456, 0xffedcba9, 0x01020304};
        byte[] original = PngFixtures.rgba(2, 2, pixels);
        byte[] tagged = PngFixtures.beforeIend(original, "tEXt", "Note\0operator image".getBytes(StandardCharsets.US_ASCII));
        var image = ImageAsset.fromPng(tagged);
        var plain = ImageAsset.fromPng(original);
        assertEquals(2, image.width()); assertEquals(2, image.height());
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(image.png()))) {
            var reader = ImageIO.getImageReadersByFormatName("png").next();
            try {
                reader.setInput(input, true, true);
                var decoded = reader.read(0);
                assertArrayEquals(pixels, decoded.getRGB(0, 0, 2, 2, null, 0, 2));
            } finally { reader.dispose(); }
        }
        assertFalse(new String(image.png(), StandardCharsets.ISO_8859_1).contains("operator image"));
        assertEquals(plain, image); assertEquals(plain.hashCode(), image.hashCode());
        assertArrayEquals(plain.png(), image.png());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image.png())), image.sha256());
        assertEquals(image, ImageAsset.fromBase64(image.base64()));
        assertEquals(image, ImageAsset.fromPng(image.png()));
    }

    @Test void ownedBytesCannotBeChangedByCallerOrByAccessor() throws Exception {
        byte[] input = PngFixtures.rgba(1, 1, 0x7f123456);
        var image = ImageAsset.fromPng(input);
        String digest = image.sha256(); byte[] expected = image.png();
        Arrays.fill(input, (byte) 0);
        byte[] returned = image.png(); Arrays.fill(returned, (byte) 0);
        assertArrayEquals(expected, image.png()); assertEquals(digest, image.sha256());
        assertNotEquals(image, ImageAsset.fromPng(PngFixtures.rgba(1, 1, 0x7f123457)));
    }

    @Test void onlyCanonicalPaddedStandardBase64IsAccepted() throws Exception {
        byte[] png = PngFixtures.rgba(1, 1);
        while (png.length % 3 == 0) png = PngFixtures.beforeIend(png, "tEXt", new byte[]{'x', 0});
        String canonical = Base64.getEncoder().encodeToString(png);
        assertNotNull(ImageAsset.fromBase64(canonical));
        assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromBase64(canonical.stripTrailing() + "\n"));
        assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromBase64(" " + canonical));
        assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromBase64(canonical.replace("=", "")));
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        int lastData = canonical.indexOf('=') - 1;
        String nonCanonical = canonical.substring(0, lastData)
                + alphabet.charAt(alphabet.indexOf(canonical.charAt(lastData)) + 1) + canonical.substring(lastData + 1);
        assertArrayEquals(Base64.getDecoder().decode(canonical), Base64.getDecoder().decode(nonCanonical));
        assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromBase64(nonCanonical));
        assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromBase64("A".repeat(8_388_612)));
        assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromBase64(null));
        assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromBase64(""));
    }

    @Test void dimensionsAndInputLengthAreBoundedBeforeDecoding() throws Exception {
        byte[] small = PngFixtures.rgba(1, 1);
        for (int[] dimensions : new int[][]{{0, 1}, {1, 0}, {-1, 1}, {1, -1}, {3841, 1}, {1, 3841}, {3840, 2161}, {3000,3000}, {Integer.MAX_VALUE, 1}}) {
            byte[] malformed = PngFixtures.withDimensions(small, dimensions[0], dimensions[1]);
            assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromPng(malformed));
        }
        var largest = ImageAsset.fromPng(PngFixtures.rgba(3840, 2160));
        assertEquals(3840, largest.width()); assertEquals(2160, largest.height());
        assertEquals(3840, ImageAsset.MAX_SIDE); assertEquals(41_943_040, ImageAsset.MAX_PNG_BYTES);
        assertTrue(largest.png().length <= 41_943_040);
        assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromPng(new byte[41_943_041]));
        assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromPng(null));
    }

    @Test void largerLandscapeRetainsEveryPixelAndInputAbovePreviousBudgetIsNormalized() throws Exception {
        int[] colors = {0x00112233, 0xffb4e5f8, 0xff153c54, 0x8088c7b5};
        byte[] landscape = PngFixtures.rgba(1024, 576, colors);
        byte[] tagged = PngFixtures.beforeIend(landscape, "tEXt", new byte[300_000]);
        assertTrue(tagged.length > 262_144); assertTrue(tagged.length <= 6_291_456);
        var image = ImageAsset.fromPng(tagged);
        assertEquals(1024, image.width()); assertEquals(576, image.height());
        assertEquals(ImageAsset.fromPng(landscape), image);
        assertEquals(image, ImageAsset.fromBase64(Base64.getEncoder().encodeToString(tagged)));
        int[] expected = new int[1024 * 576];
        for (int i = 0; i < expected.length; i++) expected[i] = colors[i % colors.length];
        assertPixels(image.png(), expected);
    }

    @Test void malformedChunkBoundsCrcOrFileEndAreRejected() throws Exception {
        byte[] png = PngFixtures.rgba(2, 2);
        byte[] wrongSignature = png.clone(); wrongSignature[0] = 0;
        byte[] wrongCrc = png.clone(); wrongCrc[29] ^= 1;
        byte[] hugeChunk = png.clone(); ByteBuffer.wrap(hugeChunk, 33, 4).putInt(Integer.MAX_VALUE);
        byte[] signedChunk = png.clone(); ByteBuffer.wrap(signedChunk, 33, 4).putInt(-1);
        byte[] trailing = Arrays.copyOf(png, png.length + 1);
        byte[][] malformed = {new byte[0], new byte[32], wrongSignature, wrongCrc, hugeChunk, signedChunk,
                Arrays.copyOf(png, png.length - 1), Arrays.copyOf(png, png.length - 12), trailing,
                PngFixtures.beforeIend(png, "IEND", new byte[0]),
                PngFixtures.beforeIend(png, "IHDR", new byte[13]),
                PngFixtures.png(PngFixtures.header(png), PngFixtures.chunk("IEND", new byte[0])),
                PngFixtures.png(PngFixtures.header(png), PngFixtures.chunk("IDAT", new byte[]{1, 2, 3}), PngFixtures.chunk("IEND", new byte[0])),
                PngFixtures.beforeIend(png, "ABCD", new byte[0]),
                PngFixtures.beforeIend(png, "bad!", new byte[0])};
        for (byte[] input : malformed) assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromPng(input));
    }

    @Test void everyApngControlOrFrameChunkIsRejected() throws Exception {
        byte[] png = PngFixtures.rgba(1, 1);
        for (String type : new String[]{"acTL", "fcTL", "fdAT"}) {
            byte[] animated = PngFixtures.beforeIend(png, type, new byte[8]);
            assertThrows(IllegalArgumentException.class, () -> ImageAsset.fromPng(animated), type);
        }
    }
}
