package dev.linqfy.bigCasares.modules.dupes;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DupeRestorePermissionTest {
    @Test void commandUsesNamespacedPermissionAndOldGrantsStillInheritIt() throws Exception {
        Map<?, ?> descriptor = new Yaml().load(Files.readString(Path.of("src/main/resources/plugin.yml")));
        var commands = (Map<?, ?>) descriptor.get("commands");
        assertEquals("bigcasares.duperestore.admin", ((Map<?, ?>) commands.get("duperestore")).get("permission"));
        var permissions = (Map<?, ?>) descriptor.get("permissions");
        var canonical = (Map<?, ?>) permissions.get("bigcasares.duperestore.admin");
        assertEquals("op", canonical.get("default"));
        var legacy = (Map<?, ?>) permissions.get("duperestore.admin");
        assertEquals(false, legacy.get("default"));
        assertEquals(true, ((Map<?, ?>) legacy.get("children")).get("bigcasares.duperestore.admin"));
        var administrator = (Map<?, ?>) permissions.get("bigcasares.*");
        assertEquals(true, ((Map<?, ?>) administrator.get("children")).get("bigcasares.duperestore.admin"));
    }
}
