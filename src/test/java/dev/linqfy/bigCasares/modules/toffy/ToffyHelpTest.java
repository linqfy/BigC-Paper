package dev.linqfy.bigCasares.modules.toffy;

import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEvents;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyHelpTest {
    private static Predicate<String> has(String... nodes) {
        Set<String> granted = Set.of(nodes);
        // Same rule as ToffyCommand.permissions: the node itself, or the admin node.
        return node -> granted.contains(node) || granted.contains(ToffyCommand.ADMIN_PERMISSION);
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }

    @Test
    void aPlayerWithNoPermissionsOnlySeesAyuda() {
        String help = joined(ToffyHelp.lines(has()));

        assertTrue(help.contains("/toffy ayuda"));
        assertFalse(help.contains("nivel"));
        assertFalse(help.contains("curva"));
        assertFalse(help.contains("torneo"));
        assertFalse(help.contains("evento"));
    }

    @Test
    void checkShowsNivelOnly() {
        String help = joined(ToffyHelp.lines(has(ToffyCommand.CHECK_PERMISSION)));

        assertTrue(help.contains("/toffy nivel [jugador]"));
        assertFalse(help.contains("reload"));
        assertFalse(help.contains("torneo"));
    }

    @Test
    void tournamentAdminSeesTorneoAndTpButNotEvents() {
        String help = joined(ToffyHelp.lines(has(ToffyCommand.TORNEO_ADMIN_PERMISSION)));

        assertTrue(help.contains("/toffy torneo lugar <torneo> [agregar|borrar]"));
        assertTrue(help.contains("/toffy programados"));
        assertTrue(help.contains("/toffy tp concursantes <torneo> [lugar | x y z [mundo]]"));
        assertFalse(help.contains("evento iniciar"));
        assertFalse(help.contains("curva"));
    }

    @Test
    void theAdminNodeSeesEverything() {
        String help = joined(ToffyHelp.lines(has(ToffyCommand.ADMIN_PERMISSION)));

        assertTrue(help.contains("/toffy reload"));
        assertTrue(help.contains("/toffy curva"));
        assertTrue(help.contains("/toffy torneo crear <nombre>"));
    }

    @Test
    void firstArgumentsFollowThePermissions() {
        assertEquals(List.of("ayuda"), ToffyHelp.firstArgs(has()));
        assertEquals(List.of("ayuda", "nivel"), ToffyHelp.firstArgs(has(ToffyCommand.CHECK_OTHERS_PERMISSION)));
        assertEquals(List.of("ayuda", "nivel", "torneo", "tp", "programados"),
            ToffyHelp.firstArgs(has(ToffyCommand.CHECK_PERMISSION, ToffyCommand.TORNEO_ADMIN_PERMISSION)));
        assertEquals(List.of("ayuda", "nivel", "curva", "reload", "torneo", "tp", "programados", "evento"),
            ToffyHelp.firstArgs(has(ToffyCommand.ADMIN_PERMISSION, ToffyCommand.CHECK_PERMISSION,
                ToffyCommand.TORNEO_ADMIN_PERMISSION, ToffyEvents.ADMIN_PERMISSION)));
        assertEquals(List.of("ayuda", "evento"), ToffyHelp.firstArgs(has(ToffyEvents.ADMIN_PERMISSION)));
    }

    @Test
    void groupsAndTheGroupPlayerCommandsAreListedForTheRightPeople() {
        String admin = joined(ToffyHelp.lines(has(ToffyCommand.TORNEO_ADMIN_PERMISSION)));
        assertTrue(admin.contains("/toffy torneo grupo <torneo> crear <grupo> [abierto]"));
        assertTrue(admin.contains("/toffy torneo grupo <torneo> repartir <parejo|nivel> [todos]"));
        assertTrue(admin.contains("/toffy torneo grupo <torneo> obligatorio <si|no>"));
        assertTrue(admin.contains("/toffy tp concursantes <torneo> grupo <grupo> [lugar | x y z [mundo]] [forzar]"));
        assertFalse(admin.contains("/anotar <torneo> [grupo]"));

        String player = joined(ToffyHelp.lines(has(ToffyAnotarCommand.PERMISSION)));
        assertTrue(player.contains("/anotar <torneo> [grupo]"));
        assertFalse(player.contains("torneo grupo"));

        String events = joined(ToffyHelp.lines(has(ToffyEvents.ADMIN_PERMISSION)));
        assertTrue(events.contains("/toffy evento iniciar <tipo> <torneo> [grupo <grupo>] [segundos]"));
    }
}
