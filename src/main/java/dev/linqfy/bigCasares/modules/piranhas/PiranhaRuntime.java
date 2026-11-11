package dev.linqfy.bigCasares.modules.piranhas;

import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TropicalFish;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Piranhas are red tropical fish tagged in their persistent data. They keep vanilla fish behaviour until a
 * swimmer comes near; then the school chases and bites, faster when the swimmer is low on health.
 */
final class PiranhaRuntime {

    static final long HUNT_PERIOD_TICKS = 2L;

    private final PiranhaSettings settings;
    private final PiranhaPolicy policy;
    private final NamespacedKey piranhaKey;
    private final Map<UUID, TropicalFish> tracked = new HashMap<>();
    private final Map<UUID, Long> lastBite = new HashMap<>();
    private long tick;

    PiranhaRuntime(PiranhaSettings settings, NamespacedKey piranhaKey) {
        this.settings = settings;
        this.policy = new PiranhaPolicy(settings);
        this.piranhaKey = piranhaKey;
    }

    boolean isPiranha(Entity entity) {
        return entity instanceof TropicalFish && entity.getPersistentDataContainer().has(piranhaKey);
    }

    void track(Entity entity) {
        if (isPiranha(entity)) {
            tracked.put(entity.getUniqueId(), (TropicalFish) entity);
        }
    }

    void forget(Entity entity) {
        tracked.remove(entity.getUniqueId());
        lastBite.remove(entity.getUniqueId());
    }

    /** Rolls a school near every eligible player. */
    void spawnTick(Collection<? extends Player> players) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (Player player : players) {
            if (!hunts(player)) {
                continue;
            }
            Location at = player.getLocation();
            String biome = at.getBlock().getBiome().key().value();
            int nearby = countNearby(at);
            if (policy.shouldSpawn(biome, nearby, random.nextDouble())) {
                Location water = findWater(at, random);
                if (water != null) {
                    spawnSchool(water, policy.schoolSize(random.nextDouble()));
                }
            }
        }
    }

    /** Spawns a school; returns how many fish were created. */
    int spawnSchool(Location water, int size) {
        World world = water.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int spawned = 0;
        for (int i = 0; i < size; i++) {
            Location spot = water.clone().add(random.nextDouble(-1.0, 1.0), random.nextDouble(-0.5, 0.5),
                random.nextDouble(-1.0, 1.0));
            if (spot.getBlock().getType() != Material.WATER) {
                spot = water;
            }
            TropicalFish fish = world.spawn(spot, TropicalFish.class, created -> {
                created.setPattern(TropicalFish.Pattern.FLOPPER);
                created.setBodyColor(DyeColor.RED);
                created.setPatternColor(DyeColor.GRAY);
                // No custom name: named fish never despawn and schools would pile up.
                created.setRemoveWhenFarAway(true);
                created.getPersistentDataContainer().set(piranhaKey, PersistentDataType.BYTE, (byte) 1);
            });
            tracked.put(fish.getUniqueId(), fish);
            spawned++;
        }
        return spawned;
    }

    /** Moves hunting piranhas towards their prey and bites when close. */
    void huntTick() {
        tick += HUNT_PERIOD_TICKS;
        Iterator<Map.Entry<UUID, TropicalFish>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TropicalFish> entry = iterator.next();
            TropicalFish fish = entry.getValue();
            if (!fish.isValid()) {
                iterator.remove();
                lastBite.remove(entry.getKey());
                continue;
            }
            if (!fish.isInWater()) {
                continue;
            }
            Player prey = nearestPrey(fish);
            if (prey == null) {
                continue;
            }
            Location mouth = fish.getLocation();
            Vector toPrey = prey.getLocation().add(0.0, 0.6, 0.0).toVector().subtract(mouth.toVector());
            double distance = toPrey.length();
            if (distance > 1.0E-3) {
                double speed = policy.huntSpeed(prey.getHealth(), maxHealth(prey));
                fish.setVelocity(toPrey.clone().normalize().multiply(Math.min(speed, distance)));
                Location facing = mouth.clone().setDirection(toPrey);
                fish.setRotation(facing.getYaw(), facing.getPitch());
            }
            long previous = lastBite.getOrDefault(entry.getKey(), Long.MIN_VALUE / 2);
            if (policy.canBite(distance, tick, previous)) {
                lastBite.put(entry.getKey(), tick);
                prey.damage(settings.damage(), fish);
                prey.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, prey.getLocation().add(0.0, 0.8, 0.0),
                    2, 0.2, 0.2, 0.2, 0.0);
            }
        }
    }

    void shutdown() {
        tracked.clear();
        lastBite.clear();
    }

    private Player nearestPrey(TropicalFish fish) {
        Player best = null;
        double bestDistance = settings.huntRange() * settings.huntRange();
        for (Player player : fish.getWorld().getPlayers()) {
            if (!hunts(player) || !player.isInWater()) {
                continue;
            }
            double distance = player.getLocation().distanceSquared(fish.getLocation());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private static boolean hunts(Player player) {
        GameMode mode = player.getGameMode();
        return !player.isDead() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE);
    }

    private int countNearby(Location at) {
        double radius = PiranhaSettings.NEARBY_RADIUS * PiranhaSettings.NEARBY_RADIUS;
        int count = 0;
        for (TropicalFish fish : tracked.values()) {
            if (fish.isValid() && fish.getWorld().equals(at.getWorld())
                && fish.getLocation().distanceSquared(at) <= radius) {
                count++;
            }
        }
        return count;
    }

    /** A water block 6-14 blocks from the player with water above it, or null. */
    private static Location findWater(Location around, ThreadLocalRandom random) {
        World world = around.getWorld();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double radius = random.nextDouble(6.0, 14.0);
            int x = around.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = around.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = around.getBlockY() + random.nextInt(-4, 3);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.WATER && block.getRelative(0, 1, 0).getType() == Material.WATER) {
                return block.getLocation().add(0.5, 0.5, 0.5);
            }
        }
        return null;
    }

    private static double maxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }
}
