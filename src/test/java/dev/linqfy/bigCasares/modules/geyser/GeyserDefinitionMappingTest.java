package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.platform.ClientEntityPresentationRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeyserDefinitionMappingTest {

    @Test
    void customEntityMappingRequiresBothTheJavaTypeAndPdcMarker() {
        GeyserCustomEntityDefinition nexus = new GeyserCustomEntityDefinition(
            "GHAST", "bigcasares:nexus", "nexus_id");

        assertTrue(nexus.matches("GHAST", Set.of("nexus_id", "nexus_visual_role")));
        assertFalse(nexus.matches("GHAST", Set.of()));
        assertFalse(nexus.matches("WARDEN", Set.of("nexus_id")));
    }

    @Test
    void customItemMappingSeparatesBaseItemModelAndBedrockIdentifier() {
        GeyserCustomItemDefinition item = new GeyserCustomItemDefinition(
            "minecraft:apple",
            "bigcasares:copper_apple",
            "bigcasares:copper_apple",
            "bigcasares.copper_apple",
            "Copper Apple"
        );

        assertEquals("minecraft:apple", item.javaBaseIdentifier());
        assertEquals("bigcasares:copper_apple", item.javaModelIdentifier());
    }

    @Test
    void entityMarkersCanBeReadFromGeyserThreadsWithoutTouchingBukkit() {
        UUID entityId = UUID.randomUUID();

        ClientEntityPresentationRegistry.register(entityId, "GHAST", "nexus_id");

        var marker = ClientEntityPresentationRegistry.find(entityId).orElseThrow();
        assertEquals("GHAST", marker.javaEntityType());
        assertEquals(Set.of("nexus_id"), marker.markerKeys());
        ClientEntityPresentationRegistry.unregister(entityId);
        assertTrue(ClientEntityPresentationRegistry.find(entityId).isEmpty());
    }
}
