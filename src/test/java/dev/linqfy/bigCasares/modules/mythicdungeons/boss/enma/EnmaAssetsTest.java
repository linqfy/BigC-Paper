package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enma;

import dev.linqfy.bigCasares.modules.pveboss.AbyssGuardianDefinition;
import dev.linqfy.bigCasares.modules.pveboss.AbyssGuardianDefinitionLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El YAML, los .bbmodel empaquetados y lo que pide el encuentro coinciden. */
class EnmaAssetsTest {

    private static final Pattern ANIMATION = Pattern.compile("\"name\":\"([a-z_]+)\",\"loop\"");
    /** Lo que pide EnmaController (ademas de idle/rage/death que pide el runtime). */
    private static final List<String> CONTROLLER_CLIPS = List.of("idle", "walk", "attack", "cast", "mirror", "slam",
        "spin", "rage", "death");
    /** Lo que pide EnmaMirror / EnmaController al espejo. */
    private static final List<String> MIRROR_CLIPS = List.of("idle", "summon", "exposed", "death");

    @Test
    void everyAnimationTheFightRequestsExistsInTheModels() throws IOException {
        AbyssGuardianDefinition definition = definition();
        assertEquals(EnmaEncounter.BOSS_ID, definition.id());
        assertEquals(EnmaEncounter.MODEL_KEY, definition.javaModelKey());
        Set<String> enma = animations(definition.javaModelKey());
        assertTrue(enma.containsAll(CONTROLLER_CLIPS), enma.toString());
        definition.abilities().forEach(ability -> assertTrue(enma.contains(ability.animationId()), ability.id()));
        try (InputStream stream = resource("bosses/myth-enma.yml")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            Set<String> declared = yaml.getConfigurationSection("animations").getKeys(false);
            assertTrue(enma.containsAll(declared), "yaml " + declared + " vs model " + enma);
            assertTrue(declared.containsAll(CONTROLLER_CLIPS), declared.toString());
        }
        Set<String> mirror = animations(EnmaMirror.MODEL);
        assertTrue(mirror.containsAll(MIRROR_CLIPS), mirror.toString());
        assertEquals(List.of("bigcasares_myth_enma_mirror"), EnmaEncounter.extraModelKeys());
    }

    @Test
    void healthScalesForOneToFourPlayers() throws IOException {
        AbyssGuardianDefinition definition = definition();
        assertEquals(1500.0, definition.maximumHealthFor(1));
        assertEquals(3000.0, definition.maximumHealthFor(4));
        assertEquals(3000.0, definition.maximumHealthFor(9));
    }

    private AbyssGuardianDefinition definition() throws IOException {
        try (InputStream stream = resource("bosses/myth-enma.yml")) {
            return new AbyssGuardianDefinitionLoader().load(
                YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    private Set<String> animations(String modelKey) throws IOException {
        try (InputStream stream = resource("bettermodel/models/" + modelKey + ".bbmodel")) {
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> names = new HashSet<>();
            Matcher matcher = ANIMATION.matcher(json);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
            return names;
        }
    }

    private InputStream resource(String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "missing " + path);
        return stream;
    }
}
