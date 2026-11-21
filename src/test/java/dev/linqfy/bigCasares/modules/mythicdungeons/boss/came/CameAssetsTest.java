package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

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

class CameAssetsTest {

    /** Lo que piden el runtime y el controlador a cada uno de los dos señores. */
    private static final List<String> LORD_CLIPS =
        List.of("idle", "walk", "attack", "slam", "dash", "throw", "revive", "rage", "death");

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
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("bosses/myth-came.yml")) {
            assertNotNull(stream);
            return new AbyssGuardianDefinitionLoader().load(
                YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    @Test
    void everyAnimationTheFightRequestsExistsInTheModels() throws IOException {
        AbyssGuardianDefinition definition = definition();
        assertEquals(CameEncounter.BOSS_ID, definition.id());
        assertEquals(CameEncounter.MODEL_KEY, definition.javaModelKey());
        String hun = model(definition.javaModelKey());
        definition.abilities().forEach(ability -> assertClips(hun, "hun", List.of(ability.animationId())));
        for (String clip : LORD_CLIPS) {
            assertNotNull(definition.animationFor(clip), "yaml lacks animation " + clip);
        }
        assertClips(hun, "hun", LORD_CLIPS);
        assertClips(model(CameVucub.MODEL), "vucub", LORD_CLIPS);
        assertClips(model(CameCourt.BALL_MODEL), "ball", List.of("idle", "spin", "death"));
        assertClips(model(CameCourt.HOOP_MODEL), "hoop", List.of("idle", "narrow_1", "narrow_2", "goal", "death"));
        assertEquals(List.of(CameVucub.MODEL, CameCourt.BALL_MODEL, CameCourt.HOOP_MODEL), CameEncounter.extraModelKeys());
    }

    @Test
    void healthScalesForOneToFourPlayers() throws IOException {
        AbyssGuardianDefinition definition = definition();
        assertEquals(720.0, definition.maximumHealthFor(1));
        assertEquals(1680.0, definition.maximumHealthFor(4));
        assertEquals(1680.0, definition.maximumHealthFor(9));
    }
}
