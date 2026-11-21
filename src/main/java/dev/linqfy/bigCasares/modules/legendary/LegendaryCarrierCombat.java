package dev.linqfy.bigCasares.modules.legendary;

import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;

/** Only item-class/tag mechanics are adapted; damage, enchantment effects and protection events stay native. */
final class LegendaryCarrierCombat implements Listener, AutoCloseable {
    private record Attack(UUID target, LegendaryWeapon weapon, float strength, float baseDamage,
                          boolean sprinting, long capturedAt) { }

    private final LegendaryVanillaBridge vanilla;
    private final LegendaryPvpProgression progression;
    private final Consumer<Runnable> afterAttack;
    private final Map<UUID, Attack> attacks = new HashMap<>();
    private final Map<UUID, Runnable> smashFinishes = new HashMap<>();

    LegendaryCarrierCombat(LegendaryVanillaBridge vanilla, LegendaryPvpProgression progression,
                           BukkitRuntimeRegistrations registrations) {
        this(vanilla, progression, callback -> registrations.scheduleImmediate("legendary-smash-finish", callback));
    }

    LegendaryCarrierCombat(LegendaryVanillaBridge vanilla, LegendaryPvpProgression progression,
                           Consumer<Runnable> afterAttack) {
        this.vanilla = vanilla;
        this.progression = progression;
        this.afterAttack = afterAttack;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void beforeAttack(PrePlayerAttackEntityEvent event) {
        if (!event.willAttack()) return;
        Player player = event.getPlayer();
        // The previous attack's native enchantment pass has finished. Complete
        // its fall reset before another packet can reuse the same smash bonus.
        finishSmash(player.getUniqueId());
        LegendaryItemCarrier.inventory(player);
        var weapon = LegendaryItemIdentity.weapon(player.getInventory().getItemInMainHand()).orElse(null);
        if (weapon == null) return;
        attacks.put(player.getUniqueId(), new Attack(event.getAttacked().getUniqueId(), weapon,
                vanilla.attackStrength(player), (float) player.getAttribute(Attribute.ATTACK_DAMAGE).getValue()
                    * vanilla.baseDamageScale(player), player.isSprinting(), System.nanoTime()));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void smashDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || progression.inAbilityDamage()) return;
        var weapon = LegendaryItemIdentity.weapon(player.getInventory().getItemInMainHand()).orElse(null);
        if (!smashEligible(weapon, player.getFallDistance(), player.isGliding())) return;
        // Native Player.attack adds the mace bonus after charge scaling and before critical scaling.
        double bonus = vanilla.smashBonus(victim, event.getDamageSource());
        event.setDamage(event.getDamage() + bonus * (event.isCritical() ? 1.5 : 1.0));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void hit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !(event.getEntity() instanceof LivingEntity victim)
                || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || progression.inAbilityDamage()) return;
        Attack attack = attacks.remove(player.getUniqueId());
        boolean absorbed = event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION)
                && event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION) < 0;
        if (event.isCancelled() || (event.getFinalDamage() <= 0 && !absorbed)) return;
        var held = player.getInventory().getItemInMainHand();
        var weapon = LegendaryItemIdentity.weapon(held).orElse(null);
        if (smashEligible(weapon, player.getFallDistance(), player.isGliding())) {
            var original = held.clone();
            vanilla.smashEffects(original, victim, player);
            // Keep fall distance through the native post-attack enchantment pass (Wind Burst uses it).
            deferSmash(player.getUniqueId(), () -> {
                if (player.isOnline()) vanilla.finishSmash(original, victim, player);
            });
        } else if (attack != null && attack.weapon() == weapon && attack.target().equals(victim.getUniqueId())
                && System.nanoTime() - attack.capturedAt() < 1_000_000_000L
                && sweepEligible(weapon, attack.strength(), event.isCritical(), attack.sprinting(), player.isOnGround())
                && vanilla.slowEnoughToSweep(player)) {
            progression.attribute(player, weapon, () -> {
                vanilla.sweep(player, victim, attack.baseDamage(), event.getDamageSource(), attack.strength());
                return null;
            });
        }
    }

    static boolean smashEligible(LegendaryWeapon weapon, double fallDistance, boolean gliding) {
        return weapon == LegendaryWeapon.WINDWEAVER && fallDistance > 1.5 && !gliding;
    }

    static boolean sweepEligible(LegendaryWeapon weapon, float strength, boolean critical, boolean sprinting,
                                 boolean onGround) {
        return (weapon == LegendaryWeapon.BLOODLUST || weapon == LegendaryWeapon.SHADOW_BLADE)
                && strength > 0.9F && !critical && !sprinting && onGround;
    }

    void deferSmash(UUID player, Runnable completion) {
        finishSmash(player);
        smashFinishes.put(player, completion);
        afterAttack.accept(() -> {
            if (smashFinishes.remove(player, completion)) completion.run();
        });
    }

    void finishSmash(UUID player) {
        Runnable completion = smashFinishes.remove(player);
        if (completion != null) completion.run();
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        finishSmash(event.getPlayer().getUniqueId());
        attacks.remove(event.getPlayer().getUniqueId());
    }

    @Override public void close() {
        for (UUID player : List.copyOf(smashFinishes.keySet())) finishSmash(player);
        attacks.clear();
    }
}
