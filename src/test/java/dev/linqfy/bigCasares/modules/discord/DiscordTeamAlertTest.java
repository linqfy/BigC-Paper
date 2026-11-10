package dev.linqfy.bigCasares.modules.discord;

import dev.linqfy.bigCasares.modules.moderation.AuditEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordTeamAlertTest {

    @Test
    void teamAlertsFallBackToDirectMessages() {
        List<String> sent = new ArrayList<>();
        DiscordGateway gateway = new DiscordGateway() {
            @Override public void start() { }
            @Override public void stop() { }
            @Override public boolean isHealthy() { return true; }
            @Override public void updateServerStatus(boolean online) { }
            @Override public void updateVisiblePlayers(List<String> playerNames) { }
            @Override public void sendMinecraftChat(String playerName, String message, String avatarUrl) { }
            @Override public void sendPrivateMessage(long userId, String message) { sent.add(userId + ":" + message); }
            @Override public void publish(AuditEvent event) { }
        };

        gateway.sendTeamAlert("team", "ABC", new LinkedHashSet<>(List.of(1L, 2L)), "raid");

        assertEquals(List.of("1:raid", "2:raid"), sent);
    }

    @Test
    void teamChannelNamesAreDiscordSafe() {
        assertEquals("raid-abc", JdaDiscordGateway.teamChannelName("ABC"));
        assertEquals("raid-los-pibes", JdaDiscordGateway.teamChannelName("§cLos  Pibes!"));
        assertEquals("raid-equipo", JdaDiscordGateway.teamChannelName("§§"));
        assertEquals("raid-equipo", JdaDiscordGateway.teamChannelName(null));
    }

    @Test
    void raidAlertCategoryIsOptional() {
        YamlConfiguration yaml = new YamlConfiguration();
        assertEquals(0L, DiscordSettings.load(yaml).raidAlertCategoryId());
        yaml.set("discord-integration.raid-alerts.category-id", 123L);
        assertEquals(123L, DiscordSettings.load(yaml).raidAlertCategoryId());
    }
}
