package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Tornado columns: pure particles (Java and Bedrock see the same thing, no model needed). A telegraph ring marks
 * each spot on the ground first; then the column wanders after a random player, slower than walking, for
 * {@link EnlilTuning#TORNADO_LIFE_TICKS}. Whoever is inside is spun, lifted and hurt every half second.
 */
final class EnlilTornadoes {

    private static final Particle.DustOptions RING = new Particle.DustOptions(Color.fromRGB(200, 214, 230), 1.4f);
    private static final Particle.DustOptions DUST = new Particle.DustOptions(Color.fromRGB(176, 160, 128), 1.2f);

    private static final class Tornado {
        private double x;
        private double z;
        private double targetX;
        private double targetZ;
        private int age;

        private Tornado(double x, double z) {
            this.x = x;
            this.z = z;
            this.targetX = x;
            this.targetZ = z;
        }
    }

    private final World world;
    private final EnlilArena arena;
    private final RandomGenerator random;
    private final List<double[]> warned = new ArrayList<>();
    private final List<Tornado> live = new ArrayList<>();
    private final Map<UUID, Long> lastHit = new HashMap<>();

    EnlilTornadoes(World world, EnlilArena arena, RandomGenerator random) {
        this.world = world;
        this.arena = arena;
        this.random = random;
    }

    /** Picks the spots (near random players, never on top of them) and starts drawing their rings. */
    void warn(List<Player> players, int count) {
        warned.clear();
        for (int index = 0; index < count; index++) {
            double baseX;
            double baseZ;
            if (players.isEmpty()) {
                baseX = arena.centerX();
                baseZ = arena.centerZ();
            } else {
                Location at = players.get(random.nextInt(players.size())).getLocation();
                baseX = at.getX();
                baseZ = at.getZ();
            }
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 3.0 + random.nextDouble() * 2.5;
            warned.add(new double[] {
                arena.clampX(baseX + Math.cos(angle) * distance, 2.0),
                arena.clampZ(baseZ + Math.sin(angle) * distance, 2.0)
            });
        }
        world.playSound(new Location(world, arena.centerX(), arena.floorY() + 2, arena.centerZ()),
            Sound.ENTITY_BREEZE_CHARGE, SoundCategory.HOSTILE, 3.0f, 0.6f);
    }

    void release() {
        for (double[] spot : warned) {
            live.add(new Tornado(spot[0], spot[1]));
            world.playSound(new Location(world, spot[0], arena.floorY(), spot[1]), Sound.ENTITY_BREEZE_WIND_BURST,
                SoundCategory.HOSTILE, 1.6f, 0.6f);
        }
        warned.clear();
    }

    void tick(long tick, List<Player> players, Entity source) {
        if (tick % 4 == 0) {
            for (double[] spot : warned) {
                ring(spot[0], spot[1]);
            }
        }
        live.removeIf(tornado -> {
            tornado.age++;
            if (tornado.age >= EnlilTuning.TORNADO_LIFE_TICKS) {
                world.spawnParticle(Particle.CLOUD, new Location(world, tornado.x, arena.floorY() + 2.0, tornado.z),
                    30, 0.8, 1.5, 0.8, 0.05);
                return true;
            }
            move(tornado, players);
            if (tick % 2 == 0) {
                draw(tornado, tick);
            }
            if (tick % 30 == 0) {
                world.playSound(new Location(world, tornado.x, arena.floorY() + 1.0, tornado.z),
                    Sound.ENTITY_BREEZE_IDLE_AIR, SoundCategory.HOSTILE, 1.2f, 0.5f);
            }
            drag(tornado, tick, players, source);
            return false;
        });
    }

    private void move(Tornado tornado, List<Player> players) {
        boolean arrived = Math.hypot(tornado.targetX - tornado.x, tornado.targetZ - tornado.z) < 0.5;
        if ((arrived || tornado.age % 60 == 1) && !players.isEmpty()) {
            Location at = players.get(random.nextInt(players.size())).getLocation();
            tornado.targetX = arena.clampX(at.getX() + (random.nextDouble() - 0.5) * 4.0, 2.0);
            tornado.targetZ = arena.clampZ(at.getZ() + (random.nextDouble() - 0.5) * 4.0, 2.0);
        }
        double[] next = EnlilWind.step(tornado.x, tornado.z, tornado.targetX, tornado.targetZ);
        tornado.x = arena.clampX(next[0], 1.5);
        tornado.z = arena.clampZ(next[1], 1.5);
    }

    private void draw(Tornado tornado, long tick) {
        for (int strand = 0; strand < 3; strand++) {
            for (int step = 0; step < 6; step++) {
                double h = step * (EnlilTuning.TORNADO_HEIGHT / 6.0) + (tick % 6) * 0.2;
                double angle = tick * 0.55 + strand * (Math.PI * 2.0 / 3.0) + h * 0.9;
                double radius = 0.5 + h * 0.17;
                world.spawnParticle(Particle.CLOUD, new Location(world, tornado.x + Math.cos(angle) * radius,
                    arena.floorY() + h, tornado.z + Math.sin(angle) * radius), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        world.spawnParticle(Particle.DUST, new Location(world, tornado.x, arena.floorY() + 0.3, tornado.z), 4,
            0.6, 0.15, 0.6, 0.0, DUST);
        if (tick % 10 == 0) {
            world.spawnParticle(Particle.SMALL_GUST, new Location(world, tornado.x, arena.floorY() + 1.0, tornado.z),
                1, 0.2, 0.2, 0.2, 0.0);
        }
    }

    private void ring(double x, double z) {
        for (int index = 0; index < 20; index++) {
            double angle = index * Math.PI / 10.0;
            world.spawnParticle(Particle.DUST, new Location(world, x + Math.cos(angle) * EnlilTuning.TORNADO_RADIUS,
                arena.floorY() + 0.15, z + Math.sin(angle) * EnlilTuning.TORNADO_RADIUS), 1, 0.0, 0.0, 0.0, 0.0, RING);
        }
        world.spawnParticle(Particle.CLOUD, new Location(world, x, arena.floorY() + 0.3, z), 2, 0.3, 0.05, 0.3, 0.01);
    }

    private void drag(Tornado tornado, long tick, List<Player> players, Entity source) {
        for (Player player : players) {
            Location at = player.getLocation();
            double dx = at.getX() - tornado.x;
            double dy = at.getY() - arena.floorY();
            double dz = at.getZ() - tornado.z;
            if (!EnlilWind.insideTornado(dx, dy, dz)) {
                continue;
            }
            EnlilWind.Push push = EnlilWind.tornadoDrag(dx, dy, dz);
            Long last = lastHit.get(player.getUniqueId());
            double damage = 0.0;
            if (last == null || tick - last >= EnlilTuning.TORNADO_HIT_TICKS) {
                lastHit.put(player.getUniqueId(), tick);
                damage = EnlilTuning.TORNADO_DAMAGE;
            }
            hitThenPush(player, damage, source, new Vector(push.x(), push.y(), push.z()));
        }
    }

    /**
     * Hurts (when {@code damage > 0}) and then sets the wind's velocity. The order matters: the vanilla knockback of
     * the damage would otherwise halve and override the push (bracing by sneaking would do nothing and the tornado
     * would throw players out instead of spinning them).
     */
    static void hitThenPush(Player player, double damage, Entity source, Vector velocity) {
        if (damage > 0.0) {
            player.damage(damage, source);
        }
        player.setVelocity(velocity);
    }

    boolean active() {
        return !live.isEmpty() || !warned.isEmpty();
    }

    void clear() {
        warned.clear();
        live.clear();
        lastHit.clear();
    }
}
