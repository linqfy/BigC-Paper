package dev.linqfy.bigCasares.modules.tideframe.engine.testing;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Small PNGs authored from mathematical pixels; no external image or asset is read. */
public final class PngFixtures {
    private PngFixtures() { }
    public static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    public static byte[] rgba(int width, int height, int... argb) throws IOException {
        var header = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0).array();
        var compressed = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(compressed)) {
            for (int y = 0; y < height; y++) {
                deflater.write(0);
                for (int x = 0; x < width; x++) {
                    int color = argb.length == 0 ? 0x7f123456 : argb[(y * width + x) % argb.length];
                    deflater.write(color >>> 16); deflater.write(color >>> 8);
                    deflater.write(color); deflater.write(color >>> 24);
                }
            }
        }
        return png(chunk("IHDR", header), chunk("IDAT", compressed.toByteArray()), chunk("IEND", new byte[0]));
    }

    public static byte[] sampled(int width, int height, int depth, int colorType, byte[] scanlines, byte[]... beforeData) throws IOException {
        byte[] header = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) depth).put((byte) colorType)
                .put((byte) 0).put((byte) 0).put((byte) 0).array();
        var compressed = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(compressed)) { deflater.write(scanlines); }
        var bytes = new ByteArrayOutputStream(); bytes.write(SIGNATURE); bytes.write(chunk("IHDR", header));
        for (byte[] chunk : beforeData) bytes.write(chunk);
        bytes.write(chunk("IDAT", compressed.toByteArray())); bytes.write(chunk("IEND", new byte[0]));
        return bytes.toByteArray();
    }

    public static byte[] png(byte[]... chunks) throws IOException {
        var bytes = new ByteArrayOutputStream(); bytes.write(SIGNATURE);
        for (byte[] chunk : chunks) bytes.write(chunk);
        return bytes.toByteArray();
    }

    public static byte[] chunk(String type, byte[] data) throws IOException {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        out.writeInt(data.length); out.write(name); out.write(data);
        var crc = new CRC32(); crc.update(name); crc.update(data); out.writeInt((int) crc.getValue());
        return bytes.toByteArray();
    }

    public static byte[] beforeIend(byte[] png, String type, byte[] data) throws IOException {
        var bytes = new ByteArrayOutputStream();
        bytes.write(png, 0, png.length - 12); bytes.write(chunk(type, data));
        bytes.write(png, png.length - 12, 12); return bytes.toByteArray();
    }

    public static byte[] withDimensions(byte[] png, int width, int height) {
        byte[] result = png.clone();
        ByteBuffer.wrap(result, 16, 8).putInt(width).putInt(height);
        var crc = new CRC32(); crc.update(result, 12, 17);
        ByteBuffer.wrap(result, 29, 4).putInt((int) crc.getValue());
        return result;
    }

    public static byte[] header(byte[] png) { return Arrays.copyOfRange(png, 8, 33); }
}
