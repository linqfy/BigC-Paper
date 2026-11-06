package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.BigCasares;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityMoveEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

import java.util.function.BiConsumer;

/** Bukkit adapter for positional locks; leaves camera, AI and gravity settings intact. */
final class LegendaryMovementLock implements Listener {
    private final BigCasares plugin;
    private final LegendaryParalysis paralysis;
    private final BiConsumer<LivingEntity, Location> entering;
    private boolean correcting;

    LegendaryMovementLock(BigCasares plugin, LegendaryParalysis paralysis,
                          BiConsumer<LivingEntity, Location> entering) {
        this.plugin = plugin;
        this.paralysis = paralysis;
        this.entering = entering;
    }

    private boolean locked(Entity entity) { return paralysis.anchor(entity.getUniqueId()).isPresent(); }

    private Location destination(LivingEntity entity, Location proposed) {
        var anchor = paralysis.anchor(entity.getUniqueId()).orElse(null);
        if (anchor == null) return proposed;
        if (!anchor.world().equals(proposed.getWorld().getUID())) {
            paralysis.forget(entity.getUniqueId());
            return proposed;
        }
        return new Location(proposed.getWorld(), anchor.x(), anchor.y(), anchor.z(), proposed.getYaw(), proposed.getPitch());
    }

    void enforce() {
        for (var id : paralysis.targets()) {
            Entity entity = plugin.getServer().getEntity(id);
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()
                || (living instanceof Player player && (!player.isOnline() || immune(player)))) {
                paralysis.forget(id);
                continue;
            }
            if (living.isInsideVehicle()) living.leaveVehicle();
            living.setVelocity(new Vector());
            living.setFallDistance(0);
            Location current = living.getLocation(), fixed = destination(living, current);
            if (current.distanceSquared(fixed) > .000001) {
                correcting = true;
                try { living.teleport(fixed); } finally { correcting = false; }
            }
        }
    }

    private static boolean immune(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || player.isInvulnerable();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null || !event.hasChangedPosition()) return;
        entering.accept(event.getPlayer(), event.getTo());
        event.setTo(destination(event.getPlayer(), event.getTo()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void moveEntity(EntityMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        entering.accept(event.getEntity(), event.getTo());
        event.setTo(destination(event.getEntity(), event.getTo()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        if (correcting || !locked(event.getPlayer())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND || event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) return;
        event.setCancelled(true);
        event.getPlayer().sendActionBar(Component.text("⚡ No puedes escapar mientras estés paralizado."));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void administrativeEscape(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND || event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            paralysis.forget(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleportEntity(EntityTeleportEvent event) {
        if (!correcting && locked(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void velocity(PlayerVelocityEvent event) {
        if (locked(event.getPlayer())) event.setVelocity(new Vector());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void knockback(EntityKnockbackEvent event) {
        if (locked(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mount(EntityMountEvent event) {
        if (locked(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void gameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.CREATIVE || event.getNewGameMode() == GameMode.SPECTATOR) {
            paralysis.forget(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler public void world(PlayerChangedWorldEvent event) { paralysis.forget(event.getPlayer().getUniqueId()); }
    @EventHandler public void death(EntityDeathEvent event) { paralysis.forget(event.getEntity().getUniqueId()); }
}
