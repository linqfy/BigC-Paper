package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los sonidos de las entidades de los backrooms estan en la lista de eventos del modulo (sin mirar los archivos). */
class BackroomsSoundsTest {

    private static final List<String> NEW = List.of(
        "caichet:entity.jumpscare", "caichet:entity.attack", "caichet:entity.step",
        "caichet:sonrisa.laugh", "caichet:e666.heartbeat_slow", "caichet:e666.heartbeat_fast",
        "caichet:juancarlos.growl", "caichet:juancarlos.grab", "caichet:arana.screech", "caichet:arana.click",
        "caichet:payaso.giggle", "caichet:payaso.laugh", "caichet:backrooms.ambient");

    @Test
    void everyNewSoundIsInTheEventList() {
        for (String event : NEW) {
            assertTrue(CaiChetSounds.ALL.contains(event), event);
        }
        assertEquals(13, NEW.size());
    }

    @Test
    void theConstantsHoldTheAgreedNames() {
        assertEquals("caichet:entity.jumpscare", CaiChetSounds.ENTITY_JUMPSCARE);
        assertEquals("caichet:entity.attack", CaiChetSounds.ENTITY_ATTACK);
        assertEquals("caichet:entity.step", CaiChetSounds.ENTITY_STEP);
        assertEquals("caichet:sonrisa.laugh", CaiChetSounds.SONRISA_LAUGH);
        assertEquals("caichet:e666.heartbeat_slow", CaiChetSounds.E666_HEARTBEAT_SLOW);
        assertEquals("caichet:e666.heartbeat_fast", CaiChetSounds.E666_HEARTBEAT_FAST);
        assertEquals("caichet:juancarlos.growl", CaiChetSounds.JUANCARLOS_GROWL);
        assertEquals("caichet:juancarlos.grab", CaiChetSounds.JUANCARLOS_GRAB);
        assertEquals("caichet:arana.screech", CaiChetSounds.ARANA_SCREECH);
        assertEquals("caichet:arana.click", CaiChetSounds.ARANA_CLICK);
        assertEquals("caichet:payaso.giggle", CaiChetSounds.PAYASO_GIGGLE);
        assertEquals("caichet:payaso.laugh", CaiChetSounds.PAYASO_LAUGH);
        assertEquals("caichet:backrooms.ambient", CaiChetSounds.BACKROOMS_AMBIENT);
    }

    @Test
    void theAmbientSoundUsesTheSoundGateCadence() {
        SoundGate gate = new SoundGate();
        UUID id = UUID.randomUUID();
        Random random = new Random(1);
        assertFalse(gate.due(id, "backrooms.ambient", 0, 500, 900, random), "la primera vez solo agenda");
        assertFalse(gate.due(id, "backrooms.ambient", 499, 500, 900, random));
        assertTrue(gate.due(id, "backrooms.ambient", 900, 500, 900, random));
    }
}
