package dev.linqfy.bigCasares.modules.auth;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent login data for one offline-mode name. Mutated on the server thread; asynchronous login hooks only
 * read it.
 */
public final class AuthAccount {

    private final String name;
    private final long registeredAtMillis;
    private volatile PasswordHasher.HashedSecret password;
    private volatile String totpSecret;
    private volatile long totpLastStep = Long.MIN_VALUE;
    private final List<String> recoveryDigests = new ArrayList<>();
    private volatile byte[] pgpKeyRing;
    private volatile String pgpFingerprint;
    private volatile PasswordHasher.HashedSecret pattern;
    private volatile boolean premium;
    private volatile UUID premiumId;
    private final Set<String> trustedFriends = new LinkedHashSet<>();
    private volatile boolean requireTwoFactors;
    private volatile boolean sessionResume = true;
    private volatile boolean discordLogin = true;
    private volatile String lastAddressHash;
    private volatile long lastLoginAtMillis;

    public AuthAccount(String name, long registeredAtMillis) {
        this.name = name;
        this.registeredAtMillis = registeredAtMillis;
    }

    public String name() {
        return name;
    }

    public String key() {
        return name.toLowerCase(Locale.ROOT);
    }

    public long registeredAtMillis() {
        return registeredAtMillis;
    }

    public PasswordHasher.HashedSecret password() {
        return password;
    }

    public void password(PasswordHasher.HashedSecret password) {
        this.password = password;
    }

    public String totpSecret() {
        return totpSecret;
    }

    public void totpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
        this.totpLastStep = Long.MIN_VALUE;
    }

    public long totpLastStep() {
        return totpLastStep;
    }

    public void totpLastStep(long totpLastStep) {
        this.totpLastStep = totpLastStep;
    }

    public synchronized List<String> recoveryDigests() {
        return List.copyOf(recoveryDigests);
    }

    public synchronized void recoveryDigests(List<String> digests) {
        recoveryDigests.clear();
        recoveryDigests.addAll(digests);
    }

    /** Consumes a recovery code; returns {@code true} when it was valid and unused. */
    public synchronized boolean consumeRecoveryDigest(String digest) {
        return recoveryDigests.remove(digest);
    }

    public byte[] pgpKeyRing() {
        return pgpKeyRing == null ? null : pgpKeyRing.clone();
    }

    public String pgpFingerprint() {
        return pgpFingerprint;
    }

    public void pgpKey(byte[] keyRing, String fingerprint) {
        this.pgpKeyRing = keyRing == null ? null : keyRing.clone();
        this.pgpFingerprint = keyRing == null ? null : fingerprint;
    }

    public PasswordHasher.HashedSecret pattern() {
        return pattern;
    }

    public void pattern(PasswordHasher.HashedSecret pattern) {
        this.pattern = pattern;
    }

    public boolean premium() {
        return premium;
    }

    public UUID premiumId() {
        return premiumId;
    }

    public void premium(boolean premium, UUID premiumId) {
        this.premium = premium;
        this.premiumId = premium ? premiumId : null;
    }

    public synchronized Set<String> trustedFriends() {
        return Set.copyOf(trustedFriends);
    }

    public synchronized boolean trusts(String friendName) {
        return trustedFriends.contains(friendName.toLowerCase(Locale.ROOT));
    }

    public synchronized boolean trust(String friendName) {
        return trustedFriends.add(friendName.toLowerCase(Locale.ROOT));
    }

    public synchronized boolean untrust(String friendName) {
        return trustedFriends.remove(friendName.toLowerCase(Locale.ROOT));
    }

    public boolean requireTwoFactors() {
        return requireTwoFactors;
    }

    public void requireTwoFactors(boolean requireTwoFactors) {
        this.requireTwoFactors = requireTwoFactors;
    }

    public boolean sessionResume() {
        return sessionResume;
    }

    public void sessionResume(boolean sessionResume) {
        this.sessionResume = sessionResume;
    }

    public boolean discordLogin() {
        return discordLogin;
    }

    public void discordLogin(boolean discordLogin) {
        this.discordLogin = discordLogin;
    }

    public String lastAddressHash() {
        return lastAddressHash;
    }

    public long lastLoginAtMillis() {
        return lastLoginAtMillis;
    }

    public void recordLogin(String addressHash, long atMillis) {
        this.lastAddressHash = addressHash;
        this.lastLoginAtMillis = atMillis;
    }

    /** Login methods this account can use right now (Discord depends on a live link, so it is excluded). */
    public Set<AuthMethod> configuredMethods() {
        EnumSet<AuthMethod> methods = EnumSet.noneOf(AuthMethod.class);
        if (password != null) {
            methods.add(AuthMethod.PASSWORD);
        }
        if (totpSecret != null) {
            methods.add(AuthMethod.TOTP);
        }
        if (!recoveryDigests().isEmpty()) {
            methods.add(AuthMethod.RECOVERY_CODE);
        }
        if (pgpKeyRing != null) {
            methods.add(AuthMethod.PGP);
        }
        if (pattern != null) {
            methods.add(AuthMethod.PATTERN);
        }
        if (!trustedFriends().isEmpty()) {
            methods.add(AuthMethod.FRIEND);
        }
        return methods;
    }
}
