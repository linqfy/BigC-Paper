package dev.linqfy.bigCasares.modules.quadrants;

import org.bukkit.Location;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;

final class QuadrantListener implements Listener {

    private final QuadrantRuntime runtime;

    QuadrantListener(QuadrantRuntime runtime) {
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        boolean allowed = QuadrantPolicy.allowsPvp(
            runtime.zoneAt(attacker.getLocation()), runtime.zoneAt(victim.getLocation()),
            runtime.isTagged(attacker), runtime.isTagged(victim));
        if (!allowed) {
            event.setCancelled(true);
            attacker.sendActionBar("§aZona SMP Chill: el PvP está desactivado.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpApplied(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (runtime.zoneAt(attacker.getLocation()).isEmpty() && runtime.zoneAt(victim.getLocation()).isEmpty()) {
            return;
        }
        runtime.tag(attacker);
        runtime.tag(victim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()
            && from.getWorld() == to.getWorld()) {
            return;
        }
        Player player = event.getPlayer();
        if (blocks(player, from, to)) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
            player.setVelocity(player.getVelocity().multiply(-0.5));
            warn(player);
            return;
        }
        runtime.observe(player, to);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (event.getTo() != null && blocks(player, event.getFrom(), event.getTo())) {
            event.setCancelled(true);
            warn(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleported(PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            runtime.observe(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        runtime.observe(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // El búfer se conserva: reconectar no limpia el combate.
        runtime.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        runtime.combat().clear(event.getEntity().getUniqueId());
    }

    private boolean blocks(Player player, Location from, Location to) {
        if (runtime.exempt(player)) {
            return false;
        }
        Optional<QuadrantZone> fromZone = from.getWorld() == to.getWorld()
            ? runtime.zoneAt(from) : Optional.empty();
        return QuadrantPolicy.blocksEntry(fromZone, runtime.zoneAt(to), runtime.isTagged(player));
    }

    private void warn(Player player) {
        player.sendActionBar("§c⚔ En combate: no podés entrar a la zona Chill por "
            + runtime.combat().remainingSeconds(player.getUniqueId()) + "s.");
    }

    static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player source) {
            return source;
        }
        return null;
    }
}
