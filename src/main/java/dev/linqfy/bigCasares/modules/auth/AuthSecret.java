package dev.linqfy.bigCasares.modules.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Server-local secret used to sign premium tickets and to store addresses only as keyed hashes, so account files
 * never contain raw IPs.
 */
public final class AuthSecret {

    private final byte[] key;

    AuthSecret(byte[] key) {
        this.key = key.clone();
    }

    public static AuthSecret loadOrCreate(Path file, SecureRandom random) throws IOException {
        if (Files.isRegularFile(file)) {
            byte[] stored = HexFormat.of().parseHex(Files.readString(file, StandardCharsets.US_ASCII).strip());
            if (stored.length >= 32) {
                return new AuthSecret(stored);
            }
        }
        byte[] fresh = new byte[32];
        random.nextBytes(fresh);
        Files.createDirectories(file.getParent());
        Files.writeString(file, HexFormat.of().formatHex(fresh), StandardCharsets.US_ASCII);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows: rely on the server directory's permissions
        }
        return new AuthSecret(fresh);
    }

    public byte[] ticketKey() {
        return derive("premium-ticket");
    }

    public byte[] addressHash(InetAddress address) {
        return Arrays.copyOf(mac(derive("address"), address.getAddress()), 16);
    }

    public String addressKey(InetAddress address) {
        return HexFormat.of().formatHex(addressHash(address));
    }

    private byte[] derive(String purpose) {
        return mac(key, purpose.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] mac(byte[] secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }
}
