package dev.linqfy.bigCasares.modules.alexscaves.core.mob;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Keeps tracked module mobs in sync with the world and applies their loot. */
public final class ModMobListener implements Listener {
    private final ModMobService mobs;

    public ModMobListener(ModMobService mobs) {
        this.mobs = mobs;
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) {
                mobs.bind(living);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        mobs.typeOf(entity).ifPresent(type -> {
            event.getDrops().clear();
            event.getDrops().addAll(mobs.rollDrops(type, entity.getKiller(), entity.getFireTicks() > 0));
            mobs.behaviourOf(entity).ifPresent(behaviour -> behaviour.onDeathDrops(entity, event.getDrops()));
            mobs.behaviourOf(entity).ifPresent(behaviour ->
                event.setDroppedExp(behaviour.experience(entity, event.getDroppedExp())));
            mobs.playSound(entity, type, "death");
            mobs.releaseDead(entity);
        });
    }

    /** Zombie, phantom and other undead bases must not burn in daylight; lava and fire still burn them. */
    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (!(event instanceof EntityCombustByBlockEvent) && !(event instanceof EntityCombustByEntityEvent)
            && mobs.typeOf(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /**
     * The mod's fish go into their own buckets, which the module does not have yet: a vanilla bucket must not turn
     * them into the cod or axolotl under them.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBucket(org.bukkit.event.player.PlayerBucketEntityEvent event) {
        if (mobs.typeOf(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    /** A zombie base turning into a drowned (or any conversion) would lose the module identity. */
    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (mobs.typeOf(event.getEntity()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            mobs.release(living);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            mobs.behaviourOf(living).ifPresent(behaviour -> behaviour.onDamaged(living, mobs, event));
            if (!event.isCancelled()) {
                mobs.typeOf(living).ifPresent(type -> mobs.playSound(living, type, "hurt"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        if (damager instanceof LivingEntity attacker && event.getEntity() instanceof LivingEntity target) {
            final Entity source = attacker;
            mobs.behaviourOf(attacker).ifPresent(behaviour -> behaviour.onAttack((LivingEntity) source, mobs, target));
        }
    }
}
