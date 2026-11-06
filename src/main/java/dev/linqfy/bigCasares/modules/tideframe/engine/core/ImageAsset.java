package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Immutable, bounded static PNG normalized to metadata-free RGBA pixels.
 * Validation, decoding and encoding perform CPU work: invoke factories on a worker,
 * never on a player/region owner thread. Custom pixels retain their operator's rights.
 */
public final class ImageAsset {
    public static final int MAX_PNG_BYTES = 41_943_040;
    public static final int MAX_SIDE = 3840;
    public static final int MAX_PIXELS = 8_294_400;
    private static final int MAX_BASE64_LENGTH = ((MAX_PNG_BYTES + 2) / 3) * 4;
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final byte[] png;
    private final int width;
    private final int height;
    private final String sha256;
    private final int hashCode;

    private ImageAsset(byte[] png, int width, int height) {
        this.png = png;
        this.width = width;
        this.height = height;
        try { sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        hashCode = Arrays.hashCode(png);
    }

    /** Accepts only the padded, standard Base64 spelling of a bounded PNG. */
    public static ImageAsset fromBase64(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.length() > MAX_BASE64_LENGTH || encoded.length() % 4 != 0)
            throw invalid("Expected canonical bounded PNG Base64");
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException malformed) { throw invalid("Invalid PNG Base64", malformed); }
        if (!Base64.getEncoder().encodeToString(decoded).equals(encoded))
            throw invalid("Noncanonical PNG Base64");
        return fromPng(decoded);
    }

    public static ImageAsset fromPng(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_PNG_BYTES) throw invalid("PNG exceeds the byte limit or is absent");
        var validated = validate(bytes.clone());
        try {
            var readers = ImageIO.getImageReadersByFormatName("png");
            if (!readers.hasNext()) throw new IOException("PNG decoder unavailable");
            var reader = readers.next();
            BufferedImage decoded;
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(validated.decoderBytes()))) {
                try {
                    reader.setInput(input, true, true);
                    decoded = reader.read(0);
                } finally { reader.dispose(); }
            }
            if (decoded == null || decoded.getWidth() != validated.width() || decoded.getHeight() != validated.height())
                throw invalid("Decoded PNG dimensions differ from IHDR");
            var rgba = new BufferedImage(validated.width(), validated.height(), BufferedImage.TYPE_INT_ARGB);
            rgba.setRGB(0, 0, rgba.getWidth(), rgba.getHeight(), rgbaPixels(decoded, validated), 0, rgba.getWidth());
            byte[] canonical = encode(rgba);
            if (canonical.length > MAX_PNG_BYTES) throw invalid("Canonical PNG exceeds the byte limit");
            return new ImageAsset(canonical, rgba.getWidth(), rgba.getHeight());
        } catch (IOException | RuntimeException malformed) {
            throw invalid("PNG could not be decoded and normalized", malformed);
        }
    }

    public String base64() { return Base64.getEncoder().encodeToString(png); }
    public byte[] png() { return png.clone(); }
    public int width() { return width; }
    public int height() { return height; }
    public String sha256() { return sha256; }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof ImageAsset image && Arrays.equals(png, image.png);
    }
    @Override public int hashCode() { return hashCode; }

    private record Validated(byte[] decoderBytes, int width, int height, int colorType, int bitDepth, int transparentGray) { }

    private static int[] rgbaPixels(BufferedImage decoded, Validated validated) {
        int width = validated.width(), height = validated.height();
        if (validated.colorType() != 0 && validated.colorType() != 4)
            return decoded.getRGB(0, 0, width, height, null, 0, width);
        // JDK grayscale ColorSpace is linear: getRGB() changes raw sample128 into sRGB188.
        // PNG browser rendering treats these samples as the shade itself after metadata is stripped.
        var raster = decoded.getRaster();
        int grayBits = raster.getSampleModel().getSampleSize(0);
        if (grayBits < 1 || grayBits > 16 || raster.getNumBands() < (validated.colorType() == 4 ? 2 : 1))
            throw invalid("Invalid decoded grayscale layout");
        int grayMax = (1 << grayBits) - 1;
        int transparent = validated.transparentGray() < 0 ? -1
                : scaleSample(validated.transparentGray(), (1 << validated.bitDepth()) - 1, grayMax);
        int alphaBits = validated.colorType() == 4 ? raster.getSampleModel().getSampleSize(1) : 8;
        if (alphaBits < 1 || alphaBits > 16) throw invalid("Invalid decoded grayscale alpha layout");
        int alphaMax = (1 << alphaBits) - 1;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int rawGray = raster.getSample(x, y, 0);
            int gray = scaleSample(rawGray, grayMax, 255);
            // JDK expands low-depth gray+tRNS to 8 bits but does not scale its tRNS comparison.
            // Derive alpha from the validated original transparent sample instead of that result.
            int alpha = validated.colorType() == 4 ? scaleSample(raster.getSample(x, y, 1), alphaMax, 255)
                    : rawGray == transparent ? 0 : 255;
            pixels[y * width + x] = alpha << 24 | gray << 16 | gray << 8 | gray;
        }
        return pixels;
    }

    private static int scaleSample(int sample, int sourceMax, int targetMax) {
        return (int) (((long) sample * targetMax + sourceMax / 2) / sourceMax);
    }

    private static Validated validate(byte[] bytes) {
        if (bytes.length < 33 || !Arrays.equals(SIGNATURE, Arrays.copyOf(bytes, 8))
                || number(bytes, 8) != 13 || !type(bytes, 12).equals("IHDR"))
            throw invalid("Expected PNG signature and initial IHDR");
        int width = number(bytes, 16), height = number(bytes, 20);
        if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE || (long)width*height>MAX_PIXELS)
            throw invalid("PNG dimensions must be 1.." + MAX_SIDE + " and at most " + MAX_PIXELS + " pixels");
        int bitDepth = bytes[24] & 255, colorType = bytes[25] & 255;
        boolean validDepth = switch (colorType) {
            case 0 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16;
            case 2, 4, 6 -> bitDepth == 8 || bitDepth == 16;
            case 3 -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8;
            default -> false;
        };
        int interlace = bytes[28] & 255;
        if (!validDepth || bytes[26] != 0 || bytes[27] != 0 || interlace > 1)
            throw invalid("Invalid PNG pixel format");
        var safe = new ByteArrayOutputStream(bytes.length); safe.writeBytes(SIGNATURE);
        var compressed = new ByteArrayOutputStream();
        boolean sawData = false, dataEnded = false, sawPalette = false, sawTransparency = false;
        int paletteEntries = 0, transparentGray = -1;
        for (int offset = 8; offset < bytes.length;) {
            if (bytes.length - offset < 12) throw invalid("Truncated PNG chunk");
            int length = number(bytes, offset);
            if (length < 0 || length > bytes.length - offset - 12) throw invalid("Invalid PNG chunk bounds");
            String name = type(bytes, offset + 4);
            for (int i = 0; i < 4; i++) {
                char c = name.charAt(i);
                if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')) throw invalid("Invalid PNG chunk type");
            }
            if (name.charAt(2) < 'A' || name.charAt(2) > 'Z') throw invalid("Invalid PNG reserved chunk bit");
            var crc = new CRC32(); crc.update(bytes, offset + 4, length + 4);
            if (crc.getValue() != (number(bytes, offset + 8 + length) & 0xffff_ffffL))
                throw invalid("PNG chunk CRC mismatch");
            if (name.equals("acTL") || name.equals("fcTL") || name.equals("fdAT")) throw invalid("Animated PNG is unsupported");
            if (sawData && !name.equals("IDAT")) dataEnded = true;
            boolean retain = true;
            switch (name) {
                case "IHDR" -> {
                    if (offset != 8 || length != 13) throw invalid("Duplicate or malformed IHDR");
                }
                case "PLTE" -> {
                    if (sawPalette || sawData || colorType == 0 || colorType == 4 || length == 0 || length > 768 || length % 3 != 0)
                        throw invalid("Invalid PNG palette");
                    paletteEntries = length / 3;
                    if (colorType == 3 && paletteEntries > 1 << bitDepth) throw invalid("PNG palette exceeds bit depth");
                    sawPalette = true;
                }
                case "tRNS" -> {
                    boolean valid = switch (colorType) {
                        case 0 -> length == 2;
                        case 2 -> length == 6;
                        case 3 -> sawPalette && length > 0 && length <= paletteEntries;
                        default -> false;
                    };
                    if (sawTransparency || sawData || !valid) throw invalid("Invalid PNG transparency chunk");
                    if (colorType == 0) {
                        transparentGray = (bytes[offset + 8] & 255) << 8 | bytes[offset + 9] & 255;
                        if (transparentGray > (1 << bitDepth) - 1) throw invalid("Invalid grayscale transparent sample");
                    }
                    sawTransparency = true;
                }
                case "IDAT" -> {
                    if (dataEnded || colorType == 3 && !sawPalette) throw invalid("Invalid PNG image data order");
                    sawData = true; compressed.write(bytes, offset + 8, length);
                }
                case "IEND" -> {
                    if (!sawData || length != 0 || offset + 12 != bytes.length) throw invalid("Invalid PNG end or trailing bytes");
                    safe.write(bytes, offset, 12);
                    validateScanlines(compressed.toByteArray(), width, height, colorType, bitDepth, interlace);
                    return new Validated(safe.toByteArray(), width, height, colorType, bitDepth, transparentGray);
                }
                default -> {
                    if (name.charAt(0) >= 'A' && name.charAt(0) <= 'Z') throw invalid("Unknown critical PNG chunk");
                    // Metadata is checked for bounds and CRC but never passed to an image decoder.
                    retain = false;
                }
            }
            if (retain) safe.write(bytes, offset, length + 12);
            offset += length + 12;
        }
        throw invalid("Missing PNG IEND");
    }

    private static void validateScanlines(byte[] compressed, int width, int height, int colorType, int depth, int interlace) {
        int channels = switch (colorType) { case 0, 3 -> 1; case 2 -> 3; case 4 -> 2; case 6 -> 4; default -> throw invalid("PNG color type"); };
        int[] startsX = interlace == 0 ? new int[]{0} : new int[]{0, 4, 0, 2, 0, 1, 0};
        int[] startsY = interlace == 0 ? new int[]{0} : new int[]{0, 0, 4, 0, 2, 0, 1};
        int[] stepsX = interlace == 0 ? new int[]{1} : new int[]{8, 8, 4, 4, 2, 2, 1};
        int[] stepsY = interlace == 0 ? new int[]{1} : new int[]{8, 8, 8, 4, 4, 2, 2};
        int expected = 0;
        for (int pass = 0; pass < startsX.length; pass++) {
            int columns = Math.max(0, (width - startsX[pass] + stepsX[pass] - 1) / stepsX[pass]);
            int rows = Math.max(0, (height - startsY[pass] + stepsY[pass] - 1) / stepsY[pass]);
            if (columns > 0) expected += rows * (1 + (columns * channels * depth + 7) / 8);
        }
        byte[] pixels = new byte[expected + 1];
        var inflater = new Inflater(); inflater.setInput(compressed);
        int count = 0;
        try {
            while (!inflater.finished() && count < pixels.length) {
                int read = inflater.inflate(pixels, count, pixels.length - count); count += read;
                if (read == 0 && !inflater.finished()) throw invalid("Truncated or invalid PNG compression");
            }
            if (!inflater.finished() || inflater.getRemaining() != 0 || count != expected)
                throw invalid("PNG scanline data size or compression end is invalid");
        } catch (DataFormatException malformed) { throw invalid("Invalid PNG compressed data", malformed); }
        finally { inflater.end(); }
        int offset = 0;
        for (int pass = 0; pass < startsX.length; pass++) {
            int columns = Math.max(0, (width - startsX[pass] + stepsX[pass] - 1) / stepsX[pass]);
            int rows = Math.max(0, (height - startsY[pass] + stepsY[pass] - 1) / stepsY[pass]);
            if (columns == 0) continue;
            int rowBytes = 1 + (columns * channels * depth + 7) / 8;
            for (int row = 0; row < rows; row++, offset += rowBytes)
                if ((pixels[offset] & 255) > 4) throw invalid("Invalid PNG scanline filter");
        }
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("png");
        if (!writers.hasNext()) throw new IOException("PNG encoder unavailable");
        var writer = writers.next(); var bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            try {
                writer.setOutput(output); writer.write(null, new IIOImage(image, null, null), writer.getDefaultWriteParam());
                output.flush();
            } finally { writer.dispose(); }
        }
        return bytes.toByteArray();
    }

    private static int number(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16 | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255;
    }
    private static String type(byte[] bytes, int offset) { return new String(bytes, offset, 4, StandardCharsets.US_ASCII); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static IllegalArgumentException invalid(String message, Throwable cause) { return new IllegalArgumentException(message, cause); }
}
