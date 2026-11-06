package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiSessionTimingTest {
    private static Screen screen(RenderQuality quality) { return new Screen("t","T",List.of(),quality); }
    @Test void clicksWaitForTheNativeFadeInOnEveryQuality() {
        for (var quality : List.of(RenderQuality.low(), RenderQuality.medium(), RenderQuality.legacy())) {
            assertFalse(UiSession.entranceComplete(screen(quality), UiSession.ENTRANCE_NANOS - 1));
            assertTrue(UiSession.entranceComplete(screen(quality), UiSession.ENTRANCE_NANOS));
        }
    }
    @Test void openingAnimationsSetTheFadeTheSlideAndWhenClicksStart() {
        var slide = screen(RenderQuality.low()).withLogic(dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.EMPTY.withAnimation(new dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Animation(
                new dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Transition("slide", 400, "linear", "right", 40), null)))
                .withPresentation(new Screen.Presentation("menu", 2, "cross"));
        assertEquals(0, UiSession.fadeNanos(slide), "a moving title cannot fade");
        assertEquals(400_000_000L, UiSession.entranceNanos(slide));
        assertEquals(80, UiSession.slideUnits(slide, 0), "logical px times the presentation scale");
        assertEquals(40, UiSession.slideUnits(slide, 200_000_000L));
        assertEquals(0, UiSession.slideUnits(slide, 400_000_000L));
        assertFalse(UiSession.entranceComplete(slide, 399_999_999L)); assertTrue(UiSession.entranceComplete(slide, 400_000_000L));
        var none = screen(RenderQuality.low()).withLogic(dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.EMPTY.withAnimation(new dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Animation(
                dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Transition.NONE, null)));
        assertTrue(UiSession.entranceComplete(none, 0)); assertEquals(0, UiSession.fadeNanos(none));
        var slow = screen(RenderQuality.low()).withLogic(dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.EMPTY.withAnimation(new dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Animation(
                dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Transition.fade(600), null)));
        assertEquals(600_000_000L, UiSession.fadeNanos(slow)); assertEquals(0, UiSession.slideUnits(slow, 0));
    }
    @Test void aClosingSlideMovesTheLastFrameOutAndNeverTheOpening() {
        var out = screen(RenderQuality.low()).withLogic(dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.EMPTY.withAnimation(new dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Animation(
                dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Transition.NONE, new dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ScreenLogic.Transition("slide", 300, "linear", "left", 30))))
                .withPresentation(new Screen.Presentation("menu", 2, "cross"));
        assertEquals(0, UiSession.exitUnits(out, 0));
        assertEquals(-30, UiSession.exitUnits(out, 150_000_000L), "towards the left, logical px times the presentation scale");
        assertEquals(-60, UiSession.exitUnits(out, 300_000_000L));
        assertEquals(0, UiSession.slideUnits(out, 0), "the opening does not move");
        assertTrue(UiSession.entranceComplete(out, 0), "a slide-out does not delay clicks");
        assertEquals(0, UiSession.exitUnits(screen(RenderQuality.low()), 100_000_000L), "default close does not move");
    }
}
