package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Network is the external boundary; lifecycle and rollback execute the production rig. */
class CameraRigTest {
    private static final class Transport implements CameraRig.Transport {
        final List<String> calls=new ArrayList<>();
        Exception opening,closing;
        public void open() throws Exception {calls.add("open");if(opening!=null)throw opening;}
        public void close() throws Exception {calls.add("restore");if(closing!=null)throw closing;}
    }
    @Test void openingAndClosingRestoreExactlyOnce() {
        var transport=new Transport();var rig=CameraRig.open(transport);
        assertEquals(List.of("open"),transport.calls);
        rig.close();rig.close();assertEquals(List.of("open","restore"),transport.calls);
    }
    @Test void failedOpeningRollsBackBeforeReportingFailure() {
        var transport=new Transport();transport.opening=new IOException("wire failed after spawn");
        var failure=assertThrows(IllegalStateException.class,()->CameraRig.open(transport));
        assertSame(transport.opening,failure.getCause());assertEquals(List.of("open","restore"),transport.calls);
    }
    @Test void rollbackFailurePreservesOriginalOpeningError() {
        var transport=new Transport();transport.opening=new IOException("open failed");
        transport.closing=new IOException("restore failed");
        var failure=assertThrows(IllegalStateException.class,()->CameraRig.open(transport));
        assertSame(transport.opening,failure.getCause());
        assertEquals(1,transport.opening.getSuppressed().length);
        assertSame(transport.closing,transport.opening.getSuppressed()[0]);
    }
    @Test void closeFailureIsReportedAndCannotSendDuplicateCleanup() {
        var transport=new Transport();var rig=CameraRig.open(transport);transport.closing=new IOException("closed wire");
        var failure=assertThrows(IllegalStateException.class,rig::close);
        assertSame(transport.closing,failure.getCause());assertDoesNotThrow(rig::close);
        assertEquals(List.of("open","restore"),transport.calls);
    }
    @Test void concurrentShutdownAndOwnerCloseSendOneRestoration() throws Exception {
        var restorations=new AtomicInteger();
        var rig=CameraRig.open(new CameraRig.Transport() {
            public void open() { }
            public void close() {restorations.incrementAndGet();}
        });
        try(var pool=Executors.newFixedThreadPool(4)) {
            var start=new CountDownLatch(1);var futures=new ArrayList<Future<?>>();
            for(int i=0;i<12;i++)futures.add(pool.submit(()->{start.await();rig.close();return null;}));
            start.countDown();for(var future:futures)future.get(5,TimeUnit.SECONDS);
        }
        assertEquals(1,restorations.get());
    }
    @Test void nullTransportIsRejectedBeforeAnyNetworkOperation() {
        assertThrows(NullPointerException.class,()->CameraRig.open(null));
    }
    @Test void untargetedVersionsFailClosedWithoutRequiringNmsClasses() {
        for(String version:new String[]{null,"1.21.11","26.1","26.2.1","26.3","26.2-pre1"}) {
            assertThrows(IllegalStateException.class,()->CameraPackets.initialize(version));
            assertFalse(CameraPackets.available());
        }
    }
    @Test void unavailableCameraRejectsChannelCreationBeforeReadingPlayer() {
        assertThrows(IllegalStateException.class,()->CameraPackets.initialize("untargeted"));
        assertThrows(IllegalStateException.class,()->CameraPackets.channel(null));
    }
}
