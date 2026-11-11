package dev.linqfy.bigCasares.modules.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * PBKDF2-HMAC-SHA256 hashing for passwords and pattern locks. Hashing is deliberately slow, so callers run it
 * off the main thread.
 */
public final class PasswordHasher {

    public static final String ALGORITHM = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final Set<String> COMMON = Set.of(
        "123456", "1234567", "12345678", "123456789", "1234567890", "password", "contraseña", "contrasena",
        "qwerty", "qwerty123", "111111", "000000", "abc123", "minecraft", "iloveyou", "admin", "654321",
        "123123", "password1", "asdasd", "asdfgh", "112233", "121212", "666666", "777777", "987654321"
    );

    private final int iterations;
    private final SecureRandom random;

    public PasswordHasher(int iterations, SecureRandom random) {
        if (iterations < 1_000) {
            throw new IllegalArgumentException("PBKDF2 iterations must be at least 1000");
        }
        this.iterations = iterations;
        this.random = Objects.requireNonNull(random, "random");
    }

    public HashedSecret hash(String secret) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new HashedSecret(ALGORITHM, iterations, encode(salt), encode(derive(secret, salt, iterations)));
    }

    public static boolean verify(String secret, HashedSecret stored) {
        if (stored == null || secret == null || !ALGORITHM.equals(stored.algorithm())) {
            return false;
        }
        byte[] expected = Base64.getDecoder().decode(stored.hash());
        byte[] actual = derive(secret, Base64.getDecoder().decode(stored.salt()), stored.iterations());
        return MessageDigest.isEqual(expected, actual);
    }

    /** Returns a Spanish rejection reason, or {@code null} when the password is acceptable. */
    public static String rejectionReason(String password, String playerName, int minimumLength) {
        if (password == null || password.length() < minimumLength) {
            return "La contraseña debe tener al menos " + minimumLength + " caracteres.";
        }
        if (password.length() > 72) {
            return "La contraseña no puede superar 72 caracteres.";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (lower.equals(playerName.toLowerCase(Locale.ROOT)) || lower.contains(playerName.toLowerCase(Locale.ROOT))) {
            return "La contraseña no puede contener tu nombre.";
        }
        if (COMMON.contains(lower)) {
            return "Esa contraseña es demasiado común.";
        }
        return null;
    }

    private static byte[] derive(String secret, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable", ex);
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    public record HashedSecret(String algorithm, int iterations, String salt, String hash) {
    }
}
