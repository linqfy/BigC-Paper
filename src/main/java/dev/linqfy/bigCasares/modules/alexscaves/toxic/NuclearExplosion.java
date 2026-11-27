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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A nuclear explosion (mod {@code NuclearExplosionEntity}): a mushroom cloud ({@link MushroomCloud}), a crater carved
 * a few 16-block cubes per tick from the centre out, fire on the crater floor that goes out after
 * {@link NukeMath#FIRE_TICKS} ticks, and a blast that hurts, flings and
 * irradiates everything in a box 1.5 times the crater radius, every tick while the crater is carved.
 * Formulas in {@link NukeMath}.
 *
 * <p>Each cube's blocks go through a {@link BlockExplodeEvent} first, so protection plugins can trim them and the
 * module's own listener drops module blocks and releases magnets as for any explosion; containers spill their
 * contents. At most {@link #MAX_ACTIVE} explosions carve at once (later ones wait), and a running explosion
 * stops when its world unloads or the module shuts down ({@link #cancelAll()}).
 */
public final class NuclearExplosion implements Runnable {
    private static final int MIN_TICKS = 40;
    private static final int IRRADIATED_TICKS = 48000;
    static final int MAX_ACTIVE = 2;
    /** Played to everyone in the world when the bomb goes off, until the white screen is gone. */
    static final String DETONATION_SOUND = "alexscaves:nuclear_detonation";
    private static final List<NuclearExplosion> ACTIVE = new ArrayList<>();
    private static final Deque<NuclearExplosion> WAITING = new ArrayDeque<>();

    private final ModMobService mobs;
    private final Location center;
    private final float size;
    private final boolean griefing;
    private final int radius;
    private final Deque<int[]> cubes = new ArrayDeque<>();
    /** Fires lit on the crater floor, oldest first, with the tick they go out. */
    private final Deque<Fire> fires = new ArrayDeque<>();
    /** Whether the blast comes after the white screen and the chunk loading (the Nuclear Bomb) or at once. */
    private final boolean sequence;
    /** False while the screen is white and the crater's chunks load. */
    private boolean ignited;
    private int whiteTicks;
    private int fadeTicks;
    private NukeWhiteout whiteout;
    private List<int[]> toLoad = List.of();
    private int loadIndex;
    private final AtomicInteger pending = new AtomicInteger();
    /** Chunks held loaded for the blast, released when it ends. */
    private final List<ChunkRef> tickets = new ArrayList<>();
    private boolean finished;
    /** Chunks held by the running explosions, shared: two overlapping craters hold a chunk between them. */
    private static final ChunkHolds<ChunkRef> HOLDS = new ChunkHolds<>();

    private record ChunkRef(World world, int x, int z) {
    }

    private record Fire(Block block, int outAt) {
    }

    /** Puts a crater fire out, never loading a chunk for it. */
    private static void putOut(Block fire) {
        if (fire.getWorld().isChunkLoaded(fire.getX() >> 4, fire.getZ() >> 4) && fire.getType() == Material.FIRE) {
            fire.setType(Material.AIR, false);
        }
    }
    private int cubesPerTick = 3;
    private BukkitTask task;
    private int ticks;

    private NuclearExplosion(ModMobService mobs, Location center, float size, boolean griefing, boolean sequence) {
        this.mobs = mobs;
        this.sequence = sequence;
        this.center = center.clone();
        this.size = size;
        this.griefing = griefing;
        this.radius = NukeMath.radius(size);
    }

    /** Starts an explosion; {@code griefing} false leaves blocks alone (mod {@code noGriefing}). */
    public static void detonate(ModMobService mobs, Location center, float size, boolean griefing) {
        queue(new NuclearExplosion(mobs, center, size, griefing, false));
    }

    /**
     * The Nuclear Bomb's blast: the screen of everyone in the world goes white for {@link NukeSchedule#WHITE_HOLD_TICKS}
     * ticks while the chunks of the crater load a few at a time, and the blast starts as the white fades.
     */
    public static void detonateWithWarning(ModMobService mobs, Location center, float size) {
        queue(new NuclearExplosion(mobs, center, size, true, true));
    }

    private static void queue(NuclearExplosion explosion) {
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
        finished = true;
        if (task != null) {
            task.cancel();
        }
        if (whiteout != null) {
            whiteout.close();
            whiteout = null;
        }
        releaseTickets();
        if (!fires.isEmpty()) {
            // The fires still burning go out on time without keeping this explosion's slot.
            List<Fire> left = List.copyOf(fires);
            fires.clear();
            long delay = Math.max(1, left.get(left.size() - 1).outAt() - ticks);
            Runnable putOutLeft = () -> left.forEach(fire -> putOut(fire.block()));
            // At module shutdown the tasks are already closed, so the fires go out at once.
            mobs.tasks().later(delay, putOutLeft, putOutLeft);
        }
        ACTIVE.remove(this);
        NuclearExplosion next = WAITING.poll();
        if (next != null && next.center.isWorldLoaded()) {
            next.start();
        }
    }

    private void start() {
        ACTIVE.add(this);
        task = mobs.plugin().getServer().getScheduler().runTaskTimer(mobs.plugin(), this, 1L, 1L);
        if (sequence) {
            beginWhiteout();
        } else {
            ignite();
        }
    }

    /** The detonation: everyone in the world sees white, and the crater's chunks start to load. */
    private void beginWhiteout() {
        World world = center.getWorld();
        whiteout = new NukeWhiteout(mobs.plugin(), world);
        for (Player player : world.getPlayers()) {
            player.playSound(player, DETONATION_SOUND, SoundCategory.MASTER, 1.0f, 1.0f);
        }
        toLoad = NukeMath.craterChunks(center.getBlockX(), center.getBlockZ(), radius);
        whiteout.update(NukeSchedule.whiteRise(1));
    }

    /** One tick of the white screen: more chunks are requested, and the blast starts once they are in. */
    private void warmUp() {
        whiteTicks++;
        whiteout.update(NukeSchedule.whiteRise(whiteTicks));
        World world = center.getWorld();
        int perTick = NukeSchedule.preloadPerTick(toLoad.size());
        for (int i = 0; i < perTick && loadIndex < toLoad.size(); i++) {
            int[] chunk = toLoad.get(loadIndex++);
            pending.incrementAndGet();
            // Loaded off the main thread first, held after: a ticket on an unloaded chunk would load it at once, on
            // the main thread. Chunks that are not generated are left alone: there is nothing there to carve.
            world.getChunkAtAsync(chunk[0], chunk[1], false).whenComplete((loadedChunk, failure) -> {
                if (loadedChunk != null && !finished) {
                    hold(new ChunkRef(world, chunk[0], chunk[1]));
                }
                pending.decrementAndGet();
            });
        }
        boolean loaded = loadIndex >= toLoad.size() && pending.get() == 0;
        if ((whiteTicks >= NukeSchedule.WHITE_HOLD_TICKS && loaded) || whiteTicks >= NukeSchedule.WHITE_MAX_HOLD_TICKS) {
            ignite();
        }
    }

    private void hold(ChunkRef chunk) {
        HOLDS.hold(chunk, () -> chunk.world().addPluginChunkTicket(chunk.x(), chunk.z(), mobs.plugin()));
        tickets.add(chunk);
    }

    private void releaseTickets() {
        for (ChunkRef chunk : tickets) {
            HOLDS.release(chunk, () -> chunk.world().removePluginChunkTicket(chunk.x(), chunk.z(), mobs.plugin()));
        }
        tickets.clear();
    }

    /** The blast itself: sound, mushroom cloud and the crater to carve. */
    private void ignite() {
        ignited = true;
        World world = center.getWorld();
        String sound = size <= 1.5F ? "alexscaves:nuclear_explosion" : "alexscaves:large_nuclear_explosion";
        world.playSound(center, sound, SoundCategory.BLOCKS, 16.0f, 1.0f);
        world.playSound(center, "alexscaves:nuclear_explosion_rumble", SoundCategory.BLOCKS, 16.0f, 1.0f);
        mushroomCloud(world);
        MushroomCloud.show(mobs, center, size);
        if (griefing) {
            int chunks = NukeMath.chunksAffected(size);
            List<int[]> all = new ArrayList<>();
            for (int i = -chunks; i <= chunks; i++) {
                for (int j = -chunks; j <= chunks; j++) {
                    for (int k = -chunks; k <= chunks; k++) {
                        if (!NukeMath.cubeMayHoldCrater(i * 16, j * 16, k * 16, radius)) {
                            continue;
                        }
                        all.add(new int[] {center.getBlockX() + i * 16, center.getBlockY() + j * 16, center.getBlockZ() + k * 16});
                    }
                }
            }
            // Mod: nearest cubes first.
            all.sort(Comparator.comparingInt(cube -> Math.abs(cube[0] - center.getBlockX())
                + Math.abs(cube[1] - center.getBlockY()) + Math.abs(cube[2] - center.getBlockZ())));
            cubes.addAll(all);
            cubesPerTick = NukeMath.cubesPerTick(all.size());
        }
    }

    @Override
    public void run() {
        if (!center.isWorldLoaded()) {
            finish();
            return;
        }
        if (!ignited) {
            warmUp();
            return;
        }
        ticks++;
        if (whiteout != null) {
            // The white goes away fast once the blast has started.
            int alpha = NukeSchedule.whiteFade(fadeTicks++);
            whiteout.update(alpha);
            if (alpha == 0) {
                whiteout.close();
                whiteout = null;
                // The detonation sound lasts until the white is gone.
                for (Player player : center.getWorld().getPlayers()) {
                    player.stopSound(DETONATION_SOUND, SoundCategory.MASTER);
                }
            }
        }
        while (!fires.isEmpty() && fires.peek().outAt() <= ticks) {
            putOut(fires.poll().block());
        }
        if (ticks > MIN_TICKS && cubes.isEmpty()) {
            finish();
            return;
        }
        for (int i = 0; i < cubesPerTick && !cubes.isEmpty(); i++) {
            carve(cubes.poll());
        }
        if (ticks <= NukeMath.BLAST_TICKS) {
            blast();
        }
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
            // Fire only on the crater floor: the block below must stay (outside the crater) and be solid, or the
            // fire would float once the cube under it is carved. It goes out after a few seconds.
            Block below = block.getRelative(0, -1, 0);
            if (random.nextFloat() < 0.15F && below.getType().isSolid()
                && !NukeMath.inCrater(below.getX(), below.getY(), below.getZ(), cx, cy, cz, radius)) {
                block.setType(Material.FIRE, false);
                fires.add(new Fire(block, ticks + NukeMath.FIRE_TICKS));
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
                entity.setVelocity(away.normalize().multiply(Math.min(NukeMath.MAX_FLING, damage * 0.1F * playerFling)));
            }
            if (landed && (!NukeMath.RESISTS_RADIATION.contains(id) || "alexscaves:tremorzilla".equals(id))) {
                mobs.effects().apply(entity, ModEffectType.IRRADIATED, NukeMath.irradiatedAmplifier(size), IRRADIATED_TICKS);
            }
        }
    }
}
