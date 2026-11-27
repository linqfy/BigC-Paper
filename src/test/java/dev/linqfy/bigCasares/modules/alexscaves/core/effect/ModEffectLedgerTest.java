package dev.linqfy.bigCasares.modules.alexscaves.core.effect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModEffectLedgerTest {
    private final UUID entity = UUID.randomUUID();

    @Test
    void higherAmplifierReplacesAndLowerIsIgnored() {
        ModEffectLedger ledger = new ModEffectLedger();
        assertTrue(ledger.apply(entity, ModEffectType.IRRADIATED, 0, 1800));
        assertTrue(ledger.apply(entity, ModEffectType.IRRADIATED, 2, 100));
        assertFalse(ledger.apply(entity, ModEffectType.IRRADIATED, 1, 9000));
        assertEquals(2, ledger.get(entity, ModEffectType.IRRADIATED).orElseThrow().amplifier());
    }

    @Test
    void sameAmplifierOnlyExtends() {
        ModEffectLedger ledger = new ModEffectLedger();
        ledger.apply(entity, ModEffectType.STUNNED, 0, 200);
        assertFalse(ledger.apply(entity, ModEffectType.STUNNED, 0, 100));
        assertTrue(ledger.apply(entity, ModEffectType.STUNNED, 0, 300));
        assertEquals(300, ledger.get(entity, ModEffectType.STUNNED).orElseThrow().remainingTicks());
    }

    @Test
    void tickExpiresEffectsAndKeepsElapsedAcrossRefresh() {
        ModEffectLedger ledger = new ModEffectLedger();
        ledger.apply(entity, ModEffectType.RAGE, 0, 3);
        ledger.apply(entity, ModEffectType.IRRADIATED, 1, 10);
        assertTrue(ledger.tick(2).isEmpty());
        ledger.apply(entity, ModEffectType.IRRADIATED, 1, 50);
        assertEquals(2, ledger.get(entity, ModEffectType.IRRADIATED).orElseThrow().elapsedTicks());
        List<ModEffectLedger.ExpiredEffect> expired = ledger.tick(1);
        assertEquals(1, expired.size());
        assertEquals(ModEffectType.RAGE, expired.get(0).effect().type());
        assertEquals(entity, expired.get(0).entity());
        assertTrue(ledger.has(entity, ModEffectType.IRRADIATED));
    }

    @Test
    void milkKeepsIrradiatedAndBubbled() {
        ModEffectLedger ledger = new ModEffectLedger();
        ledger.apply(entity, ModEffectType.IRRADIATED, 0, 100);
        ledger.apply(entity, ModEffectType.BUBBLED, 0, 100);
        ledger.apply(entity, ModEffectType.MAGNETIZING, 0, 100);
        assertEquals(List.of(ModEffectType.MAGNETIZING), ledger.cureWithMilk(entity));
        assertTrue(ledger.has(entity, ModEffectType.IRRADIATED));
        assertTrue(ledger.has(entity, ModEffectType.BUBBLED));
    }

    @Test
    void invalidApplicationsAreRejectedAndForgetClears() {
        ModEffectLedger ledger = new ModEffectLedger();
        assertFalse(ledger.apply(entity, ModEffectType.RAGE, 0, 0));
        assertFalse(ledger.apply(entity, ModEffectType.RAGE, -1, 10));
        ledger.apply(entity, ModEffectType.RAGE, 0, 10);
        ledger.forget(entity);
        assertTrue(ledger.entities().isEmpty());
    }

    @Test
    void pausedEntitiesDoNotAdvance() {
        ModEffectLedger ledger = new ModEffectLedger();
        UUID offline = UUID.randomUUID();
        ledger.apply(entity, ModEffectType.RAGE, 0, 5);
        ledger.apply(offline, ModEffectType.RAGE, 0, 5);
        ledger.tick(5, id -> id.equals(entity));
        assertFalse(ledger.has(entity, ModEffectType.RAGE));
        assertEquals(5, ledger.get(offline, ModEffectType.RAGE).orElseThrow().remainingTicks());
    }

    @Test
    void codecRoundTripsAndSkipsGarbage() {
        List<ModEffectLedger.ActiveEffect> effects = List.of(
            new ModEffectLedger.ActiveEffect(ModEffectType.IRRADIATED, 2, 1200, 37),
            new ModEffectLedger.ActiveEffect(ModEffectType.STUNNED, 0, 40, 0));
        assertEquals(effects, ModEffectCodec.decode(ModEffectCodec.encode(effects)));
        assertEquals(1, ModEffectCodec.decode("irradiated:1:100:0;nope;rage:x:1:1;unknown:0:5:0").size());
        assertTrue(ModEffectCodec.decode(null).isEmpty());
    }
}
