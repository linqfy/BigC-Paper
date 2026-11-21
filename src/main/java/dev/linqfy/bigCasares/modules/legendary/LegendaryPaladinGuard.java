package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;

import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Finite Stalwart Absorption. Reflection cannot recursively reflect itself. */
final class LegendaryPaladinGuard implements Listener {
    interface Reflection {
        boolean damage(Player source, LivingEntity target, double amount);
    }

    private static final class Guard {
        final Player owner;
        final LegendaryAbilityLevels spec;
        final Map<UUID, Double> reflected = new HashMap<>();

        Guard(Player owner, LegendaryAbilityLevels spec) {
            this.owner = owner;
            this.spec = spec;
        }
    }

    private final Map<UUID, Guard> guards = new HashMap<>();

    private record Absorbed(Guard guard, double amount) {}

    private final Map<EntityDamageEvent, Absorbed> outcomes = new IdentityHashMap<>();
    private final BukkitRuntimeRegistrations registrations;
    private final BiPredicate<Player, LivingEntity> allowed;
    private final Reflection reflection;
    private boolean reflecting;

    LegendaryPaladinGuard(
            BukkitRuntimeRegistrations registrations,
            BiPredicate<Player, LivingEntity> allowed,
            Reflection reflection) {
        this.registrations = registrations;
        this.allowed = allowed;
        this.reflection = reflection;
    }

    void start(Player player, LegendaryAbilityLevels spec) {
        guards.put(player.getUniqueId(), new Guard(player, spec));
    }

    void end(UUID player) {
        guards.remove(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void absorb(EntityDamageEvent event) {
        Guard guard = guards.get(event.getEntity().getUniqueId());
        if (reflecting
                || guard == null
                || event.getFinalDamage() <= 0
                || event.getCause() == EntityDamageEvent.DamageCause.VOID
                || event.getDamageSource().getDamageType().equals(DamageType.GENERIC_KILL)
                || event.getDamageSource().getDamageType().equals(DamageType.SONIC_BOOM)) return;
        outcomes.put(event, new Absorbed(guard, event.getFinalDamage()));
        event.setDamage(0);
        for (var modifier : EntityDamageEvent.DamageModifier.values()) {
            if (modifier != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier))
                event.setDamage(modifier, 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void outcome(EntityDamageEvent event) {
        Absorbed absorbed = outcomes.remove(event);
        if (absorbed == null || event.isCancelled()) return;
        Guard guard = absorbed.guard();
        registrations.scheduleImmediate(
                "paladin-reflection",
                () -> {
                    if (guards.get(guard.owner.getUniqueId()) != guard
                            || !guard.owner.isOnline()
                            || guard.owner.isDead()) return;
                    reflecting = true;
                    try {
                        var candidates =
                                guard.owner.getNearbyEntities(4, 4, 4).stream()
                                        .filter(e -> e instanceof LivingEntity)
                                        .map(e -> (LivingEntity) e)
                                        .toList();
                        for (var target :
                                LegendaryTargets.select(
                                        candidates,
                                        LivingEntity::getUniqueId,
                                        e ->
                                                e.getLocation()
                                                        .distanceSquared(guard.owner.getLocation()),
                                        e -> allowed.test(guard.owner, e),
                                        12)) {
                            double used = guard.reflected.getOrDefault(target.getUniqueId(), 0.0),
                                    amount =
                                            Math.min(
                                                    absorbed.amount(),
                                                    guard.spec.guardReflectCap() - used);
                            if (amount > 0 && reflection.damage(guard.owner, target, amount))
                                guard.reflected.put(target.getUniqueId(), used + amount);
                        }
                    } finally {
                        reflecting = false;
                    }
                });
    }
}
