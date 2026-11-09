package dev.linqfy.bigCasares.modules.staffevents;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffEventTypeTest {

    @Test
    void parsesIdsAndAliases() {
        assertEquals(Optional.of(StaffEventType.TNT_TAG), StaffEventType.parse("TNT-Tag"));
        assertEquals(Optional.of(StaffEventType.TNT_TAG), StaffEventType.parse("tnt_tag"));
        assertEquals(Optional.of(StaffEventType.KOTH), StaffEventType.parse("koth"));
        assertEquals(Optional.of(StaffEventType.SPLEEF), StaffEventType.parse("Spleef"));
        assertEquals(Optional.of(StaffEventType.LIFESTEAL), StaffEventType.parse("lifesteal"));
        assertEquals(Optional.empty(), StaffEventType.parse("parkour"));
        assertEquals(Optional.empty(), StaffEventType.parse(null));
    }

    @Test
    void onlyLifestealPlaysInTheNormalWorlds() {
        assertFalse(StaffEventType.LIFESTEAL.arena());
        assertTrue(StaffEventType.KOTH.arena());
        assertTrue(StaffEventType.TNT_TAG.arena());
        assertTrue(StaffEventType.SPLEEF.arena());
    }

    @Test
    void moduleIdIsStable() {
        assertEquals("staff-events", new StaffEventsModule(null, null, null).getId());
    }
}
