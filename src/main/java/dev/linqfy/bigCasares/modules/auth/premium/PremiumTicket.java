package dev.linqfy.bigCasares.modules.auth.premium;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Proof that the premium verifier authenticated {@code name} against Mojang. It travels back to the main server
 * as a client cookie, so it is HMAC-signed with the server secret, short-lived, bound to the client address and
 * carries a nonce that the receiver accepts only once.
 */
public record PremiumTicket(String name, UUID premiumId, byte[] addressHash, long issuedAtMillis,
                            long expiresAtMillis, byte[] nonce) {

    private static final byte VERSION = 1;
    private static final int MAC_BYTES = 32;

    public PremiumTicket {
        name = name.toLowerCase(Locale.ROOT);
        addressHash = addressHash.clone();
        nonce = nonce.clone();
    }

    public static PremiumTicket issue(String name, UUID premiumId, byte[] addressHash, long nowMillis,
                                      long lifetimeMillis, SecureRandom random) {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        return new PremiumTicket(name, premiumId, addressHash, nowMillis, nowMillis + lifetimeMillis, nonce);
    }

    public byte[] encode(byte[] secret) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(VERSION);
        body.write(nameBytes.length);
        body.writeBytes(nameBytes);
        body.writeBytes(ByteBuffer.allocate(16).putLong(premiumId.getMostSignificantBits())
            .putLong(premiumId.getLeastSignificantBits()).array());
        body.write(addressHash.length);
        body.writeBytes(addressHash);
        body.writeBytes(ByteBuffer.allocate(16).putLong(issuedAtMillis).putLong(expiresAtMillis).array());
        body.writeBytes(nonce);
        byte[] unsigned = body.toByteArray();
        body.writeBytes(mac(secret, unsigned));
        return body.toByteArray();
    }

    /** Decodes and authenticates a cookie; returns empty for forged, malformed or foreign payloads. */
    public static Optional<PremiumTicket> decode(byte[] encoded, byte[] secret) {
        if (encoded == null || encoded.length < 2 + MAC_BYTES || encoded[0] != VERSION) {
            return Optional.empty();
        }
        byte[] unsigned = Arrays.copyOfRange(encoded, 0, encoded.length - MAC_BYTES);
        byte[] mac = Arrays.copyOfRange(encoded, encoded.length - MAC_BYTES, encoded.length);
        if (!MessageDigest.isEqual(mac, mac(secret, unsigned))) {
            return Optional.empty();
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(unsigned, 1, unsigned.length - 1);
            byte[] nameBytes = new byte[buffer.get() & 0xFF];
            buffer.get(nameBytes);
            UUID premiumId = new UUID(buffer.getLong(), buffer.getLong());
            byte[] addressHash = new byte[buffer.get() & 0xFF];
            buffer.get(addressHash);
            long issuedAt = buffer.getLong();
            long expiresAt = buffer.getLong();
            byte[] nonce = new byte[16];
            buffer.get(nonce);
            if (buffer.hasRemaining()) {
                return Optional.empty();
            }
            return Optional.of(new PremiumTicket(new String(nameBytes, StandardCharsets.UTF_8), premiumId,
                addressHash, issuedAt, expiresAt, nonce));
        } catch (java.nio.BufferUnderflowException ex) {
            return Optional.empty();
        }
    }

    public boolean validFor(String playerName, byte[] currentAddressHash, long nowMillis, boolean bindAddress) {
        return name.equals(playerName.toLowerCase(Locale.ROOT))
            && nowMillis >= issuedAtMillis - 5_000L
            && nowMillis <= expiresAtMillis
            && (!bindAddress || MessageDigest.isEqual(addressHash, currentAddressHash));
    }

    public String nonceKey() {
        return java.util.HexFormat.of().formatHex(nonce);
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

    @Override
    public boolean equals(Object other) {
        return other instanceof PremiumTicket ticket
            && name.equals(ticket.name)
            && premiumId.equals(ticket.premiumId)
            && Arrays.equals(addressHash, ticket.addressHash)
            && issuedAtMillis == ticket.issuedAtMillis
            && expiresAtMillis == ticket.expiresAtMillis
            && Arrays.equals(nonce, ticket.nonce);
    }

    @Override
    public int hashCode() {
        return name.hashCode() * 31 + Arrays.hashCode(nonce);
    }
}
