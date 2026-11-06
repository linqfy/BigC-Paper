package dev.linqfy.bigCasares.modules.tideframe.engine.compat;

import dev.linqfy.bigCasares.modules.tideframe.engine.SchedulerBridge;
import dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/** Chat choices contain no synthetic inventory or entity that can outlive its protection. */
public final class FallbackMenu implements Listener {
    private record View(Screen screen,Consumer<String> action){}
    private final Map<UUID,View> views=new ConcurrentHashMap<>();
    public FallbackMenu(SchedulerBridge scheduler){}
    public void open(Player player,Screen screen,Consumer<String> action) {
        views.put(player.getUniqueId(),new View(screen,action));
        player.sendMessage("TIDEFRAME | "+screen.title());
        for(var entry:FallbackModel.entries(screen))
            player.sendMessage(entry.action().isEmpty()?entry.text():(entry.slot()+1)+". "+entry.text());
        player.sendMessage("Use /tideframe choose NUMBER. Use /tideframe close to exit.");
    }
    public void choose(Player player,int number) {
        var view=views.get(player.getUniqueId());if(view==null || !player.hasPermission("tideframe.use"))return;
        FallbackModel.action(view.screen,number-1).ifPresent(ignored ->
            view.action.accept(dev.linqfy.bigCasares.modules.tideframe.engine.core.MessageRules.action(view.screen.elements().get(number-1),player::hasPermission)));
    }
    public void close(Player player){views.remove(player.getUniqueId());}
    public void clear(){views.clear();}
}
