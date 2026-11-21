package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IblisVesselsTest {

    private static IblisVessels vessels() {
        return new IblisVessels(List.of(new IblisPoint(0, 0), new IblisPoint(10, 0), new IblisPoint(0, 10)));
    }

    @Test
    void anArmedVesselThatCatchesNothingCoolsDownAndRecharges() {
        IblisVessels vessels = vessels();
        assertTrue(vessels.arm(0, 100));
        assertFalse(vessels.arm(0, 101), "already armed");
        assertEquals(IblisVessels.State.ARMED, vessels.state(0));
        assertEquals(IblisVessels.ARMED_TICKS, vessels.remaining(0, 100));
        assertTrue(vessels.advance(100 + IblisVessels.ARMED_TICKS - 1).isEmpty());
        assertEquals(List.of(new IblisVessels.Event(0, IblisVessels.Change.EXPIRED)),
            vessels.advance(100 + IblisVessels.ARMED_TICKS));
        assertEquals(IblisVessels.State.COOLDOWN, vessels.state(0));
        int back = 100 + IblisVessels.ARMED_TICKS + IblisVessels.RECHARGE_TICKS;
        assertEquals(List.of(new IblisVessels.Event(0, IblisVessels.Change.RECHARGED)), vessels.advance(back));
        assertEquals(IblisVessels.State.IDLE, vessels.state(0));
    }

    @Test
    void onlyAnArmedVesselCloseEnoughTrapsHim() {
        IblisVessels vessels = vessels();
        assertTrue(vessels.capture(new IblisPoint(0.5, 0.5), 10).isEmpty(), "idle vessels do nothing");
        vessels.arm(1, 10);
        assertTrue(vessels.capture(new IblisPoint(10 - IblisVessels.CAPTURE_RADIUS - 0.2, 0), 11).isEmpty());
        OptionalInt caught = vessels.capture(new IblisPoint(10 - IblisVessels.CAPTURE_RADIUS + 0.2, 0), 12);
        assertEquals(OptionalInt.of(1), caught);
        assertTrue(vessels.trapped());
        assertEquals(IblisVessels.State.SEALED, vessels.state(1));
        vessels.arm(0, 13);
        assertTrue(vessels.capture(new IblisPoint(0, 0), 14).isEmpty(), "one trap at a time");
    }

    @Test
    void theTrapWindowWarnsBeforeHeBreaksOutAndTheVesselCracks() {
        IblisVessels vessels = vessels();
        vessels.arm(2, 0);
        vessels.capture(new IblisPoint(0, 9), 5);
        int release = 5 + IblisVessels.TRAP_TICKS;
        assertTrue(vessels.advance(release - IblisVessels.BREAKOUT_WARNING - 1).isEmpty());
        assertEquals(List.of(new IblisVessels.Event(2, IblisVessels.Change.BREAKOUT_WARNING)),
            vessels.advance(release - IblisVessels.BREAKOUT_WARNING));
        assertTrue(vessels.advance(release - 1).isEmpty(), "warned once");
        assertTrue(vessels.trapped());
        assertEquals(List.of(new IblisVessels.Event(2, IblisVessels.Change.RELEASED)), vessels.advance(release));
        assertFalse(vessels.trapped());
        assertEquals(IblisVessels.State.COOLDOWN, vessels.state(2));
        assertEquals(IblisVessels.CRACKED_TICKS, vessels.remaining(2, release));
        assertTrue(vessels.nearestIdle(new IblisPoint(0, 10)).isEmpty(), "a cracked vessel cannot be armed");
    }

    @Test
    void playersArmTheNearestIdleVesselInReach() {
        IblisVessels vessels = vessels();
        assertEquals(OptionalInt.of(1), vessels.nearestIdle(new IblisPoint(8, 0)));
        assertTrue(vessels.nearestIdle(new IblisPoint(5, 5)).isEmpty(), "too far from all");
        vessels.arm(1, 0);
        assertTrue(vessels.nearestIdle(new IblisPoint(8, 0)).isEmpty());
        assertEquals(OptionalInt.of(1), vessels.nearest(new IblisPoint(8, 0)));
        assertEquals(List.of(), vessels.reset());
        assertEquals(IblisVessels.State.IDLE, vessels.state(1));
    }
}
