package dev.linqfy.bigCasares.modules.donpollos;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Meteoritos del logo de WhatsApp (fase 2 del boss). Salen del auto, suben un poco y caen donde estaba
 * el jugador al tirarlos: la trayectoria se fija al lanzarlos y no lo persiguen. Son item displays con
 * el modelo donpollos:meteorito_wsp, prendidos fuego, que explotan al tocar un bloque o un jugador.
 */
final class DonPollosBossMeteors {

    static final String ITEM_MODEL = "meteorito_wsp";
    /** Gravedad propia del meteorito (bloques/tick^2). */
    static final double GRAVITY = 0.05;
    /** Velocidad horizontal (bloques/tick): 10 bloques tardan ~1 segundo. */
    static final double HORIZONTAL_SPEED = 0.5;
    private static final int MIN_FLIGHT_TICKS = 14;
    private static final int MAX_AGE_TICKS = 140;
    private static final double HIT_RADIUS = 1.3;
    private static final double BLAST_RADIUS = 2.6;
    private static final float SIZE = 0.75f;

    private final List<Meteor> meteors = new ArrayList<>();
    /** Se llama con cada jugador que alcanza una explosion (para el mensaje de muerte). */
    private final Consumer<Player> onHit;

    DonPollosBossMeteors(Consumer<Player> onHit) {
        this.onHit = onHit;
    }

    /**
     * Velocidad inicial para ir de {@code from} a {@code to} en una parabola (con {@link #GRAVITY} por tick).
     * Devuelve la velocidad por tick; el vuelo dura {@link #flightTicks(double)} ticks.
     */
    static Vector launchVelocity(Vector from, Vector to) {
        Vector delta = to.clone().subtract(from);
        double horizontal = Math.hypot(delta.getX(), delta.getZ());
        int ticks = flightTicks(horizontal);
        // posicion tras n ticks (sumar velocidad y despues restar gravedad): y = vy*n - g*n*(n-1)/2
        double vy = (delta.getY() + GRAVITY * ticks * (ticks - 1) / 2.0) / ticks;
        return new Vector(delta.getX() / ticks, vy, delta.getZ() / ticks);
    }

    static int flightTicks(double horizontalDistance) {
        return Math.max(MIN_FLIGHT_TICKS, (int) Math.ceil(horizontalDistance / HORIZONTAL_SPEED));
    }

    void launch(LivingEntity source, Location from, Location target, double damage) {
        World world = from.getWorld();
        Vector velocity = launchVelocity(from.toVector(), target.toVector());
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("donpollos", ITEM_MODEL));
        ItemDisplay display = world.spawn(from, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setPersistent(false);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(transformation(0));
        });
        meteors.add(new Meteor(display, source, from.toVector(), velocity, damage, onHit));
        world.playSound(from, Sound.ENTITY_BLAZE_SHOOT, 1.6f, 0.7f);
        world.playSound(from, Sound.ENTITY_GHAST_SHOOT, 1.0f, 0.8f);
        world.spawnParticle(Particle.FLAME, from, 20, 0.3, 0.3, 0.3, 0.05);
    }

    private static Transformation transformation(int age) {
        // gira como una rueda mientras vuela
        return new Transformation(new Vector3f(), new AxisAngle4f(age * 0.3f, 0, 0, 1),
            new Vector3f(SIZE, SIZE, SIZE), new AxisAngle4f());
    }

    void tick() {
        Iterator<Meteor> iterator = meteors.iterator();
        while (iterator.hasNext()) {
            Meteor meteor = iterator.next();
            if (!meteor.display.isValid() || meteor.tickAndHit()) {
                meteor.display.remove();
                iterator.remove();
            }
        }
    }

    void clear() {
        for (Meteor meteor : meteors) {
            meteor.display.remove();
        }
        meteors.clear();
    }

    private static final class Meteor {
        private final ItemDisplay display;
        private final LivingEntity source;
        private final Vector position;
        private final Vector velocity;
        private final double damage;
        private final Consumer<Player> onHit;
        private int age;

        private Meteor(ItemDisplay display, LivingEntity source, Vector position, Vector velocity, double damage,
                       Consumer<Player> onHit) {
            this.display = display;
            this.source = source;
            this.position = position;
            this.velocity = velocity;
            this.damage = damage;
            this.onHit = onHit;
        }

        /** Avanza un tick. Devuelve true si exploto (o se perdio) y hay que sacarlo. */
        private boolean tickAndHit() {
            World world = display.getWorld();
            age++;
            double step = velocity.length();
            if (step > 1e-6) {
                RayTraceResult wall = world.rayTraceBlocks(position.toLocation(world), velocity.clone().normalize(),
                    step, FluidCollisionMode.ALWAYS, true);
                if (wall != null) {
                    explode(wall.getHitPosition().toLocation(world));
                    return true;
                }
            }
            position.add(velocity);
            velocity.setY(velocity.getY() - GRAVITY);
            Location here = position.toLocation(world);
            for (Player player : world.getPlayers()) {
                if (canBeHit(player) && player.getLocation().add(0, 0.9, 0).distanceSquared(here) <= HIT_RADIUS * HIT_RADIUS) {
                    explode(here);
                    return true;
                }
            }
            if (age > MAX_AGE_TICKS || here.getY() < world.getMinHeight()) {
                return true;
            }
            display.teleport(here);
            display.setInterpolationDelay(0);
            display.setTransformation(transformation(age));
            // estela de meteorito: fuego, humo y alguna gota de lava
            world.spawnParticle(Particle.FLAME, here, 6, 0.22, 0.22, 0.22, 0.01, null, true);
            world.spawnParticle(Particle.LARGE_SMOKE, here, 2, 0.15, 0.15, 0.15, 0.01, null, true);
            if (age % 3 == 0) {
                world.spawnParticle(Particle.LAVA, here, 1, 0.1, 0.1, 0.1, 0, null, true);
            }
            if (age % 8 == 0) {
                world.playSound(here, Sound.BLOCK_FIRE_AMBIENT, 1.2f, 1.4f);
            }
            return false;
        }

        private void explode(Location at) {
            World world = at.getWorld();
            world.spawnParticle(Particle.EXPLOSION, at, 3, 0.6, 0.4, 0.6, 0, null, true);
            world.spawnParticle(Particle.FLAME, at, 40, 0.8, 0.5, 0.8, 0.08, null, true);
            world.spawnParticle(Particle.LAVA, at, 8, 0.5, 0.3, 0.5, 0, null, true);
            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 1.3f);
            for (Player player : world.getPlayers()) {
                if (canBeHit(player)
                    && player.getLocation().add(0, 0.9, 0).distanceSquared(at) <= BLAST_RADIUS * BLAST_RADIUS) {
                    onHit.accept(player);
                    player.damage(damage, source);
                    player.setFireTicks(Math.max(player.getFireTicks(), 60));
                }
            }
        }

        private static boolean canBeHit(Player player) {
            return !player.isDead() && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;
        }
    }
}
