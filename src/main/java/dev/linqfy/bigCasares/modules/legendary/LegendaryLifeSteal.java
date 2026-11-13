package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;

import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;

/** Observes actual primary melee damage; the runtime remains the owner of skill sessions. */
final class LegendaryLifeSteal implements Listener {
    record Hit(int abilityAge, boolean primary, double damage, double health, double maximum) {
        double healing() {
            if (!primary || abilityAge < 0 || abilityAge >= 200) return 0;
            if (!Double.isFinite(damage) || damage <= 0) return 0;
            if (!Double.isFinite(health) || !Double.isFinite(maximum) || health <= 0) return 0;
            double capacity = maximum - health;
            return capacity > 0 ? Math.min(capacity, 1) : 0;
        }
    }

    private final ToIntFunction<Player> skillAge;
    private final BiPredicate<Player, LivingEntity> permitted;

    LegendaryLifeSteal(ToIntFunction<Player> skillAge, BiPredicate<Player, LivingEntity> permitted) {
        this.skillAge = skillAge;
        this.permitted = permitted;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void completedMeleeHit(EntityDamageByEntityEvent event) {
        if (event.getCause() != DamageCause.ENTITY_ATTACK || !(event.getDamager() instanceof Player attacker)
            || !(event.getEntity() instanceof LivingEntity victim) || victim instanceof ArmorStand) return;
        if (!attacker.isOnline() || attacker.isDead()) return;
        var maximum = attacker.getAttribute(Attribute.MAX_HEALTH);
        if (maximum == null) return;
        double healing = new Hit(skillAge.applyAsInt(attacker), true, event.getFinalDamage(),
            attacker.getHealth(), maximum.getValue()).healing();
        if (healing == 0 || !permitted.test(attacker, victim)) return;
        double previousHealth = attacker.getHealth();
        attacker.heal(healing, RegainReason.CUSTOM);
        if (attacker.getHealth() <= previousHealth) return;
        victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 6,
            .12, .12, .12, 0, new Particle.DustOptions(Color.RED, 1.3f));
        attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 1.2, 0), 2, .25, .2, .25, 0);
    }
}
