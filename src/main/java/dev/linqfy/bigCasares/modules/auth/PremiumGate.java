package dev.linqfy.bigCasares.modules.auth;

import dev.linqfy.bigCasares.modules.auth.premium.PremiumProfile;
import dev.linqfy.bigCasares.modules.auth.premium.PremiumTicket;
import dev.linqfy.bigCasares.modules.auth.premium.PremiumVerifierServer;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Premium auto-login policy shared by the main server's login hook and the built-in verifier. Thread-safe: both
 * sides run off the server thread.
 */
final class PremiumGate {

    static final long TICKET_LIFETIME_MILLIS = 60_000L;
    static final long BOUNCE_GRACE_MILLIS = 45_000L;
    static final long OPT_IN_MILLIS = 120_000L;

    private final AuthSecret secret;
    private final Function<String, Optional<AuthAccount>> accounts;
    private final LongSupplier clock;
    private final boolean bindAddress;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> bouncedAt = new ConcurrentHashMap<>();
    private final Map<String, Long> optInUntil = new ConcurrentHashMap<>();
    private final Map<String, Long> newPlayerUntil = new ConcurrentHashMap<>();
    private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();
    private final Map<String, PremiumTicket> verifiedInMemory = new ConcurrentHashMap<>();

    PremiumGate(AuthSecret secret, Function<String, Optional<AuthAccount>> accounts, LongSupplier clock,
                boolean bindAddress) {
        this.secret = secret;
        this.accounts = accounts;
        this.clock = clock;
        this.bindAddress = bindAddress;
    }

    /** Starts a {@code /premium} opt-in window for an authenticated player. */
    void beginOptIn(String name) {
        optInUntil.put(key(name), clock.getAsLong() + OPT_IN_MILLIS);
    }

    boolean optInPending(String name) {
        Long until = optInUntil.get(key(name));
        return until != null && until >= clock.getAsLong();
    }

    void endOptIn(String name) {
        optInUntil.remove(key(name));
    }

    boolean wantsVerification(AuthAccount account) {
        return account != null && (account.premium() || optInPending(account.name()));
    }

    /** Marks an unregistered name that Mojang knows, so the verifier may create a premium account for it. */
    void markNewPlayer(String name) {
        newPlayerUntil.put(key(name), clock.getAsLong() + OPT_IN_MILLIS);
    }

    boolean newPlayerPending(String name) {
        Long until = newPlayerUntil.get(key(name));
        return until != null && until >= clock.getAsLong();
    }

    /** Records a bounce and returns {@code false} when one already happened moments ago (loop protection). */
    boolean markBounce(String name) {
        long now = clock.getAsLong();
        Long previous = bouncedAt.put(key(name), now);
        return previous == null || now - previous > BOUNCE_GRACE_MILLIS;
    }

    /**
     * Accepts a returning client when it carries a valid cookie, or when the verifier in this JVM authenticated
     * it moments ago from the same address. Each proof is single-use.
     */
    Optional<PremiumTicket> redeem(String name, InetAddress address, byte[] cookie) {
        long now = clock.getAsLong();
        byte[] addressHash = secret.addressHash(address);
        Optional<PremiumTicket> ticket = Optional.ofNullable(cookie)
            .flatMap(bytes -> PremiumTicket.decode(bytes, secret.ticketKey()));
        if (ticket.isEmpty()) {
            ticket = Optional.ofNullable(verifiedInMemory.get(key(name)));
        }
        Optional<PremiumTicket> accepted = ticket
            .filter(candidate -> candidate.validFor(name, addressHash, now, bindAddress))
            .filter(candidate -> usedNonces.putIfAbsent(candidate.nonceKey(), candidate.expiresAtMillis()) == null);
        accepted.ifPresent(used -> {
            verifiedInMemory.remove(key(name));
            bouncedAt.remove(key(name));
            newPlayerUntil.remove(key(name));
        });
        usedNonces.values().removeIf(expiry -> expiry < now - TICKET_LIFETIME_MILLIS);
        return accepted;
    }

    /** Verdict for the built-in verifier once Mojang confirmed {@code profile}. */
    PremiumVerifierServer.Decision decide(PremiumProfile profile, InetAddress address, String handshakeHost,
                                          String configuredReturnHost, int returnPort) {
        AuthAccount account = accounts.apply(profile.name()).orElse(null);
        boolean newPlayer = account == null && newPlayerPending(profile.name());
        if (!newPlayer && !wantsVerification(account)) {
            return PremiumVerifierServer.Decision.reject(
                "Esta cuenta no tiene el inicio premium activado. Entrá normalmente y usá /premium.");
        }
        if (account != null && account.premium() && account.premiumId() != null && !account.premiumId().equals(profile.id())) {
            return PremiumVerifierServer.Decision.reject(
                "Esta cuenta está vinculada a otra cuenta premium.");
        }
        PremiumTicket ticket = PremiumTicket.issue(profile.name(), profile.id(), secret.addressHash(address),
            clock.getAsLong(), TICKET_LIFETIME_MILLIS, random);
        verifiedInMemory.put(key(profile.name()), ticket);
        String host = configuredReturnHost == null || configuredReturnHost.isBlank()
            ? cleanHost(handshakeHost)
            : configuredReturnHost;
        return PremiumVerifierServer.Decision.accept(ticket.encode(secret.ticketKey()), host, returnPort);
    }

    UUID premiumIdOf(PremiumTicket ticket) {
        return ticket.premiumId();
    }

    /** Strips Forge/FML markers and a trailing dot that some clients append to the handshake host. */
    static String cleanHost(String handshakeHost) {
        String host = handshakeHost == null ? "" : handshakeHost;
        int marker = host.indexOf('\0');
        if (marker >= 0) {
            host = host.substring(0, marker);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
