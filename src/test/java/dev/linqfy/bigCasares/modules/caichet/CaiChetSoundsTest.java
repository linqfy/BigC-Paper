package dev.linqfy.bigCasares.modules.caichet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los eventos de {@link CaiChetSounds} existen en los dos packs y apuntan a archivos que estan. */
class CaiChetSoundsTest {

    private static final Path JAVA = Path.of("resourcepack/java/assets/caichet");
    private static final Path BEDROCK = Path.of("resourcepack/bedrock");
    private static final String PREFIX = "caichet:";
    /** Tope de peso de los sonidos nuevos en un pack. */
    private static final long MAX_BYTES = 2_000_000L;
    /** Las canciones del hotel y del Sueño van aparte (carpeta musica/), con su propio tope. */
    private static final long MAX_MUSIC_BYTES = 7_000_000L;

    @Test
    void everyEventIsDeclaredInTheJavaPackWithExistingFiles() throws IOException {
        JsonObject sounds = json(JAVA.resolve("sounds.json"));
        Set<String> problems = new TreeSet<>();
        for (String event : CaiChetSounds.ALL) {
            String key = event.substring(PREFIX.length());
            if (!sounds.has(key)) {
                problems.add("falta el evento Java " + key);
                continue;
            }
            for (String name : javaFiles(sounds.getAsJsonObject(key))) {
                if (!name.startsWith(PREFIX)) {
                    problems.add(key + ": archivo de otro namespace " + name);
                } else if (!Files.isRegularFile(JAVA.resolve("sounds/" + name.substring(PREFIX.length()) + ".ogg"))) {
                    problems.add(key + ": no existe " + name);
                }
            }
        }
        for (String key : sounds.keySet()) {
            assertTrue(CaiChetSounds.ALL.contains(PREFIX + key), "evento Java sin constante: " + key);
        }
        assertEquals(Set.of(), problems);
    }

    @Test
    void theBedrockPackDefinesTheSameEventsWithExistingFiles() throws IOException {
        JsonObject definitions = json(BEDROCK.resolve("sounds/sound_definitions.json")).getAsJsonObject("sound_definitions");
        JsonObject java = json(JAVA.resolve("sounds.json"));
        Set<String> problems = new TreeSet<>();
        for (String event : CaiChetSounds.ALL) {
            if (!definitions.has(event)) {
                problems.add("falta la definicion Bedrock " + event);
                continue;
            }
            List<String> bedrockFiles = new ArrayList<>();
            for (JsonElement sound : definitions.getAsJsonObject(event).getAsJsonArray("sounds")) {
                String name = sound.getAsJsonObject().get("name").getAsString();
                bedrockFiles.add(name.substring(name.lastIndexOf('/') + 1));
                if (!Files.isRegularFile(BEDROCK.resolve(name + ".ogg"))) {
                    problems.add(event + ": no existe " + name);
                }
            }
            List<String> javaFiles = new ArrayList<>();
            for (String name : javaFiles(java.getAsJsonObject(event.substring(PREFIX.length())))) {
                String bare = name.substring(PREFIX.length());
                javaFiles.add(bare.substring(bare.lastIndexOf('/') + 1));
            }
            if (!new HashSet<>(javaFiles).equals(new HashSet<>(bedrockFiles))) {
                problems.add(event + ": Java " + javaFiles + " y Bedrock " + bedrockFiles + " no coinciden");
            }
        }
        assertEquals(Set.of(), problems);
    }

    @Test
    void newSoundsStayUnderTheSizeBudgetInEachPack() throws IOException {
        long javaMusic = size(JAVA.resolve("sounds/musica"));
        long bedrockMusic = size(BEDROCK.resolve("sounds/caichet/musica"));
        assertTrue(size(JAVA.resolve("sounds")) - javaMusic <= MAX_BYTES, "pack Java demasiado pesado");
        assertTrue(size(BEDROCK.resolve("sounds/caichet")) - bedrockMusic <= MAX_BYTES, "pack Bedrock demasiado pesado");
        assertTrue(javaMusic <= MAX_MUSIC_BYTES, "musica demasiado pesada");
        assertEquals(size(JAVA.resolve("sounds")), size(BEDROCK.resolve("sounds/caichet")));
    }

    @Test
    void eventNamesAreUnique() {
        assertEquals(CaiChetSounds.ALL.size(), new HashSet<>(CaiChetSounds.ALL).size());
        for (String event : CaiChetSounds.ALL) {
            assertTrue(event.startsWith(PREFIX), event);
        }
    }

    private static List<String> javaFiles(JsonObject event) {
        List<String> names = new ArrayList<>();
        for (JsonElement sound : event.getAsJsonArray("sounds")) {
            names.add(sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString());
        }
        return names;
    }

    private static long size(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            long total = 0;
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                total += Files.size(file);
            }
            return total;
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
