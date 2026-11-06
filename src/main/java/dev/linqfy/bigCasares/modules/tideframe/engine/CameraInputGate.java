package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Only the synthetic camera's self-target input is filtered; every other packet passes. */
final class CameraInputGate implements AutoCloseable {
    /** INTERACT is a main-hand right click; INTERACT_OFFHAND is its off-hand echo (consumed, never dispatched). */
    enum Kind { ATTACK, INTERACT, INTERACT_OFFHAND, OTHER }
    static final long RESTORATION_NANOS=1_500_000_000L;
    private record State(boolean active,long restorationStarted) { }
    private final int playerId;
    private final Runnable click,rightClick;
    private final LongSupplier clock;
    private final AtomicReference<State> state=new AtomicReference<>(new State(true,0));
    private long lastClick=Long.MIN_VALUE,lastRightClick=Long.MIN_VALUE;
    CameraInputGate(int playerId,Runnable click,LongSupplier clock) {this(playerId,click,()->{},clock);}
    CameraInputGate(int playerId,Runnable click,Runnable rightClick,LongSupplier clock) {
        this.playerId=playerId;this.click=Objects.requireNonNull(click);this.rightClick=Objects.requireNonNull(rightClick);this.clock=Objects.requireNonNull(clock);
    }
    /** Called serially by the network event loop; the supplied callback must schedule the owner. */
    boolean consume(Kind kind,int targetId) {
        State phase=state.get();
        if(phase==null || kind==Kind.OTHER || targetId!=playerId)return false;
        if(!phase.active())return clock.getAsLong()-phase.restorationStarted()<RESTORATION_NANOS;
        if(kind==Kind.ATTACK) {
            long now=clock.getAsLong();
            // The normal connection limiter cannot see a consumed packet. Bound dispatch explicitly.
            if(lastClick==Long.MIN_VALUE || now-lastClick>=100_000_000L) {
                lastClick=now;click.run();
            }
        } else if(kind==Kind.INTERACT) {
            long now=clock.getAsLong();
            if(lastRightClick==Long.MIN_VALUE || now-lastRightClick>=100_000_000L) {
                lastRightClick=now;rightClick.run();
            }
        }
        return true;
    }
    void beginRestoration() {
        for(;;) {
            State previous=state.get();if(previous==null || !previous.active())return;
            if(state.compareAndSet(previous,new State(false,clock.getAsLong())))return;
        }
    }
    boolean isActive() {State current=state.get();return current!=null && current.active();}
    boolean isRestoring() {State current=state.get();return current!=null && !current.active();}
    @Override public void close() {state.set(null);}
}
