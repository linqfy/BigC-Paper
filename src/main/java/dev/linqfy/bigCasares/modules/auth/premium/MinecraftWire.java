package dev.linqfy.bigCasares.modules.auth.premium;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.UUID;

/** Minimal Java Edition wire primitives (VarInt framing without compression, strings, UUIDs). */
public final class MinecraftWire {

    public static final int MAX_PACKET_BYTES = 32 * 1024;

    private MinecraftWire() {
    }

    public static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        for (int position = 0; position < 5; position++) {
            int read = in.read();
            if (read < 0) {
                throw new EOFException();
            }
            value |= (read & 0x7F) << (position * 7);
            if ((read & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("VarInt too long");
    }

    public static void writeVarInt(OutputStream out, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            out.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.write(remaining);
    }

    /** Reads one length-prefixed frame and returns its body (packet id followed by fields). */
    public static byte[] readFrame(InputStream in) throws IOException {
        int length = readVarInt(in);
        if (length <= 0 || length > MAX_PACKET_BYTES) {
            throw new IOException("Bad frame length " + length);
        }
        byte[] body = in.readNBytes(length);
        if (body.length != length) {
            throw new EOFException("Short frame");
        }
        return body;
    }

    public static void writeFrame(OutputStream out, byte[] body) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(body.length + 5);
        writeVarInt(frame, body.length);
        frame.writeBytes(body);
        out.write(frame.toByteArray());
        out.flush();
    }

    public static String readString(InputStream in, int maxChars) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > maxChars * 3) {
            throw new IOException("String too long");
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException();
        }
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.length() > maxChars) {
            throw new IOException("String too long");
        }
        return value;
    }

    public static void writeString(OutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    public static byte[] readByteArray(InputStream in, int maxBytes) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > maxBytes) {
            throw new IOException("Byte array too long");
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new EOFException();
        }
        return bytes;
    }

    public static void writeByteArray(OutputStream out, byte[] value) throws IOException {
        writeVarInt(out, value.length);
        out.write(value);
    }

    public static UUID readUuid(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(16);
        if (bytes.length != 16) {
            throw new EOFException();
        }
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public static void writeUuid(OutputStream out, UUID value) throws IOException {
        out.write(java.nio.ByteBuffer.allocate(16)
            .putLong(value.getMostSignificantBits())
            .putLong(value.getLeastSignificantBits())
            .array());
    }

    public static int readUnsignedShort(InputStream in) throws IOException {
        int high = in.read();
        int low = in.read();
        if ((high | low) < 0) {
            throw new EOFException();
        }
        return high << 8 | low;
    }

    /** Mojang's "server hash": SHA-1 over server id, shared secret and public key, as a signed hex number. */
    public static String serverHash(String serverId, byte[] sharedSecret, byte[] encodedPublicKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(serverId.getBytes(StandardCharsets.ISO_8859_1));
            sha1.update(sharedSecret);
            sha1.update(encodedPublicKey);
            return new BigInteger(sha1.digest()).toString(16);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-1 unavailable", ex);
        }
    }

    /**
     * Wraps a stream with AES/CFB8 decryption. Each byte is decrypted as soon as it is read: the JDK's
     * CipherInputStream may wait for more input than a short packet provides.
     */
    public static InputStream decrypting(InputStream in, javax.crypto.Cipher cipher) {
        return new java.io.FilterInputStream(in) {
            @Override
            public int read() throws IOException {
                int value = super.read();
                if (value < 0) {
                    return -1;
                }
                byte[] plain = cipher.update(new byte[] {(byte) value});
                return plain[0] & 0xFF;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                int count = super.read(buffer, offset, length);
                if (count > 0) {
                    byte[] plain = cipher.update(buffer, offset, count);
                    System.arraycopy(plain, 0, buffer, offset, count);
                }
                return count;
            }
        };
    }

    /** Wraps a stream with AES/CFB8 encryption that writes every byte through immediately. */
    public static OutputStream encrypting(OutputStream out, javax.crypto.Cipher cipher) {
        return new java.io.FilterOutputStream(out) {
            @Override
            public void write(int value) throws IOException {
                out.write(cipher.update(new byte[] {(byte) value}));
            }

            @Override
            public void write(byte[] buffer, int offset, int length) throws IOException {
                out.write(cipher.update(buffer, offset, length));
            }
        };
    }

    public static String chatJson(String text) {
        StringBuilder escaped = new StringBuilder("{\"text\":\"");
        for (char character : text.toCharArray()) {
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append("\"}").toString();
    }
}
