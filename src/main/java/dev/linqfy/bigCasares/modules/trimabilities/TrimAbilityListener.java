package dev.linqfy.bigCasares.modules.trimabilities;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

final class TrimAbilityListener implements Listener {

    private final TrimAbilityRuntime runtime;

    TrimAbilityListener(TrimAbilityRuntime runtime) {
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        runtime.abilityOf(player).ifPresent(ability -> {
            TrimAbility.DamageKind kind = kind(event.getCause());
            double adjusted = ability.perk().adjustDamage(kind, event.getDamage(),
                ThreadLocalRandom.current().nextDouble());
            if (adjusted <= 0.0) {
                event.setCancelled(true);
                player.sendActionBar("§d¡Esquivado!");
            } else if (adjusted != event.getDamage()) {
                event.setDamage(adjusted);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PotionEffect added = event.getNewEffect();
        if (added == null) {
            return;
        }
        runtime.abilityOf(player).ifPresent(ability -> {
            if (ability.perk().type() == TrimAbility.PerkType.IMMUNITY
                && ability.perk().keys().contains(added.getType().getKey().getKey().toLowerCase(Locale.ROOT))) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFoodLevel() >= player.getFoodLevel()) {
            return;
        }
        runtime.abilityOf(player).ifPresent(ability -> {
            if (ability.perk().type() == TrimAbility.PerkType.HUNGER
                && ThreadLocalRandom.current().nextDouble() < ability.perk().amount()) {
                event.setCancelled(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // Attribute modifiers are saved with the player; drop any left behind by a crash before re-evaluating.
        runtime.stripModifiers(event.getPlayer());
        runtime.check(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        runtime.forget(event.getPlayer());
    }

    static TrimAbility.DamageKind kind(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case PROJECTILE -> TrimAbility.DamageKind.PROJECTILE;
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> TrimAbility.DamageKind.EXPLOSION;
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> TrimAbility.DamageKind.MELEE;
            case FALL -> TrimAbility.DamageKind.FALL;
            default -> null;
        };
    }
}
