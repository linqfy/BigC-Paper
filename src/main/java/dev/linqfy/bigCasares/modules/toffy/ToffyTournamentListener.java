package dev.linqfy.bigCasares.modules.toffy;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

final class ToffyTournamentListener implements Listener {
    private final ToffyModule module;

    ToffyTournamentListener(ToffyModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        module.playerJoined(event.getPlayer());
    }
}
