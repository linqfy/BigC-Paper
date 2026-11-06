package dev.linqfy.bigCasares.modules.tideframe.engine.edit;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.io.IOException;
public interface ScreenStorage {
    Screen load(String id) throws IOException;
    void save(Screen screen) throws IOException;
}
