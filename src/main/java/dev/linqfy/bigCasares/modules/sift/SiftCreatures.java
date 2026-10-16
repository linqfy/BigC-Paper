package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.content.ContentItemFactory;
import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * The Blub: in the mod a small rabbit-like neutral creature of the overgrown
 * biomes. Here rabbits spawned (natively, by the biome spawn table) or bred in
 * the Sift become Blubs: silent rabbits dressed with the mod's Blub model through
 * BetterModel, with their own sounds and drops.
 *
 * <p>Ported: spawning biomes, passive rabbit AI, model, sounds, drop.
 * Not yet ported: Soul Block attraction and taming, sitting/following, ear droop
 * by health (the mod's model animations are kept for a later port).</p>
 */
final class SiftCreatures implements Listener {

    static final String BLUB_MODEL = "the_sift_blub";

    private final JavaPlugin plugin;
    private final JavaModelGateway models;
    private final ContentItemFactory items;
    private final NamespacedKey blubKey;
    private final Map<UUID, JavaModelHandle> attached = new HashMap<>();
    private boolean modelAvailable = true;
    private ActiveDimension dimension;

    SiftCreatures(JavaPlugin plugin, JavaModelGateway models, ContentItemFactory items) {
        this.plugin = plugin;
        this.models = models;
        this.items = items;
        this.blubKey = new NamespacedKey(plugin, "sift_blub");
    }

    void activate(ActiveDimension active) {
        this.dimension = active;
        active.registrations().registerListener("sift-creatures", this);
        active.registrations().ownCleanup("sift-creature-models", this::detachAll);
        for (Entity entity : active.world().getEntities()) {
            if (isBlub(entity)) {
                dress((LivingEntity) entity);
            }
        }
        active.registrations().scheduleRepeating("blub-ambience", this::ambience, 100L, 100L);
    }

    boolean isBlub(Entity entity) {
        return entity instanceof Rabbit && entity.getPersistentDataContainer().has(blubKey, PersistentDataType.BYTE);
    }

    private boolean inSift(World world) {
        return dimension != null && dimension.world().getUID().equals(world.getUID());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Rabbit rabbit) || !inSift(rabbit.getWorld())) {
            return;
        }
        rabbit.getPersistentDataContainer().set(blubKey, PersistentDataType.BYTE, (byte) 1);
        rabbit.setRabbitType(Rabbit.Type.WHITE);
        rabbit.setSilent(true);
        dress(rabbit);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!inSift(event.getWorld())) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (isBlub(entity)) {
                dress((LivingEntity) entity);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            undress(entity.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        if (!isBlub(event.getEntity())) {
            return;
        }
        undress(event.getEntity().getUniqueId());
        event.getDrops().clear();
        // Original addition: a little Ichor, the fluid Blubs swim in.
        if (ThreadLocalRandom.current().nextFloat() < 0.5F) {
            items.create("the_sift:ichor_shard", 1).ifPresent(event.getDrops()::add);
        }
        event.getEntity().getWorld().playSound(event.getEntity().getLocation(), "the_sift:entity.blub.death", 1.0F, 1.0F);
    }

    private void dress(LivingEntity entity) {
        if (!modelAvailable || attached.containsKey(entity.getUniqueId())) {
            return;
        }
        try {
            attached.put(entity.getUniqueId(), models.attach(entity, BLUB_MODEL));
            entity.setInvisible(true);
        } catch (RuntimeException unavailable) {
            // Keep the rabbit visible rather than showing nothing.
            modelAvailable = false;
            entity.setInvisible(false);
            plugin.getLogger().log(Level.WARNING, "[TheSift] Blub model unavailable; Blubs render as rabbits", unavailable);
        }
    }

    private void undress(UUID entityId) {
        JavaModelHandle handle = attached.remove(entityId);
        if (handle != null) {
            models.close(handle);
        }
    }

    private void detachAll() {
        for (JavaModelHandle handle : attached.values()) {
            models.close(handle);
        }
        attached.clear();
    }

    /** Occasional squeaks and a soft soul glow, like the mod's glowing Blub. */
    private void ambience() {
        ActiveDimension active = dimension;
        if (active == null || active.world().getPlayers().isEmpty()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (UUID id : attached.keySet()) {
            Entity entity = active.world().getEntity(id);
            if (entity == null || random.nextFloat() > 0.25F) {
                continue;
            }
            boolean nearPlayer = false;
            for (Player player : active.world().getPlayers()) {
                if (player.getLocation().distanceSquared(entity.getLocation()) < 24 * 24) {
                    nearPlayer = true;
                    break;
                }
            }
            if (!nearPlayer) {
                continue;
            }
            String sound = entity.isInWater() ? "the_sift:entity.blub.idle_water" : "the_sift:entity.blub.idle_air";
            entity.getWorld().playSound(entity.getLocation(), sound, 0.8F, 0.9F + random.nextFloat() * 0.2F);
            entity.getWorld().spawnParticle(Particle.SOUL, entity.getLocation().add(0, 0.4, 0), 1, 0.2, 0.2, 0.2, 0.0);
        }
    }
}
