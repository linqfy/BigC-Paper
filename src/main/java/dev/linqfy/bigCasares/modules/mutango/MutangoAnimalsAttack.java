package dev.linqfy.bigCasares.modules.mutango;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tercer ataque de Neguechen (el menos probable): invoca animales poseidos que te persiguen y te pegan. La primera
 * vez: 3 vacas, 2 gallinas, 1 caballo, 1 burro y 5 chanchos (despues mas, ver MutangoDifficulty). Cada uno corre a
 * su velocidad (la de Minecraft) y pega segun su tamano. Se los puede matar (no sueltan nada) y a los 15 segundos los
 * que quedan desaparecen.
 */
final class MutangoAnimalsAttack {

    /** Un tipo de animal: cuantos salen, su velocidad (la de Minecraft) y su dano (medio corazon = 1). */
    private record Kind(Class<? extends Mob> type, int count, double speed, double damage) {
    }

    private static final List<Kind> KINDS = List.of(
        new Kind(Cow.class, 3, 0.2, 4.0),
        new Kind(Chicken.class, 2, 0.25, 1.0),
        new Kind(Horse.class, 1, 0.3, 6.0),
        new Kind(Donkey.class, 1, 0.175, 5.0),
        new Kind(Pig.class, 5, 0.25, 3.0)
    );

    /** Corren (la velocidad de cada uno multiplicada por esto), manteniendo las diferencias entre ellos. */
    private static final double CHASE = 1.6;
    private static final int LIFETIME_TICKS = 300;
    private static final int HIT_COOLDOWN_TICKS = 20;
    private static final double REACH = 0.4;
    private static final double KNOCKBACK = 0.45;
    private static final double LIFT = 0.3;
    private static final double SPAWN_RADIUS = 4.0;
    private static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 70), 1.2f);

    private final MutangoPhaseThree.Host host;
    private final MutangoDifficulty difficulty;
    /** Cada animal invocado y su dano; cuando le toca volver a pegar y cuando desaparece. */
    private final Map<UUID, Double> damage = new HashMap<>();
    private final Map<UUID, Integer> nextHit = new HashMap<>();
    private int ticks;
    private int expires = -1;

    MutangoAnimalsAttack(MutangoPhaseThree.Host host, MutangoDifficulty difficulty) {
        this.host = host;
        this.difficulty = difficulty;
    }

    /** Quedan animales invocados (no se vuelve a invocar hasta que no quede ninguno). */
    boolean active() {
        damage.keySet().removeIf(id -> !(Bukkit.getEntity(id) instanceof Mob mob) || !mob.isValid());
        return !damage.isEmpty();
    }

    /** Neguechen empuja las manos y aparecen todos en un anillo a su alrededor, con un estallido cada uno. */
    void start(UUID neguechen, Location home) {
        stop();
        host.animations().once(neguechen, "lanzar");
        World world = home.getWorld();
        world.playSound(home, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2f, 0.7f);
        world.playSound(home, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 2f, 0.6f);
        NamespacedKey key = host.summonKey();
        // cada victoria: mas animales, mas rapidos, que pegan mas y con armadura
        int total = KINDS.stream().mapToInt(kind -> difficulty.count(kind.count())).sum();
        int i = 0;
        for (Kind kind : KINDS) {
            for (int n = 0; n < difficulty.count(kind.count()); n++, i++) {
                double angle = 2 * Math.PI * i / total;
                Location at = home.clone().add(Math.cos(angle) * SPAWN_RADIUS, 0, Math.sin(angle) * SPAWN_RADIUS);
                at.setY(MutangoGround.y(at) + 1);
                Mob animal = world.spawn(at, kind.type(), mob -> {
                    mob.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
                    mob.setPersistent(false);
                    mob.setRemoveWhenFarAway(false);
                    if (mob instanceof Ageable ageable) {
                        ageable.setAdult();
                    }
                    if (mob instanceof AbstractHorse horse) {
                        horse.setTamed(false);
                    }
                    var speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
                    if (speed != null) {
                        speed.setBaseValue(difficulty.speed(kind.speed()));
                    }
                    var range = mob.getAttribute(Attribute.FOLLOW_RANGE);
                    if (range != null) {
                        range.setBaseValue(64);
                    }
                    MutangoCombat.armor(mob, difficulty.level());
                    // mas vida (la de cada animal en Minecraft, mas segun las victorias)
                    var health = mob.getAttribute(Attribute.MAX_HEALTH);
                    if (health != null) {
                        health.setBaseValue(difficulty.health(health.getBaseValue()));
                        mob.setHealth(health.getBaseValue());
                    }
                });
                // sin su IA de animal (pastar, escaparse...): solo te persiguen
                Bukkit.getMobGoals().removeAllGoals(animal);
                damage.put(animal.getUniqueId(), difficulty.damage(kind.damage()));
                nextHit.put(animal.getUniqueId(), 0);
                Location mid = at.clone().add(0, 0.6, 0);
                world.spawnParticle(Particle.END_ROD, mid, 15, 0.4, 0.6, 0.4, 0.06);
                world.spawnParticle(Particle.DUST, mid, 15, 0.5, 0.6, 0.5, 0, GOLD);
                world.spawnParticle(Particle.CLOUD, at, 8, 0.4, 0.1, 0.4, 0.03);
            }
        }
        expires = ticks + LIFETIME_TICKS;
    }

    /** Cada tick (aunque no este atacando): los animales te persiguen y te pegan; se van cuando se les acaba el tiempo. */
    void tick() {
        ticks++;
        if (damage.isEmpty()) {
            return;
        }
        if (expires >= 0 && ticks >= expires) {
            stop();
            return;
        }
        for (Map.Entry<UUID, Double> entry : new HashMap<>(damage).entrySet()) {
            if (!(Bukkit.getEntity(entry.getKey()) instanceof Mob animal) || !animal.isValid()) {
                damage.remove(entry.getKey());
                nextHit.remove(entry.getKey());
                continue;
            }
            Player target = MutangoTargets.nearest(animal);
            if (ticks % 3 == 0) {
                animal.getWorld().spawnParticle(Particle.DUST, animal.getLocation().add(0, animal.getHeight() * 0.6, 0),
                    1, 0.25, 0.25, 0.25, 0, GOLD);
            }
            if (target == null) {
                continue;
            }
            if (ticks % 10 == 0) {
                animal.getPathfinder().moveTo(target, CHASE);
            }
            if (ticks >= nextHit.getOrDefault(entry.getKey(), 0)
                && animal.getBoundingBox().expand(REACH).overlaps(target.getBoundingBox())) {
                nextHit.put(entry.getKey(), ticks + difficulty.cooldownTicks(HIT_COOLDOWN_TICKS));
                MutangoDamage.hurt(target, entry.getValue(), animal);
                Vector push = target.getLocation().toVector().subtract(animal.getLocation().toVector()).setY(0);
                push = push.lengthSquared() > 1e-4 ? push.normalize().multiply(KNOCKBACK) : new Vector();
                MutangoDamage.push(target, push.setY(LIFT));
                animal.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3,
                    0.1);
            }
        }
    }

    /** Desaparecen todos los que quedan, con un poof. */
    void stop() {
        for (UUID id : damage.keySet()) {
            if (Bukkit.getEntity(id) instanceof Entity animal && animal.isValid()) {
                animal.getWorld().spawnParticle(Particle.POOF, animal.getLocation().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3,
                    0.02);
                animal.remove();
            }
        }
        damage.clear();
        nextHit.clear();
        expires = -1;
    }
}
