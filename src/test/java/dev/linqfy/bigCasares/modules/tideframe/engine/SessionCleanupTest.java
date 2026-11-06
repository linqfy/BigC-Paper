package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.PlaneProjection;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import dev.linqfy.bigCasares.modules.tideframe.engine.pack.HudAssets;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionCleanupTest {
    private static final Screen SCREEN=new Screen("cleanup","CLEANUP",List.of());
    private static final HudAssets ASSETS=HudAssets.create(List.of(SCREEN));
    private static CameraRig rig(List<String> calls,Exception failure) {
        return CameraRig.open(new CameraRig.Transport() {
            public void open() { }
            public void close() throws Exception {calls.add("camera");if(failure!=null)throw failure;}
        });
    }
    private static HudRenderer renderer(List<String> calls,RuntimeException failure) {
        return new HudRenderer(ASSETS,SCREEN,new HudRenderer.Channel() {
            public void show(String glyphs) {calls.add("show");}
            public void clear() {calls.add("title");if(failure!=null)throw failure;}
        });
    }
    @Test void failedTaskCancellationStillRestoresCameraTitleAndBossBar() {
        var calls=new ArrayList<String>();var failure=new IllegalStateException("cancel failed");
        var camera=rig(calls,null);var renderer=renderer(calls,null);
        var thrown=assertThrows(IllegalStateException.class,()->SessionCleanup.close(
            ()->{calls.add("cancel");throw failure;},camera,renderer,()->calls.add("boss")));
        assertSame(failure,thrown);assertEquals(List.of("cancel","camera","title","boss"),calls);
        camera.close();renderer.close();assertEquals(4,calls.size());
    }
    @Test void failedCameraRestorationStillClearsOwnedTitleAndBossBar() {
        var calls=new ArrayList<String>();var failure=new IOException("connection closed");
        var thrown=assertThrows(IllegalStateException.class,()->SessionCleanup.close(
            ()->calls.add("cancel"),rig(calls,failure),renderer(calls,null),()->calls.add("boss")));
        assertSame(failure,thrown.getCause());assertEquals(List.of("cancel","camera","title","boss"),calls);
    }
    @Test void failedRendererClearStillHidesTheBossBar() {
        var calls=new ArrayList<String>();var failure=new IllegalArgumentException("title clear failed");
        var thrown=assertThrows(IllegalArgumentException.class,()->SessionCleanup.close(
            ()->calls.add("cancel"),rig(calls,null),renderer(calls,failure),()->calls.add("boss")));
        assertSame(failure,thrown);assertEquals(List.of("cancel","camera","title","boss"),calls);
    }
    @Test void shutdownRestoresNetworkCameraWithoutCallingThePlayerHudChannelEvenIfCancelFails() {
        var calls=new ArrayList<String>();var cancellation=new IllegalStateException("cancel failed");
        var renderer=new HudRenderer(ASSETS,SCREEN,new HudRenderer.Channel() {
            public void show(String glyphs) {fail("Shutdown must not publish to a player from another Folia owner");}
            public void clear() {fail("Shutdown must not clear a player title from another Folia owner");}
        });
        assertSame(cancellation,assertThrows(IllegalStateException.class,()->SessionCleanup.abandon(
            ()->{calls.add("cancel");throw cancellation;},rig(calls,null),renderer)));
        assertEquals(List.of("cancel","camera"),calls);
        assertDoesNotThrow(()->renderer.frame(new PlaneProjection.Point(0,0),null,1));
        assertDoesNotThrow(renderer::close);
    }
    @Test void allFailuresAreRetainedAfterEveryRemainingStageRuns() {
        var first=new IllegalStateException("first");var second=new IllegalArgumentException("second");
        var last=new AssertionError("last");var reached=new ArrayList<String>();
        var thrown=assertThrows(IllegalStateException.class,()->SessionCleanup.run(
            ()->{throw first;},()->{throw second;},()->reached.add("state removed"),()->{throw last;}));
        assertSame(first,thrown);assertArrayEquals(new Throwable[]{second,last},thrown.getSuppressed());
        assertEquals(List.of("state removed"),reached);
    }
    @Test void repeatedFailureObjectDoesNotPreventLaterRestorationThroughSelfSuppression() {
        var same=new IllegalStateException("same");var reached=new ArrayList<String>();
        assertSame(same,assertThrows(IllegalStateException.class,()->SessionCleanup.run(
            ()->{throw same;},()->{throw same;},()->reached.add("restored"))));
        assertEquals(List.of("restored"),reached);assertEquals(0,same.getSuppressed().length);
    }
    @Test void closingAnInputSessionRestoresCameraBeforeRemovingInputThenClearsHud() {
        var calls=new ArrayList<String>();
        SessionCleanup.close(()->calls.add("cancel"),rig(calls,null),()->calls.add("input"),
            renderer(calls,null),()->calls.add("boss"));
        assertEquals(List.of("cancel","camera","input","title","boss"),calls);
    }
    @Test void failedCameraRestorationStillRemovesTheInputLeaseAndRemainingHud() {
        var calls=new ArrayList<String>();var failure=new IOException("restore failed");
        var thrown=assertThrows(IllegalStateException.class,()->SessionCleanup.close(
            ()->calls.add("cancel"),rig(calls,failure),()->calls.add("input"),renderer(calls,null),()->calls.add("boss")));
        assertSame(failure,thrown.getCause());assertEquals(List.of("cancel","camera","input","title","boss"),calls);
    }
    @Test void failedInputRemovalDoesNotSkipTitleOrBossCleanup() {
        var calls=new ArrayList<String>();var failure=new IllegalStateException("input cleanup failed");
        var thrown=assertThrows(IllegalStateException.class,()->SessionCleanup.close(
            ()->calls.add("cancel"),rig(calls,null),()->{calls.add("input");throw failure;},
            renderer(calls,null),()->calls.add("boss")));
        assertSame(failure,thrown);assertEquals(List.of("cancel","camera","input","title","boss"),calls);
    }
    @Test void shutdownInputCleanupRestoresNetworkWithoutInvokingAHudChannel() {
        var calls=new ArrayList<String>();var renderer=renderer(calls,null);
        var failure=new IllegalStateException("cancel failed");
        assertSame(failure,assertThrows(IllegalStateException.class,()->SessionCleanup.abandon(
            ()->{calls.add("cancel");throw failure;},rig(calls,null),()->calls.add("input"),renderer)));
        renderer.close();renderer.passiveFrame(true);
        assertEquals(List.of("cancel","camera","input"),calls);
    }
    @Test void failedOpeningRollsBackCameraBeforeReleasingItsInputLease() {
        var calls=new ArrayList<String>();var failure=new IOException("partial opening failed");
        var thrown=assertThrows(IllegalStateException.class,()->SessionCleanup.openCamera(()->new CameraRig.Transport() {
            public void open() throws IOException {calls.add("open");throw failure;}
            public void close() {calls.add("restore");}
        },()->calls.add("input")));
        assertSame(failure,thrown.getCause());assertEquals(List.of("open","restore","input"),calls);
    }
    @Test void failedChannelPreparationReleasesInputWithoutSendingACameraPacket() {
        var calls=new ArrayList<String>();var failure=new IllegalStateException("unsupported pose");
        assertSame(failure,assertThrows(IllegalStateException.class,()->SessionCleanup.openCamera(
            ()->{calls.add("prepare");throw failure;},()->calls.add("input"))));
        assertEquals(List.of("prepare","input"),calls);
    }
    @Test void inputRollbackFailureCannotReplaceTheOriginalOpeningError() {
        var opening=new IllegalStateException("prepare failed");var cleanup=new IllegalArgumentException("input failed");
        var thrown=assertThrows(IllegalStateException.class,()->SessionCleanup.openCamera(
            ()->{throw opening;},()->{throw cleanup;}));
        assertSame(opening,thrown);assertArrayEquals(new Throwable[]{cleanup},thrown.getSuppressed());
    }
    @Test void failedAnimationFrameRestoresCameraInputHudAndRemovesTheSession() {
        var calls=new ArrayList<String>();var animation=new IllegalStateException("animation failed");
        var transport=new IOException("restore also failed");
        var camera=rig(calls,transport);var renderer=renderer(calls,null);
        var thrown=assertThrows(IllegalStateException.class,()->SessionCleanup.advanceFrame(
            ()->{calls.add("animate");throw animation;},
            ()->SessionCleanup.run(()->SessionCleanup.close(()->calls.add("cancel"),camera,()->calls.add("input"),
                renderer,()->calls.add("boss")),()->calls.add("remove-session"))));
        assertSame(animation,thrown);assertEquals(1,thrown.getSuppressed().length);
        assertSame(transport,thrown.getSuppressed()[0].getCause());
        assertEquals(List.of("animate","cancel","camera","input","title","boss","remove-session"),calls);
    }
    @Test void aSuccessfulAnimationDoesNotCloseItsSession() {
        var calls=new ArrayList<String>();
        SessionCleanup.advanceFrame(()->calls.add("animate"),()->fail("Successful frame must keep the menu open"));
        assertEquals(List.of("animate"),calls);
    }
    @Test void anAnimationErrorAlsoRunsRestorationBeforeReportingTheOriginalError() {
        var error=new AssertionError("frame invariant failed");var calls=new ArrayList<String>();
        assertSame(error,assertThrows(AssertionError.class,()->SessionCleanup.advanceFrame(
            ()->{throw error;},()->calls.add("restore"))));
        assertEquals(List.of("restore"),calls);
    }
}
