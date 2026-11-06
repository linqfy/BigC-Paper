package dev.linqfy.bigCasares.modules.tideframe.engine.edit;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded file-work queue, separate from pack HTTP workers. */
public final class SerialScreenStore implements AutoCloseable {
    private final ScreenStorage storage;
    private final AtomicBoolean closed=new AtomicBoolean();
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r -> {
        var t=new Thread(r,"Tideframe-storage");t.setDaemon(true);return t;
    });
    private record Work<T>(CompletableFuture<T> result,Callable<T> operation) implements Runnable {
        @Override public void run(){try{result.complete(operation.call());}catch(Exception e){result.completeExceptionally(e);}}
    }
    public SerialScreenStore(ScreenStorage storage){this.storage=storage;}
    private <T> CompletableFuture<T> submit(Callable<T> operation) {
        var future=new CompletableFuture<T>();
        if(closed.get()){future.completeExceptionally(new RejectedExecutionException("Storage closed"));return future;}
        try{executor.execute(new Work<>(future,operation));}catch(RejectedExecutionException e){future.completeExceptionally(e);}return future;
    }
    public CompletableFuture<Screen> load(String id){return submit(() -> storage.load(id));}
    public CompletableFuture<Void> save(Screen screen){return submit(() -> {storage.save(screen);return null;});}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        for(Runnable work:executor.shutdownNow())if(work instanceof Work<?> pending)pending.result.completeExceptionally(new CancellationException("Storage stopped"));
    }
}
