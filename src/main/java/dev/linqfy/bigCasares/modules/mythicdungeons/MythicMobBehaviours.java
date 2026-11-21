package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.LlamaSpit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.Fireball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Las conductas extra de los mobs miticos ({@link MythicBehaviour}), encima de la IA vanilla de su base. Corre cada
 * {@link MythicMobs#TICK_PERIOD} ticks; los enfriamientos se cuentan en esas corridas.
 */
final class MythicMobBehaviours {

    private static final int RUNS_PER_SECOND = (int) (20 / MythicMobs.TICK_PERIOD);
    private static final int MAX_SUMMONS = 3;
    /** Un {@link MythicBehaviour#BLINK_CASTER} se teletransporta cuando el objetivo esta a menos de esto. */
    static final double BLINK_TRIGGER = 4.0;
    /** A que distancia del objetivo reaparece. */
    static final double BLINK_RANGE = 8.0;
    /** Velocidad (bloques por tick) de un volador dirigido por cada punto de su {@code speed}. */
    static final double STEER_FACTOR = 0.5;
    /** Cada cuantas corridas puede pegar por contacto un volador dirigido (1 s). */
    private static final int CONTACT_RUNS = RUNS_PER_SECOND;

    private final MythicMobs mobs;

    MythicMobBehaviours(MythicMobs mobs) {
        this.mobs = mobs;
    }

    void tick(MythicMobs.Tracked mob, long run) {
        if (!(mob.entity instanceof Mob entity)) {
            return;
        }
        if (run % RUNS_PER_SECOND == 0) {
            ensureTarget(entity);
        }
        LivingEntity target = entity.getTarget();
        if (target == null || !target.isValid() || target.isDead() || !target.getWorld().equals(entity.getWorld())) {
            if (mob.type.steeredFlyer()) {
                entity.setVelocity(entity.getVelocity().multiply(0.5)); // sin objetivo se queda flotando
            }
            return;
        }
        double distance = entity.getLocation().distance(target.getLocation());
        if (mob.type.steeredFlyer()) {
            steer(mob, entity, target, distance, run);
        }
        if (run < mob.nextAbilityRun) {
            return;
        }
        boolean used = switch (mob.type.behaviour()) {
            case MELEE -> false;
            case LEAPER -> leap(mob, entity, target, distance);
            case RANGED -> shoot(mob, entity, target, distance);
            case FLYER_DIVE -> dive(entity, target, distance);
            case CASTER -> aura(mob, entity, distance);
            case SUMMONER -> summon(mob, entity, target);
            case PHASER -> phase(mob, entity, target, distance);
            case CONSTRICTOR -> constrict(mob, entity, target, distance);
            case TANK -> slam(mob, entity, distance);
            case BLINK_CASTER -> shouldBlink(distance) ? blinkAway(mob, entity, target)
                : shoot(mob, entity, target, distance);
        };
        if (used) {
            mob.nextAbilityRun = run + Math.round(cooldownSeconds(mob.type) * RUNS_PER_SECOND);
        }
    }

    static double cooldownSeconds(MythicMobType type) {
        double base = switch (type.behaviour()) {
            case MELEE -> 1.0;
            case LEAPER -> 4.0;
            case RANGED -> 3.0;
            case FLYER_DIVE -> 5.0;
            case CASTER -> 7.0;
            case SUMMONER -> 12.0;
            case PHASER -> 6.0;
            case CONSTRICTOR -> 6.0;
            case TANK -> 8.0;
            case BLINK_CASTER -> 4.0;
        };
        return type.elite() ? base * 0.8 : base;
    }

    /** Si un {@link MythicBehaviour#BLINK_CASTER} a esta distancia del objetivo se aleja (si no, dispara). */
    static boolean shouldBlink(double distance) {
        return distance < BLINK_TRIGGER;
    }

    private static void ensureTarget(Mob entity) {
        LivingEntity current = entity.getTarget();
        if (current instanceof Player player && MythicMobs.isVulnerable(player)
            && player.getWorld().equals(entity.getWorld())) {
            return;
        }
        if (current != null && current.isValid() && !(current instanceof Player)) {
            return;
        }
        Player nearest = MythicMobs.nearestVulnerablePlayer(entity, MythicMobs.targetRadius());
        if (nearest != null) {
            entity.setTarget(nearest);
        }
    }

    /**
     * Los voladores dirigidos van hacia el objetivo con velocidad (chocan con las paredes como cualquier entidad: no
     * hay teletransporte) y pegan por contacto, porque su IA vanilla esta apagada.
     */
    private static void steer(MythicMobs.Tracked mob, Mob entity, LivingEntity target, double distance, long run) {
        Location aim = target.getLocation().add(0, target.getHeight() * 0.6, 0);
        Vector toward = aim.toVector().subtract(entity.getLocation().toVector());
        if (toward.lengthSquared() > 1.0E-6) {
            Vector velocity = toward.normalize().multiply(mob.type.speed() * STEER_FACTOR);
            entity.setVelocity(velocity);
            Location facing = entity.getLocation().setDirection(velocity);
            entity.setRotation(facing.getYaw(), facing.getPitch());
        }
        double reach = mob.type.hitboxWidth() / 2.0 + 1.0;
        if (distance <= reach && run >= mob.nextContactRun && entity.hasLineOfSight(target)) {
            // el evento de dano aplica los efectos al pegar (MythicMobs#onDamage)
            target.damage(mob.type.damage(), entity);
            mob.nextContactRun = run + CONTACT_RUNS;
        }
    }

    private boolean leap(MythicMobs.Tracked mob, Mob entity, LivingEntity target, double distance) {
        if (distance < 3.0 || distance > 10.0 || !entity.isOnGround() || !entity.hasLineOfSight(target)) {
            return false;
        }
        Vector direction = horizontal(entity, target);
        entity.setVelocity(direction.multiply(mob.type.elite() ? 1.2 : 0.9).setY(0.45));
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1.0F, 0.7F);
        if (mob.type.elite()) {
            // los elites caen con un golpe al piso
            mobs.schedulerFor().later("leap-slam-" + entity.getUniqueId(), 14L, () -> {
                if (entity.isValid() && !entity.isDead()) {
                    shockwave(entity, 3.0, mob.type.damage() * 0.6);
                }
            });
        }
        return true;
    }

    private boolean shoot(MythicMobs.Tracked mob, Mob entity, LivingEntity target, double distance) {
        if (distance < 3.0 || distance > 20.0 || entity instanceof AbstractSkeleton || !entity.hasLineOfSight(target)) {
            return false; // los esqueletos ya disparan con su arco
        }
        Vector direction = target.getEyeLocation().toVector().subtract(entity.getEyeLocation().toVector()).normalize();
        int shots = mob.type.elite() ? 3 : 1;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int shot = 0; shot < shots; shot++) {
            Vector spread = direction.clone().add(new Vector(random.nextGaussian(), random.nextGaussian() * 0.3,
                random.nextGaussian()).multiply(shots == 1 ? 0.0 : 0.08)).normalize();
            switch (mob.type.projectile()) {
                case FIREBALL -> {
                    SmallFireball fireball = entity.launchProjectile(SmallFireball.class, spread.clone().multiply(0.9));
                    tameFireball(fireball, spread);
                }
                case SKULL -> {
                    WitherSkull skull = entity.launchProjectile(WitherSkull.class, spread.clone().multiply(0.8));
                    skull.setCharged(false);
                    tameFireball(skull, spread);
                }
                case SPIT -> entity.launchProjectile(LlamaSpit.class, spread.clone().multiply(1.4));
                case ARROW, NONE -> entity.launchProjectile(Arrow.class, spread.clone().multiply(1.6));
            }
        }
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.8F);
        return true;
    }

    private static void tameFireball(Fireball fireball, Vector direction) {
        fireball.setIsIncendiary(false);
        fireball.setYield(0.0F);
        fireball.setAcceleration(direction.clone().multiply(0.1));
    }

    private static boolean dive(Mob entity, LivingEntity target, double distance) {
        if (distance < 2.0 || distance > 16.0 || !entity.hasLineOfSight(target)) {
            return false;
        }
        Vector toward = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
        entity.setVelocity(toward.multiply(1.1));
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.0F, 1.0F);
        return true;
    }

    private boolean aura(MythicMobs.Tracked mob, Mob entity, double distance) {
        if (distance > 8.0) {
            return false;
        }
        boolean hit = false;
        for (Player player : entity.getWorld().getPlayers()) {
            if (MythicMobs.isVulnerable(player) && player.getLocation().distanceSquared(entity.getLocation()) <= 36.0
                && entity.hasLineOfSight(player)) {
                mobs.applyEffects(player, mob.type.onHit());
                hit = true;
            }
        }
        World world = entity.getWorld();
        world.spawnParticle(Particle.SOUL, entity.getLocation().add(0, 1, 0), 40, 3.0, 0.6, 3.0, 0.02);
        world.playSound(entity.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0F, 0.6F);
        mobs.animateOnce(entity, "attack");
        return hit;
    }

    private boolean summon(MythicMobs.Tracked mob, Mob entity, LivingEntity target) {
        mob.summons.removeIf(summoned -> !summoned.isValid() || summoned.isDead());
        if (mob.summons.size() >= MAX_SUMMONS) {
            return false;
        }
        Optional<MythicMobType> summoned = mobs.catalog().find(mob.type.summonId());
        if (summoned.isEmpty()) {
            return false;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location spot = entity.getLocation().add(random.nextDouble(-1.5, 1.5), 0.0, random.nextDouble(-1.5, 1.5));
        Optional<LivingEntity> created = mobs.spawnSummon(summoned.get(), spot);
        if (created.isEmpty()) {
            return false;
        }
        mob.summons.add(created.get());
        if (created.get() instanceof Mob summonedMob) {
            summonedMob.setTarget(target);
        }
        entity.getWorld().spawnParticle(Particle.SCULK_SOUL, spot.clone().add(0, 0.5, 0), 25, 0.4, 0.6, 0.4, 0.02);
        entity.getWorld().playSound(spot, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0F, 0.8F);
        mobs.animateOnce(entity, "attack");
        return true;
    }

    /**
     * Solo el Fantasma ({@link MythicMobType#phasesThroughWalls()}): reaparece detras o al costado del objetivo, en un
     * lugar donde entra su hitbox y desde el que ve al objetivo (nunca adentro de un bloque ni detras de una pared).
     */
    private static boolean phase(MythicMobs.Tracked mob, Mob entity, LivingEntity target, double distance) {
        if (distance < 3.0 || distance > 16.0) {
            return false;
        }
        Vector facing = target.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1.0E-6) {
            facing = new Vector(1, 0, 0);
        }
        facing.normalize();
        Vector side = new Vector(-facing.getZ(), 0, facing.getX());
        for (Vector offset : List.of(facing.clone().multiply(-2.0), side.clone().multiply(2.0),
            side.clone().multiply(-2.0))) {
            Location destination = target.getLocation().add(offset);
            if (!fits(mob.type, destination) || !clearPath(destination.clone().add(0, 1.0, 0),
                target.getEyeLocation())) {
                continue;
            }
            Location from = entity.getLocation();
            destination.setDirection(target.getLocation().toVector().subtract(destination.toVector()));
            entity.teleport(destination);
            World world = entity.getWorld();
            world.spawnParticle(Particle.LARGE_SMOKE, from.add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.01);
            world.spawnParticle(Particle.PORTAL, destination.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.2);
            world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.7F);
            return true;
        }
        return false;
    }

    /**
     * Reaparece a {@link #BLINK_RANGE} bloques del objetivo, probando primero del lado opuesto al que esta y despues
     * girando de a 45 grados. Solo va a lugares donde entra su hitbox y que ve desde donde esta parado (no salta
     * paredes); si no hay ninguno no hace nada y reintenta en la proxima corrida.
     */
    private static boolean blinkAway(MythicMobs.Tracked mob, Mob entity, LivingEntity target) {
        Vector away = entity.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 1.0E-6) {
            away = new Vector(1, 0, 0);
        }
        away.normalize();
        for (int step = 0; step < 8; step++) {
            double angle = (step % 2 == 0 ? 1 : -1) * ((step + 1) / 2) * (Math.PI / 4.0);
            Vector direction = away.clone().rotateAroundY(angle).multiply(BLINK_RANGE);
            Location destination = target.getLocation().add(direction);
            if (!fits(mob.type, destination) || !clearPath(entity.getEyeLocation(),
                destination.clone().add(0, mob.type.hitboxHeight() * 0.85, 0))) {
                continue;
            }
            Location from = entity.getLocation();
            destination.setDirection(target.getLocation().toVector().subtract(destination.toVector()));
            entity.teleport(destination);
            World world = entity.getWorld();
            world.spawnParticle(Particle.FLAME, from.add(0, 1, 0), 30, 0.3, 0.7, 0.3, 0.03);
            world.spawnParticle(Particle.LARGE_SMOKE, destination.clone().add(0, 1, 0), 20, 0.3, 0.7, 0.3, 0.01);
            world.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.3F);
            return true;
        }
        return false;
    }

    private boolean constrict(MythicMobs.Tracked mob, Mob entity, LivingEntity target, double distance) {
        if (distance > 2.5) {
            return false;
        }
        PotionEffectType slowness = Registry.EFFECT.get(NamespacedKey.minecraft("slowness"));
        if (slowness != null) {
            target.addPotionEffect(new PotionEffect(slowness, 30, 3));
        }
        mobs.applyEffects(target, mob.type.onHit());
        Vector pull = entity.getLocation().toVector().subtract(target.getLocation().toVector());
        if (pull.lengthSquared() > 1.0E-6) {
            target.setVelocity(pull.normalize().multiply(0.3).setY(0.05));
        }
        entity.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 0.6, 0), 20, 0.4, 0.4, 0.4);
        entity.getWorld().playSound(target.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1.0F, 0.6F);
        mobs.animateOnce(entity, "attack");
        return true;
    }

    private boolean slam(MythicMobs.Tracked mob, Mob entity, double distance) {
        if (distance > 4.0 || !entity.isOnGround()) {
            return false;
        }
        mobs.animateOnce(entity, "attack");
        shockwave(entity, 4.0, mob.type.damage() * 0.75);
        return true;
    }

    private void shockwave(Mob entity, double radius, double damage) {
        World world = entity.getWorld();
        Location center = entity.getLocation();
        world.spawnParticle(Particle.EXPLOSION, center, 3, radius / 2, 0.2, radius / 2, 0.0);
        world.playSound(center, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2F, 0.5F);
        for (Entity nearby : entity.getNearbyEntities(radius, 2.0, radius)) {
            // el golpe al piso no atraviesa paredes
            if (nearby instanceof Player player && MythicMobs.isVulnerable(player) && entity.hasLineOfSight(player)) {
                Vector away = player.getLocation().toVector().subtract(center.toVector()).setY(0);
                if (away.lengthSquared() < 1.0E-6) {
                    away = new Vector(0.1, 0, 0);
                }
                player.damage(damage, entity);
                player.setVelocity(away.normalize().multiply(1.0).setY(0.5));
            }
        }
    }

    private static Vector horizontal(Entity from, Entity to) {
        Vector direction = to.getLocation().toVector().subtract(from.getLocation().toVector()).setY(0);
        return direction.lengthSquared() < 1.0E-6 ? new Vector() : direction.normalize();
    }

    /** Si la hitbox del mob entra parada en ese lugar (aire para todo su ancho y alto, piso abajo). */
    private static boolean fits(MythicMobType type, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        MobPlacement.Spot spot = new MobPlacement.Spot(location.getBlockX(), location.getBlockY(),
            location.getBlockZ());
        if (!MobPlacement.fits(new WorldBlockGrid(world), spot, type.hitboxWidth(), type.hitboxHeight())) {
            return false;
        }
        // centrado en el bloque, que es donde la regla comprobo el lugar
        location.setX(spot.x() + 0.5);
        location.setY(spot.y());
        location.setZ(spot.z() + 0.5);
        return true;
    }

    /** Si entre los dos puntos no hay ningun bloque que corte la vista. */
    private static boolean clearPath(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld())) {
            return false;
        }
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length < 1.0E-6) {
            return true;
        }
        return world.rayTraceBlocks(from, direction.normalize(), length, FluidCollisionMode.NEVER, true) == null;
    }
}
