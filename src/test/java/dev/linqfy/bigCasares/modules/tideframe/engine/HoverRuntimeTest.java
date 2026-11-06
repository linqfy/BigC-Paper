package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.RenderQuality;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.ViewState;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.logic.ActionParser;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.ScreenCodec;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** on-hover / on-leave actions, tooltips and hover keyframes at run time. */
class HoverRuntimeTest {
    private static Screen.Element element(String id, String kind, double y, String logic) {
        var e = new Screen.Element(id, id.toUpperCase(java.util.Locale.ROOT), 0, y, .5, .12, kind, "");
        return logic == null ? e : e.withLogic(ActionParser.element(ScreenCodec.parse(logic)));
    }

    @Test void hoverShowsTheTooltipRunsActionsAndHidesItAgain() {
        var tip = element("tip", "label", .6, null);
        var buy = element("buy", "button", 0, """
            {"tooltip":"tip","on-hover":[{"type":"actionbar","value":"over {target}"}],"on-leave":[{"type":"message","value":"left"}],
             "keyframes":{"trigger":"hover","steps":[{"at":0},{"at":100,"scale":1.1}]}}""");
        var sell = element("sell", "button", -.4, "{\"on-hover\":[{\"type\":\"sound\",\"value\":\"ui.button.click\"}]}");
        var screen = new Screen("shop", "SHOP", List.of(tip, buy, sell), RenderQuality.low());
        var host = new ScreenRuntimeTest.FakeHost(screen);
        var runtime = new ScreenRuntime(screen, new HashMap<>(), host);
        runtime.open();
        assertFalse(host.view.shown("tip"));
        runtime.hover(null, "buy");
        assertTrue(host.view.shown("tip"), "the tooltip shows while the cursor is on the button");
        assertEquals(new ViewState.Play(42, 0, false), host.view.play("buy"), "hover keyframes play forward");
        runtime.hover("buy", "sell");
        assertFalse(host.view.shown("tip"));
        assertTrue(host.view.play("buy").reverse(), "and back when the cursor leaves");
        runtime.hover("sell", null);
        assertEquals(List.of("actionbar over buy", "message left", "sound ui.button.click"), host.log);
        runtime.hover("ghost", "nothing");
        assertEquals(3, host.log.size(), "unknown targets are ignored");
    }

    @Test void listRowsGetTheirOwnScopeOnHover() {
        var row = element("row", "button", .5, "{\"list\":{\"source\":\"names\",\"rows\":3,\"spacing\":14},\"on-hover\":[{\"type\":\"actionbar\",\"value\":\"{item} #{index}\"}]}");
        var screen = new Screen("l", "L", List.of(row), RenderQuality.low())
                .withLogic(ActionParser.screen(ScreenCodec.parse("{\"variables\":{\"names\":\"Ana, Beto\"}}")));
        var host = new ScreenRuntimeTest.FakeHost(screen);
        var runtime = new ScreenRuntime(screen, new HashMap<>(), host);
        runtime.open();
        runtime.hover(null, "row#1");
        runtime.hover("row#1", "row#2");
        assertEquals(List.of("actionbar Beto #2"), host.log, "a row without data runs nothing");
    }
    @Test void theShowcaseDemoIsValidBakesAndAnimates() {
        var demo = dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.ShowcaseDemo.screen();
        assertEquals(demo, ScreenCodec.decode(ScreenCodec.encode(demo)));
        assertEquals(List.of(), demo.logicProblems());
        assertDoesNotThrow(demo::validateActionAreas);
        var prepared = dev.linqfy.bigCasares.modules.tideframe.engine.core.CloseControl.prepare(demo);
        assertEquals(java.util.Set.of("heart", "hover", "star", "tip"), demo.runtimeIds());
        var assets = dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets.create(List.of(prepared));
        assertEquals(17, assets.frameCount(prepared, "heart")); assertEquals(13, assets.frameCount(prepared, "star")); assertEquals(4, assets.frameCount(prepared, "hover"));
        assertNotNull(assets.liveFont(prepared, prepared.element("live").orElseThrow(), 4), "live text as written has its font");
        var host = new ScreenRuntimeTest.FakeHost(demo);
        var runtime = new ScreenRuntime(demo, new HashMap<>(), host);
        runtime.open();
        assertFalse(host.view.shown("tip"));
        runtime.hover(null, "hover"); assertTrue(host.view.shown("tip"));
        runtime.click("play", UiSession.Click.LEFT, "");
        assertEquals(new ViewState.Play(42, 0, false), host.view.play("star"), "the button plays the star");
        assertEquals("slide", demo.logic().animation().open().effect()); assertEquals(.5, demo.presentation().cursorSize());
    }
}
