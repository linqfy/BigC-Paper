package dev.linqfy.bigCasares.modules.toffy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToffyMessagesTest {
    private static final Map<String, String> PLACEHOLDERS = Map.of("jugador", "Ana", "torneo", "Copa", "cantidad", "3");

    @Test
    void everyCaseHasADefaultAndTheBundledFileCoversThemAll() {
        YamlConfiguration bundled =
            YamlConfiguration.loadConfiguration(new File("src/main/resources/toffy_mensajes.yml"));
        ToffyMessages.Loaded loaded = ToffyMessages.fromValues(bundled.getValues(false));

        assertTrue(loaded.warnings().isEmpty());
        for (String key : ToffyMessages.CASES) {
            assertTrue(bundled.contains(key), key);
            assertEquals(ToffyMessages.defaultTemplate(key), loaded.messages().template(key, null), key);
        }
        assertEquals(96, ToffyMessages.CASES.size());
        assertTrue(ToffyMessages.CASES.containsAll(List.of("grupo-anotado", "grupo-cambiado", "grupo-cerrado",
            "grupo-lleno", "grupo-elegir", "grupo-asignado")));
        assertEquals("evento-lobby", ToffyMessages.EVENTO_LOBBY);
        assertEquals("evento-fin", ToffyMessages.EVENTO_FIN);
    }

    @Test
    void defaultsApplyPlaceholdersAndColors() {
        assertEquals("§aTe anotaste al torneo §eCopa§a. Sos el participante §e#3§a.",
            ToffyMessages.builtIn().render(ToffyMessages.ANOTADO, null, PLACEHOLDERS));
    }

    @Test
    void emptyTemplatesMeanNothingIsSent() {
        ToffyMessages messages = ToffyMessages.builtIn();

        assertNull(messages.render(ToffyMessages.ANUNCIO_ANOTADO, null, PLACEHOLDERS));
        assertNull(messages.render(ToffyMessages.APERTURA, null, PLACEHOLDERS));
        assertNull(ToffyMessages.fromValues(Map.of("anotado", "")).messages()
            .render(ToffyMessages.ANOTADO, null, PLACEHOLDERS));
        assertNull(ToffyMessages.format("   ", PLACEHOLDERS));
    }

    @Test
    void fileValuesReplaceDefaultsAndMissingKeysFallBack() {
        ToffyMessages messages = ToffyMessages.fromValues(Map.of("llevado", "&6{jugador} va a {torneo}")).messages();

        assertEquals("§6Ana va a Copa", messages.render(ToffyMessages.LLEVADO, null, PLACEHOLDERS));
        assertEquals(ToffyMessages.format(ToffyMessages.defaultTemplate("devuelto"), PLACEHOLDERS),
            messages.render(ToffyMessages.DEVUELTO, null, PLACEHOLDERS));
    }

    @Test
    void overrideBeatsFileAndFileBeatsDefault() {
        ToffyMessages messages = ToffyMessages.fromValues(Map.of("anotado", "archivo {jugador}")).messages();

        assertEquals("torneo Ana", messages.render(ToffyMessages.ANOTADO, "torneo {jugador}", PLACEHOLDERS));
        assertEquals("archivo Ana", messages.render(ToffyMessages.ANOTADO, null, PLACEHOLDERS));
        assertNull(messages.render(ToffyMessages.ANOTADO, "", PLACEHOLDERS));
    }

    @Test
    void ampersandCodesBecomeSectionSignsButPlayerTextIsKept() {
        assertEquals("§l§cHola", ToffyMessages.colors("&l&cHola"));
        assertEquals("§chola & chau &", ToffyMessages.colors("&Chola & chau &"));
        assertEquals("§aAna&b", ToffyMessages.format("&a{jugador}", Map.of("jugador", "Ana&b")));
        assertEquals("{x} Ana", ToffyMessages.format("{x} {jugador}", Map.of("jugador", "Ana")));
    }

    @Test
    void unknownKeysAndBadValuesOnlyWarn() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("anotado", "ok");
        raw.put("inventado", "x");
        raw.put("cierre", List.of("no", "texto"));
        raw.put("apertura", null);
        raw.put("llevado", 5);

        ToffyMessages.Loaded loaded = ToffyMessages.fromValues(raw);

        assertEquals(2, loaded.warnings().size());
        assertTrue(loaded.warnings().get(0).contains("inventado"));
        assertTrue(loaded.warnings().get(1).contains("cierre"));
        assertEquals("ok", loaded.messages().template("anotado", null));
        assertEquals(ToffyMessages.defaultTemplate("cierre"), loaded.messages().template("cierre", null));
        assertEquals("", loaded.messages().template("apertura", null));
        assertEquals("5", loaded.messages().template("llevado", null));
    }

    @Test
    void aMissingFileLeavesTheBuiltInDefaults() {
        ToffyMessages.Loaded loaded = ToffyMessages.fromValues(null);

        assertTrue(loaded.warnings().isEmpty());
        assertFalse(ToffyMessages.isCase("nada"));
        assertEquals(ToffyMessages.defaultTemplate("anotado"), loaded.messages().template("anotado", null));
    }
}
