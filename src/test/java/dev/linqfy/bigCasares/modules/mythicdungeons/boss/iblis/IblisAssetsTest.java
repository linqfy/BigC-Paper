package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import dev.linqfy.bigCasares.modules.pveboss.AbyssGuardianDefinition;
import dev.linqfy.bigCasares.modules.pveboss.AbyssGuardianDefinitionLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IblisAssetsTest {

    private String model(String key) throws IOException {
        try (InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("bettermodel/models/" + key + ".bbmodel")) {
            assertNotNull(stream, "missing model " + key);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertClips(String json, String key, List<String> clips) {
        for (String clip : clips) {
            assertTrue(json.contains("\"name\":\"" + clip + "\",\"loop\""), key + " lacks animation " + clip);
        }
    }

    private AbyssGuardianDefinition definition() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("bosses/myth-iblis.yml")) {
            assertNotNull(stream);
            return new AbyssGuardianDefinitionLoader().load(
                YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    @Test
    void everyAnimationTheFightRequestsExistsInTheModels() throws IOException {
        AbyssGuardianDefinition definition = definition();
        assertEquals(IblisEncounter.BOSS_ID, definition.id());
        assertEquals(IblisEncounter.MODEL, definition.javaModelKey());
        String iblis = model(definition.javaModelKey());
        definition.abilities().forEach(ability -> assertClips(iblis, "iblis", List.of(ability.animationId())));
        // ciclo de vida del runtime + lo que pide el encuentro
        assertClips(iblis, "iblis", List.of("idle", "walk", "dash", "attack", "trapped", "rage", "death"));
        assertClips(model(IblisFight.SOLOMON_MODEL), "solomon", List.of("idle", "give"));
        assertClips(model(IblisVesselProps.MODEL), "vessel", List.of("idle", "armed", "capture"));
        assertEquals(List.of("bigcasares_myth_iblis_solomon", "bigcasares_myth_iblis_vessel"),
            IblisEncounter.extraModelKeys());
    }

    @Test
    void healthScalesForOneToFourPlayers() throws IOException {
        AbyssGuardianDefinition definition = definition();
        assertEquals(1400.0, definition.maximumHealthFor(1));
        assertEquals(3200.0, definition.maximumHealthFor(4));
        assertEquals(3200.0, definition.maximumHealthFor(9));
    }
}
