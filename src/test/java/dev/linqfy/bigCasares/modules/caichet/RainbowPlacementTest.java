package dev.linqfy.bigCasares.modules.caichet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RainbowPlacementTest {

    private static final double EPS = 1.0e-9;

    @Test
    void theCopyIsAtMostFortyEightBlocksFromTheEyes() {
        assertEquals(48.0, RainbowPlacement.placementDistance(120), EPS);
        assertEquals(48.0, RainbowPlacement.placementDistance(48), EPS);
        assertEquals(30.0, RainbowPlacement.placementDistance(30), EPS);
        assertEquals(0.0, RainbowPlacement.placementDistance(-5), EPS);
    }

    @Test
    void theCopySitsOnTheLineFromTheEyesToTheRainbow() {
        RainbowPlacement.Placement p = RainbowPlacement.place(10, 70, 20, 10, 70, 140, 100);
        assertEquals(10, p.x(), EPS);
        assertEquals(70, p.y(), EPS);
        assertEquals(68, p.z(), EPS);
        RainbowPlacement.Placement diagonal = RainbowPlacement.place(0, 0, 0, 60, 80, 0, 100);
        assertEquals(28.8, diagonal.x(), 1.0e-9);
        assertEquals(38.4, diagonal.y(), 1.0e-9);
        assertEquals(0, diagonal.z(), EPS);
    }

    @Test
    void scaleKeepsTheApparentSizeAndIsDividedByTheBakedModelSize() {
        // 100 blocks at 120 with the copy at 48: apparent 40 blocks, 2.5 times the 16-block model
        assertEquals(2.5, RainbowPlacement.place(0, 0, 0, 0, 0, 120, 100).scale(), EPS);
        // closer than 48 the copy sits on the rainbow itself: scale = size / 16
        assertEquals(100 / 16.0, RainbowPlacement.place(0, 0, 0, 0, 0, 30, 100).scale(), EPS);
        assertEquals(110 * 48.0 / 90.0 / 16.0, RainbowPlacement.place(0, 0, 0, 90, 0, 0, 110).scale(), EPS);
    }

    @Test
    void scaleIsClampedToTheAttributeLimits() {
        assertEquals(RainbowPlacement.MAX_SCALE, RainbowPlacement.place(0, 0, 0, 0, 0, 1, 10000).scale(), EPS);
        assertEquals(RainbowPlacement.MIN_SCALE, RainbowPlacement.place(0, 0, 0, 0, 0, 1000, 1).scale(), EPS);
    }

    @Test
    void standingOnTheAnchorGivesAFiniteCopy() {
        RainbowPlacement.Placement p = RainbowPlacement.place(5, 6, 7, 5, 6, 7, 80);
        assertEquals(5, p.x(), EPS);
        assertEquals(6, p.y(), EPS);
        assertEquals(7, p.z(), EPS);
        assertEquals(5.0, p.scale(), EPS);
        assertEquals(0f, p.yaw());
    }

    @Test
    void theCopyFacesTheViewer() {
        // rainbow to the south (+z): the copy looks north (-z), yaw 180; east (+x): looks west, yaw 90
        assertEquals(180f, Math.abs(RainbowPlacement.place(0, 0, 0, 0, 0, 100, 90).yaw()), 1.0e-3f);
        assertEquals(0f, RainbowPlacement.place(0, 0, 0, 0, 0, -100, 90).yaw(), 1.0e-3f);
        assertEquals(90f, RainbowPlacement.place(0, 0, 0, 100, 0, 0, 90).yaw(), 1.0e-3f);
        assertEquals(-90f, RainbowPlacement.place(0, 0, 0, -100, 0, 0, 90).yaw(), 1.0e-3f);
    }
}
