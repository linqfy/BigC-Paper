package dev.linqfy.bigCasares.modules.tideframe.engine;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.*;
import dev.linqfy.bigCasares.modules.tideframe.engine.edit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.io.IOException;
class AsyncOperationsTest {
    @TempDir Path dir;
    @Test void newerRequestAndCloseInvalidateAnOlderCompletion() {
        var gate=new OperationGate();var owner=UUID.randomUUID();
        var old=gate.begin(owner);var newest=gate.begin(owner);
        assertFalse(gate.current(old));assertTrue(gate.current(newest));
        gate.invalidate(owner);assertFalse(gate.current(newest));
    }
    @Test void leavingAndRejoiningCannotReactivateAnOldTicket() {
        var gate=new OperationGate();var owner=UUID.randomUUID();var old=gate.begin(owner);
        gate.forget(owner);var fresh=gate.begin(owner);
        assertFalse(gate.current(old));assertTrue(gate.current(fresh));
        gate.clear();assertFalse(gate.current(fresh));
    }
    @Test void heldOlderSaveCannotOverwriteANewerSnapshot() throws Exception {
        var real=new ScreenStore(dir);var started=new CountDownLatch(1);var release=new CountDownLatch(1);var newerFinished=new CountDownLatch(1);
        ScreenStorage slow=new ScreenStorage() {
            public Screen load(String id)throws IOException{return real.load(id);}
            public void save(Screen screen)throws IOException {
                if(screen.title().equals("OLD")){started.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}
                real.save(screen);if(screen.title().equals("NEW"))newerFinished.countDown();
            }
        };
        try(var serial=new SerialScreenStore(slow)) {
            var old=serial.save(new Screen("menu","OLD",List.of()));assertTrue(started.await(2,TimeUnit.SECONDS));
            var newest=serial.save(new Screen("menu","NEW",List.of()));
            try{assertFalse(newerFinished.await(300,TimeUnit.MILLISECONDS));}finally{release.countDown();}
            old.get(3,TimeUnit.SECONDS);newest.get(3,TimeUnit.SECONDS);
            assertEquals("NEW",real.load("menu").title());
        }
    }
    @Test void closedRepositoryRejectsNewWorkWithoutBlocking() {
        var serial=new SerialScreenStore(new ScreenStore(dir));serial.close();serial.close();
        assertThrows(CompletionException.class,() -> serial.load("menu").join());
    }
}
