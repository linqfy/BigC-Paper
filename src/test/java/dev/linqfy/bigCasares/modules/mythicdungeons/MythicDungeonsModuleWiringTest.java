package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.Material;
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

/** Que el modulo este declarado donde BigC lo busca: switch, comando, permisos y su configuracion. */
class MythicDungeonsModuleWiringTest {

    private static YamlConfiguration yaml(String resource) throws IOException {
        try (InputStream stream = MythicDungeonsModuleWiringTest.class.getClassLoader()
            .getResourceAsStream(resource)) {
            assertNotNull(stream, "falta " + resource);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    @Test
    void moduleIdIsStableAndEnablingWithoutAPluginIsANoOp() {
        MythicDungeonsModule module = new MythicDungeonsModule(null, null, null, null, null);
        assertEquals("mythic-dungeons", module.getId());
        module.onEnable();
        module.onDisable();
        module.onDisable();
    }

    @Test
    void theModuleSwitchIsDeclaredAndOnByDefault() throws IOException {
        YamlConfiguration config = yaml("config.yml");
        assertTrue(config.isSet("modules." + MythicDungeonsModule.MODULE_ID + ".enabled"));
        assertTrue(config.getBoolean("modules." + MythicDungeonsModule.MODULE_ID + ".enabled"));
    }

    @Test
    void theCommandAndItsPermissionsAreDeclared() throws IOException {
        YamlConfiguration plugin = yaml("plugin.yml");
        assertTrue(plugin.isConfigurationSection("commands." + MythicDungeonsModule.COMMAND));
        // the command itself is open so that /mazmorra creditos works; the executor checks each subcommand
        assertEquals(null, plugin.getString("commands.mazmorra.permission"));
        assertEquals("op", plugin.getString("permissions." + MythicDungeonCommand.ADMIN_PERMISSION + ".default"));
        assertTrue(plugin.getBoolean("permissions.bigcasares.*.children." + MythicDungeonCommand.ADMIN_PERMISSION));
        assertTrue(plugin.getBoolean("permissions." + MythicDungeonCommand.CREDITS_PERMISSION + ".default"));
    }

    @Test
    void theShippedConfigurationLoadsWithoutWarnings() throws IOException {
        MythicDungeonSettings settings = MythicDungeonSettings.load(yaml(MythicDungeonsModule.CONFIG_FILE));
        assertEquals(List.of(), settings.warnings());
        assertEquals(Material.LODESTONE, settings.altarMaterial());
        assertEquals(27, settings.mobs().all().size());
        for (MythicCulture culture : MythicCulture.bossCultures()) {
            assertTrue(settings.rewardFor(culture).xp() > 0, culture.id());
            assertTrue(!settings.rewardFor(culture).items().isEmpty(), culture.id());
        }
    }

    @Test
    void invalidSettingsFallBackWithAWarning() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("altar.material", "NOT_A_BLOCK");
        yaml.set("spawns.natural-replace-chance", 3.0);
        yaml.set("rewards.myth-zeus.items", List.of("DIAMOND:2", "BANANA:1", "EMERALD:x"));
        MythicDungeonSettings settings = MythicDungeonSettings.load(yaml);
        assertEquals(MythicDungeonSettings.DEFAULT_ALTAR, settings.altarMaterial());
        assertEquals(0.6, settings.naturalReplaceChance());
        assertEquals(1, settings.rewardFor(MythicCulture.GREEK).items().size());
        assertEquals(3, settings.warnings().size());
        assertEquals(1500, settings.rewardFor(MythicCulture.GREEK).xpFor(1));
        assertEquals(1125, settings.rewardFor(MythicCulture.GREEK).xpFor(2));
        assertEquals(750, settings.rewardFor(MythicCulture.GREEK).xpFor(3));
    }

    @Test
    void creditsPrintEvenWhenEmpty() {
        assertTrue(MythicCredits.lines().size() >= 2);
        assertEquals(MythicCredits.entries().isEmpty() ? 2 : 1 + 2 * MythicCredits.entries().size(),
            MythicCredits.lines().size());
    }
}
