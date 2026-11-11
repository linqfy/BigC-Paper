package dev.linqfy.bigCasares.modules.missions;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionCatalogLoaderTest {

    @Test
    void loadsEnabledMissionDefinitionsFromConfig() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("mission-system.missions.daily-bed.scope", "daily");
        config.set("mission-system.missions.daily-bed.title", "Salto de cama");
        config.set("mission-system.missions.daily-bed.description", "Agachate 5 veces arriba de una cama ocupada.");
        config.set("mission-system.missions.daily-bed.type", "CROUCH_ON_SLEEPING_BED");
        config.set("mission-system.missions.daily-bed.goal", 5);
        config.set("mission-system.missions.daily-bed.reward", 250.0);
        config.set("mission-system.missions.daily-bed.weight", 1);
        config.set("mission-system.missions.daily-bed.enabled", true);

        MissionCatalog catalog = new MissionCatalogLoader().load(config);

        assertEquals(1, catalog.byScope(MissionScope.DAILY).size());
        assertEquals("Salto de cama", catalog.byScope(MissionScope.DAILY).getFirst().title());
    }

    @Test
    void includesBundledDefaultMissionsMissingFromServerConfig() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("mission-system.missions.weekly-new.scope", "weekly");
        defaults.set("mission-system.missions.weekly-new.type", "PLACE_BLOCK_COUNT");
        defaults.set("mission-system.missions.weekly-new.goal", 320);
        defaults.set("mission-system.missions.weekly-new.reward", 1500.0);
        defaults.set("mission-system.missions.weekly-new.params.material", "POPPY");
        defaults.set("mission-system.missions.daily-old.scope", "daily");
        defaults.set("mission-system.missions.daily-old.type", "WAX_BLOCK_COUNT");
        defaults.set("mission-system.missions.daily-old.goal", 16);
        defaults.set("mission-system.missions.daily-old.reward", 100.0);

        YamlConfiguration config = new YamlConfiguration();
        config.set("mission-system.missions.daily-old.scope", "daily");
        config.set("mission-system.missions.daily-old.type", "WAX_BLOCK_COUNT");
        config.set("mission-system.missions.daily-old.goal", 16);
        config.set("mission-system.missions.daily-old.reward", 100.0);
        config.set("mission-system.missions.daily-old.enabled", false);
        config.setDefaults(defaults);

        MissionCatalog catalog = new MissionCatalogLoader().load(config);

        assertEquals(1, catalog.byScope(MissionScope.WEEKLY).size());
        assertEquals("POPPY", catalog.byScope(MissionScope.WEEKLY).getFirst().params().get("material"));
        assertEquals(0, catalog.byScope(MissionScope.DAILY).size(), "server override keeps the mission disabled");
    }
}
