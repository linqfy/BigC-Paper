package dev.linqfy.bigCasares.modules.pveboss;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PveBossModuleExternalBossTest {

    private static final BossEncounterControllerFactory FACTORY = context -> new BossEncounterController() {
    };

    @Test
    void registrationRequiresTheModuleToBeEnabled() {
        PveBossModule module = new PveBossModule();

        assertThrows(IllegalStateException.class,
            () -> module.registerExternalBoss("myth-zeus", FACTORY, standings -> { }));

        module.onEnable();
        module.onDisable();
        assertThrows(IllegalStateException.class,
            () -> module.registerExternalBoss("myth-zeus", FACTORY, standings -> { }));
    }

    @Test
    void withoutALivePluginRegistrationFailsAndLeavesNoTrace() {
        PveBossModule module = new PveBossModule();
        module.onEnable();

        assertThrows(IllegalStateException.class,
            () -> module.registerExternalBoss("myth-zeus", FACTORY, standings -> { }));

        assertEquals(List.of(), module.externalBossIds());
        assertThrows(IllegalArgumentException.class,
            () -> module.spawn("myth-zeus", null));
        module.onDisable();
    }

    @Test
    void rejectsBuiltInIdsAndInvalidArgumentsBeforeTouchingTheServer() {
        PveBossModule module = new PveBossModule();
        module.onEnable();

        assertThrows(IllegalArgumentException.class,
            () -> module.registerExternalBoss("abyss-guardian", FACTORY, standings -> { }));
        assertThrows(IllegalArgumentException.class,
            () -> module.registerExternalBoss(" Tung-Tung-Sahur ", FACTORY, standings -> { }));
        assertThrows(IllegalArgumentException.class,
            () -> module.registerExternalBoss(" ", FACTORY, standings -> { }));
        assertThrows(NullPointerException.class,
            () -> module.registerExternalBoss(null, FACTORY, standings -> { }));
        assertThrows(NullPointerException.class,
            () -> module.registerExternalBoss("myth-zeus", null, standings -> { }));
        assertThrows(NullPointerException.class,
            () -> module.registerExternalBoss("myth-zeus", FACTORY, null));
        module.onDisable();
    }

    @Test
    void unregisteringAndInstanceQueriesAreSafeForUnknownBosses() {
        PveBossModule module = new PveBossModule();
        module.onEnable();
        UUID unknown = UUID.randomUUID();

        module.unregisterExternalBoss("myth-zeus");
        module.unregisterExternalBoss("myth-zeus");

        assertFalse(module.isActive(unknown));
        assertFalse(module.removeBoss(unknown));
        assertEquals(0, module.activeBossCount());
        assertEquals(List.of("abyss-guardian", "tung-tung-sahur"), PveBossModule.supportedBossIds());
        module.onDisable();
    }
}
