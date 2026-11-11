package dev.linqfy.bigCasares.modules.missions;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionTargetsTest {

    @Test
    void matchesTargetIgnoringCaseSpacesAndColours() {
        assertTrue(MissionTargets.matches("ghi ghi", List.of("GhiGhi")));
        assertTrue(MissionTargets.matches("ghi ghi", List.of("§cGhi_Ghi")));
        assertTrue(MissionTargets.matches("jeremy", List.of("zombie", "jeremy")));
        assertFalse(MissionTargets.matches("ghi ghi", List.of("ghi")));
        assertFalse(MissionTargets.matches(null, List.of("")));
    }

    @Test
    void parsesWhisperCommands() {
        assertEquals("Pepe", MissionTargets.whisperTarget("/msg Pepe hola"));
        assertEquals("Pepe", MissionTargets.whisperTarget("/minecraft:tell Pepe hola que tal"));
        assertEquals("Pepe", MissionTargets.whisperTarget("/W Pepe 67"));
        assertNull(MissionTargets.whisperTarget("/msg Pepe"));
        assertNull(MissionTargets.whisperTarget("/spawn Pepe hola"));
    }

    @Test
    void recognisesHelmets() {
        assertTrue(MissionTargets.isHelmet(Material.IRON_HELMET));
        assertTrue(MissionTargets.isHelmet(Material.TURTLE_HELMET));
        assertFalse(MissionTargets.isHelmet(Material.CARVED_PUMPKIN));
    }
}
