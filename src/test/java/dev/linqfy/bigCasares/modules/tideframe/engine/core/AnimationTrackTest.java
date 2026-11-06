package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationTrackTest {
    @Test void fadeStartsTransparentAndFinishesOpaque() {
        assertEquals(new AnimationTrack.Frame(0, 0, 1), AnimationTrack.at(AnimationTrack.Kind.FADE, 0));
        assertEquals(new AnimationTrack.Frame(1, 0, 1), AnimationTrack.at(AnimationTrack.Kind.FADE, 1));
    }
    @Test void slideMovesFromQuarterUnitOffsetToZero() {
        assertEquals(new AnimationTrack.Frame(1, .25, 1), AnimationTrack.at(AnimationTrack.Kind.SLIDE, 0));
        assertEquals(new AnimationTrack.Frame(1, .125, 1), AnimationTrack.at(AnimationTrack.Kind.SLIDE, .5));
        assertEquals(new AnimationTrack.Frame(1, 0, 1), AnimationTrack.at(AnimationTrack.Kind.SLIDE, 1));
    }
    @Test void scaleNeverReachesInvalidZeroAndFinishesAtUnitScale() {
        assertEquals(new AnimationTrack.Frame(1, 0, .05), AnimationTrack.at(AnimationTrack.Kind.SCALE, 0));
        assertEquals(new AnimationTrack.Frame(1, 0, 1), AnimationTrack.at(AnimationTrack.Kind.SCALE, 1));
    }
    @Test void clampsProgressAndUsesSmoothstepInsteadOfLinearMotion() {
        assertEquals(0, AnimationTrack.at(AnimationTrack.Kind.FADE, -5).opacity());
        assertEquals(1, AnimationTrack.at(AnimationTrack.Kind.FADE, 5).opacity());
        assertEquals(.15625, AnimationTrack.at(AnimationTrack.Kind.FADE, .25).opacity(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> AnimationTrack.at(AnimationTrack.Kind.FADE, Double.NaN));
    }
    @Test void allTracksAreMonotonicAndBounded() {
        double opacity = -1, offset = 1, scale = 0;
        for (int step = 0; step <= 100; step++) {
            double t = step / 100.0;
            var fade = AnimationTrack.at(AnimationTrack.Kind.FADE, t);
            var slide = AnimationTrack.at(AnimationTrack.Kind.SLIDE, t);
            var scaling = AnimationTrack.at(AnimationTrack.Kind.SCALE, t);
            assertTrue(fade.opacity() >= opacity && fade.opacity() <= 1);
            assertTrue(slide.offsetY() <= offset && slide.offsetY() >= 0);
            assertTrue(scaling.scale() >= scale && scaling.scale() <= 1);
            opacity = fade.opacity(); offset = slide.offsetY(); scale = scaling.scale();
        }
    }
}
