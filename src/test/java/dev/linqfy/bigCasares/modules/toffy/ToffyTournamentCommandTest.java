package dev.linqfy.bigCasares.modules.toffy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyTournamentCommandTest {
    @Test
    void forzarIsOnlyAFlagAfterAnArgumentNotAName() {
        assertTrue(ToffyTournamentCommand.forced(new String[] {"tp", "concursantes", "copa", "forzar"}));
        assertTrue(ToffyTournamentCommand.forced(new String[] {"tp", "concursantes", "copa", "lugar", "forzar"}));
        assertFalse(ToffyTournamentCommand.forced(new String[] {"tp", "concursantes", "forzar"}),
            "forzar alone is a tournament name, not a flag");
        assertFalse(ToffyTournamentCommand.forced(new String[] {"evento", "iniciar", "forzar"}));
        assertFalse(ToffyTournamentCommand.forced(new String[] {"tp", "concursantes", "copa"}));
    }
}
