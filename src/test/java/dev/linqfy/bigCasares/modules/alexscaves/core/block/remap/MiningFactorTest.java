package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.remap.MiningFactor.Profile;
import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningFactorTest {
    private static final Profile NOTE_BLOCK_HAND = new Profile(1.0, 0.8, true);
    private static final Profile LEAVES_HAND = new Profile(1.0, 0.2, true);

    @Test
    void handOnNoteBlockSeenAsLeavesIsSlowedToAQuarter() {
        assertEquals(0.25, MiningFactor.factor(NOTE_BLOCK_HAND, LEAVES_HAND).getAsDouble(), 1e-9);
    }

    @Test
    void shearsOnLeavesAreFasterOnTheClientSoTheFactorShrinks() {
        Profile shearsOnLeaves = new Profile(15.0, 0.2, true);
        Profile shearsOnNoteBlock = new Profile(1.0, 0.8, true);
        assertEquals(1.0 / 60.0, MiningFactor.factor(shearsOnNoteBlock, shearsOnLeaves).getAsDouble(), 1e-9);
    }

    @Test
    void axeOnNoteBlockIsFasterOnTheServerThanOnTheLeavesTheClientSees() {
        Profile axeOnNoteBlock = new Profile(6.0, 0.8, true);
        Profile axeOnLeaves = new Profile(1.0, 0.2, true);
        assertEquals(1.5, MiningFactor.factor(axeOnNoteBlock, axeOnLeaves).getAsDouble(), 1e-9);
    }

    @Test
    void harvestDivisorDifferenceIsPartOfTheQuotient() {
        Profile server = new Profile(1.0, 1.0, false);
        Profile client = new Profile(1.0, 1.0, true);
        assertEquals(0.3, MiningFactor.factor(server, client).getAsDouble(), 1e-9);
    }

    @Test
    void equalSpeedsNeedNoModifier() {
        assertTrue(MiningFactor.factor(LEAVES_HAND, LEAVES_HAND).isEmpty());
        assertTrue(MiningFactor.factor(LEAVES_HAND, new Profile(1.0, 0.2000001, true)).isEmpty());
    }

    @Test
    void instantOrUnbreakableBlocksAreSkipped() {
        assertTrue(MiningFactor.factor(new Profile(1.0, 0.0, true), LEAVES_HAND).isEmpty());
        assertTrue(MiningFactor.factor(new Profile(1.0, -1.0, true), LEAVES_HAND).isEmpty());
        assertTrue(MiningFactor.factor(NOTE_BLOCK_HAND, new Profile(1.0, 0.0, true)).isEmpty());
        assertTrue(MiningFactor.factor(new Profile(0.0, 0.8, true), LEAVES_HAND).isEmpty());
    }

    @Test
    void predictedProgressMatchesAfterApplyingTheFactor() {
        Profile server = new Profile(4.0, 0.8, true);
        Profile client = new Profile(1.0, 0.4, true);
        OptionalDouble factor = MiningFactor.factor(server, client);
        assertEquals(server.progress(), client.progress() * factor.getAsDouble(), 1e-12);
    }

    @Test
    void clampKeepsTheValueInsideTheAttributeRange() {
        assertEquals(1024.0, MiningFactor.clamp(5000.0, 1.0), 1e-9);
        assertEquals(512.0, MiningFactor.clamp(5000.0, 2.0), 1e-9);
        assertEquals(0.25, MiningFactor.clamp(0.25, 1.0), 1e-9);
        assertEquals(0.0, MiningFactor.clamp(-1.0, 1.0), 1e-9);
    }
}
