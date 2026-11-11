package dev.linqfy.bigCasares.modules.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TotpTest {

    /** RFC 6238 appendix B SHA-1 seed "12345678901234567890". */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void base32RoundTripsTheRfcSeed() {
        byte[] seed = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertEquals(RFC_SECRET, Totp.base32Encode(seed));
        assertArrayEquals(seed, Totp.base32Decode(RFC_SECRET.toLowerCase()));
    }

    @Test
    void matchesRfc6238Vectors() {
        assertEquals("287082", Totp.codeAt(RFC_SECRET, Totp.stepAt(59L)));
        assertEquals("081804", Totp.codeAt(RFC_SECRET, Totp.stepAt(1_111_111_109L)));
        assertEquals("005924", Totp.codeAt(RFC_SECRET, Totp.stepAt(1_234_567_890L)));
        assertEquals("279037", Totp.codeAt(RFC_SECRET, Totp.stepAt(2_000_000_000L)));
    }

    @Test
    void acceptsNeighbouringStepsAndRejectsReplays() {
        long now = 1_234_567_890L;
        String previous = Totp.codeAt(RFC_SECRET, Totp.stepAt(now) - 1);

        OptionalLong step = Totp.verify(RFC_SECRET, previous, now, 1, Long.MIN_VALUE);
        assertEquals(OptionalLong.of(Totp.stepAt(now) - 1), step);
        assertTrue(Totp.verify(RFC_SECRET, previous, now, 1, step.getAsLong()).isEmpty());
        assertTrue(Totp.verify(RFC_SECRET, previous, now, 0, Long.MIN_VALUE).isEmpty());
        assertTrue(Totp.verify(RFC_SECRET, "12a456", now, 1, Long.MIN_VALUE).isEmpty());
        assertTrue(Totp.verify(RFC_SECRET, "00592", now, 1, Long.MIN_VALUE).isEmpty());
        assertTrue(Totp.verify(RFC_SECRET, "005 924", now, 0, Long.MIN_VALUE).isPresent());
    }

    @Test
    void buildsAuthenticatorUri() {
        String secret = Totp.newSecret(new SecureRandom());
        assertEquals(32, secret.length());
        assertEquals("otpauth://totp/BigCasares:Don%20Pollo?secret=" + secret
                + "&issuer=BigCasares&algorithm=SHA1&digits=6&period=30",
            Totp.otpauthUri("BigCasares", "Don Pollo", secret));
        assertEquals("ABCD EFGH IJ", Totp.grouped("ABCDEFGHIJ"));
    }
}
