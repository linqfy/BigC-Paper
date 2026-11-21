package dev.linqfy.bigCasares.modules.alexscaves.core.magnetism;

import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetForce.Polarity;
import dev.linqfy.bigCasares.modules.alexscaves.core.magnetism.MagnetForce.Vec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagnetForceTest {

    @Test
    void scarletPullsTowardsTheMagnetAndAzurePushesAway() {
        Vec entity = new Vec(0, 0, 0);
        Vec magnet = new Vec(2, 0, 0);
        Vec scarlet = MagnetForce.pull(entity, magnet, Polarity.SCARLET);
        Vec azure = MagnetForce.pull(entity, magnet, Polarity.AZURE);
        assertTrue(scarlet.x() > 0);
        assertTrue(azure.x() < 0);
        assertEquals(scarlet.x(), -azure.x(), 1e-12);
    }

    @Test
    void forceFallsOffLinearlyToZeroAtFiveBlocks() {
        Vec entity = new Vec(0, 0, 0);
        assertEquals((1 - 2.0 / 5) * 0.25, MagnetForce.pull(entity, new Vec(2, 0, 0), Polarity.SCARLET).length(), 1e-12);
        assertEquals((1 - 4.0 / 5) * 0.25, MagnetForce.pull(entity, new Vec(0, 4, 0), Polarity.SCARLET).length(), 1e-12);
        assertEquals(Vec.ZERO, MagnetForce.pull(entity, new Vec(5, 0, 0), Polarity.SCARLET));
        assertEquals(Vec.ZERO, MagnetForce.pull(entity, new Vec(0, 0, 9), Polarity.AZURE));
    }

    @Test
    void underOneBlockTheDirectionIsNotNormalised() {
        Vec pull = MagnetForce.pull(new Vec(0, 0, 0), new Vec(0.5, 0, 0), Polarity.SCARLET);
        assertEquals(0.5 * (1 - 0.5 / 5) * 0.25, pull.x(), 1e-12);
    }

    @Test
    void accumulatedDeltaDecaysAndIsCapped() {
        Vec delta = MagnetForce.accumulate(new Vec(1, 0, 0), Vec.ZERO);
        assertEquals(0.9, delta.x(), 1e-12);
        Vec huge = MagnetForce.accumulate(new Vec(50, 50, 0), new Vec(10, 0, 0));
        assertEquals(MagnetForce.MAX_SPEED, huge.length(), 1e-9);
    }

    @Test
    void polarityOpposite() {
        assertEquals(Polarity.AZURE, Polarity.SCARLET.opposite());
        assertEquals(Polarity.SCARLET, Polarity.AZURE.opposite());
    }
}
