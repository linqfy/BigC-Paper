package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.util.BoundingBox;

/**
 * Los cuerpos vanilla que ve Bedrock son solo decoracion: no se montan ni se les puede usar nada. Tambien olvida la
 * vista de Bedrock de prueba cuando el jugador se desconecta.
 */
final class StandGuard implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnter(VehicleEnterEvent event) {
        if (Bedrock.isStand(event.getVehicle())) {
            event.setCancelled(true);
        }
    }

    /**
     * Un cuerpo de adorno (que la mayoria no ve) no impide poner bloques: si lo unico que estorba son cuerpos de
     * adorno y el bloque se sostiene ahi, se puede construir.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCanBuild(BlockCanBuildEvent event) {
        if (event.isBuildable() || !event.getBlockData().isSupported(event.getBlock())) {
            return;
        }
        BoundingBox box = BoundingBox.of(event.getBlock());
        boolean stand = false;
        for (Entity entity : event.getBlock().getWorld().getNearbyEntities(box)) {
            if (Bedrock.isStand(entity)) {
                stand = true;
            } else if (entity instanceof LivingEntity || entity instanceof Vehicle) {
                return; // estorba algo de verdad
            }
        }
        if (stand) {
            event.setBuildable(true);
        }
    }

    /** Las flechas y demas proyectiles atraviesan los cuerpos de adorno. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectile(ProjectileHitEvent event) {
        if (event.getHitEntity() != null && Bedrock.isStand(event.getHitEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TeamLookup.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (Bedrock.isStand(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
