package dev.linqfy.bigCasares.modules.mythicdungeons.boss.diablo;

import dev.linqfy.bigCasares.modules.pveboss.AbyssGuardianDefinition;
import dev.linqfy.bigCasares.modules.pveboss.AbyssGuardianDefinitionLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La definicion de Diablo y sus modelos: cada clip que pide el encuentro existe en el modelo. */
class DiabloEncounterTest {

    private static final Path MODELS = Path.of("src/main/resources/bettermodel/models");
    /** Los clips que pide DiabloController o el runtime. */
    private static final List<String> REQUESTED = List.of(
        "idle", "walk", "circle", "throw", "attack", "rage", "sit", "grab", "stunned", "death");

    private static AbyssGuardianDefinition definition() throws Exception {
        try (InputStream stream = DiabloEncounterTest.class.getClassLoader()
            .getResourceAsStream("bosses/myth-diablo.yml")) {
            assertNotNull(stream);
            return new AbyssGuardianDefinitionLoader().load(
                YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    private static List<String> animations(String modelKey) throws Exception {
        String json = Files.readString(MODELS.resolve(modelKey + ".bbmodel"), StandardCharsets.UTF_8);
        int start = json.indexOf("\"animations\"");
        assertTrue(start >= 0, modelKey + " has animations");
        Matcher matcher = Pattern.compile("\"uuid\":\\s*\"[^\"]+\",\\s*\"name\":\\s*\"([a-z_]+)\",\\s*\"loop\"")
            .matcher(json.substring(start));
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    @Test
    void healthScalesWithPlayersAndPhaseTwoIsAtHalf() throws Exception {
        AbyssGuardianDefinition definition = definition();
        assertEquals("myth-diablo", definition.id());
        assertEquals("bigcasares_myth_diablo", definition.javaModelKey());
        assertEquals(0.5, definition.phaseTwoAt());
        assertEquals(700.0, definition.maximumHealthFor(1));
        assertEquals(1600.0, definition.maximumHealthFor(4));
        assertEquals(1600.0, definition.maximumHealthFor(9));
    }

    @Test
    void everyRequestedClipIsDeclaredAndExistsInTheModel() throws Exception {
        AbyssGuardianDefinition definition = definition();
        List<String> clips = animations("bigcasares_myth_diablo");
        for (String clip : REQUESTED) {
            assertNotNull(definition.animationFor(clip), clip);
            assertTrue(clips.contains(clip), "model lacks " + clip + " (has " + clips + ")");
        }
        definition.abilities().forEach(ability -> assertTrue(clips.contains(ability.animationId())));
    }

    @Test
    void extraModelsShip() throws Exception {
        assertEquals(List.of("bigcasares_myth_diablo_bottle", "bigcasares_myth_diablo_hand"),
            DiabloEncounter.extraModelKeys());
        for (String key : DiabloEncounter.extraModelKeys()) {
            assertTrue(animations(key).contains("idle"), key);
        }
        assertNotNull(DiabloEncounter.factory(null));
    }
}
