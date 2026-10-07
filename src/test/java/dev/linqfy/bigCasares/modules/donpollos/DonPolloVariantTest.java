package dev.linqfy.bigCasares.modules.donpollos;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DonPolloVariantTest {

    @Test
    void exposesFiveStableIdsInOrder() {
        assertEquals(List.of("comun", "gordito", "salsero", "aura-67", "fino"), DonPolloVariant.ids());
    }

    @Test
    void resolvesIdsIgnoringCaseAndWhitespace() {
        assertEquals(Optional.of(DonPolloVariant.AURA_67), DonPolloVariant.fromId(" AURA-67 "));
        assertEquals(Optional.of(DonPolloVariant.FINO), DonPolloVariant.fromId("fino"));
    }

    @Test
    void returnsEmptyForUnknownOrNullIds() {
        assertEquals(Optional.empty(), DonPolloVariant.fromId("pollo-loco"));
        assertEquals(Optional.empty(), DonPolloVariant.fromId(null));
    }
}
