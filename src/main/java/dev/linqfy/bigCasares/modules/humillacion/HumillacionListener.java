package dev.linqfy.bigCasares.modules.humillacion;

import dev.linqfy.bigCasares.modules.tombstone.TombstoneModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class HumillacionListener implements Listener {
    private final HumillacionSettings settings;
    private final HumillacionPolicy policy;
    private final TombstoneModule tombstones;
    private final Clock clock;
    private final Map<UUID, Instant> lastHumiliation = new HashMap<>();

    HumillacionListener(HumillacionSettings settings, TombstoneModule tombstones, Clock clock) {
        this.settings = settings;
        this.policy = new HumillacionPolicy(settings);
        this.tombstones = tombstones;
        this.clock = clock;
    }

    // HIGHEST so armor, enchantments and other modules have already shaped the final damage.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker != null && attacker.getUniqueId().equals(victim.getUniqueId())) attacker = null;
        Instant now = clock.instant();
        if (!policy.shouldHumiliate(event.getFinalDamage(), victim.getHealth(), victim.getAbsorptionAmount(),
            attacker != null, lastHumiliation.get(victim.getUniqueId()), now)) {
            return;
        }
        // Where the tombstone would not capture a death (e.g. the End event) the hit kills normally.
        if (!tombstones.capturesDeathOf(victim.getUniqueId())) return;
        Location beside = besideLocation(victim);
        // Nothing to strip (or tombstones are off): let the hit kill instead of making the victim immortal.
        if (!tombstones.bury(victim, beside)) return;

        lastHumiliation.put(victim.getUniqueId(), now);
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (event.isApplicable(modifier)) event.setDamage(modifier, 0.0);
        }
        victim.setAbsorptionAmount(0.0);
        victim.setHealth(policy.healthAfter(maxHealth(victim)));
        effects(victim, attacker, beside);
    }

    void shutdown() {
        lastHumiliation.clear();
    }

    private void effects(Player victim, Player attacker, Location beside) {
        victim.sendTitle(ChatColor.DARK_RED + "" + ChatColor.BOLD + "HUMILLADO",
            ChatColor.GRAY + "Te dejaron en pelotas", 5, 60, 20);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.4f);
        victim.getWorld().spawnParticle(Particle.SOUL, beside.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.02);
        if (attacker != null) {
            attacker.sendTitle(ChatColor.GOLD + "" + ChatColor.BOLD + "HUMILLACIÓN",
                ChatColor.YELLOW + victim.getName() + " quedó sin nada", 5, 50, 15);
        }
        if (settings.broadcast()) {
            String by = attacker == null ? "" : ChatColor.RED + " por " + ChatColor.GOLD + attacker.getName();
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "¡HUMILLACIÓN! "
                + ChatColor.GOLD + victim.getName() + ChatColor.RED + " fue humillado" + by
                + ChatColor.RED + " y quedó a un corazón.");
        }
    }

    private static Location besideLocation(Player victim) {
        Location location = victim.getLocation();
        double[] offset = HumillacionPolicy.besideOffset(location.getYaw());
        return location.clone().add(offset[0], 0.0, offset[1]);
    }

    private static double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }

    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
