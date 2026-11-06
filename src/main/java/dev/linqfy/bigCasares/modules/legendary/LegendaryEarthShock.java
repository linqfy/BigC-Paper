package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Owns Earthbreaker's single initial shock, including native cancellation and resurrection. */
final class LegendaryEarthShock implements Listener {
    private static final double HEALTH_DAMAGE = 4;
    private final Map<UUID, Impact> pending = new HashMap<>();

    private static final class Impact {
        final UUID attacker;
        boolean delivered;

        Impact(UUID attacker) {
            this.attacker = attacker;
        }
    }

    void pulse(
            Player attacker,
            Location center,
            int maximumTargets,
            Predicate<LivingEntity> permitted) {
        pulse(attacker, center, maximumTargets, permitted, ignored -> {});
    }

    void pulse(
            Player attacker,
            Location center,
            int maximumTargets,
            Predicate<LivingEntity> permitted,
            java.util.function.Consumer<LivingEntity> applied) {
        var candidates = new ArrayList<LivingEntity>();
        for (var entity : center.getWorld().getNearbyEntities(center, 8, 4, 8)) {
            if (entity instanceof LivingEntity living) candidates.add(living);
        }
        var selected =
                LegendaryTargets.select(
                        candidates,
                        LivingEntity::getUniqueId,
                        target -> target.getLocation().distanceSquared(center),
                        target -> {
                            Location position = target.getLocation();
                            double dx = position.getX() - center.getX(),
                                    dz = position.getZ() - center.getZ();
                            return dx * dx + dz * dz <= 64
                                    && Math.abs(position.getY() - center.getY()) <= 4
                                    && permitted.test(target);
                        },
                        maximumTargets);
        for (LivingEntity victim : selected) if (deliver(attacker, victim)) applied.accept(victim);
    }

    private boolean deliver(Player attacker, LivingEntity victim) {
        if (victim.isDead() || !victim.isValid() || victim.isInvulnerable()) return false;
        UUID id = victim.getUniqueId();
        var impact = new Impact(attacker.getUniqueId());
        Impact previous = pending.put(id, impact);
        int previousImmunity = victim.getNoDamageTicks();
        try {
            victim.setNoDamageTicks(0);
            victim.damage(
                    HEALTH_DAMAGE,
                    DamageSource.builder(DamageType.SONIC_BOOM)
                            .withDirectEntity(attacker)
                            .withCausingEntity(attacker)
                            .build());
        } finally {
            if (previous == null) pending.remove(id);
            else pending.put(id, previous);
            if (!impact.delivered && victim.isValid() && !victim.isDead())
                victim.setNoDamageTicks(previousImmunity);
        }
        return impact.delivered;
    }

    private Impact context(EntityDamageByEntityEvent event) {
        Impact impact = pending.get(event.getEntity().getUniqueId());
        if (impact == null || !impact.attacker.equals(event.getDamager().getUniqueId()))
            return null;
        return event.getDamageSource().getDamageType().equals(DamageType.SONIC_BOOM)
                ? impact
                : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void applyHealthDamage(EntityDamageByEntityEvent event) {
        if (context(event) != null) setHealthDamage(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void damageOutcome(EntityDamageByEntityEvent event) {
        Impact impact = context(event);
        if (impact != null) impact.delivered = !event.isCancelled() && event.getFinalDamage() > 0;
    }

    static void setHealthDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        event.setDamage(HEALTH_DAMAGE);
        for (var modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier == EntityDamageEvent.DamageModifier.BASE || !event.isApplicable(modifier))
                continue;
            event.setDamage(modifier, 0);
        }
    }
}
