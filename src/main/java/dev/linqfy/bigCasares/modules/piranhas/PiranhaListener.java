package dev.linqfy.bigCasares.modules.piranhas;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

final class PiranhaListener implements Listener {

    private final PiranhaRuntime runtime;

    PiranhaListener(PiranhaRuntime runtime) {
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            runtime.track(entity);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        runtime.forget(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEntityEvent event) {
        if (runtime.isPiranha(event.getEntity())) {
            event.setCancelled(true);
            event.getPlayer().damage(1.0, event.getEntity());
            event.getPlayer().sendActionBar(ChatColor.RED + "¡La piraña te mordió la mano!");
        }
    }
}
