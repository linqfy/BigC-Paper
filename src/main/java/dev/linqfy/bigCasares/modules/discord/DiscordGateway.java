package dev.linqfy.bigCasares.modules.discord;

import dev.linqfy.bigCasares.modules.moderation.AuditSink;

import java.util.List;
import java.util.Set;

public interface DiscordGateway extends AuditSink {

    void start();

    void stop();

    boolean isHealthy();

    void updateServerStatus(boolean online);

    void updateVisiblePlayers(List<String> playerNames);

    void sendMinecraftChat(String playerName, String message, String avatarUrl);
    
    void sendPrivateMessage(long userId, String message);

    /**
     * Alerta privada para un equipo. Por defecto llega por mensaje directo a cada miembro vinculado;
     * el gateway de Discord la publica en el canal privado del equipo cuando hay categoría configurada.
     *
     * @param teamKey   identificador estable del equipo
     * @param teamTag   etiqueta visible, usada para nombrar el canal
     * @param memberIds IDs de Discord de los miembros vinculados
     */
    default void sendTeamAlert(String teamKey, String teamTag, Set<Long> memberIds, String message) {
        for (long memberId : memberIds) {
            sendPrivateMessage(memberId, message);
        }
    }
}
