package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

/** Synchronous player-owner events only. Closing removes the lock; no attributes are changed. */
public final class MenuMovementGuard implements Listener {
    private final Map<UUID,?> menus;
    private final Consumer<Player> close;
    public MenuMovementGuard(Map<UUID,?> menus, Consumer<Player> close) {
        this.menus=Objects.requireNonNull(menus);this.close=Objects.requireNonNull(close);
    }
    private boolean active(Player player) { return menus.containsKey(player.getUniqueId()); }
    private void release(Player player) { if(active(player))close.accept(player); }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) {
        if(event.isCancelled() || event instanceof PlayerTeleportEvent || !active(event.getPlayer()))return;
        Player player=event.getPlayer();
        if(player.isDead() || !player.hasPermission("tideframe.use") || player.isInsideVehicle()) {
            release(player);return;
        }
        Location from=event.getFrom(),to=event.getTo();
        if(to==null)return;
        if(!Objects.equals(from.getWorld(),to.getWorld())) {release(player);return;}
        if(from.getX()==to.getX() && from.getY()==to.getY() && from.getZ()==to.getZ())return;
        // Cancellation returns to getFrom() without a Bukkit teleport event. Preserve the
        // requested mouse angles in that SAME object (Paper uses its original reference).
        // setTo() would trigger a PLUGIN teleport and our teleport listener would close the menu.
        from.setYaw(to.getYaw());from.setPitch(to.getPitch());
        event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void velocity(PlayerVelocityEvent event) {
        if(!event.isCancelled() && active(event.getPlayer()))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) {
        if(!event.isCancelled())release(event.getPlayer());
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void worldChanged(PlayerChangedWorldEvent event) {release(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent event) {release(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent event) {release(event.getEntity());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void mount(EntityMountEvent event) {
        if(!event.isCancelled() && event.getEntity() instanceof Player player)release(player);
    }
}
