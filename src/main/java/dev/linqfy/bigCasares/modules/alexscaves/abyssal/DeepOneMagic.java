package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobContext;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * The mage's spells, simulated tick by tick with particles (the mod's {@code WaterBoltEntity} and {@code WaveEntity} are
 * entities with their own models, which the module does not draw): a homing water bolt and a fan of five waves.
 */
final class DeepOneMagic {
    private DeepOneMagic() {
    }

    /** Who a spell may hurt: not the Deep Ones, not its caster and nobody the caster is allied to. */
    static Predicate<LivingEntity> victims(ModMobContext context, LivingEntity owner) {
        return other -> other.isValid() && !other.equals(owner) && !DeepOnes.friendly(context, owner, other);
    }

    /** Mod {@code WaterBoltEntity}: arcs toward its target, splashes everything around where it lands. */
    static void bolt(ModMobContext context, LivingEntity owner, Location from, Vector direction, double speed,
                     double inaccuracy, boolean bubbling, UUID arcTowards) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector velocity = direction.clone().normalize().multiply(speed).add(new Vector(
            random.nextGaussian() * 0.0075 * inaccuracy, random.nextGaussian() * 0.0075 * inaccuracy,
            random.nextGaussian() * 0.0075 * inaccuracy));
        context.tasks().repeat(new Bolt(context, owner, from.clone(), velocity, bubbling, arcTowards), 1L, 1L, () -> { });
    }

    private static final class Bolt extends BukkitRunnable {
        private final ModMobContext context;
        private final LivingEntity owner;
        private final Location position;
        private final Vector velocity;
        private final boolean bubbling;
        private final UUID arcTowards;
        private int age;
        private int dieIn = -1;
        private int woosh;

        Bolt(ModMobContext context, LivingEntity owner, Location position, Vector velocity, boolean bubbling, UUID arcTowards) {
            this.context = context;
            this.owner = owner;
            this.position = position;
            this.velocity = velocity;
            this.bubbling = bubbling;
            this.arcTowards = arcTowards;
        }

        @Override
        public void run() {
            World world = position.getWorld();
            if (!position.isChunkLoaded() || age > 400) {
                cancel();
                return;
            }
            age++;
            Entity arc = arcTowards == null ? null : context.plugin().getServer().getEntity(arcTowards);
            if (arc != null && age > 3 && dieIn == -1 && arc.isValid() && arc.getWorld() == position.getWorld() && arc.getLocation().distance(position) > 1.5 && age < 20) {
                Vector toward = arc.getLocation().add(0, 0.85 * arc.getHeight(), 0).toVector().subtract(position.toVector());
                if (toward.lengthSquared() > 1.0E-6) {
                    velocity.add(toward.normalize().multiply(0.3));
                }
            }
            if (woosh-- <= 0) {
                woosh = 30 + ThreadLocalRandom.current().nextInt(30);
                world.playSound(position, "alexscaves:sea_staff_woosh", 1.0F, 1.0F);
            }
            boolean inWater = position.getBlock().getType() == Material.WATER;
            world.spawnParticle(!inWater && !bubbling ? Particle.SPLASH : Particle.BUBBLE, position, 4, 0.25, 0.25, 0.25, 0.0);
            Vector step = velocity.clone();
            velocity.multiply(0.9);
            velocity.setY(velocity.getY() - 0.07);
            if (dieIn == -1) {
                Location next = position.clone().add(step);
                double length = step.length();
                if (length > 1.0E-6) {
                    RayTraceResult hit = world.rayTrace(position, step.clone().normalize(), length, FluidCollisionMode.NEVER, true, 0.3,
                        other -> other instanceof LivingEntity living && age > 2 && victims(context, owner).test(living));
                    if (hit != null) {
                        position.setX(hit.getHitPosition().getX());
                        position.setY(hit.getHitPosition().getY());
                        position.setZ(hit.getHitPosition().getZ());
                        splash();
                        return;
                    }
                }
                position.setX(next.getX());
                position.setY(next.getY());
                position.setZ(next.getZ());
            } else if (--dieIn <= 0) {
                world.spawnParticle(Particle.SPLASH, position.clone().add(0, 0.5, 0), 30, 0.7, 0.3, 0.7, 0.0);
                cancel();
            }
        }

        private void splash() {
            World world = position.getWorld();
            world.playSound(position, "alexscaves:sea_staff_hit", 1.0F, 1.0F);
            BoundingBox box = BoundingBox.of(position.toVector(), 0.3, 0.3, 0.3).expand(2.0);
            for (Entity entity : world.getNearbyEntities(box)) {
                if (entity instanceof LivingEntity living && victims(context, owner).test(living)) {
                    double before = living.getHealth();
                    living.damage(3.0, owner);
                    if (bubbling && (living.getHealth() < before || living.isDead())) {
                        context.effects().apply(living, ModEffectType.BUBBLED, 0, 200);
                    }
                }
            }
            dieIn = 5;
        }
    }

    /** Mod {@code WaveEntity}: a wave that speeds up along the floor, hurts what it touches and breaks after a while. */
    static void wave(ModMobContext context, LivingEntity owner, Location from, float yaw, int lifespan) {
        context.tasks().repeat(new Wave(context, owner, from.clone(), yaw, lifespan), 1L, 1L, () -> { });
    }

    private static final class Wave extends BukkitRunnable {
        private final ModMobContext context;
        private final LivingEntity owner;
        private final Location position;
        private final double yawRadians;
        private final int lifespan;
        private final Vector velocity = new Vector();
        private int active;
        private boolean slamming;
        private int slamProgress;

        Wave(ModMobContext context, LivingEntity owner, Location position, float yaw, int lifespan) {
            this.context = context;
            this.owner = owner;
            this.position = position;
            this.yawRadians = Math.toRadians(-yaw);
            this.lifespan = lifespan;
        }

        @Override
        public void run() {
            World world = position.getWorld();
            if (!position.isChunkLoaded() || active > 400) {
                cancel();
                return;
            }
            if (slamming && ++slamProgress >= 10) {
                cancel();
                return;
            }
            boolean inWater = position.getBlock().getType() == Material.WATER;
            if (!inWater) {
                velocity.setY(velocity.getY() - 0.04);
            }
            float f = Math.min(active / 10.0F, 1.0F);
            double push = f * f * 0.2;
            Vector step = velocity.clone().multiply(0.9).add(new Vector(Math.sin(yawRadians) * push, 0, Math.cos(yawRadians) * push));
            attack(2.0 + slamProgress * 0.2);
            move(step);
            velocity.copy(step).multiply(new Vector(0.99, 0.98, 0.99));
            for (int i = 0; i <= 4; i++) {
                double side = (i / 4.0 - 0.5) * 1.2;
                Location at = position.clone().add(Math.cos(yawRadians) * side, 0.3 + ThreadLocalRandom.current().nextDouble() * 0.2,
                    -Math.sin(yawRadians) * side);
                world.spawnParticle(Particle.SPLASH, at, 1, 0.05, 0.05, 0.05, 0.0);
                world.spawnParticle(Particle.BUBBLE, at, 1, 0.05, 0.05, 0.05, 0.0);
            }
            double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
            if (active > lifespan || active > 10 && horizontal < 0.04) {
                slamming = true;
            }
            active++;
        }

        /** Step height 2: a wave climbs up to two blocks and stops at anything taller. */
        private void move(Vector step) {
            Location next = position.clone().add(step.getX(), 0, step.getZ());
            boolean moved = false;
            for (int up = 0; up <= 2 && !moved; up++) {
                Block block = next.clone().add(0, up, 0).getBlock();
                if (block.isPassable() && block.getRelative(0, 1, 0).isPassable() || block.getType() == Material.WATER) {
                    next.add(0, up, 0);
                    moved = true;
                }
            }
            if (moved) {
                position.setX(next.getX());
                position.setZ(next.getZ());
                position.setY(next.getY());
            } else {
                velocity.setX(0);
                velocity.setZ(0);
            }
            Block below = position.clone().add(0, step.getY() - 0.05, 0).getBlock();
            if (below.isPassable()) {
                position.add(0, step.getY(), 0);
            } else {
                velocity.setY(0);
            }
        }

        private void attack(double scale) {
            BoundingBox box = BoundingBox.of(position.toVector().add(new Vector(0, 0.45, 0)), 0.45, 0.45, 0.45).expand(0.5);
            for (Entity entity : position.getWorld().getNearbyEntities(box)) {
                if (entity instanceof LivingEntity living && victims(context, owner).test(living)) {
                    living.damage(scale + 1.0, owner);
                    slamming = true;
                    living.knockback(0.1 + 0.5 * scale, Math.sin(yawRadians), -Math.cos(yawRadians));
                }
            }
        }
    }
}
