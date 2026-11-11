package dev.linqfy.bigCasares.modules.schizophrenia;

import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HallucinationPickerTest {

    @Test
    void everyBeatHasSomethingFromTheFirstMinute() {
        HallucinationPicker picker = new HallucinationPicker(Set.of());
        for (Hallucination.Beat beat : Hallucination.Beat.values()) {
            assertFalse(picker.eligible(1, beat).isEmpty(), beat::name);
        }
        assertTrue(picker.eligible(1, Hallucination.Beat.SCARE).contains(Hallucination.CREEPER_HISS));
        assertTrue(picker.eligible(1, Hallucination.Beat.SCARE).contains(Hallucination.RIGHT_BEHIND_YOU));
        assertTrue(picker.eligible(1, Hallucination.Beat.DREAD).contains(Hallucination.FOOTSTEPS));
        assertTrue(picker.eligible(1, Hallucination.Beat.DREAD).contains(Hallucination.PHANTOM_CLONE));
    }

    @Test
    void nastyHallucinationsUnlockWithIntensity() {
        HallucinationPicker picker = new HallucinationPicker(Set.of());
        assertFalse(picker.eligible(2, Hallucination.Beat.SCARE).contains(Hallucination.CAMERA_SNAP));
        assertTrue(picker.eligible(3, Hallucination.Beat.SCARE).contains(Hallucination.CAMERA_SNAP));
        assertFalse(picker.eligible(2, Hallucination.Beat.SCARE).contains(Hallucination.VOID_VISIT));
        assertFalse(picker.eligible(3, Hallucination.Beat.SCARE).contains(Hallucination.FLESH_ROOM));
        assertTrue(picker.eligible(4, Hallucination.Beat.SCARE).contains(Hallucination.FLESH_ROOM));
        assertFalse(picker.eligible(2, Hallucination.Beat.SCARE).contains(Hallucination.CHASE));
        assertTrue(picker.eligible(3, Hallucination.Beat.SCARE).contains(Hallucination.CHASE));
        assertFalse(picker.eligible(4, Hallucination.Beat.SCARE).contains(Hallucination.BREAKDOWN));
        assertTrue(picker.eligible(5, Hallucination.Beat.SCARE).contains(Hallucination.BREAKDOWN));
        int all = 0;
        for (Hallucination.Beat beat : Hallucination.Beat.values()) {
            all += picker.eligible(5, beat).size();
        }
        assertEquals(Hallucination.values().length, all);
    }

    @Test
    void staysInItsBeatNeverRepeatsAndRespectsDisabledOnes() {
        HallucinationPicker picker = new HallucinationPicker(Set.of(Hallucination.CREEPER_HISS));
        SplittableRandom random = new SplittableRandom(7);
        Hallucination previous = null;
        EnumSet<Hallucination> seen = EnumSet.noneOf(Hallucination.class);
        for (int i = 0; i < 2000; i++) {
            Hallucination next = picker.pick(5, Hallucination.Beat.SCARE, previous, random).orElseThrow();
            assertEquals(Hallucination.Beat.SCARE, next.beat());
            assertNotEquals(previous, next);
            assertNotEquals(Hallucination.CREEPER_HISS, next);
            seen.add(next);
            previous = next;
        }
        assertEquals(picker.eligible(5, Hallucination.Beat.SCARE).size(), seen.size());
    }

    @Test
    void everythingDisabledPicksNothing() {
        HallucinationPicker picker = new HallucinationPicker(EnumSet.allOf(Hallucination.class));
        assertTrue(picker.pick(5, Hallucination.Beat.DREAD, null, new SplittableRandom(1)).isEmpty());
    }

    @Test
    void configIdsRoundTripAndEveryHallucinationHasAnEffect() throws Exception {
        // The director needs a live server registry for its sounds, so its wiring is read from the bytecode.
        CompiledClass director = CompiledClass.of(getClass(),
            "dev.linqfy.bigCasares.modules.schizophrenia.HallucinationDirector");
        for (Hallucination hallucination : Hallucination.values()) {
            assertEquals(hallucination, Hallucination.parse(hallucination.id()).orElseThrow());
            assertTrue(director.hasConstant(hallucination.name()), hallucination::id);
        }
        assertTrue(Hallucination.parse(" ").isEmpty());
    }
}
