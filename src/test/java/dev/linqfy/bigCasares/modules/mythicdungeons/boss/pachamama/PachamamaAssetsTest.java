package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

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

class PachamamaAssetsTest {

    private static final Path MODELS = Path.of("src/main/resources/bettermodel/models");
    /** Clips the encounter requests by name (PachamamaFight) plus the runtime's lifecycle clips. */
    private static final List<String> REQUESTED = List.of("idle", "walk", "attack", "cast", "rise", "rage", "death");
    /** Clips PachamamaMandinga and PachamamaFight request on Mandinga. */
    private static final List<String> MANDINGA = List.of("idle", "emerge", "strum", "offer");

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
        try (InputStream stream = PachamamaAssetsTest.class.getClassLoader()
            .getResourceAsStream("bosses/myth-pachamama.yml")) {
            assertNotNull(stream);
            return new AbyssGuardianDefinitionLoader().load(
                YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    @Test
    void everyClipUsedByTheYamlAndTheEncounterExistsInTheModel() throws Exception {
        AbyssGuardianDefinition definition = definition();
        assertEquals(PachamamaEncounter.BOSS_ID, definition.id());
        assertEquals(PachamamaEncounter.MODEL_KEY, definition.javaModelKey());
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
    void mandingaIsAnExtraModelWithItsClips() throws Exception {
        assertEquals(List.of("bigcasares_myth_pachamama_mandinga"), PachamamaEncounter.extraModelKeys());
        assertTrue(animations(PachamamaMandinga.MODEL).containsAll(MANDINGA));
    }

    @Test
    void theFactoryDoesNotTouchItsSupportUntilAnInstanceStarts() {
        assertNotNull(PachamamaEncounter.factory(null));
    }
}
