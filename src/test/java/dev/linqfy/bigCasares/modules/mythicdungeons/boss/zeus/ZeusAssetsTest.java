package dev.linqfy.bigCasares.modules.mythicdungeons.boss.zeus;

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

class ZeusAssetsTest {

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

    @Test
    void everyAnimationTheFightRequestsExistsInTheModels() throws IOException {
        AbyssGuardianDefinition definition;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("bosses/myth-zeus.yml")) {
            assertNotNull(stream);
            definition = new AbyssGuardianDefinitionLoader().load(
                YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
        assertEquals("myth-zeus", definition.id());
        assertEquals("bigcasares_myth_zeus", definition.javaModelKey());
        String zeus = model(definition.javaModelKey());
        definition.abilities().forEach(ability -> assertClips(zeus, "zeus", List.of(ability.animationId())));
        // ciclo de vida del runtime + lo que pide el controlador
        assertClips(zeus, "zeus", List.of("idle", "rage", "death", "walk", "attack", "cast", "lanza", "vulnerable"));
        assertClips(model(ZeusClouds.MODEL), "cloud", List.of("idle", "walk", "attack", "death"));
        assertClips(model(ZeusController.SPEAR_MODEL), "spear", List.of("idle", "stuck", "death"));
        assertEquals(List.of(ZeusClouds.MODEL, ZeusController.SPEAR_MODEL), ZeusEncounter.extraModelKeys());
    }

    @Test
    void healthScalesForOneToFourPlayers() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("bosses/myth-zeus.yml")) {
            AbyssGuardianDefinition definition = new AbyssGuardianDefinitionLoader().load(
                YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
            assertEquals(1600.0, definition.maximumHealthFor(1));
            assertEquals(3400.0, definition.maximumHealthFor(4));
            assertEquals(3400.0, definition.maximumHealthFor(9));
        }
    }
}
