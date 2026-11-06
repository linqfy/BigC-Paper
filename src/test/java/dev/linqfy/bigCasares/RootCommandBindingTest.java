package dev.linqfy.bigCasares;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every command BigCasaresCommand answers by its own name ("misiones".equalsIgnoreCase(command.getName())) is bound to
 * it and declared in plugin.yml. Without the binding Bukkit has no executor and only echoes the command's usage.
 */
class RootCommandBindingTest {

    @Test
    void everyCommandAnsweredByNameIsBoundAndDeclared() throws IOException {
        String source = Files.readString(Path.of("src/main/java/dev/linqfy/bigCasares/command/BigCasaresCommand.java"),
            StandardCharsets.UTF_8);
        Matcher names = Pattern.compile("\"([a-z]+)\"\\.equalsIgnoreCase\\(command\\.getName\\(\\)\\)").matcher(source);
        List<String> answered = new ArrayList<>();
        while (names.find()) {
            if (!answered.contains(names.group(1))) answered.add(names.group(1));
        }
        assertTrue(answered.containsAll(List.of("misiones", "peligro", "tumba", "baltop", "resourcepack")), answered.toString());

        YamlConfiguration pluginYml = YamlConfiguration.loadConfiguration(new File("src/main/resources/plugin.yml"));
        List<String> unbound = new ArrayList<>();
        for (String name : answered) {
            assertTrue(pluginYml.isConfigurationSection("commands." + name), name + " is declared in plugin.yml");
            if (!BigCasares.ROOT_COMMAND_NAMES.contains(name)) unbound.add(name);
        }
        assertEquals(List.of(), unbound, "commands BigCasaresCommand answers but nobody binds to it");
    }
}
