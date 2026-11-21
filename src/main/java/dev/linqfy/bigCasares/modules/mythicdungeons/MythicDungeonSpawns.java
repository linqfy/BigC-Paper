package dev.linqfy.bigCasares.modules.mythicdungeons;

import com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Llena las mazmorras de las culturas con mobs ({@link MythicCulture#hasMobs()}, hoy las nueve, tengan jefe o no): los
 * spawners que estan adentro crean mobs de su cultura (sorteo con pesos) en vez del vanilla, y los hostiles naturales
 * que nacen adentro se reemplazan con cierta probabilidad. Los dos respetan un tope de mobs de mazmorra cerca y solo
 * ponen un mob donde entra entero ({@link MobPlacement}): si el sorteado no entra prueban con uno mas chico de la
 * cultura y, si ninguno entra, ese intento se pierde. Una cultura sin mobs seguiria con los spawns vanilla.
 */
final class MythicDungeonSpawns implements Listener {

    /** El alcance vanilla de un spawner cuando no se puede leer el suyo. */
    private static final int DEFAULT_SPAWN_RANGE = 4;
    /** Los spawners vanilla ponen mobs entre un bloque abajo y uno arriba del spawner. */
    private static final int SPAWN_VERTICAL = 1;
    /** Cuanto se corre un reemplazo de spawn natural buscando lugar. */
    private static final int NATURAL_RANGE = 2;

    private final MythicMobs mobs;
    private final DungeonLocator locator;
    private final MythicDungeonSettings settings;

    MythicDungeonSpawns(MythicMobs mobs, DungeonLocator locator, MythicDungeonSettings settings) {
        this.mobs = mobs;
        this.locator = locator;
        this.settings = settings;
    }

    /**
     * Paper avisa antes de crear la entidad del spawner: cancelarlo aca le reinicia la espera al spawner (no reintenta
     * cada tick) y no se crea el mob vanilla.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreSpawner(PreSpawnerSpawnEvent event) {
        Optional<MythicCulture> culture = populatedAt(event.getSpawnerLocation());
        if (culture.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        event.setShouldAbortSpawn(true);
        Location spawner = event.getSpawnerLocation();
        int range = DEFAULT_SPAWN_RANGE;
        if (spawner.getBlock().getState(false) instanceof CreatureSpawner state) {
            range = Math.max(1, state.getSpawnRange());
        }
        spawnFor(culture.get(), event.getSpawnLocation(), spawner, range);
    }

    /** Red de seguridad: si un mob vanilla de un spawner de la mazmorra se coló igual, no nace. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawner(SpawnerSpawnEvent event) {
        if (mobs.keys().isOwnEntity(event.getEntity()) || event.getSpawner() == null) {
            return;
        }
        if (populatedAt(event.getSpawner().getLocation()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNatural(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
            || !(event.getEntity() instanceof Monster)
            || settings.naturalReplaceChance() <= 0.0) {
            return;
        }
        Location location = event.getLocation();
        Optional<MythicCulture> culture = populatedAt(location);
        if (culture.isEmpty()) {
            return;
        }
        // adentro de la mazmorra no nacen hostiles vanilla: o se reemplazan o no nacen
        event.setCancelled(true);
        if (ThreadLocalRandom.current().nextDouble() < settings.naturalReplaceChance()) {
            spawnFor(culture.get(), location, location, NATURAL_RANGE);
        }
    }

    /** La cultura de la mazmorra en ese lugar, si tiene mobs propios; las que no tengan siguen vanilla. */
    private Optional<MythicCulture> populatedAt(Location location) {
        return locator.cultureAt(location).filter(MythicCulture::hasMobs);
    }

    private void spawnFor(MythicCulture culture, Location preferred, Location center, int range) {
        World world = center.getWorld();
        if (world == null || mobs.countNearby(center, settings.capRadius(), true) >= settings.spawnerCap()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        MobPlacement.place(mobs.catalog().ofCulture(culture), random.nextDouble(), new WorldBlockGrid(world),
                spot(preferred), spot(center), range, SPAWN_VERTICAL)
            .ifPresent(placement -> mobs.spawn(placement.mob(), new Location(world, placement.spot().x() + 0.5,
                placement.spot().y(), placement.spot().z() + 0.5, random.nextFloat() * 360.0F, 0.0F), true));
    }

    private static MobPlacement.Spot spot(Location location) {
        return new MobPlacement.Spot(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * El mob que nace en la mazmorra de una cultura con una tirada uniforme en [0, 1): sorteo con los pesos de sus mobs.
     * Vacio si la cultura no tiene mobs (o todos tienen peso 0).
     */
    static Optional<MythicMobType> pick(MythicMobCatalog catalog, MythicCulture culture, double roll) {
        if (!culture.hasMobs()) {
            return Optional.empty();
        }
        return WeightedPicker.pick(catalog.ofCulture(culture), MythicMobType::weight, roll);
    }
}
