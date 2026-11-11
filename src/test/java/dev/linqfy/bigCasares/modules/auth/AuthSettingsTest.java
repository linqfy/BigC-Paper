package dev.linqfy.bigCasares.modules.auth;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AuthSettingsTest {

    @Test
    void bundledConfigEnablesModuleWithSafeDefaults() throws Exception {
        YamlConfiguration config;
        try (var reader = new InputStreamReader(
            AuthSettingsTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            config = YamlConfiguration.loadConfiguration(reader);
        }
        AuthSettings settings = AuthSettings.load(config);

        assertTrue(config.getBoolean("modules.auth-system.enabled"));
        assertEquals("bigcasares_auth_limbo", settings.limboWorld());
        assertEquals(210_000, settings.pbkdf2Iterations());
        assertEquals(6 * 3_600_000L, settings.sessionResumeMillis());
        assertFalse(settings.premium().enabled(), "premium needs a port and accepts-transfers, so it is opt-in");
        assertEquals(25599, settings.premium().publicPort());
    }

    @Test
    void missingSectionFallsBackToDefaults() {
        AuthSettings settings = AuthSettings.load(new YamlConfiguration());

        assertEquals(120, settings.loginTimeoutSeconds());
        assertTrue(settings.discordEnabled());
        assertEquals("https://sessionserver.mojang.com", settings.premium().sessionServer());
    }
}
