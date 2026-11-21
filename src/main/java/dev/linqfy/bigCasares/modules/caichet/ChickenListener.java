package dev.linqfy.bigCasares.modules.caichet;

import com.destroystokyo.paper.entity.ai.Goal;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gallinas de la dimension (y los dobles con cuerpo de jugador, que por dentro son gallinas): si les pegas se
 * enojan y te atacan; si las matas te dan un Reloj de los Sueños.
 */
public final class ChickenListener implements Listener {

    /** El maniqui de un doble no recibe dano: los golpes de jugadores pasan a su gallina, el resto se ignora. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBodyDamaged(EntityDamageEvent event) {
        Doppelganger doppel = Doppelganger.of(event.getEntity());
        if (doppel == null || event.getEntity() != doppel.body) {
            return;
        }
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity && doppel.brain.isValid()) {
            Player attacker = attacker(byEntity.getDamager());
            doppel.brain.damage(event.getDamage(), attacker != null ? attacker : byEntity.getDamager());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Chicken chicken) || !inDimension(chicken)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        anger(chicken, attacker);
        Doppelganger doppel = Doppelganger.of(chicken);
        if (doppel != null) {
            // la gallina es invisible: el que "se queja" es el cuerpo
            doppel.body.playHurtAnimation(0);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Chicken chicken) || !inDimension(chicken)) {
            return;
        }
        Doppelganger doppel = Doppelganger.of(chicken);
        if (doppel != null) {
            // un "jugador" no suelta plumas ni pollo crudo
            event.getDrops().clear();
            doppel.remove(true);
        }
        Player killer = chicken.getKiller();
        if (killer == null) {
            return;
        }
        // directo al inventario: si cayera al piso saldria flotando (ver ItemFloater)
        for (ItemStack rest : killer.getInventory().addItem(DreamItems.clock()).values()) {
            killer.getWorld().dropItem(killer.getLocation(), rest);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(DreamItems.RECIPE_KEY);
    }

    static void anger(Chicken chicken, Player target) {
        AttributeInstance speed = chicken.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(ChickenAttackGoal.BABY_ZOMBIE_SPEED);
        }
        Goal<Chicken> existing = Bukkit.getMobGoals().getGoal(chicken, ChickenAttackGoal.KEY);
        if (existing instanceof ChickenAttackGoal goal) {
            goal.setTarget(target);
        } else {
            Bukkit.getMobGoals().addGoal(chicken, 0, new ChickenAttackGoal(chicken, target));
        }
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private static boolean inDimension(Entity entity) {
        return entity.getWorld().equals(PortalListener.dimension());
    }
}
