package dev.linqfy.bigCasares.modules.mythicdungeons.boss.odin;

import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import dev.linqfy.bigCasares.modules.mythicdungeons.boss.MythicBossSupport;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Los guardianes de la fase 1 en el mundo: los crea (via {@link MythicBossSupport#spawnOwned}, asi se borran con el
 * jefe), les pone su modelo de BetterModel, los mantiene apuntando a los jugadores de la pelea y adentro de la sala, y
 * cuenta cuantos siguen vivos.
 *
 * <p>Bedrock no ve los modelos: ve la base vanilla (Vex, lobo, zombi con armadura), que es la que pelea igual.
 * La animacion de ataque es aproximada (se pide cuando el objetivo esta al alcance), es solo cosmetica.</p>
 */
final class OdinGuardianSquad {

    static final String RAVEN_MODEL = "bigcasares_myth_odin_raven";
    static final String WOLF_MODEL = "bigcasares_myth_odin_wolf";
    static final String EINHERJAR_MODEL = "bigcasares_myth_odin_einherjar";

    /** Si un guardian se aleja mas que esto del trono vuelve al lado de Odín (los Vex atraviesan paredes). */
    private static final double LEASH = 22.0;
    private static final long ATTACK_ANIMATION_GAP = 20L;
    private static final long CLOSE_AFTER_DEATH = 30L;

    private final MythicBossSupport support;
    private final UUID bossId;
    private final List<Member> members = new ArrayList<>();
    private final Set<String> missingModels = new HashSet<>();

    OdinGuardianSquad(MythicBossSupport support, UUID bossId) {
        this.support = Objects.requireNonNull(support, "support");
        this.bossId = Objects.requireNonNull(bossId, "bossId");
    }

    /** Crea a todos alrededor de {@code origin}, del lado de {@code forward} (vector horizontal unitario). */
    int summon(Location origin, Vector forward, int players) {
        int created = 0;
        for (OdinGuardians.Guardian guardian : OdinGuardians.plan(players)) {
            Location at = placement(origin, forward, guardian);
            LivingEntity entity;
            try {
                entity = spawn(guardian, at);
            } catch (RuntimeException failure) {
                support.logger().warning("[Odín] No pudo nacer " + guardian.name() + ": " + failure.getMessage());
                continue;
            }
            if (entity == null || !entity.isValid()) {
                continue;
            }
            JavaModelHandle handle = attachModel(entity, modelKey(guardian.kind()));
            members.add(new Member(guardian, entity, handle));
            World world = at.getWorld();
            world.spawnParticle(Particle.SOUL, at.clone().add(0.0, 1.0, 0.0), 25, 0.4, 0.8, 0.4, 0.04);
            world.spawnParticle(Particle.CLOUD, at.clone().add(0.0, 0.2, 0.0), 12, 0.5, 0.1, 0.5, 0.02);
            created++;
        }
        return created;
    }

    /** Lugares donde van a nacer (para el aviso previo). */
    static List<Location> placements(Location origin, Vector forward, int players) {
        List<Location> spots = new ArrayList<>();
        for (OdinGuardians.Guardian guardian : OdinGuardians.plan(players)) {
            spots.add(placement(origin, forward, guardian));
        }
        return spots;
    }

    int alive() {
        int count = 0;
        for (Member member : members) {
            if (member.entity.isValid() && !member.entity.isDead()) {
                count++;
            }
        }
        return count;
    }

    /** Objetivos, correa a la sala, animaciones de ataque y cierre de modelos de los muertos. */
    void tick(long tick, List<Player> targets, Location origin) {
        for (Member member : members) {
            LivingEntity entity = member.entity;
            if (!entity.isValid() || entity.isDead()) {
                if (member.diedAt < 0L) {
                    member.diedAt = tick;
                    animateOnce(member, "death");
                } else if (member.handle != null && tick - member.diedAt >= CLOSE_AFTER_DEATH) {
                    close(member);
                }
                continue;
            }
            if (!entity.getWorld().equals(origin.getWorld())
                || entity.getLocation().distanceSquared(origin) > LEASH * LEASH
                || entity.getLocation().getY() < origin.getY() - 8.0) {
                entity.teleport(placement(origin, new Vector(0, 0, 1), member.guardian));
            }
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            Player target = mob.getTarget() instanceof Player current && targets.contains(current) ? current : null;
            if (target == null) {
                target = nearest(entity.getLocation(), targets);
                mob.setTarget(target);
                if (mob instanceof Wolf wolf) {
                    wolf.setAngry(target != null);
                }
            }
            if (target != null && tick - member.lastAttackAnimation >= ATTACK_ANIMATION_GAP
                && target.getLocation().distanceSquared(entity.getLocation()) <= reach(member.guardian.kind())) {
                member.lastAttackAnimation = tick;
                animateOnce(member, "attack");
            }
        }
    }

    /** Cierra los modelos que siguen abiertos; las entidades las borra {@link MythicBossSupport#cleanup}. */
    void close() {
        for (Member member : members) {
            close(member);
        }
        members.clear();
    }

    private LivingEntity spawn(OdinGuardians.Guardian guardian, Location at) {
        return switch (guardian.kind()) {
            case RAVEN -> support.spawnOwned(bossId, at, Vex.class, vex -> {
                common(vex, guardian.name(), 14.0, 3.0);
                vex.setLimitedLifetime(false);
                EntityEquipment equipment = vex.getEquipment();
                if (equipment != null) {
                    equipment.setItemInMainHand(null);
                }
            });
            case WOLF -> support.spawnOwned(bossId, at, Wolf.class, wolf -> {
                common(wolf, guardian.name(), 30.0, 5.0);
                attribute(wolf, Attribute.MOVEMENT_SPEED, 0.38);
                wolf.setAngry(true);
            });
            case EINHERJAR -> support.spawnOwned(bossId, at, Zombie.class, zombie -> {
                common(zombie, guardian.name(), 36.0, 1.0);
                zombie.setAdult();
                zombie.setShouldBurnInDay(false);
                zombie.setCanBreakDoors(false);
                attribute(zombie, Attribute.MOVEMENT_SPEED, 0.25);
                attribute(zombie, Attribute.ARMOR, 6.0);
                attribute(zombie, Attribute.KNOCKBACK_RESISTANCE, 0.3);
                attribute(zombie, Attribute.SPAWN_REINFORCEMENTS, 0.0);
                EntityEquipment equipment = zombie.getEquipment();
                if (equipment != null) {
                    // Bedrock ve esta armadura (Java ve el modelo); la espada suma +5 al ataque base de 1
                    equipment.setHelmet(new ItemStack(Material.IRON_HELMET));
                    equipment.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                    equipment.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                    equipment.setItemInOffHand(new ItemStack(Material.SHIELD));
                    equipment.setHelmetDropChance(0.0F);
                    equipment.setChestplateDropChance(0.0F);
                    equipment.setItemInMainHandDropChance(0.0F);
                    equipment.setItemInOffHandDropChance(0.0F);
                }
            });
        };
    }

    private static void common(Mob mob, String name, double health, double damage) {
        attribute(mob, Attribute.MAX_HEALTH, health);
        attribute(mob, Attribute.ATTACK_DAMAGE, damage);
        attribute(mob, Attribute.FOLLOW_RANGE, 40.0);
        mob.setHealth(health);
        mob.customName(Component.text(name));
        mob.setCustomNameVisible(false);
        mob.setCanPickupItems(false);
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        // sin botin vanilla (la carne podrida del zombi); si el servidor no expone la tabla vacia, queda el normal
        LootTable empty = Bukkit.getLootTable(NamespacedKey.minecraft("empty"));
        if (empty != null) {
            mob.setLootTable(empty);
        }
    }

    private static void attribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private JavaModelHandle attachModel(LivingEntity entity, String key) {
        JavaModelGateway models = support.models();
        try {
            return models.attach(entity, key);
        } catch (RuntimeException | LinkageError missing) {
            if (missingModels.add(key)) {
                support.logger().warning("[Odín] Sin modelo " + key + " (" + missing.getMessage()
                    + "): el guardian se ve como su mob vanilla.");
            }
            return null;
        }
    }

    private void animateOnce(Member member, String animation) {
        if (member.handle == null) {
            return;
        }
        try {
            support.models().animateOnce(member.handle, animation, () -> {
            });
        } catch (RuntimeException ignored) {
            // cosmetico
        }
    }

    private void close(Member member) {
        if (member.handle == null) {
            return;
        }
        try {
            support.models().close(member.handle);
        } catch (RuntimeException failure) {
            support.logger().warning("[Odín] No se pudo cerrar el modelo " + member.handle.modelKey() + ": " + failure);
        }
        member.handle = null;
    }

    private static String modelKey(OdinGuardians.Kind kind) {
        return switch (kind) {
            case RAVEN -> RAVEN_MODEL;
            case WOLF -> WOLF_MODEL;
            case EINHERJAR -> EINHERJAR_MODEL;
        };
    }

    private static double reach(OdinGuardians.Kind kind) {
        return switch (kind) {
            case RAVEN -> 2.0 * 2.0;
            case WOLF -> 2.2 * 2.2;
            case EINHERJAR -> 2.6 * 2.6;
        };
    }

    private static Player nearest(Location from, List<Player> targets) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player player : targets) {
            if (!player.getWorld().equals(from.getWorld())) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    /** Del frente de Odín hacia su derecha segun el angulo del plan; sobre el primer piso libre que encuentra. */
    private static Location placement(Location origin, Vector forward, OdinGuardians.Guardian guardian) {
        double radians = Math.toRadians(guardian.angleDegrees());
        // a la derecha de quien mira hacia (fx, fz) en Minecraft queda (-fz, fx)
        double dirX = forward.getX() * Math.cos(radians) - forward.getZ() * Math.sin(radians);
        double dirZ = forward.getZ() * Math.cos(radians) + forward.getX() * Math.sin(radians);
        Location base = origin.clone().add(dirX * guardian.distance(), 0.0, dirZ * guardian.distance());
        Location ground = OdinWorld.groundNear(base, origin.getBlockY() + 3, 8).orElse(origin.clone());
        return ground.add(0.0, guardian.height(), 0.0);
    }

    /** Un guardian con su modelo (o sin, si falta) y su estado cosmetico. */
    private static final class Member {
        private final OdinGuardians.Guardian guardian;
        private final LivingEntity entity;
        private JavaModelHandle handle;
        private long lastAttackAnimation = Long.MIN_VALUE / 2;
        private long diedAt = -1L;

        private Member(OdinGuardians.Guardian guardian, LivingEntity entity, JavaModelHandle handle) {
            this.guardian = guardian;
            this.entity = entity;
            this.handle = handle;
        }
    }
}
