package dev.linqfy.bigCasares.modules.mythicdungeons.boss.iblis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IblisDashTest {

    /** 33x33 room (x, z from 1 to 33). */
    private static final IblisRoom ROOM = new IblisRoom(1, 33, 1, 33, 65);

    @Test
    void restAimDashRestWithTheWarningBeforeTheMove() {
        IblisDash dash = new IblisDash(0);
        IblisDash.Plan plan = new IblisDash.Plan(new IblisPoint(5, 5), new IblisPoint(19, 5), 0);
        assertNull(dash.advance(IblisDash.REST_TICKS - 1, () -> plan));
        assertEquals(IblisDash.Phase.AIM, dash.advance(IblisDash.REST_TICKS, () -> plan));
        assertEquals(0.0, dash.progress(IblisDash.REST_TICKS + 5), "nothing moves while aiming");
        int go = IblisDash.REST_TICKS + IblisDash.AIM_TICKS;
        assertTrue(IblisDash.AIM_TICKS >= IblisTelegraphs.MINIMUM_WARNING, "at least 0.8 s of warning");
        assertEquals(IblisDash.Phase.DASH, dash.advance(go, () -> plan));
        assertEquals(5 + 10 * IblisDash.SPEED, dash.position(go + 10).x(), 1.0e-9);
        int ticks = (int) Math.ceil(14 / IblisDash.SPEED - 1.0e-9);
        assertNull(dash.advance(go + ticks - 1, () -> plan));
        assertEquals(IblisDash.Phase.REST, dash.advance(go + ticks, () -> plan));
        assertNull(dash.plan());
    }

    @Test
    void vesselDashesRunThroughTheVesselAndStayInTheRoom() {
        List<IblisPoint> vessels = List.of(new IblisPoint(10, 17), new IblisPoint(24, 17), new IblisPoint(17, 10),
            new IblisPoint(17, 24));
        SplittableRandom random = new SplittableRandom(7);
        int throughVessels = 0;
        for (int round = 0; round < 200; round++) {
            IblisPoint boss = new IblisPoint(3 + random.nextDouble() * 28, 3 + random.nextDouble() * 28);
            IblisDash.Plan plan = IblisDash.plan(boss, vessels, List.of(new IblisPoint(17, 17)), ROOM, random);
            assertTrue(ROOM.containsColumn(plan.to().x(), plan.to().z()), "end inside: " + plan);
            assertTrue(plan.to().x() >= 1 + IblisDash.MARGIN - 1.0e-9 && plan.to().x() <= 34 - IblisDash.MARGIN + 1.0e-9);
            if (plan.vessel() >= 0) {
                throughVessels++;
                IblisPoint vessel = vessels.get(plan.vessel());
                assertTrue(vessel.distance(boss) > IblisVessels.CAPTURE_RADIUS, "never the vessel he stands on");
                assertTrue(vessel.distanceToSegment(plan.from(), plan.to()) < IblisVessels.CAPTURE_RADIUS,
                    "the path passes over the vessel: " + plan);
            }
        }
        assertTrue(throughVessels > 100 && throughVessels < 180, "about 70% through vessels: " + throughVessels);
    }

    @Test
    void withoutPlayersHeStillRunsThroughVessels() {
        IblisDash.Plan plan = IblisDash.plan(new IblisPoint(17, 17), List.of(new IblisPoint(25, 17)), List.of(), ROOM,
            new SplittableRandom(1));
        assertEquals(0, plan.vessel());
        assertEquals(25 + IblisDash.OVERSHOOT, plan.to().x(), 1.0e-9);
        IblisDash.Plan none = IblisDash.plan(new IblisPoint(17, 17), List.of(new IblisPoint(17.5, 17)), List.of(),
            ROOM, new SplittableRandom(1));
        assertEquals(-1, none.vessel(), "only a vessel under him: goes to the centre");
    }

    @Test
    void clipCutsAtTheWallMargin() {
        IblisPoint end = IblisDash.clip(new IblisPoint(17, 17), new IblisPoint(60, 17), ROOM);
        assertEquals(34 - IblisDash.MARGIN, end.x(), 1.0e-9);
        assertEquals(17, end.z(), 1.0e-9);
    }
}
