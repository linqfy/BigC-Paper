package dev.linqfy.bigCasares.modules.discord;

import dev.linqfy.bigCasares.modules.moderation.AuditSink;

import java.util.List;

public interface DiscordGateway extends AuditSink {

    void start();

    void stop();

    boolean isHealthy();

    void updateServerStatus(boolean online);

    void updateVisiblePlayers(List<String> playerNames);

    void sendMinecraftChat(String playerName, String message, String avatarUrl);
    
    void sendPrivateMessage(long userId, String message);

    /**
     * Sends a login-approval DM with approve/deny buttons. Returns {@code false} when the gateway cannot deliver
     * it (not connected or not supported), so callers can fall back to other login methods.
     */
    default boolean sendLoginApproval(long userId, String requestId, String message) {
        return false;
    }

    /** Receives button decisions for {@link #sendLoginApproval}; {@code null} removes the handler. */
    default void setLoginApprovalHandler(LoginApprovalHandler handler) {
    }

    @FunctionalInterface
    interface LoginApprovalHandler {
        /** Called from a Discord thread; returns the text that replaces the DM. */
        String onDecision(String requestId, long discordUserId, boolean approved);
    }
}
