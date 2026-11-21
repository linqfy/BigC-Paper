package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.damage.ModDamageTypes;
import dev.linqfy.bigCasares.modules.alexscaves.core.effect.ModEffectType;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A nuclear explosion (mod {@code NuclearExplosionEntity}): a mushroom cloud, a crater carved a few
 * 16-block cubes per tick from the centre out, fire on the crater floor, and a blast that hurts, flings and
 * irradiates everything in a box 1.5 times the crater radius, every tick while the crater is carved.
 * Formulas in {@link NukeMath}.
 *
 * <p>Each cube's blocks go through a {@link BlockExplodeEvent} first, so protection plugins can trim them and the
 * module's own listener drops module blocks and releases magnets as for any explosion; containers spill their
 * contents. At most {@link #MAX_ACTIVE} explosions carve at once (later ones wait), and a running explosion
 * stops when its world unloads or the module shuts down ({@link #cancelAll()}).
 */
public final class NuclearExplosion implements Runnable {
    private static final int CUBES_PER_TICK = 3;
    private static final int MIN_TICKS = 40;
    private static final int IRRADIATED_TICKS = 48000;
    static final int MAX_ACTIVE = 2;
    private static final List<NuclearExplosion> ACTIVE = new ArrayList<>();
    private static final Deque<NuclearExplosion> WAITING = new ArrayDeque<>();

    private final ModMobService mobs;
    private final Location center;
    private final float size;
    private final boolean griefing;
    private final int radius;
    private final Deque<int[]> cubes = new ArrayDeque<>();
    private BukkitTask task;
    private int ticks;

    private NuclearExplosion(ModMobService mobs, Location center, float size, boolean griefing) {
        this.mobs = mobs;
        this.center = center.clone();
        this.size = size;
        this.griefing = griefing;
        this.radius = NukeMath.radius(size);
    }

    /** Starts an explosion; {@code griefing} false leaves blocks alone (mod {@code noGriefing}). */
    public static void detonate(ModMobService mobs, Location center, float size, boolean griefing) {
        NuclearExplosion explosion = new NuclearExplosion(mobs, center, size, griefing);
        if (ACTIVE.size() >= MAX_ACTIVE) {
            WAITING.add(explosion);
            return;
        }
        explosion.start();
    }

    /** Stops every running and waiting explosion (module shutdown). */
    public static void cancelAll() {
        WAITING.clear();
        for (NuclearExplosion explosion : List.copyOf(ACTIVE)) {
            explosion.finish();
        }
    }

    private void finish() {
        if (task != null) {
            task.cancel();
        }
        ACTIVE.remove(this);
        NuclearExplosion next = WAITING.poll();
        if (next != null && next.center.isWorldLoaded()) {
            next.start();
        }
    }

    private void start() {
        ACTIVE.add(this);
        World world = center.getWorld();
        String sound = size <= 1.5F ? "alexscaves:nuclear_explosion" : "alexscaves:large_nuclear_explosion";
        world.playSound(center, sound, SoundCategory.BLOCKS, 16.0f, 1.0f);
        world.playSound(center, "alexscaves:nuclear_explosion_rumble", SoundCategory.BLOCKS, 16.0f, 1.0f);
        mushroomCloud(world);
        if (griefing) {
            int chunks = NukeMath.chunksAffected(size);
            List<int[]> all = new ArrayList<>();
            for (int i = -chunks; i <= chunks; i++) {
                for (int j = -chunks; j <= chunks; j++) {
                    for (int k = -chunks; k <= chunks; k++) {
                        all.add(new int[] {center.getBlockX() + i * 16, center.getBlockY() + j * 16, center.getBlockZ() + k * 16});
                    }
                }
            }
            // Mod: nearest cubes first.
            all.sort(Comparator.comparingInt(cube -> Math.abs(cube[0] - center.getBlockX())
                + Math.abs(cube[1] - center.getBlockY()) + Math.abs(cube[2] - center.getBlockZ())));
            cubes.addAll(all);
        }
        task = mobs.plugin().getServer().getScheduler().runTaskTimer(mobs.plugin(), this, 1L, 1L);
    }

    @Override
    public void run() {
        ticks++;
        if (!center.isWorldLoaded() || ticks > MIN_TICKS && cubes.isEmpty()) {
            finish();
            return;
        }
        for (int i = 0; i < CUBES_PER_TICK && !cubes.isEmpty(); i++) {
            carve(cubes.poll());
        }
        blast();
    }

    private void mushroomCloud(World world) {
        Location ground = center.clone();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, ground, 6 + (int) (size * 4), radius * 0.3, 2, radius * 0.3, 0, null, true);
        world.spawnParticle(Particle.FLASH, ground, 3, 0, 0, 0, 0, org.bukkit.Color.WHITE, true);
        int stem = (int) (radius * 1.2);
        for (int y = 0; y < stem; y += 2) {
            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, ground.clone().add(0, y, 0), 6, 1.5, 0.5, 1.5, 0.02, null, true);
        }
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, ground.clone().add(0, stem, 0), 120, radius * 0.4, 3, radius * 0.4,
            0.03, null, true);
        world.spawnParticle(Particle.LAVA, ground.clone().add(0, stem * 0.5, 0), 60, radius * 0.2, stem * 0.3, radius * 0.2, 0, null, true);
    }

    private void carve(int[] corner) {
        World world = center.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float dropChance = NukeMath.dropChance(size);
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        List<Block> affected = new ArrayList<>();
        for (int x = corner[0]; x < corner[0] + 16; x++) {
            for (int z = corner[2]; z < corner[2] + 16; z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = corner[1] + 15; y >= corner[1]; y--) {
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()
                        || !NukeMath.inCrater(x, y, z, cx, cy, cz, radius)) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (!type.isAir() && destroyable(type)) {
                        affected.add(block);
                    }
                }
            }
        }
        // Mod: a tremorzilla egg in the crater hatches instead of being destroyed.
        dev.linqfy.bigCasares.modules.alexscaves.primordial.Tremorzilla.hatchEggsInCrater(mobs, world,
            new BoundingBox(corner[0], corner[1], corner[2], corner[0] + 16, corner[1] + 16, corner[2] + 16),
            block -> NukeMath.inCrater(block.getX(), block.getY(), block.getZ(), cx, cy, cz, radius));
        if (affected.isEmpty()) {
            return;
        }
        Block origin = center.getBlock();
        BlockExplodeEvent event = new BlockExplodeEvent(origin, origin.getState(), affected, dropChance, ExplosionResult.DESTROY);
        if (!event.callEvent()) {
            return;
        }
        for (Block block : event.blockList()) {
            if (block.getType().isAir()) {
                continue;
            }
            if (random.nextFloat() < event.getYield() && !block.isLiquid()) {
                block.breakNaturally();
            } else {
                if (block.getState() instanceof Container container) {
                    for (ItemStack item : container.getInventory().getContents()) {
                        if (item != null && !item.isEmpty()) {
                            world.dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), item);
                        }
                    }
                    container.getInventory().clear();
                }
                block.setType(Material.AIR, false);
            }
            if (random.nextFloat() < 0.15F && !block.getRelative(0, -1, 0).getType().isAir()) {
                block.setType(Material.FIRE, false);
            }
        }
    }

    private static boolean destroyable(Material type) {
        return type.getBlastResistance() < NukeMath.MAX_BLOCK_RESISTANCE && type != Material.BEDROCK
            && type != Material.END_PORTAL_FRAME && type != Material.BARRIER && type != Material.COMMAND_BLOCK;
    }

    private void blast() {
        World world = center.getWorld();
        float fling = size * 0.33F;
        BoundingBox box = BoundingBox.of(center, radius * 1.5 + 0.5, radius * 0.6 + 0.5, radius * 1.5 + 0.5);
        for (var nearby : world.getNearbyEntities(box, entity -> entity instanceof LivingEntity)) {
            LivingEntity entity = (LivingEntity) nearby;
            if (entity instanceof Player player && (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                continue;
            }
            float damage = NukeMath.damage(size, (float) entity.getLocation().distance(center));
            float playerFling = entity instanceof Player ? 0.5F * fling : fling;
            String id = mobs.typeOf(entity).map(type -> "alexscaves:" + type.id()).orElse("");
            if ("alexscaves:raycat".equals(id)) {
                damage = 0.0F;
            } else if ("alexscaves:tremorzilla".equals(id)) {
                // Mod: unhurt and not flung; the radiation below heals it.
                damage = 0.0F;
                playerFling = 0.0F;
            } else if (NukeMath.RESISTS_RADIATION.contains(id)) {
                damage *= 0.25F;
                playerFling *= 0.1F;
            }
            // A blast that a zone or claim cancelled does not irradiate either; no damage at all (Tremorzilla) always does.
            boolean landed = true;
            if (damage > 0.0F) {
                double before = DamageProbe.pool(entity);
                entity.damage(damage, mobs.damageTypes().source(ModDamageTypes.NUKE));
                landed = DamageProbe.landed(before, DamageProbe.pool(entity), entity.isDead());
            }
            Vector away = entity.getLocation().toVector().subtract(center.toVector()).add(new Vector(0, 0.3, 0));
            if (away.lengthSquared() > 1.0E-6) {
                entity.setVelocity(away.normalize().multiply(damage * 0.1F * playerFling));
            }
            if (landed && (!NukeMath.RESISTS_RADIATION.contains(id) || "alexscaves:tremorzilla".equals(id))) {
                mobs.effects().apply(entity, ModEffectType.IRRADIATED, NukeMath.irradiatedAmplifier(size), IRRADIATED_TICKS);
            }
        }
    }
}
