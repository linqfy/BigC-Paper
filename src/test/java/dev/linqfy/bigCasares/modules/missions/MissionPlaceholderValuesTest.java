package dev.linqfy.bigCasares.modules.missions;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MissionPlaceholderValuesTest {
    private final UUID juan = UUID.randomUUID();
    private final MissionPlaceholderValues values = new MissionPlaceholderValues(
        id -> id.equals(juan) ? Optional.of("Compraste stone por $64.") : Optional.empty());

    @Test
    void readsTheLastSnapshotAndTheShop() {
        assertEquals("0", values.resolve(juan, "misiones_listas"), "nothing known yet");
        assertEquals("", values.resolve(juan, "misiones_premio"));
        values.update(juan, 2, "$650");
        assertEquals("2", values.resolve(juan, "MISIONES_LISTAS "));
        assertEquals("$650", values.resolve(juan, "misiones_premio"));
        assertEquals("Compraste stone por $64.", values.resolve(juan, "tienda_ultima"));
        assertEquals("", values.resolve(UUID.randomUUID(), "tienda_ultima"));
    }

    @Test
    void unknownKeysAndForgottenPlayersGiveNothing() {
        values.update(juan, 1, "$100");
        assertEquals("", values.resolve(juan, "otra_cosa"));
        assertEquals("", values.resolve(null, "misiones_listas"));
        values.forget(juan);
        assertEquals("0", values.resolve(juan, "misiones_listas"));
    }
}
