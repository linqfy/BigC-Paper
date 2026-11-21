package dev.linqfy.bigCasares.modules.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * One-time backup codes ({@code XXXX-XXXX}, 40 bits each). Only SHA-256 digests are stored; the high entropy
 * of each code makes a per-code salt unnecessary.
 */
public final class RecoveryCodes {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private RecoveryCodes() {
    }

    public static List<String> generate(SecureRandom random, int count) {
        List<String> codes = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            StringBuilder code = new StringBuilder(9);
            for (int position = 0; position < 8; position++) {
                if (position == 4) {
                    code.append('-');
                }
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return codes;
    }

    public static boolean looksLikeCode(String input) {
        return input != null && normalize(input).length() == 8;
    }

    public static String digest(String code) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(normalize(code).getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String normalize(String code) {
        String upper = code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        for (int index = 0; index < upper.length(); index++) {
            if (ALPHABET.indexOf(upper.charAt(index)) < 0) {
                return "";
            }
        }
        return upper;
    }
}
