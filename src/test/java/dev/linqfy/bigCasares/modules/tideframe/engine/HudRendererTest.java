package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.PlaneProjection;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HudRendererTest {
    private static final Screen SCREEN = new Screen("menu", "MENU", List.of(
        new Screen.Element("button", "GO", 0, 0, .5, .25, "button", "close")));
    private static final class Target implements HudRenderer.Channel {
        final List<String> shown = new ArrayList<>();
        int cleared;
        public void show(String glyphs) { shown.add(glyphs); }
        public void clear() { cleared++; }
    }
    @Test void firstFrameAndChangedCursorPublishImmediatelyWithoutWorldEntities() {
        var target = new Target();
        var renderer = new HudRenderer(HudAssets.create(List.of(SCREEN)), SCREEN, target);
        renderer.frame(new PlaneProjection.Point(0, 0), null, 1);
        renderer.frame(new PlaneProjection.Point(.5, .25), null, 1);
        assertEquals(2, target.shown.size());
        assertNotEquals(target.shown.getFirst(), target.shown.getLast());
        assertTrue(target.shown.stream().allMatch(text -> text.length() <= 60),"Up to 16 canvas and 4 cursor tiles, three codepoints each");
        assertEquals(0, target.cleared);
    }
    @Test void stableFrameIsThrottledAndRenewedBeforeShortNativeTitleExpiry() {
        var target = new Target();
        var renderer = new HudRenderer(HudAssets.create(List.of(SCREEN)), SCREEN, target);
        renderer.frame(new PlaneProjection.Point(0, 0), null, 1);
        for (int i=0; i<9; i++) renderer.frame(new PlaneProjection.Point(0, 0), null, 1);
        assertEquals(1, target.shown.size());
        renderer.frame(new PlaneProjection.Point(0, 0), null, 1);
        assertEquals(2, target.shown.size());
        assertEquals(target.shown.getFirst(), target.shown.getLast());
    }
    @Test void closeClearsOnlyOnceAndClosedFramesCannotReopenChannel() {
        var target = new Target();
        var renderer = new HudRenderer(HudAssets.create(List.of(SCREEN)), SCREEN, target);
        renderer.frame(new PlaneProjection.Point(0, 0), null, 1);
        renderer.close(); renderer.close();
        renderer.frame(new PlaneProjection.Point(.5, .5), "button", 1);
        assertEquals(1, target.shown.size()); assertEquals(1, target.cleared);
    }
    @Test void abandonStopsPublishingWithoutMutatingAPlayerFromWrongFoliaOwner() {
        var target = new Target();
        var renderer = new HudRenderer(HudAssets.create(List.of(SCREEN)), SCREEN, target);
        renderer.frame(new PlaneProjection.Point(0, 0), null, 1);
        renderer.abandon(); renderer.close();
        renderer.frame(new PlaneProjection.Point(.5, .5), null, 1);
        assertEquals(1, target.shown.size()); assertEquals(0, target.cleared);
    }
    @Test void unknownLayoutIsRejectedBeforeAnyChannelSideEffect() {
        var target = new Target();
        assertThrows(IllegalArgumentException.class, () -> new HudRenderer(
            HudAssets.create(List.of(SCREEN)), SCREEN.move("button", .2, .2), target));
        assertTrue(target.shown.isEmpty()); assertEquals(0, target.cleared);
    }
    @Test void passiveFramesPublishOnlyTheScreenWithoutACursor() {
        var assets=HudAssets.create(List.of(SCREEN));var target=new Target();
        var renderer=new HudRenderer(assets,SCREEN,target);
        renderer.passiveFrame();
        assertEquals(List.of(assets.frame(SCREEN,null,8)),target.shown);
        for(int i=0;i<9;i++)renderer.passiveFrame();
        assertEquals(1,target.shown.size());
        renderer.passiveFrame();assertEquals(2,target.shown.size());
    }
    @Test void replacingAKnownScreenPublishesAtomicallyWithoutClearingTheChannel() {
        var other=new Screen("other","OTHER",List.of(new Screen.Element("label","STATUS",.3,.2,.4,.2,"label","")));
        var assets=HudAssets.create(List.of(SCREEN,other));var target=new Target();
        var renderer=new HudRenderer(assets,SCREEN,target);renderer.passiveFrame();
        renderer.replaceScreen(other);
        assertEquals(1,target.shown.size());assertEquals(0,target.cleared);
        renderer.passiveFrame();
        assertEquals(assets.frame(other,null,8),target.shown.getLast());
        assertNotEquals(target.shown.getFirst(),target.shown.getLast());
    }
    @Test void anUnknownReplacementPreservesThePreviousScreenAndPublishState() {
        var assets=HudAssets.create(List.of(SCREEN));var target=new Target();
        var renderer=new HudRenderer(assets,SCREEN,target);renderer.passiveFrame();
        assertThrows(IllegalArgumentException.class,()->renderer.replaceScreen(SCREEN.move("button",.2,.2)));
        renderer.passiveFrame();
        assertEquals(List.of(assets.frame(SCREEN,null,8)),target.shown);assertEquals(0,target.cleared);
    }
    @Test void underlayIsComposedBeforeMenuAndCursorAndItsChangesPublishImmediately() {
        var other=new Screen("other","OTHER",List.of(new Screen.Element("label","HUD",.5,-.5,.3,.2,"label","")));
        var assets=HudAssets.create(List.of(SCREEN,other));var target=new Target();
        var underlay=new AtomicReference<>(assets.frame(other,null,8));
        var renderer=new HudRenderer(assets,SCREEN,target,underlay::get);
        var cursor=new PlaneProjection.Point(0,0);renderer.frame(cursor,null,1);
        String menu=assets.frame(SCREEN,null,8)+assets.cursor(SCREEN,0,0);
        assertEquals(underlay.get()+menu,target.shown.getFirst());
        underlay.set("");renderer.frame(cursor,null,1);
        assertEquals(List.of(assets.frame(other,null,8)+menu,menu),target.shown);
        assertEquals(0,target.cleared);
    }
    @Test void passiveForceRefreshOverridesThrottlingAndClosedFramesDoNotReadUnderlay() {
        var assets=HudAssets.create(List.of(SCREEN));var target=new Target();
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        var renderer=new HudRenderer(assets,SCREEN,target,()->{reads.incrementAndGet();return "";});
        renderer.passiveFrame();renderer.passiveFrame(true);
        assertEquals(2,target.shown.size());assertEquals(2,reads.get());
        renderer.close();renderer.close();renderer.passiveFrame(true);
        renderer.frame(new PlaneProjection.Point(0,0),null,1);
        assertEquals(2,target.shown.size());assertEquals(2,reads.get());assertEquals(1,target.cleared);
    }
}
