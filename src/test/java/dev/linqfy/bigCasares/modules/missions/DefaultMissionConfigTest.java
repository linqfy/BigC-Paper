package dev.linqfy.bigCasares.modules.missions;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMissionConfigTest {

    @Test
    void bundledMissionsLoadWithValidTypesAndGoals() throws Exception {
        MissionCatalog catalog = new MissionCatalogLoader().load(bundledConfig());

        assertFalse(catalog.all().isEmpty());
        for (MissionDefinition definition : catalog.all()) {
            assertTrue(definition.goal() > 0, definition.id() + " needs a positive goal");
            assertTrue(definition.reward() > 0, definition.id() + " needs a reward");
        }
    }

    @Test
    void bundledConfigShipsCommunityMissions() throws Exception {
        Map<String, MissionDefinition> byId = new MissionCatalogLoader().load(bundledConfig()).all().stream()
            .collect(Collectors.toMap(MissionDefinition::id, definition -> definition));

        assertEquals(MissionType.ADMIN_APPROVAL, byId.get("weekly-admin-idea").type());
        assertEquals("jeremy", byId.get("daily-kiss-jeremy").params().get("target"));
        assertEquals(5000.0, byId.get("weekly-kill-ghi-ghi").reward());
        assertEquals(320, byId.get("weekly-poppy-5-stacks").goal());
        assertEquals(MissionType.KILL_SAME_TEAMMATE, byId.get("weekly-clan-kill-20").type());
        assertEquals(67, byId.get("daily-whisper-spam-67").goal());
        assertEquals(MissionType.BREAK_CURSED_HELMET, byId.get("daily-break-cursed-helmet").type());
        assertEquals(67, byId.get("daily-horse-67").goal());
    }

    private static YamlConfiguration bundledConfig() throws Exception {
        var stream = DefaultMissionConfigTest.class.getResourceAsStream("/config.yml");
        assertNotNull(stream);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
}
