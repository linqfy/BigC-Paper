package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Que el modulo este declarado donde BigC lo busca: config, comando, permiso y archivos del pack. */
class MutangoModuleWiringTest {

    private static YamlConfiguration yaml(String resource) throws IOException {
        try (InputStream stream = MutangoModuleWiringTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "falta " + resource);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    @Test
    void moduleIdIsStable() {
        assertEquals("mutango", new MutangoModule(null).getId());
        assertEquals("mutango", MutangoModule.MODULE_ID);
    }

    @Test
    void enablingWithoutAPluginIsANoOp() {
        MutangoModule module = new MutangoModule(null);
        module.onEnable();
        module.onDisable();
    }

    @Test
    void theModuleSwitchIsDeclaredAndOnByDefault() throws IOException {
        assertTrue(yaml("config.yml").getBoolean("modules." + MutangoModule.MODULE_ID + ".enabled"));
    }

    @Test
    void theCommandAndItsAdminPermissionAreDeclared() throws IOException {
        YamlConfiguration plugin = yaml("plugin.yml");
        assertEquals("bigcasares.mutango.admin", plugin.getString("commands.mutango.permission"));
        assertEquals("op", plugin.getString("permissions.bigcasares.mutango.admin.default"));
        assertTrue(plugin.getBoolean("permissions.bigcasares.*.children.bigcasares.mutango.admin"));
    }

    @Test
    void ownConfigurationShipsWithItsDefaults() throws IOException {
        YamlConfiguration config = yaml(MutangoModule.CONFIG_FILE);
        assertEquals("auto", config.getString("bedrock.modelos-3d"));
        assertTrue(config.getBoolean("bedrock.items-personalizados"));
        assertEquals("titulo", config.getString("bedrock.texto-en-pantalla"));
    }

    @Test
    void theRewardTexturesLiveInTheBigCasaresJavaPack() {
        Path assets = Path.of("resourcepack/java/assets/mutango");
        for (String relic : new String[] {MutangoRelics.BOOTS, MutangoRelics.CROSS}) {
            assertTrue(Files.isRegularFile(assets.resolve("items/" + relic + ".json")), relic + " item definition");
            assertTrue(Files.isRegularFile(assets.resolve("models/item/" + relic + ".json")), relic + " model");
            assertTrue(Files.isRegularFile(assets.resolve("textures/item/" + relic + ".png")), relic + " texture");
        }
    }

    @Test
    void theBedrockRewardPackShipsInTheJar() {
        for (String file : MutangoRelics.BEDROCK_PACK_FILES) {
            assertNotNull(MutangoModuleWiringTest.class.getClassLoader()
                .getResource("mutango/relics-bedrock/" + file), file);
        }
    }
}
