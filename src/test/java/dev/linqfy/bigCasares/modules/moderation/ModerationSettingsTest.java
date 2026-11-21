package dev.linqfy.bigCasares.modules.moderation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ModerationSettingsTest {

    @Test
    void existingConfigWithPointsStillTreatsNoisyRulesAsAuditOnly() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation-system.signals.rare-item-gain.points", 25);

        ModerationSettings settings = ModerationSettings.load(config);

        assertEquals(0, settings.pointsFor("rare-item-gain", 25));
        assertEquals(0, settings.pointsFor("ip-sudden-change", 100));
        assertEquals(25, settings.pointsFor("movement-speed", 1));
        assertEquals(30, settings.pointsFor("mutango-boots-flight", 30));
    }

    @Test
    void explicitListAndZeroPointsAreHonored() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation-system.audit-only-rules", List.of("chat-rate"));
        config.set("moderation-system.signals.melee-rate.points", 0);

        ModerationSettings settings = ModerationSettings.load(config);

        assertEquals(0, settings.pointsFor("chat-rate", 20));
        assertEquals(25, settings.pointsFor("rare-item-gain", 1));
        assertEquals(0, settings.pointsFor("melee-rate", 25));
    }
}
