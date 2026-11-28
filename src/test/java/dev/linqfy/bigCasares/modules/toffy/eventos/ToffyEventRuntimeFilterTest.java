package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyEventRuntimeFilterTest {

    @Test
    void theSignUpCommandAndToffyAndItsAliasStayUsableDuringAnEvent() {
        assertTrue(ToffyEventRuntime.allowedDuringEvent("anotar"));
        assertTrue(ToffyEventRuntime.allowedDuringEvent("toffy"));
        assertTrue(ToffyEventRuntime.allowedDuringEvent("tcomp"));
    }

    @Test
    void eventoAndEventosAreAllowedAndEverythingElseIsBlockedDuringAnEvent() {
        assertTrue(ToffyEventRuntime.allowedDuringEvent("evento"));
        assertTrue(ToffyEventRuntime.allowedDuringEvent("eventos"));
        assertFalse(ToffyEventRuntime.allowedDuringEvent("spawn"));
        assertFalse(ToffyEventRuntime.allowedDuringEvent("anotarx"));
    }
}
