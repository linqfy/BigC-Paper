package dev.linqfy.bigCasares.modules.alexscaves.core.effect;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/** Gameplay hooks of the module effects that need Bukkit events. */
public final class ModEffectListener implements Listener {
    private final ModEffectService effects;

    public ModEffectListener(ModEffectService effects) {
        this.effects = effects;
    }

    /** Irradiated blocks every kind of healing at every level (mod: {@code livingHeal}). */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onRegain(EntityRegainHealthEvent event) {
        if (effects.has(event.getEntity(), ModEffectType.IRRADIATED)) {
            event.setCancelled(true);
        }
    }

    /** A stunned attacker cannot deal direct damage. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Projectile) {
            return;
        }
        if (effects.has(damager, ModEffectType.STUNNED)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMilk(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET) {
            effects.cureWithMilk(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        effects.forget(entity);
    }

    /** Entities that despawn or are removed never come back: drop their ledger entries. */
    @EventHandler
    public void onRemove(EntityRemoveEvent event) {
        if (event.getCause() != EntityRemoveEvent.Cause.UNLOAD && !(event.getEntity() instanceof Player)) {
            effects.forget(event.getEntity());
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof LivingEntity living) {
                effects.clearStaleStun(living);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        effects.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        effects.save(event.getPlayer());
        effects.forget(event.getPlayer());
    }
}
