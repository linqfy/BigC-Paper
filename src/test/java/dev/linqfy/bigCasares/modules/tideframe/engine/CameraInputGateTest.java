package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production self-target filter without requiring a running server. */
class CameraInputGateTest {
    @Test void activeSelfAttackIsConsumedAndDispatchesOneClick() {
        var clicks=new AtomicInteger();var clock=new AtomicLong();
        var gate=new CameraInputGate(123,clicks::incrementAndGet,clock::get);
        assertTrue(gate.consume(CameraInputGate.Kind.ATTACK,123));
        assertEquals(1,clicks.get());
    }
    @Test void selfInteractIsConsumedWithoutActivatingALeftClick() {
        var clicks=new AtomicInteger();
        var gate=new CameraInputGate(123,clicks::incrementAndGet,()->0L);
        assertTrue(gate.consume(CameraInputGate.Kind.INTERACT,123));
        assertEquals(0,clicks.get());
    }
    @Test void otherEntitiesAndOtherPacketKindsRemainUntouched() {
        var clicks=new AtomicInteger();
        var gate=new CameraInputGate(123,clicks::incrementAndGet,()->0L);
        assertFalse(gate.consume(CameraInputGate.Kind.ATTACK,124));
        assertFalse(gate.consume(CameraInputGate.Kind.INTERACT,-1));
        assertFalse(gate.consume(CameraInputGate.Kind.OTHER,123));
        assertEquals(0,clicks.get());
    }
    @Test void closingRestoresOrdinarySelfInteractionProtection() {
        var clicks=new AtomicInteger();
        var gate=new CameraInputGate(123,clicks::incrementAndGet,()->0L);
        gate.close();gate.close();
        assertFalse(gate.consume(CameraInputGate.Kind.ATTACK,123));
        assertFalse(gate.consume(CameraInputGate.Kind.INTERACT,123));
        assertEquals(0,clicks.get());
    }
    @Test void aBurstIsConsumedWithoutFloodingTheOwnerScheduler() {
        var clicks=new AtomicInteger();var clock=new AtomicLong(1_000_000);
        var gate=new CameraInputGate(123,clicks::incrementAndGet,clock::get);
        for(int i=0;i<1000;i++)assertTrue(gate.consume(CameraInputGate.Kind.ATTACK,123));
        assertEquals(1,clicks.get());
        clock.addAndGet(99_999_999);gate.consume(CameraInputGate.Kind.ATTACK,123);
        assertEquals(1,clicks.get());
        clock.incrementAndGet();gate.consume(CameraInputGate.Kind.ATTACK,123);
        assertEquals(2,clicks.get());
    }
    @Test void aFailedSchedulerCallbackDoesNotChangeTheFilteringLease() {
        var gate=new CameraInputGate(123,()->{throw new IllegalStateException("scheduler stopped");},()->0L);
        assertThrows(IllegalStateException.class,()->gate.consume(CameraInputGate.Kind.ATTACK,123));
        assertTrue(gate.consume(CameraInputGate.Kind.INTERACT,123));
        gate.close();assertFalse(gate.consume(CameraInputGate.Kind.INTERACT,123));
    }
    @Test void restorationConsumesLateSelfPacketsWithoutDispatchingClicks() {
        var clicks=new AtomicInteger();var clock=new AtomicLong(10);
        var gate=new CameraInputGate(123,clicks::incrementAndGet,clock::get);
        assertTrue(gate.consume(CameraInputGate.Kind.ATTACK,123));
        gate.beginRestoration();assertTrue(gate.isRestoring());
        clock.addAndGet(1_499_999_999L);
        assertTrue(gate.consume(CameraInputGate.Kind.ATTACK,123));
        assertTrue(gate.consume(CameraInputGate.Kind.INTERACT,123));assertEquals(1,clicks.get());
        assertFalse(gate.consume(CameraInputGate.Kind.ATTACK,124));
        clock.incrementAndGet();
        assertFalse(gate.consume(CameraInputGate.Kind.ATTACK,123));
        assertFalse(gate.consume(CameraInputGate.Kind.INTERACT,123));assertEquals(1,clicks.get());
    }
    @Test void repeatedRestorationRequestsCannotExtendTheBoundedGuard() {
        var clock=new AtomicLong();var gate=new CameraInputGate(123,()->fail("Restoring must not dispatch"),clock::get);
        gate.beginRestoration();clock.addAndGet(1_000_000_000L);gate.beginRestoration();
        clock.addAndGet(500_000_000L);assertFalse(gate.consume(CameraInputGate.Kind.ATTACK,123));
        gate.close();gate.beginRestoration();assertFalse(gate.isRestoring());
        assertFalse(gate.consume(CameraInputGate.Kind.ATTACK,123));
    }
}
