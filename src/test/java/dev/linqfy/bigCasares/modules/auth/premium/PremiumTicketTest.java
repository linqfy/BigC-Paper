package dev.linqfy.bigCasares.modules.auth.premium;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PremiumTicketTest {

    private static final byte[] KEY = new byte[32];
    private static final byte[] ADDRESS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    @Test
    void roundTripsAndValidates() {
        PremiumTicket ticket = PremiumTicket.issue("Notch", UUID.randomUUID(), ADDRESS, 1_000L, 60_000L,
            new SecureRandom());
        byte[] encoded = ticket.encode(KEY);

        assertTrue(encoded.length < 5120, "fits in a cookie");
        assertEquals(Optional.of(ticket), PremiumTicket.decode(encoded, KEY));
        assertTrue(ticket.validFor("NOTCH", ADDRESS, 30_000L, true));
        assertFalse(ticket.validFor("Notch", ADDRESS, 61_001L, true));
        assertFalse(ticket.validFor("Notch", new byte[16], 30_000L, true));
        assertTrue(ticket.validFor("Notch", new byte[16], 30_000L, false));
        assertFalse(ticket.validFor("Jeb_", ADDRESS, 30_000L, true));
    }

    @Test
    void rejectsTamperingAndWrongKeys() {
        byte[] encoded = PremiumTicket.issue("Notch", UUID.randomUUID(), ADDRESS, 1_000L, 60_000L,
            new SecureRandom()).encode(KEY);
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        byte[] tampered = encoded.clone();
        tampered[5] ^= 0x20;

        assertTrue(PremiumTicket.decode(encoded, otherKey).isEmpty());
        assertTrue(PremiumTicket.decode(tampered, KEY).isEmpty());
        assertTrue(PremiumTicket.decode(new byte[3], KEY).isEmpty());
        assertTrue(PremiumTicket.decode(null, KEY).isEmpty());
    }
}
