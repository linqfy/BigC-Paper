package dev.linqfy.bigCasares.modules.mythicdungeons.boss.came;

import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vucub Camé en el servidor: un esqueleto wither invisible y sin IA (del encuentro, via
 * {@link MythicBossSupport#spawnOwned}) que lleva el modelo {@link #MODEL} y da el hitbox. El encuentro no puede
 * registrar listeners, asi que los golpes se encuentran mirando la vida del ancla: se mantiene al maximo y cada baja es
 * un golpe (con su atacante en {@code getLastDamageCause()}); la vida de verdad la lleva {@link CameDuoHealth}.
 *
 * <p>Bedrock: ve un esqueleto escalado con casco de jade (no ve el modelo); sus golpes pasan al ancla.</p>
 */
final class CameVucub implements CameController.LordBody {

    static final String MODEL = "bigcasares_myth_came_vucub";
    private static final double HEALTH = 1024.0;
    /** El hitbox del esqueleto wither (2.4) escalado a ~3 bloques, el cuerpo del modelo sin el penacho. */
    private static final double SCALE = 1.25;
    private static final double STAND_IN_SCALE = 1.45;

    /** Un golpe encontrado por {@link #pollHits()}; {@code attacker} puede ser {@code null}. */
    record Hit(Player attacker, double damage) {
    }

    private final MythicBossSupport support;
    private final UUID bossId;
    private WitherSkeleton anchor;
    private JavaModelHandle model;
    private String currentLoop;

    CameVucub(MythicBossSupport support, UUID bossId) {
        this.support = support;
        this.bossId = bossId;
    }

    void spawn(Location at) {
        anchor = support.spawnOwned(bossId, at, WitherSkeleton.class, body -> {
            body.setAI(false);
            body.setSilent(true);
            body.setInvisible(true);
            body.setCollidable(false);
            body.setPersistent(false);
            body.setRemoveWhenFarAway(false);
            body.setCanPickupItems(false);
            body.setLootTable(null);
            body.customName(Component.text(CameLord.VUCUB.displayName()));
            body.setCustomNameVisible(false);
            EntityEquipment equipment = body.getEquipment();
            if (equipment != null) {
                equipment.clear();
            }
            base(body, Attribute.MAX_HEALTH, HEALTH);
            base(body, Attribute.SCALE, SCALE);
            base(body, Attribute.KNOCKBACK_RESISTANCE, 1.0);
            body.setHealth(HEALTH);
        });
        try {
            model = support.models().attach(anchor, MODEL);
        } catch (RuntimeException | LinkageError missing) {
            // sin modelo los de Java verian nada: que vean al esqueleto
            anchor.setInvisible(false);
            support.logger().warning("[Camé] Sin modelo " + MODEL + ": " + missing.getMessage());
        }
        loop("idle");
        try {
            Skeleton standIn = support.spawnBedrockStandIn(bossId, anchor, Skeleton.class, STAND_IN_SCALE);
            standIn.setShouldBurnInDay(false);
            standIn.customName(Component.text(CameLord.VUCUB.displayName()));
            EntityEquipment equipment = standIn.getEquipment();
            if (equipment != null) {
                equipment.clear();
                equipment.setHelmet(jadeHelmet());
                equipment.setHelmetDropChance(0.0F);
            }
        } catch (RuntimeException unavailable) {
            support.logger().warning("[Camé] Sin doble de Bedrock para Vucub: " + unavailable.getMessage());
        }
    }

    LivingEntity entity() {
        return anchor;
    }

    @Override
    public boolean valid() {
        return anchor != null && anchor.isValid();
    }

    @Override
    public Location location() {
        return anchor.getLocation();
    }

    @Override
    public void moveTo(Location to) {
        if (valid()) {
            anchor.teleport(to);
        }
    }

    @Override
    public void loop(String animation) {
        if (model == null || animation.equals(currentLoop)) {
            currentLoop = animation;
            return;
        }
        try {
            if (currentLoop != null) {
                support.models().stopAnimation(model, currentLoop);
            }
            support.models().animate(model, animation);
        } catch (RuntimeException ignored) {
            // cosmetico
        }
        currentLoop = animation;
    }

    @Override
    public void once(String animation) {
        if (model == null) {
            return;
        }
        try {
            support.models().animateOnce(model, animation, () -> {
            });
        } catch (RuntimeException ignored) {
            // cosmetico
        }
    }

    /** Golpes desde el ultimo llamado; deja la vida del ancla al maximo. */
    List<Hit> pollHits() {
        if (!valid()) {
            return List.of();
        }
        double health = anchor.getHealth();
        if (health >= HEALTH - 0.01) {
            return List.of();
        }
        List<Hit> hits = new ArrayList<>(1);
        hits.add(new Hit(attacker(anchor.getLastDamageCause()), HEALTH - health));
        anchor.setHealth(HEALTH);
        return hits;
    }

    void close() {
        if (model != null) {
            try {
                support.models().close(model);
            } catch (RuntimeException ignored) {
                // ya cerrado
            }
            model = null;
        }
        if (anchor != null && anchor.isValid()) {
            anchor.remove();
        }
        // el doble de Bedrock se borra solo al no tener ancla (BedrockStandIns#tick) y con support.cleanup
    }

    static ItemStack jadeHelmet() {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        if (helmet.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(Color.fromRGB(47, 181, 138));
            helmet.setItemMeta(meta);
        }
        return helmet;
    }

    static Player attacker(EntityDamageEvent cause) {
        if (!(cause instanceof EntityDamageByEntityEvent byEntity)) {
            return null;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        return damager instanceof Player player ? player : null;
    }

    private static void base(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
