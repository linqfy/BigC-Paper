package dev.linqfy.bigCasares.modules.tideframe.engine.compat;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.util.*;
public final class FallbackModel {
    public record Entry(int slot,String text,String action) {}
    public static List<Entry> entries(Screen screen) {
        var entries=new ArrayList<Entry>();
        for(int i=0;i<screen.elements().size();i++) {
            var e=screen.elements().get(i);entries.add(new Entry(i,e.text(),e.kind().equals("button")?e.action():""));
        }
        return List.copyOf(entries);
    }
    public static Optional<String> action(Screen screen,int slot) {
        if(slot<0 || slot>=screen.elements().size())return Optional.empty();
        var e=screen.elements().get(slot);
        return e.kind().equals("button") && !e.action().isEmpty()?Optional.of(e.action()):Optional.empty();
    }
}
