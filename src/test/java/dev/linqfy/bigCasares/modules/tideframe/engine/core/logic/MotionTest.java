package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Keyframes, open/close animations, hover actions and tooltips: model, parsing and JSON. */
class MotionTest {
    private static Motion motion(String json) { return ActionParser.motion(ScreenCodec.parse(json)); }

    @Test void keyframesInterpolateWithEasingAndCarryValuesForward() {
        var m = motion("{\"trigger\":\"open\",\"easing\":\"linear\",\"steps\":[{\"at\":0,\"x\":-40,\"opacity\":0},{\"at\":200,\"x\":0,\"opacity\":1},{\"at\":400,\"scale\":1.5}]}");
        assertEquals(new Motion.Pose(-40, 0, 1, 0, 0), m.at(0));
        assertEquals(new Motion.Pose(-20, 0, 1, .5, 0), m.at(100));
        assertEquals(new Motion.Pose(0, 0, 1.25, 1, 0), m.at(300), "x and opacity carry over into the next segment");
        assertEquals(new Motion.Pose(0, 0, 1.5, 1, 0), m.at(999));
        assertEquals(9, m.frames()); assertEquals(50, m.intervalMs());
        assertEquals(m.at(0), m.frame(0)); assertEquals(m.at(400), m.frame(8));
        var eased = motion("{\"easing\":\"ease-out\",\"steps\":[{\"at\":0,\"x\":0},{\"at\":100,\"x\":100}]}");
        assertTrue(eased.at(50).x() > 50, "ease-out is ahead at the midpoint");
        assertEquals("open", eased.trigger());
        var longOne = motion("{\"steps\":[{\"at\":0},{\"at\":9000,\"rotation\":90}]}");
        assertEquals(Motion.MAX_FRAMES, longOne.frames()); assertEquals(143, longOne.intervalMs());
        assertEquals(.5, Motion.ease("ease-in-out", .5), 1e-9);
    }

    @Test void playbackRunsForwardLoopsAndTurnsBack() {
        long ms = 1_000_000;
        assertEquals(0, Motion.frameAt(0, 50, 9, 0, false, false));
        assertEquals(3, Motion.frameAt(160 * ms, 50, 9, 0, false, false));
        assertEquals(8, Motion.frameAt(10_000 * ms, 50, 9, 0, false, false), "a one-shot holds its last frame");
        assertEquals(1, Motion.frameAt(500 * ms, 50, 9, 0, false, true), "loops wrap");
        assertEquals(2, Motion.frameAt(100 * ms, 50, 9, 4, true, false), "back towards the first frame");
        assertEquals(0, Motion.frameAt(10_000 * ms, 50, 9, 4, true, false));
    }

    @Test void invalidKeyframesAreRejected() {
        for (String bad : new String[]{
                "{\"steps\":[{\"at\":0}]}",
                "{\"steps\":[{\"at\":10},{\"at\":20}]}",
                "{\"steps\":[{\"at\":0},{\"at\":0}]}",
                "{\"trigger\":\"click\",\"steps\":[{\"at\":0},{\"at\":50}]}",
                "{\"easing\":\"bounce\",\"steps\":[{\"at\":0},{\"at\":50}]}",
                "{\"steps\":[{\"at\":0,\"scale\":9},{\"at\":50}]}",
                "{\"steps\":[{\"at\":0,\"wobble\":1},{\"at\":50}]}"})
            assertThrows(IllegalArgumentException.class, () -> motion(bad), bad);
    }

    @Test void screenAnimationParsesSlidesFadesAndReversedCloses() {
        var a = ActionParser.animation(ScreenCodec.parse("{\"open\":{\"effect\":\"slide\",\"from\":\"right\",\"distance\":40,\"duration-ms\":400,\"easing\":\"linear\"},\"close-reversed\":true}"));
        assertEquals("slide", a.open().effect()); assertEquals(ScreenLogic.Transition.fade(400), a.close());
        assertEquals(40, a.open().offset(0)); assertEquals(20, a.open().offset(200)); assertEquals(0, a.open().offset(400)); assertEquals(0, a.open().offset(9999));
        var left = new ScreenLogic.Transition("slide", 100, "linear", "left", 10);
        assertEquals(-10, left.offset(0)); assertEquals(0, ScreenLogic.Transition.fade(300).offset(0), "fades never move");
        assertEquals(ScreenLogic.Animation.DEFAULT, ActionParser.animation(null));
        var out = ActionParser.animation(ScreenCodec.parse("{\"close\":{\"effect\":\"slide\",\"from\":\"right\",\"distance\":40,\"duration-ms\":400,\"easing\":\"linear\"}}")).close();
        assertEquals(0, out.exitOffset(0)); assertEquals(20, out.exitOffset(200)); assertEquals(40, out.exitOffset(400)); assertEquals(40, out.exitOffset(9999), "it stays gone");
        assertEquals(0, ScreenLogic.Transition.fade(300).exitOffset(100), "fades never move");
        assertEquals(new ScreenLogic.Transition("slide", 450, "ease-in", "left", 64), new ScreenLogic.Transition("slide", 450, "ease-out", "left", 64).reversed());
        assertEquals("linear", left.reversed().easing());
        var roundTrip = ActionParser.screen(ScreenCodec.parse(LogicJson.screen(ScreenLogic.EMPTY.withAnimation(new ScreenLogic.Animation(ScreenLogic.Animation.DEFAULT.open(), out)))));
        assertEquals(out, roundTrip.animation().close(), "a slide-out survives the JSON");
        assertThrows(IllegalArgumentException.class, () -> ActionParser.animation(ScreenCodec.parse("{\"open\":{\"effect\":\"zoom\"}}")));
        assertEquals(0, new ScreenLogic.Transition("none", 500, "linear", "left", 10).durationMs());
    }

    @Test void newLogicRoundTripsThroughSchemaNine() {
        var tip = new Screen.Element("tip", "BUY IT", .5, .5, .4, .12, "label", "");
        var buy = new Screen.Element("buy", "BUY", 0, 0, .5, .15, "button", "").withLogic(ActionParser.element(ScreenCodec.parse("""
            {"on-hover":[{"type":"sound","value":"ui.button.click"}],"on-leave":[{"type":"message","value":"bye"}],"tooltip":"tip",
             "keyframes":{"trigger":"hover","easing":"ease-out","steps":[{"at":0},{"at":150,"scale":1.08,"y":-1.5}]}}""")));
        var logo = new Screen.Element("logo", "LOGO", 0, .7, .4, .15, "label", "").withLogic(ActionParser.element(ScreenCodec.parse(
            "{\"keyframes\":{\"trigger\":\"loop\",\"steps\":[{\"at\":0,\"rotation\":0},{\"at\":1000,\"rotation\":360}]}}")));
        var screen = new Screen("shop", "SHOP", List.of(tip, buy, logo), RenderQuality.low())
                .withLogic(ActionParser.screen(ScreenCodec.parse("{\"animation\":{\"open\":{\"effect\":\"slide\",\"from\":\"left\",\"duration-ms\":300},\"close\":{\"effect\":\"fade\",\"duration-ms\":250}}}")));
        String json = ScreenCodec.encode(screen);
        assertEquals(screen, ScreenCodec.decode(json), json);
        assertTrue(json.contains("\"tooltip\":\"tip\"") && json.contains("\"on-hover\"") && json.contains("\"keyframes\"") && json.contains("\"slide\""), json);
        assertEquals(java.util.Set.of("buy", "logo", "tip"), screen.runtimeIds(), "tooltips and animated elements are runtime layers");
        assertTrue(screen.logicProblems().isEmpty(), screen.logicProblems().toString());
        var view = ViewState.initial(screen, 5);
        assertFalse(view.shown("tip"), "a tooltip starts hidden");
        assertEquals(ViewState.Play.REST, view.play("buy")); assertEquals(new ViewState.Play(5, 0, false), view.play("logo"));
    }

    @Test void hoverLogicNeedsAButtonAndKnownTargets() {
        var hoverLabel = ActionParser.element(ScreenCodec.parse("{\"on-hover\":[{\"type\":\"message\",\"value\":\"x\"}]}"));
        assertThrows(IllegalArgumentException.class, () -> new Screen.Element("a", "A", 0, 0, .2, .2, "label", "").withLogic(hoverLabel));
        var hoverMotion = ActionParser.element(ScreenCodec.parse("{\"keyframes\":{\"trigger\":\"hover\",\"steps\":[{\"at\":0},{\"at\":50,\"scale\":1.1}]}}"));
        assertThrows(IllegalArgumentException.class, () -> new Screen.Element("a", "A", 0, 0, .2, .2, "label", "").withLogic(hoverMotion));
        var self = ActionParser.element(ScreenCodec.parse("{\"tooltip\":\"a\"}"));
        assertThrows(IllegalArgumentException.class, () -> new Screen.Element("a", "A", 0, 0, .2, .2, "button", "").withLogic(self));
        assertThrows(IllegalArgumentException.class, () -> ActionParser.element(ScreenCodec.parse(
                "{\"frames\":{\"folder\":\"spin\"},\"keyframes\":{\"steps\":[{\"at\":0},{\"at\":50}]}}")), "frames and keyframes together");
        var missing = new Screen("m", "M", List.of(new Screen.Element("b", "B", 0, 0, .2, .2, "button", "").withLogic(ActionParser.element(ScreenCodec.parse("{\"tooltip\":\"ghost\"}")))), RenderQuality.low());
        assertTrue(missing.logicProblems().stream().anyMatch(p -> p.contains("ghost")), missing.logicProblems().toString());
    }

    @Test void hoverPlaybackTurnsAroundFromTheShownFrame() {
        var buy = new Screen.Element("buy", "BUY", 0, 0, .5, .15, "button", "").withLogic(ActionParser.element(ScreenCodec.parse(
                "{\"keyframes\":{\"trigger\":\"hover\",\"easing\":\"linear\",\"steps\":[{\"at\":0},{\"at\":400,\"scale\":1.2}]}}")));
        var screen = new Screen("s", "S", List.of(buy), RenderQuality.low());
        long ms = 1_000_000;
        var view = ViewState.initial(screen, 0);
        assertEquals(0, view.motionFrame(buy, 1000 * ms, 9));
        view = view.hoverMotion(buy, true, 1000 * ms, 9);
        assertEquals(3, view.motionFrame(buy, 1150 * ms, 9));
        assertSame(view, view.hoverMotion(buy, true, 1150 * ms, 9), "already playing forward");
        view = view.hoverMotion(buy, false, 1150 * ms, 9);
        assertEquals(new ViewState.Play(1150 * ms, 3, true), view.play("buy"));
        assertEquals(1, view.motionFrame(buy, 1250 * ms, 9)); assertEquals(0, view.motionFrame(buy, 9000 * ms, 9));
        var action = screen.withElements(List.of(buy.withLogic(ActionParser.element(ScreenCodec.parse(
                "{\"keyframes\":{\"trigger\":\"action\",\"steps\":[{\"at\":0},{\"at\":100,\"x\":5}]}}")))));
        var resting = ViewState.initial(action, 0);
        assertEquals(0, resting.motionFrame(action.elements().getFirst(), 5000 * ms, 3));
        var played = resting.restart("buy", 5000 * ms);
        assertEquals(2, played.motionFrame(action.elements().getFirst(), 5200 * ms, 3), "the animation action plays it");
    }
}
