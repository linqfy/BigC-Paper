package dev.linqfy.bigCasares.modules.phasearrows;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.OptionalDouble;

/** Lets spectral arrows phase through thin walls instead of sticking in them. */
final class PhaseArrowListener implements Listener {

    private final PhaseArrowSettings settings;
    private final NamespacedKey phasesKey;

    PhaseArrowListener(PhaseArrowSettings settings, NamespacedKey phasesKey) {
        this.settings = settings;
        this.phasesKey = phasesKey;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof SpectralArrow arrow) || event.getHitBlock() == null
            || event.getHitEntity() != null) {
            return;
        }
        if (settings.playersOnly() && !(arrow.getShooter() instanceof Player)) {
            return;
        }
        PersistentDataContainer data = arrow.getPersistentDataContainer();
        int phases = data.getOrDefault(phasesKey, PersistentDataType.INTEGER, 0);
        if (phases >= settings.maxPhases()) {
            return;
        }
        Vector velocity = arrow.getVelocity();
        if (velocity.lengthSquared() < 1.0E-4) {
            return;
        }
        Vector direction = velocity.clone().normalize();
        Location start = arrow.getLocation();
        World world = arrow.getWorld();
        OptionalDouble exit = PhasePath.exitDistance(
            distance -> cellAt(world, start.clone().add(direction.clone().multiply(distance))),
            settings.maxThickness());
        if (exit.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Location target = start.clone().add(direction.clone().multiply(exit.getAsDouble()));
        target.setDirection(direction);
        data.set(phasesKey, PersistentDataType.INTEGER, phases + 1);
        world.spawnParticle(Particle.REVERSE_PORTAL, start, 12, 0.1, 0.1, 0.1, 0.02);
        arrow.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        arrow.setVelocity(velocity);
        world.spawnParticle(Particle.END_ROD, target, 8, 0.1, 0.1, 0.1, 0.02);
        world.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.9f);
    }

    private PhasePath.Cell cellAt(World world, Location location) {
        Block block = world.getBlockAt(location);
        if (settings.unphaseable().contains(block.getType().name())) {
            return PhasePath.Cell.BLOCKED;
        }
        return block.isPassable() ? PhasePath.Cell.OPEN : PhasePath.Cell.SOLID;
    }
}
