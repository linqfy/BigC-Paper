package dev.linqfy.bigCasares.modules.caichet;

import dev.linqfy.bigCasares.platform.ClientEntityPresentationRegistry;
import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaiChetBedrockEntitiesTest {

    private static final String ENTITIES = "dev/linqfy/bigCasares/modules/caichet/CaiChetBedrockEntities";

    @AfterEach
    void reset() {
        CaiChetBedrockEntities.releaseAll();
        CaiChetBedrockEntities.setAvailable(false);
    }

    @Test
    void identifiersAndMarkersAreUniqueAndInTheCaiChetNamespace() {
        Set<String> identifiers = new HashSet<>();
        Set<String> markers = new HashSet<>();
        for (CaiChetBedrockEntities.Entry entry : CaiChetBedrockEntities.all()) {
            assertTrue(entry.bedrockIdentifier().startsWith("caichet:"), entry.bedrockIdentifier());
            assertTrue(identifiers.add(entry.bedrockIdentifier()), "repeated " + entry.bedrockIdentifier());
            assertTrue(markers.add(entry.markerKey()), "repeated " + entry.markerKey());
        }
        assertEquals(14, CaiChetBedrockEntities.all().size());
    }

    @Test
    void figuresSharingAJavaBodyHaveTheirOwnMarkers() {
        assertEquals("HAPPY_GHAST", CaiChetBedrockEntities.DREAM_CLOUD.javaEntityType());
        assertEquals("HAPPY_GHAST", CaiChetBedrockEntities.DREAM_EYE_1.javaEntityType());
        assertNotEquals(CaiChetBedrockEntities.DREAM_CLOUD.markerKey(), CaiChetBedrockEntities.DREAM_EYE_1.markerKey());
        assertNotEquals(CaiChetBedrockEntities.DREAM_WINDOW.markerKey(), CaiChetBedrockEntities.DREAM_DOOR.markerKey());
        assertNotEquals(CaiChetBedrockEntities.WATCHER_MOYAN.markerKey(), CaiChetBedrockEntities.WATCHER_ESTEVO.markerKey());
        assertEquals(CaiChetBedrockEntities.WATCHER_ESTEVO, CaiChetBedrockEntities.watcher("watcher_estevo"));
        assertEquals(CaiChetBedrockEntities.WATCHER_MOYAN, CaiChetBedrockEntities.watcher("watcher_moyan"));
        assertEquals(CaiChetBedrockEntities.DREAM_EYE_1, CaiChetBedrockEntities.dreamEye("dream_eye1"));
        assertEquals(CaiChetBedrockEntities.DREAM_EYE_2, CaiChetBedrockEntities.dreamEye("dream_eye2"));
        assertEquals(CaiChetBedrockEntities.DREAM_EYE_3, CaiChetBedrockEntities.dreamEye("dream_eye3"));
    }

    @Test
    void everyDreamSpriteHasItsOwnBodyEntryAndTheRainbowOwnsAMarkerOnAnArmorStand() {
        Set<CaiChetBedrockEntities.Entry> used = new HashSet<>();
        for (DreamEntities.Kind kind : DreamEntities.Kind.values()) {
            CaiChetBedrockEntities.Entry entry = DreamEntities.bedrockEntry(kind, "dream_eye2");
            assertNotNull(entry, kind.name());
            assertTrue(used.add(entry), kind + " shares its entity");
        }
        assertEquals(CaiChetBedrockEntities.DREAM_RAINBOW, DreamEntities.bedrockEntry(DreamEntities.Kind.RAINBOW, "dream_rainbow"));
        assertEquals("caichet_dream_rainbow", CaiChetBedrockEntities.DREAM_RAINBOW.markerKey());
        assertTrue(CaiChetBedrockEntities.all().contains(CaiChetBedrockEntities.DREAM_RAINBOW));
        assertEquals(CaiChetBedrockEntities.DREAM_EYE_2, DreamEntities.bedrockEntry(DreamEntities.Kind.EYE, "dream_eye2"));
        assertEquals(CaiChetBedrockEntities.DREAM_CLOUD, DreamEntities.bedrockEntry(DreamEntities.Kind.CLOUD, "dream_cloud"));
    }

    @Test
    void theRainbowCopiesAreDiscardedAndTheParticlesOnlyDrawnWithoutGeyserEntities() throws IOException {
        CompiledClass compiled = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.modules.caichet.DreamEntities");
        assertTrue(compiled.calls(ENTITIES, "discard"));
        assertTrue(compiled.calls(ENTITIES, "available"));
        assertTrue(compiled.calls("dev/linqfy/bigCasares/modules/caichet/RainbowPlacement", "place"));
    }

    @Test
    void bodiesAreOnlyAnnotatedWhenGeyserHasTheEntities() {
        UUID body = UUID.randomUUID();
        CaiChetBedrockEntities.present(body, CaiChetBedrockEntities.BOSS_EYE);
        assertTrue(ClientEntityPresentationRegistry.find(body).isEmpty(), "without Geyser entities nothing is annotated");

        CaiChetBedrockEntities.setAvailable(true);
        CaiChetBedrockEntities.present(body, CaiChetBedrockEntities.BOSS_EYE);
        ClientEntityPresentationRegistry.Presentation presentation = ClientEntityPresentationRegistry.find(body).orElseThrow();
        assertEquals("ALLAY", presentation.javaEntityType());
        assertEquals(Set.of("caichet_boss_eye"), presentation.markerKeys());

        CaiChetBedrockEntities.release(body);
        assertTrue(ClientEntityPresentationRegistry.find(body).isEmpty());
    }

    @Test
    void releaseAllDropsEveryAnnotationItMade() {
        CaiChetBedrockEntities.setAvailable(true);
        List<UUID> bodies = List.of(UUID.randomUUID(), UUID.randomUUID());
        CaiChetBedrockEntities.present(bodies.get(0), CaiChetBedrockEntities.TRAIN);
        CaiChetBedrockEntities.present(bodies.get(1), CaiChetBedrockEntities.DREAM_VOID);
        CaiChetBedrockEntities.releaseAll();
        bodies.forEach(body -> assertTrue(ClientEntityPresentationRegistry.find(body).isEmpty()));
    }

    @Test
    void geyserEntitiesAreOffUntilTheIntegrationTurnsThemOn() {
        assertFalse(CaiChetBedrockEntities.available());
        CaiChetBedrockEntities.setAvailable(true);
        assertTrue(CaiChetBedrockEntities.available());
    }

    @Test
    void everyStandIsAnnotatedWhenItIsCreatedAndReleasedWhenItIsRemoved() throws IOException {
        for (String owner : List.of("EyeBoss", "Watchers", "DreamEntities", "Train")) {
            CompiledClass compiled = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.modules.caichet." + owner);
            assertTrue(compiled.calls(ENTITIES, "present"), owner + " annotates its stand");
            // Train drops its carts through a method reference, which is not a direct call
            assertTrue(compiled.calls(ENTITIES, "discard") || owner.equals("Train") || owner.equals("Watchers"),
                owner + " releases its stand");
        }
        // the figure's own cleanup lives in its nested class
        CompiledClass watcher = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.modules.caichet.Watchers$Watcher");
        assertTrue(watcher.calls(ENTITIES, "discard"), "a watcher releases its stand");
        CompiledClass train = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.modules.caichet.Train");
        assertTrue(train.calls(ENTITIES, "available"), "Train shows one locomotive only with the Geyser model");
    }

    @Test
    void mannequinsReplaceTheBedrockOnlyBodiesWhenGeyserHasTheEntities() throws IOException {
        for (String owner : List.of("TrainPassengers", "Doppelganger")) {
            CompiledClass compiled = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.modules.caichet." + owner);
            assertTrue(compiled.calls(ENTITIES, "available"), owner);
        }
    }
}
