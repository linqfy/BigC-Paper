package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tephra (mod {@code TephraEntity} and {@code TephraExplosion}): the glowing rocks the volcano and the Luxtructosaurus
 * throw. A rock grows to its maximum scale while it flies (gravity 0.1, drag 0.9, ignoring blocks for its first 5 ticks)
 * and explodes where it hits a block or an entity: a radius of {@code 1 + scale} that blasts the blocks its rays cross,
 * hurts what it reaches and lights fires. The mod entity is a projectile; here it is a block display moved by this class
 * every tick (a handful at a time, capped), removed on shutdown.
 *
 * <p>Blocks go through {@link EntityExplodeEvent} so protection plugins see them; with mob griefing off the explosion
 * only hurts. Terrain that regenerates after the fight (stone, dirt, sand, the volcano's own blocks) turns into
 * fissure magma instead of air, as the mod's {@code regenerates_after_primordial_boss_fight} tag does.
 */
public final class Tephra implements Listener, Runnable, AutoCloseable {
    private static final Map<Plugin, Tephra> INSTANCES = new WeakHashMap<>();
    /** More than this many rocks in the air and the oldest vanish: nothing here reads the world without a cap. */
    static final int MAX_ROCKS = 96;
    /** A rock that has not hit anything after this many ticks is dropped (the mod discards it inside solid blocks). */
    static final int MAX_AGE = 600;
    private static final double SIZE = 0.6;

    private final Plugin plugin;
    private final PrimordialBlocks blocks;
    private final List<Rock> rocks = new ArrayList<>();

    public Tephra(Plugin plugin, PrimordialBlocks blocks) {
        this.plugin = plugin;
        this.blocks = blocks;
        synchronized (INSTANCES) {
            INSTANCES.put(plugin, this);
        }
    }

    public static Optional<Tephra> get(Plugin plugin) {
        synchronized (INSTANCES) {
            return Optional.ofNullable(INSTANCES.get(plugin));
        }
    }

    /** One rock in flight. */
    private static final class Rock {
        final BlockDisplay display;
        final UUID owner;
        final UUID arcTowards;
        final float maxScale;
        Vector position;
        Vector velocity;
        float scale = 0.1F;
        int age;
        int noPhysics = 5;

        Rock(BlockDisplay display, UUID owner, UUID arcTowards, float maxScale, Vector position, Vector velocity) {
            this.display = display;
            this.owner = owner;
            this.arcTowards = arcTowards;
            this.maxScale = maxScale;
            this.position = position;
            this.velocity = velocity;
        }
    }

    /**
     * Mod {@code Projectile.shoot}: the direction (x, y, z) normalised, nudged by a triangular spread of
     * {@code 0.0172275 * inaccuracy} and given the speed.
     */
    static Vector shoot(double x, double y, double z, float speed, float inaccuracy, Random random) {
        Vector direction = new Vector(x, y, z);
        if (direction.lengthSquared() < 1.0E-12) {
            return new Vector();
        }
        direction.normalize();
        double spread = 0.0172275 * inaccuracy;
        direction.add(new Vector((random.nextDouble() - random.nextDouble()) * spread,
            (random.nextDouble() - random.nextDouble()) * spread, (random.nextDouble() - random.nextDouble()) * spread));
        return direction.multiply(speed);
    }

    /** Throws a rock from a position with a velocity; {@code arcTowards} makes it curve toward that entity early on. */
    public void launch(Location at, Vector velocity, float maxScale, LivingEntity owner, UUID arcTowards) {
        World world = at.getWorld();
        if (world == null || !at.isChunkLoaded()) {
            return;
        }
        while (rocks.size() >= MAX_ROCKS) {
            remove(rocks.get(0));
        }
        BlockDisplay display = world.spawn(at, BlockDisplay.class, spawned -> {
            spawned.setBlock(Bukkit.createBlockData(Material.MAGMA_BLOCK));
            spawned.setPersistent(false);
            spawned.setTeleportDuration(1);
            spawned.setViewRange(8.0F);
            spawned.setTransformation(transformation(0.1F));
        });
        Rock rock = new Rock(display, owner == null ? null : owner.getUniqueId(), arcTowards, maxScale, at.toVector(),
            velocity.clone());
        rocks.add(rock);
        // Mod: the whistle plays once, higher for small rocks.
        world.playSound(at, "alexscaves:tephra_whistle", SoundCategory.HOSTILE, 8.0F,
            Math.max(0.5F, Math.min(2.0F, 2.0F - maxScale * 0.5F)));
    }

    private static Transformation transformation(float scale) {
        float half = scale * 0.5F;
        return new Transformation(new Vector3f(-half, -half, -half), new AxisAngle4f(), new Vector3f(scale, scale, scale),
            new AxisAngle4f());
    }

    int flying() {
        return rocks.size();
    }

    @Override
    public void run() {
        if (rocks.isEmpty()) {
            return;
        }
        for (Iterator<Rock> iterator = new ArrayList<>(rocks).iterator(); iterator.hasNext(); ) {
            Rock rock = iterator.next();
            if (!rock.display.isValid()) {
                rocks.remove(rock);
                continue;
            }
            try {
                tick(rock);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "[AlexsCaves] Falló una tephra", failure);
                remove(rock);
            }
        }
    }

    private void tick(Rock rock) {
        World world = rock.display.getWorld();
        rock.age++;
        rock.scale = SauropodRules.tephraScale(rock.scale, rock.maxScale);
        if (rock.arcTowards != null && rock.age > 3 && rock.age < 20) {
            Entity target = plugin.getServer().getEntity(rock.arcTowards);
            if (target != null && target.getWorld() == world) {
                Vector toward = target.getLocation().toVector().add(new Vector(0, 0.3 * target.getHeight(), 0))
                    .subtract(rock.position);
                if (toward.length() > 1.5) {
                    rock.velocity.add(toward.normalize().multiply(0.1));
                }
            }
        }
        Vector from = rock.position.clone();
        Vector to = from.clone().add(rock.velocity);
        if (!world.isChunkLoaded(to.getBlockX() >> 4, to.getBlockZ() >> 4)) {
            remove(rock);
            return;
        }
        Location here = to.toLocation(world);
        // Mod: with no air in its box where it stands (and not in water) it is discarded.
        if (solidAround(from.toLocation(world)) && !from.toLocation(world).getBlock().isLiquid()) {
            remove(rock);
            return;
        }
        rock.position = to;
        rock.velocity.multiply(0.9);
        rock.velocity.setY(rock.velocity.getY() - 0.1);
        // The step is ray traced from where it was: it moves 5 to 7 blocks a tick, so the end of the step is often
        // well inside the rock or past it. It explodes at the first thing the step meets.
        Vector impact = null;
        Vector step = to.clone().subtract(from);
        double length = step.length();
        if (rock.noPhysics > 0) {
            rock.noPhysics--;
        } else if (length > 1.0E-6) {
            Vector direction = step.clone().normalize();
            RayTraceResult blockHit = world.rayTraceBlocks(from.toLocation(world), direction, length, FluidCollisionMode.NEVER,
                true);
            RayTraceResult entityHit = world.rayTraceEntities(from.toLocation(world), direction, length, SIZE * 0.5,
                candidate -> hittable(candidate, rock));
            double blockAt = blockHit == null ? Double.MAX_VALUE : blockHit.getHitPosition().distanceSquared(from);
            double entityAt = entityHit == null ? Double.MAX_VALUE : entityHit.getHitPosition().distanceSquared(from);
            if (blockHit != null || entityHit != null) {
                impact = (blockAt <= entityAt ? blockHit : entityHit).getHitPosition();
            }
        }
        rock.display.setTransformation(transformation(rock.scale));
        if (impact != null) {
            Location at = impact.toLocation(world);
            rock.display.teleport(at);
            explode(rock, at);
            remove(rock);
            return;
        }
        rock.display.teleport(here);
        if (rock.age > MAX_AGE) {
            remove(rock);
        }
    }

    private boolean hittable(Entity candidate, Rock rock) {
        if (candidate == rock.display || candidate instanceof Interaction || candidate instanceof BlockDisplay
            || !(candidate instanceof LivingEntity living) || living.isDead()) {
            return false;
        }
        if (rock.owner != null && candidate.getUniqueId().equals(rock.owner)) {
            return false;
        }
        return !(candidate instanceof Player player && player.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            && !isBoss(candidate);
    }

    private static boolean isBoss(Entity entity) {
        return Luxtructosaurus.isBossPart(entity);
    }

    private static boolean solidAround(Location at) {
        double half = SIZE * 0.5;
        for (int x = (int) Math.floor(at.getX() - half); x <= (int) Math.floor(at.getX() + half); x++) {
            for (int y = (int) Math.floor(at.getY() - half); y <= (int) Math.floor(at.getY() + half); y++) {
                for (int z = (int) Math.floor(at.getZ() - half); z <= (int) Math.floor(at.getZ() + half); z++) {
                    if (at.getWorld().getBlockAt(x, y, z).getType().isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void remove(Rock rock) {
        rocks.remove(rock);
        if (rock.display.isValid()) {
            rock.display.remove();
        }
    }

    // --- explosion ---

    private void explode(Rock rock, Location at) {
        World world = at.getWorld();
        float radius = SauropodRules.tephraRadius(rock.maxScale);
        Entity owner = rock.owner == null ? null : plugin.getServer().getEntity(rock.owner);
        boolean griefing = !Boolean.FALSE.equals(world.getGameRuleValue(GameRules.MOB_GRIEFING));
        boolean decay = !Boolean.FALSE.equals(world.getGameRuleValue(GameRules.BLOCK_EXPLOSION_DROP_DECAY));
        Random random = ThreadLocalRandom.current();
        List<Block> blown = new ArrayList<>();
        if (griefing) {
            java.util.Set<Long> positions = SauropodRules.tephraBlocks(at.getX(), at.getY(), at.getZ(), radius, random,
                (x, y, z) -> {
                    if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
                        return Float.NaN;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (PrimordialBlocks.unmovable(type)) {
                        return Float.NaN;
                    }
                    return block.isLiquid() ? -0.3F : type.isAir() ? 0.0F : type.getBlastResistance();
                });
            for (long packed : positions) {
                blown.add(world.getBlockAt(SauropodRules.unpackX(packed), SauropodRules.unpackY(packed),
                    SauropodRules.unpackZ(packed)));
            }
            Collections.shuffle(blown, random);
        }
        damageEntities(rock, at, radius, owner);
        world.playSound(at, "alexscaves:tephra_hit", SoundCategory.BLOCKS, 4.0F,
            (1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F) * 0.7F);
        int particles = (int) (5.0F + radius * 10.0F);
        world.spawnParticle(Particle.LAVA, at, particles, radius * 0.5, radius * 0.5, radius * 0.5, 0.0);
        world.spawnParticle(Particle.LARGE_SMOKE, at, particles / 2, radius * 0.5, radius * 0.5, radius * 0.5, 0.02);
        if (!griefing || blown.isEmpty()) {
            return;
        }
        EntityExplodeEvent event = new EntityExplodeEvent(rock.display, at, blown, 1.0F / radius,
            decay ? org.bukkit.ExplosionResult.DESTROY_WITH_DECAY : org.bukkit.ExplosionResult.DESTROY);
        if (!event.callEvent()) {
            return;
        }
        float yield = decay ? event.getYield() : 1.0F;
        List<Block> toBlow = new ArrayList<>(event.blockList());
        for (Block block : toBlow) {
            Material type = block.getType();
            if (type.isAir() || block.isLiquid() || blocks.isFissure(block.getBlockData())) {
                // Mod: fluids and fissure magma stay.
                continue;
            }
            if (blocks.regenerates(block)) {
                // Mod: no drops, the block becomes fissure magma that regenerates once the fight is over.
                block.setBlockData(blocks.fissure[0], false);
                BossTerrain.get(plugin).ifPresent(terrain -> terrain.addFissure(block));
            } else if (random.nextFloat() < yield) {
                block.breakNaturally();
            } else {
                block.setType(Material.AIR, false);
            }
        }
        // Mod: a third of the blown spots that are now air over a solid block catch fire.
        for (Block block : toBlow) {
            if (random.nextInt(3) == 0 && block.getType().isAir() && block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                BlockIgniteEvent ignite = new BlockIgniteEvent(block, BlockIgniteEvent.IgniteCause.EXPLOSION, owner);
                if (ignite.callEvent()) {
                    block.setType(Material.FIRE, true);
                }
            }
        }
    }

    private void damageEntities(Rock rock, Location at, float radius, Entity owner) {
        World world = at.getWorld();
        float reach = radius * 2.0F;
        DamageSource source = DamageSource.builder(DamageType.EXPLOSION).withDirectEntity(rock.display)
            .withCausingEntity(owner == null ? rock.display : owner).build();
        for (Entity entity : world.getNearbyEntities(at, reach + 1, reach + 1, reach + 1)) {
            if (!(entity instanceof LivingEntity living) || living.isGliding() || isBoss(entity)
                || entity instanceof Interaction) {
                continue;
            }
            Location eyes = living.getEyeLocation();
            double d12 = Math.sqrt(living.getLocation().distanceSquared(at)) / reach;
            if (d12 > 1.0) {
                continue;
            }
            Vector away = eyes.toVector().subtract(at.toVector());
            double distance = away.length();
            if (distance < 1.0E-6) {
                continue;
            }
            away.multiply(1.0 / distance);
            // Mod getSeenPercent: the share of the box that sees the blast; here the eyes and the feet.
            double seen = (visible(at, eyes) ? 0.5 : 0.0) + (visible(at, living.getLocation().add(0, 0.2, 0)) ? 0.5 : 0.0);
            double d10 = (1.0 - d12) * seen;
            living.damage(SauropodRules.tephraDamage(d12, seen, radius), source);
            double dampened = d10;
            AttributeInstance resistance = living.getAttribute(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE);
            if (resistance != null) {
                dampened = d10 * (1.0 - resistance.getValue());
            }
            living.setVelocity(living.getVelocity().add(away.multiply(dampened)));
        }
    }

    private static boolean visible(Location from, Location to) {
        Vector line = to.toVector().subtract(from.toVector());
        double length = line.length();
        return length < 1.0E-3 || from.getWorld().rayTraceBlocks(from, line.normalize(), length, FluidCollisionMode.NEVER, true)
            == null;
    }

    @Override
    public void close() {
        for (Rock rock : new ArrayList<>(rocks)) {
            remove(rock);
        }
        synchronized (INSTANCES) {
            INSTANCES.remove(plugin, this);
        }
    }

    BlockData fissure(int regen) {
        return blocks.fissure[Math.max(0, Math.min(blocks.fissure.length - 1, regen))];
    }
}
