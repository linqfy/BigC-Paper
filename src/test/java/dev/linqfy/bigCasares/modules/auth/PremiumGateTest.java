package dev.linqfy.bigCasares.modules.auth;

import dev.linqfy.bigCasares.modules.auth.premium.PremiumProfile;
import dev.linqfy.bigCasares.modules.auth.premium.PremiumTicket;
import dev.linqfy.bigCasares.modules.auth.premium.PremiumVerifierServer;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PremiumGateTest {

    private final AuthSecret secret = new AuthSecret(new byte[32]);
    private final Map<String, AuthAccount> accounts = new HashMap<>();
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final PremiumGate gate = new PremiumGate(secret,
        name -> Optional.ofNullable(accounts.get(name.toLowerCase())), clock::get, true);
    private final UUID premiumId = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void rejectsAccountsWithoutPremiumMode() throws Exception {
        accounts.put("steve", new AuthAccount("Steve", 0L));

        assertFalse(decide("Steve", "1.2.3.4").accepted());
    }

    @Test
    void issuesCookieThatRedeemsOnceFromTheSameAddress() throws Exception {
        AuthAccount account = new AuthAccount("Steve", 0L);
        account.premium(true, premiumId);
        accounts.put("steve", account);

        PremiumVerifierServer.Decision decision = decide("Steve", "1.2.3.4");
        assertTrue(decision.accepted());
        assertEquals("play.example.com", decision.returnHost());
        assertEquals(25565, decision.returnPort());

        InetAddress address = InetAddress.getByName("1.2.3.4");
        assertTrue(gate.redeem("steve", InetAddress.getByName("5.6.7.8"), decision.cookie()).isEmpty());
        Optional<PremiumTicket> ticket = gate.redeem("Steve", address, decision.cookie());
        assertEquals(Optional.of(premiumId), ticket.map(PremiumTicket::premiumId));
        assertTrue(gate.redeem("Steve", address, decision.cookie()).isEmpty(), "tickets are single-use");
    }

    @Test
    void expiredOrForeignCookiesAreRejected() throws Exception {
        AuthAccount account = new AuthAccount("Steve", 0L);
        account.premium(true, premiumId);
        accounts.put("steve", account);
        InetAddress address = InetAddress.getByName("1.2.3.4");
        byte[] cookie = decide("Steve", "1.2.3.4").cookie();

        assertTrue(gate.redeem("Alex", address, cookie).isEmpty());
        clock.addAndGet(PremiumGate.TICKET_LIFETIME_MILLIS + 1);
        assertTrue(gate.redeem("Steve", address, cookie).isEmpty());
        byte[] forged = decide("Steve", "1.2.3.4").cookie();
        forged[3] ^= 1;
        assertTrue(new PremiumGate(new AuthSecret(new byte[32]), name -> Optional.empty(), clock::get, true)
            .redeem("Steve", address, forged).isEmpty());
    }

    @Test
    void rejectsADifferentPremiumAccountForTheSameName() throws Exception {
        AuthAccount account = new AuthAccount("Steve", 0L);
        account.premium(true, UUID.randomUUID());
        accounts.put("steve", account);

        assertFalse(decide("Steve", "1.2.3.4").accepted());
    }

    @Test
    void optInAndNewPlayerWindowsAllowVerification() throws Exception {
        accounts.put("steve", new AuthAccount("Steve", 0L));
        gate.beginOptIn("Steve");
        assertTrue(decide("Steve", "1.2.3.4").accepted());

        gate.markNewPlayer("Notch");
        assertTrue(decide("Notch", "1.2.3.4").accepted());
        clock.addAndGet(PremiumGate.OPT_IN_MILLIS + 1);
        assertFalse(decide("Notch", "1.2.3.4").accepted());
    }

    @Test
    void bounceIsSuppressedRightAfterAFailedVerification() {
        assertTrue(gate.markBounce("Steve"));
        assertFalse(gate.markBounce("steve"), "a cracked client that failed must fall back to the normal login");
        clock.addAndGet(PremiumGate.BOUNCE_GRACE_MILLIS + 1);
        assertTrue(gate.markBounce("Steve"));
    }

    @Test
    void cleansForgeMarkersFromHandshakeHost() {
        assertEquals("play.example.com", PremiumGate.cleanHost("play.example.com.\0FML3\0"));
    }

    private PremiumVerifierServer.Decision decide(String name, String address) throws Exception {
        return gate.decide(new PremiumProfile(premiumId, name, List.of()), InetAddress.getByName(address),
            "play.example.com", "", 25565);
    }
}
