package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameStrikesTest {

    private static final CameVec CENTER = new CameVec(0.0, 64.0, 0.0);

    @Test
    void circlesIgnoreHeightWithinAJump() {
        assertTrue(CameStrikes.inCircle(CENTER, 3.0, new CameVec(2.0, 64.0, 2.0)));
        assertTrue(CameStrikes.inCircle(CENTER, 3.0, new CameVec(0.0, 66.0, 0.0)));
        assertFalse(CameStrikes.inCircle(CENTER, 3.0, new CameVec(2.5, 64.0, 2.5)));
        assertFalse(CameStrikes.inCircle(CENTER, 3.0, new CameVec(0.0, 68.0, 0.0)));
    }

    @Test
    void conesOnlyHitInFront() {
        CameVec facing = new CameVec(0.0, 0.0, 1.0);
        assertTrue(CameStrikes.inCone(CENTER, facing, 38.0, 6.0, new CameVec(0.0, 64.0, 4.0)));
        assertTrue(CameStrikes.inCone(CENTER, facing, 38.0, 6.0, new CameVec(2.0, 64.0, 4.0)));
        assertFalse(CameStrikes.inCone(CENTER, facing, 38.0, 6.0, new CameVec(4.0, 64.0, 2.0)), "beside");
        assertFalse(CameStrikes.inCone(CENTER, facing, 38.0, 6.0, new CameVec(0.0, 64.0, -3.0)), "behind");
        assertFalse(CameStrikes.inCone(CENTER, facing, 38.0, 6.0, new CameVec(0.0, 64.0, 7.0)), "too far");
    }

    @Test
    void sidesteppingOutOfTheDoubleSweepIsPossible() {
        CameVec target = new CameVec(0.0, 64.0, 0.0);
        CameVec axis = new CameVec(1.0, 0.0, 0.0);
        CameVec hun = CameStrikes.flankStart(target, axis, CameLord.HUN);
        CameVec vucub = CameStrikes.flankStart(target, axis, CameLord.VUCUB);
        CameVec towardFromHun = target.subtract(hun).horizontal();
        CameVec towardFromVucub = target.subtract(vucub).horizontal();
        assertTrue(CameStrikes.inCone(hun, towardFromHun, CameStrikes.SWEEP_HALF_ANGLE, CameStrikes.SWEEP_RANGE, target));
        assertTrue(CameStrikes.inCone(vucub, towardFromVucub, CameStrikes.SWEEP_HALF_ANGLE, CameStrikes.SWEEP_RANGE,
            target));
        CameVec dodged = new CameVec(0.0, 64.0, 3.2);
        assertFalse(CameStrikes.inCone(hun, towardFromHun, CameStrikes.SWEEP_HALF_ANGLE, CameStrikes.SWEEP_RANGE, dodged));
        assertFalse(CameStrikes.inCone(vucub, towardFromVucub, CameStrikes.SWEEP_HALF_ANGLE, CameStrikes.SWEEP_RANGE,
            dodged));
    }

    @Test
    void lanesHaveAWidthAndEnds() {
        CameVec from = new CameVec(0.0, 64.0, 0.0);
        CameVec to = new CameVec(10.0, 64.0, 0.0);
        assertTrue(CameStrikes.inLane(from, to, 1.3, new CameVec(5.0, 64.0, 1.0)));
        assertFalse(CameStrikes.inLane(from, to, 1.3, new CameVec(5.0, 64.0, 2.0)));
        assertFalse(CameStrikes.inLane(from, to, 1.3, new CameVec(12.0, 64.0, 0.0)));
    }

    @Test
    void meleeStandsBesidesTheTargetOnItsOwnSide() {
        CameVec target = new CameVec(0.0, 64.0, 0.0);
        CameVec spot = CameStrikes.besides(new CameVec(10.0, 64.0, 0.0), target, 1.8);
        assertEquals(1.8, spot.x(), 1.0e-9);
        assertEquals(0.0, spot.z(), 1.0e-9);
        CameVec dash = CameStrikes.dashEnd(new CameVec(7.0, 64.0, 0.0), target);
        assertEquals(7.0 - CameStrikes.DASH_LENGTH, dash.x(), 1.0e-9);
    }
}
