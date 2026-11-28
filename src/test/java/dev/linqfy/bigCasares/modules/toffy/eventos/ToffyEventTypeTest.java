package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyEventTypeTest {

    @Test
    void parsesIdsAndAliases() {
        assertEquals(Optional.of(ToffyEventType.TNT_TAG), ToffyEventType.parse("TNT-Tag"));
        assertEquals(Optional.of(ToffyEventType.TNT_TAG), ToffyEventType.parse("tnt_tag"));
        assertEquals(Optional.of(ToffyEventType.KOTH), ToffyEventType.parse("koth"));
        assertEquals(Optional.of(ToffyEventType.SPLEEF), ToffyEventType.parse("Spleef"));
        assertEquals(Optional.of(ToffyEventType.LIFESTEAL), ToffyEventType.parse("lifesteal"));
        assertEquals(Optional.empty(), ToffyEventType.parse("parkour"));
        assertEquals(Optional.empty(), ToffyEventType.parse(null));
    }

    @Test
    void onlyLifestealPlaysInTheNormalWorlds() {
        assertFalse(ToffyEventType.LIFESTEAL.arena());
        assertTrue(ToffyEventType.KOTH.arena());
        assertTrue(ToffyEventType.TNT_TAG.arena());
        assertTrue(ToffyEventType.SPLEEF.arena());
    }
}
