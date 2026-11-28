package dev.linqfy.bigCasares.modules.toffy;

import dev.linqfy.bigCasares.modules.toffy.eventos.ToffyEvents;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyMessageDisplayTest {
    private static final Map<String, String> PLACEHOLDERS = Map.of("jugador", "Ana", "evento", "Spleef");

    @Test
    void theStringFormIsChatWithTheEventPrefix() {
        ToffyMessages messages = ToffyMessages.fromValues(Map.of("evento-unido", "&aHola {jugador}")).messages();

        ToffyEvents.Text text = messages.display(ToffyEvents.CASE_JOINED, null, PLACEHOLDERS, true);

        assertEquals(ToffyEvents.Text.Kind.CHAT, text.kind());
        assertEquals(ToffyMessages.colors(ToffyMessages.DEFAULT_EVENT_PREFIX) + "§aHola Ana", text.text());
    }

    @Test
    void theSectionFormSetsTitleSubtitleSoundVolumeAndPitch() {
        Map<String, Object> section = Map.of("texto", "&eEmpieza {evento}", "tipo", "titulo", "subtitulo", "&7ya",
            "sonido", "minecraft:block.note_block.pling", "volumen", 0.5, "tono", 1.5);
        ToffyMessages messages = ToffyMessages.fromValues(Map.of("evento-empieza", section)).messages();

        ToffyEvents.Text text = messages.display(ToffyEvents.CASE_STARTED, null, PLACEHOLDERS, true);

        assertEquals(ToffyEvents.Text.Kind.TITLE, text.kind());
        assertEquals("§eEmpieza Spleef", text.text());
        assertEquals("§7ya", text.subtitle());
        assertEquals("minecraft:block.note_block.pling", text.sound());
        assertEquals(0.5f, text.volume());
        assertEquals(1.5f, text.pitch());
    }

    @Test
    void ningunoKeepsOnlyTheSound() {
        Map<String, Object> section = Map.of("texto", "no se ve", "tipo", "ninguno", "sonido", "minecraft:ui.toast.challenge_complete");
        ToffyMessages messages = ToffyMessages.fromValues(Map.of("evento-ganador", section)).messages();

        ToffyEvents.Text text = messages.display(ToffyEvents.CASE_WINNER, null, PLACEHOLDERS, true);

        assertEquals(ToffyEvents.Text.Kind.NONE, text.kind());
        assertEquals("minecraft:ui.toast.challenge_complete", text.sound());
    }

    @Test
    void anUnknownKindWarnsAndFallsBackToChat() {
        Map<String, Object> section = Map.of("texto", "x", "tipo", "raro");
        ToffyMessages.Loaded loaded = ToffyMessages.fromValues(Map.of("evento-fin", section));

        assertFalse(loaded.warnings().isEmpty());
        assertEquals(ToffyEvents.Text.Kind.CHAT, loaded.messages().display(ToffyEvents.CASE_FINISHED, null,
            PLACEHOLDERS, true).kind());
    }

    @Test
    void anEmptyCaseSendsNothing() {
        ToffyMessages messages = ToffyMessages.fromValues(Map.of("evento-unido", "")).messages();

        assertNull(messages.display(ToffyEvents.CASE_JOINED, null, PLACEHOLDERS, true));
    }

    @Test
    void thePrefixesCanBeChangedAndPlacedWithThePlaceholder() {
        ToffyMessages messages = ToffyMessages.fromValues(Map.of("prefijo-eventos", "&bX ",
            "evento-unido", "{prefijo}hola")).messages();

        ToffyEvents.Text text = messages.display(ToffyEvents.CASE_JOINED, null, PLACEHOLDERS, true);

        // The placeholder takes the place of the automatic prefix, so the prefix is not repeated.
        assertEquals("§bX hola", text.text());
    }

    @Test
    void piecesOfOtherMessagesAreNeverPrefixed() {
        ToffyMessages messages = ToffyMessages.builtIn();

        assertEquals("§6§lPalo del Rey", messages.display(ToffyEvents.CASE_KIT_PALO, null, PLACEHOLDERS, true).text());
        assertEquals("murió", messages.display(ToffyEvents.CASE_CAUSA_MURIO, null, PLACEHOLDERS, true).text());
    }

    @Test
    void liveStatusLinesAreActionBarByDefault() {
        ToffyEvents.Text text = ToffyMessages.builtIn().display(ToffyEvents.CASE_TNT_CORRE, null,
            Map.of("segundos", "9"), true);

        assertEquals(ToffyEvents.Text.Kind.ACTIONBAR, text.kind());
        assertTrue(text.text().contains("9 s"));
    }

    @Test
    void aSectionWithoutTextStillPlaysItsSoundButSendsNoChatLine() {
        Map<String, Object> section = Map.of("texto", "", "tipo", "chat", "sonido", "minecraft:ui.toast.challenge_complete");
        ToffyMessages messages = ToffyMessages.fromValues(Map.of("evento-ganador", section)).messages();

        ToffyEvents.Text text = messages.display(ToffyEvents.CASE_WINNER, null, PLACEHOLDERS, true);

        assertEquals(ToffyEvents.Text.Kind.NONE, text.kind());
        assertEquals("", text.text());
        assertEquals("minecraft:ui.toast.challenge_complete", text.sound());
    }

    @Test
    void anInvalidSectionSoundWarnsAndIsDropped() {
        Map<String, Object> section = Map.of("texto", "hola", "sonido", "Mal Key!", "volumen", 50, "tono", 0.1);
        ToffyMessages.Loaded loaded = ToffyMessages.fromValues(Map.of("evento-fin", section));

        ToffyEvents.Text text = loaded.messages().display(ToffyEvents.CASE_FINISHED, null, PLACEHOLDERS, true);

        assertEquals("", text.sound());
        assertEquals(10f, text.volume());
        assertEquals(0.5f, text.pitch());
        assertEquals(3, loaded.warnings().size());
    }
}
