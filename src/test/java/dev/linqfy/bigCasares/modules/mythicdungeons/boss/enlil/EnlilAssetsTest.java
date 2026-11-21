package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.pveboss.AbyssGuardianDefinition;
import dev.linqfy.bigCasares.modules.pveboss.AbyssGuardianDefinitionLoader;
import dev.linqfy.bigCasares.modules.pveboss.BossAbilityDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnlilAssetsTest {

    private static final Path MODELS = Path.of("src/main/resources/bettermodel/models");
    /** Clips the encounter requests by name (EnlilFight) plus the runtime's lifecycle clips. */
    private static final List<String> REQUESTED = List.of(
        "idle", "walk", "cast", "tornado", "descend", "land", "ascend", "rage", "fall", "death");

    private static Set<String> animations(String key) throws Exception {
        JsonObject model = JsonParser.parseString(Files.readString(MODELS.resolve(key + ".bbmodel"))).getAsJsonObject();
        assertEquals(key, model.get("name").getAsString());
        Set<String> names = new HashSet<>();
        for (JsonElement animation : model.getAsJsonArray("animations")) {
            names.add(animation.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    private static AbyssGuardianDefinition definition() throws Exception {
        try (InputStream stream = EnlilAssetsTest.class.getClassLoader().getResourceAsStream("bosses/myth-enlil.yml")) {
            assertNotNull(stream);
            return new AbyssGuardianDefinitionLoader().load(
                YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    @Test
    void everyClipUsedByTheYamlAndTheEncounterExistsInTheModel() throws Exception {
        AbyssGuardianDefinition definition = definition();
        assertEquals("bigcasares_myth_enlil", definition.javaModelKey());
        Set<String> clips = animations(definition.javaModelKey());
        assertTrue(clips.containsAll(REQUESTED), "model clips " + clips);
        for (BossAbilityDefinition ability : definition.abilities()) {
            assertTrue(clips.contains(ability.animationId()), ability.id());
            assertNotNull(definition.animationFor(ability.animationId()), ability.id());
        }
        for (String clip : REQUESTED) {
            assertNotNull(definition.animationFor(clip), "YAML animation " + clip);
        }
    }

    @Test
    void theTabletModelIsAnExtraModelWithItsClips() throws Exception {
        assertEquals(List.of(EnlilTablets.MODEL), EnlilEncounter.extraModelKeys());
        assertTrue(animations(EnlilTablets.MODEL).containsAll(List.of("idle", "break", "reform")));
    }

    @Test
    void theFactoryDoesNotTouchItsSupportUntilAnInstanceStarts() {
        assertNotNull(EnlilEncounter.factory(null));
    }
}
