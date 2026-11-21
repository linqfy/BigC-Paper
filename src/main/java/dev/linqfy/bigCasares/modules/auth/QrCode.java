package dev.linqfy.bigCasares.modules.auth;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Byte-mode QR code encoder (ISO/IEC 18004, error correction level M) used to draw the authenticator
 * {@code otpauth://} URI on an in-game map. Follows the structure of Project Nayuki's reference encoder.
 */
public final class QrCode {

    private static final int[] ECC_CODEWORDS_PER_BLOCK_M = {
        -1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26,
        26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28
    };
    private static final int[] NUM_ERROR_CORRECTION_BLOCKS_M = {
        -1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16,
        17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49
    };
    /** Format bits for error correction level M. */
    private static final int ECC_FORMAT_BITS_M = 0;

    private final int version;
    private final int size;
    private final boolean[][] modules;
    private final boolean[][] isFunction;
    private final int mask;

    private QrCode(int version, byte[] dataCodewords, int forcedMask) {
        this.version = version;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];
        drawFunctionPatterns();
        drawCodewords(addEccAndInterleave(dataCodewords));
        int chosen = forcedMask;
        if (chosen < 0) {
            int minPenalty = Integer.MAX_VALUE;
            for (int candidate = 0; candidate < 8; candidate++) {
                applyMask(candidate);
                drawFormatBits(candidate);
                int penalty = penaltyScore();
                if (penalty < minPenalty) {
                    chosen = candidate;
                    minPenalty = penalty;
                }
                applyMask(candidate);
            }
        }
        this.mask = chosen;
        applyMask(chosen);
        drawFormatBits(chosen);
    }

    public static QrCode encodeText(String text) {
        return encode(text.getBytes(StandardCharsets.UTF_8), -1);
    }

    static QrCode encode(byte[] data, int forcedMask) {
        for (int version = 1; version <= 40; version++) {
            int capacityBits = numDataCodewords(version) * 8;
            int usedBits = 4 + (version <= 9 ? 8 : 16) + data.length * 8;
            if (usedBits <= capacityBits) {
                return new QrCode(version, dataCodewords(version, data, capacityBits), forcedMask);
            }
        }
        throw new IllegalArgumentException("Data too long for a QR code");
    }

    public int version() {
        return version;
    }

    public int size() {
        return size;
    }

    public int mask() {
        return mask;
    }

    public boolean module(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size && modules[y][x];
    }

    private static byte[] dataCodewords(int version, byte[] data, int capacityBits) {
        BitBuffer bits = new BitBuffer();
        bits.append(0b0100, 4);
        bits.append(data.length, version <= 9 ? 8 : 16);
        for (byte value : data) {
            bits.append(value & 0xFF, 8);
        }
        bits.append(0, Math.min(4, capacityBits - bits.length()));
        bits.append(0, (8 - bits.length() % 8) % 8);
        for (int pad = 0xEC; bits.length() < capacityBits; pad ^= 0xEC ^ 0x11) {
            bits.append(pad, 8);
        }
        return bits.toBytes();
    }

    private void drawFunctionPatterns() {
        for (int index = 0; index < size; index++) {
            setFunction(6, index, index % 2 == 0);
            setFunction(index, 6, index % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] positions = alignmentPositions();
        int count = positions.length;
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < count; j++) {
                if (!(i == 0 && j == 0 || i == 0 && j == count - 1 || i == count - 1 && j == 0)) {
                    drawAlignment(positions[i], positions[j]);
                }
            }
        }
        drawFormatBits(0);
        drawVersion();
    }

    private void drawFormatBits(int msk) {
        int data = ECC_FORMAT_BITS_M << 3 | msk;
        int remainder = data;
        for (int i = 0; i < 10; i++) {
            remainder = (remainder << 1) ^ ((remainder >>> 9) * 0x537);
        }
        int bits = (data << 10 | remainder) ^ 0x5412;
        for (int i = 0; i <= 5; i++) {
            setFunction(8, i, bit(bits, i));
        }
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) {
            setFunction(14 - i, 8, bit(bits, i));
        }
        for (int i = 0; i < 8; i++) {
            setFunction(size - 1 - i, 8, bit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            setFunction(8, size - 15 + i, bit(bits, i));
        }
        setFunction(8, size - 8, true);
    }

    private void drawVersion() {
        if (version < 7) {
            return;
        }
        int remainder = version;
        for (int i = 0; i < 12; i++) {
            remainder = (remainder << 1) ^ ((remainder >>> 11) * 0x1F25);
        }
        int bits = version << 12 | remainder;
        for (int i = 0; i < 18; i++) {
            boolean value = bit(bits, i);
            int a = size - 11 + i % 3;
            int b = i / 3;
            setFunction(a, b, value);
            setFunction(b, a, value);
        }
    }

    private void drawFinder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx;
                int yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) {
                    setFunction(xx, yy, distance != 2 && distance != 4);
                }
            }
        }
    }

    private void drawAlignment(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunction(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private byte[] addEccAndInterleave(byte[] data) {
        int numBlocks = NUM_ERROR_CORRECTION_BLOCKS_M[version];
        int blockEccLength = ECC_CODEWORDS_PER_BLOCK_M[version];
        int rawCodewords = numRawDataModules(version) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLength = rawCodewords / numBlocks;
        byte[][] blocks = new byte[numBlocks][];
        byte[] divisor = reedSolomonDivisor(blockEccLength);
        for (int i = 0, offset = 0; i < numBlocks; i++) {
            int dataLength = shortBlockLength - blockEccLength + (i < numShortBlocks ? 0 : 1);
            byte[] blockData = java.util.Arrays.copyOfRange(data, offset, offset + dataLength);
            offset += dataLength;
            byte[] block = new byte[shortBlockLength + 1];
            System.arraycopy(blockData, 0, block, 0, dataLength);
            byte[] ecc = reedSolomonRemainder(blockData, divisor);
            System.arraycopy(ecc, 0, block, block.length - blockEccLength, ecc.length);
            blocks[i] = block;
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream(rawCodewords);
        for (int i = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < numBlocks; j++) {
                if (i != shortBlockLength - blockEccLength || j >= numShortBlocks) {
                    result.write(blocks[j][i]);
                }
            }
        }
        return result.toByteArray();
    }

    private void drawCodewords(byte[] data) {
        int index = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vertical = 0; vertical < size; vertical++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vertical : vertical;
                    if (!isFunction[y][x] && index < data.length * 8) {
                        modules[y][x] = bit(data[index >>> 3], 7 - (index & 7));
                        index++;
                    }
                }
            }
        }
    }

    private void applyMask(int msk) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert = switch (msk) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                };
                modules[y][x] ^= invert && !isFunction[y][x];
            }
        }
    }

    private int penaltyScore() {
        int result = 0;
        for (int line = 0; line < size; line++) {
            result += runPenalty(line, true) + runPenalty(line, false);
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean color = modules[y][x];
                if (color == modules[y][x + 1] && color == modules[y + 1][x] && color == modules[y + 1][x + 1]) {
                    result += 3;
                }
            }
        }
        boolean[] finderLike = {true, false, true, true, true, false, true};
        for (int line = 0; line < size; line++) {
            for (int start = 0; start + 7 <= size; start++) {
                for (boolean horizontal : new boolean[] {true, false}) {
                    if (matches(line, start, horizontal, finderLike)
                        && (lightRun(line, start - 4, horizontal) || lightRun(line, start + 7, horizontal))) {
                        result += 40;
                    }
                }
            }
        }
        int dark = 0;
        for (boolean[] row : modules) {
            for (boolean module : row) {
                if (module) {
                    dark++;
                }
            }
        }
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        return result + Math.max(0, k) * 10;
    }

    private int runPenalty(int line, boolean horizontal) {
        int penalty = 0;
        int run = 0;
        boolean previous = false;
        for (int index = 0; index < size; index++) {
            boolean color = horizontal ? modules[line][index] : modules[index][line];
            if (index > 0 && color == previous) {
                run++;
            } else {
                if (run >= 5) {
                    penalty += run - 2;
                }
                run = 1;
                previous = color;
            }
        }
        if (run >= 5) {
            penalty += run - 2;
        }
        return penalty;
    }

    private boolean matches(int line, int start, boolean horizontal, boolean[] pattern) {
        for (int offset = 0; offset < pattern.length; offset++) {
            int position = start + offset;
            boolean color = horizontal ? modules[line][position] : modules[position][line];
            if (color != pattern[offset]) {
                return false;
            }
        }
        return true;
    }

    private boolean lightRun(int line, int start, boolean horizontal) {
        for (int position = start; position < start + 4; position++) {
            if (position >= 0 && position < size
                && (horizontal ? modules[line][position] : modules[position][line])) {
                return false;
            }
        }
        return true;
    }

    private int[] alignmentPositions() {
        if (version == 1) {
            return new int[0];
        }
        int count = version / 7 + 2;
        int step = (version * 8 + count * 3 + 5) / (count * 4 - 4) * 2;
        int[] result = new int[count];
        result[0] = 6;
        for (int i = count - 1, position = size - 7; i >= 1; i--, position -= step) {
            result[i] = position;
        }
        return result;
    }

    private static int numRawDataModules(int version) {
        int result = (16 * version + 128) * version + 64;
        if (version >= 2) {
            int count = version / 7 + 2;
            result -= (25 * count - 10) * count - 55;
            if (version >= 7) {
                result -= 36;
            }
        }
        return result;
    }

    private static int numDataCodewords(int version) {
        return numRawDataModules(version) / 8
            - ECC_CODEWORDS_PER_BLOCK_M[version] * NUM_ERROR_CORRECTION_BLOCKS_M[version];
    }

    private static byte[] reedSolomonDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) multiply(result[j] & 0xFF, root);
                if (j + 1 < degree) {
                    result[j] ^= result[j + 1];
                }
            }
            root = multiply(root, 0x02);
        }
        return result;
    }

    private static byte[] reedSolomonRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte value : data) {
            int factor = (value ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) multiply(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int multiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private static boolean bit(int value, int index) {
        return ((value >>> index) & 1) != 0;
    }

    private static final class BitBuffer {
        private final java.util.BitSet bits = new java.util.BitSet();
        private int length;

        void append(int value, int count) {
            for (int i = count - 1; i >= 0; i--) {
                bits.set(length++, ((value >>> i) & 1) != 0);
            }
        }

        int length() {
            return length;
        }

        byte[] toBytes() {
            byte[] out = new byte[length / 8];
            for (int i = 0; i < length; i++) {
                if (bits.get(i)) {
                    out[i >>> 3] |= (byte) (1 << (7 - (i & 7)));
                }
            }
            return out;
        }
    }
}
