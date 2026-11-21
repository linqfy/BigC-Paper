package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los clips que piden el YAML y el encuentro existen con el mismo nombre en los .bbmodel empaquetados. */
class OdinModelContractTest {

    private static final Pattern ANIMATION = Pattern.compile("\"name\":\"([a-z_]+)\",\"loop\"");
    /** Los que pide OdinController (ademas de idle/death que pide el runtime). */
    private static final List<String> CONTROLLER_CLIPS = List.of("idle", "cast", "rage", "idle_casco", "walk", "carga",
        "embestida", "stunned", "death");

    @Test
    void theBossModelHasEveryClipOfTheYamlAndTheEncounter() throws IOException {
        Set<String> clips = animations("bigcasares_myth_odin");
        assertTrue(clips.containsAll(CONTROLLER_CLIPS), clips.toString());
        try (InputStream stream = resource("bosses/myth-odin.yml")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            Set<String> declared = yaml.getConfigurationSection("animations").getKeys(false);
            assertTrue(clips.containsAll(declared), "yaml " + declared + " vs model " + clips);
            for (String ability : yaml.getConfigurationSection("abilities").getKeys(false)) {
                assertTrue(clips.contains(yaml.getString("abilities." + ability + ".animation")), ability);
            }
        }
    }

    @Test
    void everyGuardianModelShipsWithItsMobClips() throws IOException {
        for (String key : OdinEncounter.extraModelKeys()) {
            Set<String> clips = animations(key);
            assertTrue(clips.containsAll(Set.of("idle", "walk", "attack", "death")), key + " " + clips);
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
