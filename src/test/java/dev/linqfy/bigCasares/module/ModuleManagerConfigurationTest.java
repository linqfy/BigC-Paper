package dev.linqfy.bigCasares.module;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleManagerConfigurationTest {

    @Test
    void readsFrameworkV2NestedEnabledFlag() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("modules.resource-pack-system.enabled", false);

        assertFalse(ModuleManager.isEnabledInConfig(config, "resource-pack-system"));
    }

    @Test
    void migratesLegacyShopToggleToEntityShopId() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("modules.shop-system", true);

        assertTrue(ModuleManager.isEnabledInConfig(config, "entity-shop-system"));
    }

    @Test
    void explicitV2ShopFlagOverridesLegacyToggle() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("modules.shop-system", true);
        config.set("modules.entity-shop-system.enabled", false);

        assertFalse(ModuleManager.isEnabledInConfig(config, "entity-shop-system"));
    }

    @Test
    void aServerConfigWithoutTheSwitchUsesTheOneBundledInTheJar() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("modules.tideframe-ui.enabled", false);
        bundled.set("modules.copper-apple", true);
        YamlConfiguration server = new YamlConfiguration();
        server.set("modules.mission-system", true);
        server.setDefaults(bundled);

        assertFalse(ModuleManager.isEnabledInConfig(server, "tideframe-ui"), "shipped off: stays off on upgrade");
        assertTrue(ModuleManager.isEnabledInConfig(server, "copper-apple"), "a bundled flat switch still counts");
        assertTrue(ModuleManager.isEnabledInConfig(server, "unknown-module"), "no switch anywhere: on, as before");

        server.set("modules.tideframe-ui.enabled", true);
        assertTrue(ModuleManager.isEnabledInConfig(server, "tideframe-ui"), "the server's own switch wins");
    }
}
