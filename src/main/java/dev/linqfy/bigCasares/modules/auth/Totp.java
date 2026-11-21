package dev.linqfy.bigCasares.modules.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * RFC 6238 time-based one-time passwords (HMAC-SHA1, 30 s, 6 digits): the format Google Authenticator,
 * Aegis, Authy and every other authenticator app reads from an {@code otpauth://} URI.
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30L;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;

    private Totp() {
    }

    public static String newSecret(SecureRandom random) {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return base32Encode(secret);
    }

    public static long stepAt(long epochSeconds) {
        return Math.floorDiv(epochSeconds, STEP_SECONDS);
    }

    public static String codeAt(String base32Secret, long step) {
        byte[] key = base32Decode(base32Secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA1 unavailable", ex);
        }
    }

    /**
     * Returns the matched step when {@code code} is valid within {@code window} steps of {@code epochSeconds}
     * and newer than {@code lastUsedStep}, so a code observed over someone's shoulder cannot be replayed.
     */
    public static OptionalLong verify(String base32Secret, String code, long epochSeconds, int window,
                                      long lastUsedStep) {
        if (code == null) {
            return OptionalLong.empty();
        }
        String normalized = code.replace(" ", "").replace("-", "");
        if (normalized.length() != DIGITS || !normalized.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        long now = stepAt(epochSeconds);
        for (long step = now - window; step <= now + window; step++) {
            if (step > lastUsedStep && constantTimeEquals(codeAt(base32Secret, step), normalized)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public static String otpauthUri(String issuer, String accountName, String base32Secret) {
        String label = encode(issuer) + ":" + encode(accountName);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + encode(issuer)
            + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** Groups the secret in blocks of four so it can be typed into an app by hand. */
    public static String grouped(String base32Secret) {
        StringBuilder grouped = new StringBuilder();
        for (int index = 0; index < base32Secret.length(); index += 4) {
            if (!grouped.isEmpty()) {
                grouped.append(' ');
            }
            grouped.append(base32Secret, index, Math.min(base32Secret.length(), index + 4));
        }
        return grouped.toString();
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
    }

    static byte[] base32Decode(String encoded) {
        String normalized = encoded.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int index = 0; index < normalized.length(); index++) {
            int value = BASE32_ALPHABET.indexOf(normalized.charAt(index));
            if (value < 0) {
                throw new IllegalArgumentException("Invalid base32 character");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
            left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
