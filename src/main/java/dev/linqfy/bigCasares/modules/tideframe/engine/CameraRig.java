package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns a client-only camera lease. Its transport must prebuild restoration on the owner thread. */
public final class CameraRig implements AutoCloseable {
    public interface Transport {
        void open() throws Exception;
        /** Network-only restoration; must not read or mutate a Bukkit entity. */
        void close() throws Exception;
    }
    private final Transport transport;
    private final AtomicBoolean closed=new AtomicBoolean();
    private CameraRig(Transport transport) {this.transport=Objects.requireNonNull(transport);}
    public static CameraRig open(Transport transport) {
        var rig=new CameraRig(transport);
        try {transport.open();return rig;}
        catch(Exception failure) {
            try {rig.close();}
            catch(IllegalStateException rollback) {failure.addSuppressed(rollback.getCause());}
            throw new IllegalStateException("Could not open the fixed camera; restoration was attempted.",failure);
        }
    }
    @Override public void close() {
        if(!closed.compareAndSet(false,true))return;
        try {transport.close();}
        catch(Exception failure) {throw new IllegalStateException("Could not restore the player camera.",failure);}
    }
}
