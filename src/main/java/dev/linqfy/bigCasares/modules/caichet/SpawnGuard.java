package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * En la dimension solo existen las criaturas del plugin (los dobles) y el tren. Nada aparece solo: ni conejos, ni
 * husks, ni phantoms, ni patrullas. Se permiten /summon y los huevos de spawn (los usa la administracion).
 */
public final class SpawnGuard implements Listener {

    /** CUSTOM = lo que crea un plugin (los dobles); COMMAND = /summon; SPAWNER_EGG = huevo de spawn. */
    private static final Set<CreatureSpawnEvent.SpawnReason> ALLOWED = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.CUSTOM,
            CreatureSpawnEvent.SpawnReason.COMMAND,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (guarded(event.getEntity().getWorld()) && !ALLOWED.contains(event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    /** La dimension del desierto y el hotel. */
    private static boolean guarded(World world) {
        return world.equals(PortalListener.dimension()) || HotelWorld.isHotel(world) || DreamWorld.isDream(world);
    }

    /**
     * Mobs que quedaron guardados de antes (conejos del desierto viejo, gallinas comunes de versiones anteriores):
     * se borran al cargar su chunk. Los dobles no se guardan nunca, asi que no los toca.
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!guarded(event.getWorld())) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity && !(entity instanceof Player) && !(entity instanceof ArmorStand)
                    && Doppelganger.of(entity) == null) {
                entity.remove();
            }
        }
    }
}
