package dev.linqfy.bigCasares.modules.glider;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.OptionalDouble;

/** Player input and lifecycle bridge; physics and state remain in the runtime. */
public final class GliderListener implements Listener {

    private final GliderRuntime runtime;

    public GliderListener(GliderRuntime runtime) {
        this.runtime = java.util.Objects.requireNonNull(runtime, "runtime");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBoost(PlayerInteractEvent event) {
        // RIGHT_CLICK_AIR is delivered pre-cancelled, so ignoreCancelled would drop it;
        // honour other plugins through the item-use result instead.
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (runtime.tryBoost(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        OptionalDouble reduced = runtime.consumeFallDamageReduction(player, event.getDamage());
        if (reduced.isEmpty()) {
            return;
        }
        if (reduced.getAsDouble() <= 0.0) {
            event.setCancelled(true);
        } else {
            event.setDamage(reduced.getAsDouble());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        runtime.stop(event.getPlayer().getUniqueId(), GliderStopReason.QUIT);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        runtime.stop(event.getEntity().getUniqueId(), GliderStopReason.DEATH);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        runtime.stop(event.getPlayer().getUniqueId(), GliderStopReason.TELEPORT);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        runtime.stop(event.getPlayer().getUniqueId(), GliderStopReason.WORLD_CHANGE);
    }
}
