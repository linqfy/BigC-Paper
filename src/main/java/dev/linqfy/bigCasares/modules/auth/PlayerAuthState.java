package dev.linqfy.bigCasares.modules.auth;

import net.kyori.adventure.text.Component;

import java.net.InetAddress;
import java.util.UUID;

/** Login state of one online player. Only touched on the server thread. */
final class PlayerAuthState {

    final UUID playerId;
    final String name;
    final InetAddress address;
    final String addressKey;
    final long joinedAtMillis;
    AuthAccount account;
    LoginProgress progress;
    boolean authenticated;
    boolean busy;
    int failures;
    boolean allowTeleport;
    org.bukkit.Location returnLocation;
    Component deferredJoinMessage;

    String pgpChallenge;
    long pgpChallengeExpiresAt;
    final StringBuilder pgpBuffer = new StringBuilder();
    OpenPgp.KeyRing pendingRegistrationKey;
    final StringBuilder keyBuffer = new StringBuilder();

    String discordRequestId;
    String discordCode;
    long discordExpiresAt;
    long lastDiscordPromptAt;

    String friendApprover;
    long friendExpiresAt;

    String pendingTotpSecret;
    String pendingPattern;

    PlayerAuthState(UUID playerId, String name, InetAddress address, String addressKey, long joinedAtMillis,
                    AuthAccount account) {
        this.playerId = playerId;
        this.name = name;
        this.address = address;
        this.addressKey = addressKey;
        this.joinedAtMillis = joinedAtMillis;
        this.account = account;
        this.progress = new LoginProgress(account != null && account.requireTwoFactors());
    }

    boolean registered() {
        return account != null;
    }

    void clearDiscord() {
        discordRequestId = null;
        discordCode = null;
        discordExpiresAt = 0L;
    }
}
