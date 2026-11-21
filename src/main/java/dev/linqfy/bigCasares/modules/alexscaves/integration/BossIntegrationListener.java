package dev.linqfy.bigCasares.modules.alexscaves.integration;

import dev.linqfy.bigCasares.modules.alexscaves.config.AlexsCavesSettings.IntegrationSettings;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Hooks the module bosses into the plugin's other systems without touching their code: it watches the damage
 * players deal (through the body or the hit parts, which both end up as damage to the boss entity), keeps the table
 * in the boss's persistent data and, when the boss dies in combat, pays the placements to every configured
 * {@link PlacementReward}. A placement needs a minimum share of the boss's health in damage. Deaths by {@code /kill}
 * or the void pay nothing; any other death (including a non-player final blow) pays the players who did the damage.
 */
public final class BossIntegrationListener implements Listener {
    /** Same key as the taming code: the owner of a tamed Tremorzilla (plugin namespace). */
    private static final String OWNER_KEY = "alexscaves_owner";
    private static final String FIGHT_KEY = "ac_boss_fight";

    private final ModMobService mobs;
    private final Plugin plugin;
    private final List<PlacementReward> rewards;
    private final IntegrationSettings settings;
    private final NamespacedKey fightKey;
    private final NamespacedKey ownerKey;
    /** Health of the boss being hit, read before any boss logic changes it (events run one at a time). */
    private UUID stashedBoss;
    private double stashedHealth;

    public BossIntegrationListener(ModMobService mobs, List<PlacementReward> rewards, IntegrationSettings settings) {
        this.mobs = mobs;
        this.plugin = mobs.plugin();
        this.rewards = List.copyOf(rewards);
        this.settings = settings;
        this.fightKey = new NamespacedKey(plugin, FIGHT_KEY);
        this.ownerKey = new NamespacedKey(plugin, OWNER_KEY);
    }

    /** Runs before the boss's own handler (which sets its health to 1 when it starts dying). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void stashHealth(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity boss && bossId(boss).isPresent()) {
            stashedBoss = boss.getUniqueId();
            stashedHealth = boss.getHealth();
        } else {
            stashedBoss = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss) || !boss.getUniqueId().equals(stashedBoss)) {
            return;
        }
        stashedBoss = null;
        UUID credited = creditedPlayer(event.getDamager());
        if (credited == null) {
            return;
        }
        double before = stashedHealth;
        double finalDamage = event.getFinalDamage();
        // A cancelled hit counts only when it sent the boss into its death animation (not when another plugin
        // cancelled it).
        boolean countable = !event.isCancelled()
            || BossFightTracker.enteredDying(true, before, boss.getHealth(), finalDamage);
        if (countable) {
            PersistentDataContainer data = boss.getPersistentDataContainer();
            Map<UUID, Double> table = BossFightTracker.decode(data.get(fightKey, PersistentDataType.STRING));
            BossFightTracker.record(table, credited, Math.min(finalDamage, before));
            data.set(fightKey, PersistentDataType.STRING, BossFightTracker.encode(table));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity boss = event.getEntity();
        Optional<String> id = bossId(boss);
        if (id.isEmpty()) {
            return;
        }
        EntityDamageEvent last = boss.getLastDamageCause();
        if (last != null && (last.getCause() == EntityDamageEvent.DamageCause.KILL
            || last.getCause() == EntityDamageEvent.DamageCause.VOID)) {
            return;
        }
        Map<UUID, Double> table = BossFightTracker.decode(
            boss.getPersistentDataContainer().get(fightKey, PersistentDataType.STRING));
        AttributeInstance maxHealth = boss.getAttribute(Attribute.MAX_HEALTH);
        double minDamage = (maxHealth == null ? 0.0 : maxHealth.getValue()) * settings.minDamagePercent() / 100.0;
        for (BossFightTracker.Standing standing : BossFightTracker.standings(table, minDamage)) {
            for (PlacementReward reward : rewards) {
                try {
                    reward.award(standing.playerId(), id.get(), standing.placement());
                } catch (RuntimeException failure) {
                    plugin.getLogger().warning("[AlexsCaves] No se pudo dar el premio de " + id.get() + " puesto "
                        + standing.placement() + ": " + failure.getMessage());
                }
            }
        }
    }

    public void close() {
        stashedBoss = null;
    }

    private Optional<String> bossId(LivingEntity entity) {
        return mobs.typeOf(entity).map(type -> type.id())
            .filter(id -> BossIntegrationRules.counts(id, ownerOf(entity) != null));
    }

    private UUID ownerOf(Entity entity) {
        String owner = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (owner == null) {
            return null;
        }
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException broken) {
            return null;
        }
    }

    /** The player behind a hit: the attacker, a projectile's shooter, or the owner of a tamed pet. */
    private UUID creditedPlayer(Entity damager) {
        Entity source = damager;
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            source = shooter;
        }
        if (source instanceof Player player) {
            return player.getUniqueId();
        }
        if (source instanceof Tameable pet && pet.isTamed() && pet.getOwnerUniqueId() != null) {
            return pet.getOwnerUniqueId();
        }
        return ownerOf(source);
    }
}
