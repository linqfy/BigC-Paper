package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los cinco tipos de entidad: el orden de las imagenes, los nombres del comando y los mensajes. */
class BackroomsKindTest {

    @Test
    void theImageOrderIsSonrisa666JuanCarlosAranaPayaso() {
        assertEquals(0, BackroomsKind.SONRISA.frame());
        assertEquals(1, BackroomsKind.E666.frame());
        assertEquals(2, BackroomsKind.JUANCARLOS.frame());
        assertEquals(3, BackroomsKind.ARANA.frame());
        assertEquals(4, BackroomsKind.PAYASO.frame());
        assertEquals(5, BackroomsKind.values().length);
    }

    @Test
    void commandNamesAreUniqueAndFoundIgnoringCase() {
        Set<String> names = new HashSet<>();
        for (BackroomsKind kind : BackroomsKind.values()) {
            assertTrue(names.add(kind.commandName));
            assertEquals(kind, BackroomsKind.byCommandName(kind.commandName.toUpperCase()));
        }
        assertEquals(Set.of("sonrisa", "666", "juancarlos", "arana", "payaso"), names);
        assertNull(BackroomsKind.byCommandName("dragon"));
        assertNull(BackroomsKind.byCommandName(null));
    }

    @Test
    void eachEntityHasItsOwnDeathMessagesWithThePlayerName() {
        assertEquals("Pepe se quedó mirando a la Sonrisa demasiado tiempo", BackroomsKind.SONRISA.deathMessage("Pepe", 0));
        assertEquals("hola Pepe. chau Pepe.", BackroomsKind.JUANCARLOS.deathMessage("Pepe", 0));
        assertEquals("Pepe escuchó el clic-clic demasiado tarde", BackroomsKind.ARANA.deathMessage("Pepe", 0));
        Set<String> all = new HashSet<>();
        for (BackroomsKind kind : BackroomsKind.values()) {
            assertTrue(kind.deathMessages.size() >= 3, kind.name());
            for (int i = 0; i < kind.deathMessages.size(); i++) {
                String message = kind.deathMessage("Pepe", i);
                assertTrue(message.contains("Pepe") && !message.contains("{j}"), message);
                assertTrue(all.add(message), "repetido: " + message);
            }
            assertEquals(kind.deathMessage("Pepe", 0), kind.deathMessage("Pepe", kind.deathMessages.size()));
            assertTrue(kind.deathMessages.contains(
                kind.deathMessage("{j}", new java.util.Random(7))), "al azar sale uno de los suyos");
        }
    }

    @Test
    void theHintsAreTheOnesOfTheSpec() {
        assertEquals("no la mires...", BackroomsKind.SONRISA.hint);
        assertEquals("corre", BackroomsKind.E666.hint);
        assertEquals("hola Pepe", BackroomsKind.JUANCARLOS.hint.formatted("Pepe"));
        assertEquals("corre", BackroomsKind.ARANA.hint);
        assertEquals("date vuelta y alejate", BackroomsKind.PAYASO.hint);
    }

    @Test
    void everyKindHasItsBedrockEntry() {
        assertEquals(CaiChetBedrockEntities.SONRISA, BackroomsKind.SONRISA.bedrockEntry);
        assertEquals(CaiChetBedrockEntities.E666, BackroomsKind.E666.bedrockEntry);
        assertEquals(CaiChetBedrockEntities.JUANCARLOS, BackroomsKind.JUANCARLOS.bedrockEntry);
        assertEquals(CaiChetBedrockEntities.ARANA, BackroomsKind.ARANA.bedrockEntry);
        assertEquals(CaiChetBedrockEntities.PAYASO, BackroomsKind.PAYASO.bedrockEntry);
    }
}
