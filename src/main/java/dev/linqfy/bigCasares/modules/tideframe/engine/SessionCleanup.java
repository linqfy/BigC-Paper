package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import java.util.function.Supplier;

/** Attempts every independent cleanup step and retains failures without skipping restoration. */
public final class SessionCleanup {
    private SessionCleanup() { }
    /** Preserve the original animation error while ensuring owner-thread cleanup. */
    public static void advanceFrame(Runnable frame,Runnable cleanup) {
        Objects.requireNonNull(frame);Objects.requireNonNull(cleanup);
        try {frame.run();}
        catch(RuntimeException | Error failure) {
            try {cleanup.run();}
            catch(RuntimeException | Error restoration) {if(restoration!=failure)failure.addSuppressed(restoration);}
            throw failure;
        }
    }
    public static void close(Runnable cancel,CameraRig camera,HudRenderer renderer,Runnable hideBoss) {
        run(cancel,camera::close,renderer::close,hideBoss);
    }
    public static void close(Runnable cancel,CameraRig camera,CameraPackets.InputLease input,HudRenderer renderer,Runnable hideBoss) {
        run(cancel,camera::close,input::close,renderer::close,hideBoss);
    }
    /** No player HUD channel is invoked from Folia's global shutdown context. */
    public static void abandon(Runnable cancel,CameraRig camera,HudRenderer renderer) {
        run(cancel,camera::close,renderer::abandon);
    }
    public static void abandon(Runnable cancel,CameraRig camera,CameraPackets.InputLease input,HudRenderer renderer) {
        run(cancel,camera::close,input::close,renderer::abandon);
    }
    /** Input is installed first; rollback also covers failures while preparing the camera transport. */
    public static CameraRig openCamera(Supplier<CameraRig.Transport> transport,CameraPackets.InputLease input) {
        Objects.requireNonNull(transport);Objects.requireNonNull(input);
        try {return CameraRig.open(transport.get());}
        catch(RuntimeException | Error failure) {
            try {input.close();}
            catch(RuntimeException | Error cleanup) {if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    public static void run(Runnable... stages) {
        Objects.requireNonNull(stages);
        Throwable first=null;
        for(var stage:stages) {
            try {Objects.requireNonNull(stage).run();}
            catch(RuntimeException | Error failure) {
                if(first==null)first=failure;
                else if(first!=failure)first.addSuppressed(failure);
            }
        }
        if(first instanceof RuntimeException failure)throw failure;
        if(first instanceof Error failure)throw failure;
    }
}
