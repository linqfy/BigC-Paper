package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The terrain a Primordial boss fight changes (mod {@code PrimalMagmaBlock} and {@code FissurePrimalMagmaBlock}):
 * while a boss is erupting the volcano's primal magma lights up, and the fissures its stomp and tephra open are lava-hot;
 * when the fight is over the light goes out and the fissures close back into the terrain they cut.
 *
 * <p>The mod does it with random ticks and scheduled ticks. Here the blocks changed are recorded per chunk (saved in the
 * chunk, so a restart in the middle of a fight still closes everything), the lighting is a random sample around each
 * erupting boss, and the closing is processed a few blocks a tick. Standing on magma (lit or fissure) burns, as the
 * mod's {@code entityInside} does for what is not sneaking.
 */
public final class BossTerrain implements Listener, Runnable, AutoCloseable {
    private static final Map<Plugin, BossTerrain> INSTANCES = new WeakHashMap<>();
    private static final NamespacedKey FISSURES = new NamespacedKey("bigcasares", "ac_boss_fissures");
    private static final NamespacedKey LIT = new NamespacedKey("bigcasares", "ac_boss_magma");
    /** Samples around an erupting boss per call (the mod's random tick of 3 per section, about half as many here). */
    static final int SAMPLES = 400;
    static final int SAMPLE_RADIUS = 44;
    static final int CLOSES_PER_TICK = 24;

    private final Plugin plugin;
    private final PrimordialBlocks blocks;
    private final Map<UUID, Map<Long, Set<Long>>> fissures = new HashMap<>();
    private final Map<UUID, Map<Long, Set<Long>>> lit = new HashMap<>();
    private long tick;
    /** Chunks changed this tick, written once at the end of it instead of once per block. */
    private final Map<UUID, Set<Long>> dirty = new HashMap<>();

    public BossTerrain(Plugin plugin, PrimordialBlocks blocks) {
        this.plugin = plugin;
        this.blocks = blocks;
        synchronized (INSTANCES) {
            INSTANCES.put(plugin, this);
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                load(chunk);
            }
        }
    }

    public static Optional<BossTerrain> get(Plugin plugin) {
        synchronized (INSTANCES) {
            return Optional.ofNullable(INSTANCES.get(plugin));
        }
    }

    private static long chunkKey(int x, int z) {
        return ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
    }

    void addFissure(Block block) {
        fissures.computeIfAbsent(block.getWorld().getUID(), id -> new HashMap<>())
            .computeIfAbsent(chunkKey(block.getX(), block.getZ()), key -> new HashSet<>())
            .add(SauropodRules.pack(block.getX(), block.getY(), block.getZ()));
        markDirty(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    /** Writes the chunk's records by the end of the tick, so a crash or an unclean stop cannot lose a fissure or a lit block. */
    private void markDirty(World world, int chunkX, int chunkZ) {
        dirty.computeIfAbsent(world.getUID(), id -> new HashSet<>()).add(chunkKey(chunkX << 4, chunkZ << 4));
    }

    private void flushDirty() {
        if (dirty.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Set<Long>> entry : dirty.entrySet()) {
            World world = plugin.getServer().getWorld(entry.getKey());
            if (world == null) {
                continue;
            }
            for (long key : entry.getValue()) {
                int chunkX = (int) (key >> 32);
                int chunkZ = (int) key;
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    save(world.getChunkAt(chunkX, chunkZ), false);
                }
            }
        }
        dirty.clear();
    }

    private void addLit(Block block) {
        lit.computeIfAbsent(block.getWorld().getUID(), id -> new HashMap<>())
            .computeIfAbsent(chunkKey(block.getX(), block.getZ()), key -> new HashSet<>())
            .add(SauropodRules.pack(block.getX(), block.getY(), block.getZ()));
        markDirty(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }

    // --- events ---

    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        load(event.getChunk());
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent event) {
        save(event.getChunk(), true);
    }

    private void load(Chunk chunk) {
        read(chunk, FISSURES, fissures);
        read(chunk, LIT, lit);
    }

    private void read(Chunk chunk, NamespacedKey key, Map<UUID, Map<Long, Set<Long>>> into) {
        long[] saved = chunk.getPersistentDataContainer().get(key, PersistentDataType.LONG_ARRAY);
        if (saved == null || saved.length == 0) {
            return;
        }
        Set<Long> set = into.computeIfAbsent(chunk.getWorld().getUID(), id -> new HashMap<>())
            .computeIfAbsent(chunkKey(chunk.getX() << 4, chunk.getZ() << 4), k -> new HashSet<>());
        for (long packed : saved) {
            set.add(packed);
        }
    }

    private void save(Chunk chunk, boolean forget) {
        write(chunk, FISSURES, fissures, forget);
        write(chunk, LIT, lit, forget);
    }

    private void write(Chunk chunk, NamespacedKey key, Map<UUID, Map<Long, Set<Long>>> from, boolean forget) {
        Map<Long, Set<Long>> world = from.get(chunk.getWorld().getUID());
        long chunkKey = chunkKey(chunk.getX() << 4, chunk.getZ() << 4);
        Set<Long> set = world == null ? null : world.get(chunkKey);
        if (set == null || set.isEmpty()) {
            chunk.getPersistentDataContainer().remove(key);
        } else {
            chunk.getPersistentDataContainer().set(key, PersistentDataType.LONG_ARRAY,
                set.stream().mapToLong(Long::longValue).toArray());
        }
        if (forget && world != null) {
            world.remove(chunkKey);
        }
    }

    // --- tick ---

    @Override
    public void run() {
        tick++;
        for (World world : plugin.getServer().getWorlds()) {
            boolean active = PrimordialBoss.active(world);
            if (active && tick % 2 == 0) {
                lightUp(world);
            }
            if (!active) {
                close(world);
            }
        }
        if (tick % 10 == 0) {
            burn();
        }
        flushDirty();
    }

    /** Mod random tick of {@code PrimalMagmaBlock}: with a boss erupting an inactive magma block lights up. */
    private void lightUp(World world) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (UUID id : PrimordialBoss.activeBosses()) {
            Entity boss = plugin.getServer().getEntity(id);
            if (boss == null || boss.getWorld() != world) {
                continue;
            }
            int bx = boss.getLocation().getBlockX();
            int bz = boss.getLocation().getBlockZ();
            for (int i = 0; i < SAMPLES; i++) {
                int x = bx + random.nextInt(SAMPLE_RADIUS * 2 + 1) - SAMPLE_RADIUS;
                int z = bz + random.nextInt(SAMPLE_RADIUS * 2 + 1) - SAMPLE_RADIUS;
                int y = Volcano.CENTER_Y + random.nextInt(100);
                if (!world.isChunkLoaded(x >> 4, z >> 4) || y < world.getMinHeight() || y >= world.getMaxHeight()) {
                    continue;
                }
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == Material.NOTE_BLOCK && blocks.primalMagma.equals(block.getBlockData())) {
                    block.setBlockData(blocks.bossMagma, false);
                    addLit(block);
                }
            }
        }
    }

    /** Mod: with no boss erupting, lit magma goes out and fissures close back into the terrain. */
    private void close(World world) {
        int budget = CLOSES_PER_TICK;
        budget = processLit(world, budget);
        processFissures(world, budget);
    }

    private int processLit(World world, int budget) {
        Map<Long, Set<Long>> chunks = lit.get(world.getUID());
        if (chunks == null) {
            return budget;
        }
        for (Map.Entry<Long, Set<Long>> entry : new ArrayList<>(chunks.entrySet())) {
            for (Long packed : new ArrayList<>(entry.getValue())) {
                if (budget <= 0) {
                    return 0;
                }
                int x = SauropodRules.unpackX(packed);
                int z = SauropodRules.unpackZ(packed);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    break;
                }
                Block block = world.getBlockAt(x, SauropodRules.unpackY(packed), z);
                if (blocks.bossMagma.equals(block.getBlockData())) {
                    block.setBlockData(blocks.primalMagma, false);
                }
                entry.getValue().remove(packed);
                markDirty(world, x >> 4, z >> 4);
                budget--;
            }
            if (entry.getValue().isEmpty()) {
                chunks.remove(entry.getKey());
            }
        }
        return budget;
    }

    private void processFissures(World world, int budget) {
        Map<Long, Set<Long>> chunks = fissures.get(world.getUID());
        if (chunks == null) {
            return;
        }
        for (Map.Entry<Long, Set<Long>> entry : new ArrayList<>(chunks.entrySet())) {
            for (Long packed : new ArrayList<>(entry.getValue())) {
                if (budget <= 0) {
                    return;
                }
                int x = SauropodRules.unpackX(packed);
                int z = SauropodRules.unpackZ(packed);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    break;
                }
                regenerate(world.getBlockAt(x, SauropodRules.unpackY(packed), z));
                entry.getValue().remove(packed);
                markDirty(world, x >> 4, z >> 4);
                budget--;
            }
            if (entry.getValue().isEmpty()) {
                chunks.remove(entry.getKey());
            }
        }
    }

    private static Chunk chunkOf(World world, long key) {
        int x = (int) (key >> 32);
        int z = (int) key;
        return world.isChunkLoaded(x, z) ? world.getChunkAt(x, z) : null;
    }

    /** Mod {@code FissurePrimalMagmaBlock.tick}: the column over a fissure fills back in with what lies next to it. */
    void regenerate(Block fissure) {
        int regen = blocks.fissureRegen(fissure.getBlockData());
        if (regen < 0) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < regen + 1; i++) {
            Block at = fissure.getRelative(0, i, 0);
            if (i != 0 && !at.getType().isAir()) {
                continue;
            }
            List<BlockData> neighbors = new ArrayList<>();
            boolean grass = false;
            boolean moss = false;
            boolean dirt = false;
            for (BlockFace face : new BlockFace[] {BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.WEST, BlockFace.EAST}) {
                Block next = at.getRelative(face);
                if (blocks.regenerates(next) && !blocks.isFissure(next.getBlockData())) {
                    neighbors.add(next.getBlockData());
                    grass |= next.getType() == Material.GRASS_BLOCK;
                    moss |= next.getType() == Material.MOSS_BLOCK;
                    dirt |= next.getType() == Material.DIRT;
                }
            }
            BlockData state = blocks.floodBasalt;
            if (!neighbors.isEmpty()) {
                state = grass ? PrimordialBlocks.vanilla(Material.GRASS_BLOCK)
                    : moss ? PrimordialBlocks.vanilla(Material.MOSS_BLOCK)
                    : dirt ? PrimordialBlocks.vanilla(Material.DIRT) : neighbors.get(random.nextInt(neighbors.size()));
            } else {
                Block below = fissure;
                while (below.getY() > fissure.getWorld().getMinHeight() && blocks.isFissure(below.getBlockData())) {
                    below = below.getRelative(BlockFace.DOWN);
                }
                if (blocks.regenerates(below)) {
                    state = below.getBlockData();
                }
            }
            at.setBlockData(state, false);
        }
        if (random.nextInt(2) == 0) {
            fissure.getWorld().playSound(fissure.getLocation(), "alexscaves:primal_magma_fissure_close", SoundCategory.BLOCKS,
                1.0F, 1.0F);
        }
    }

    /** Mod {@code entityInside} / {@code stepOn}: lit magma and fissures hurt (1) and burn (3 or 6 s) what is not sneaking. */
    private void burn() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Player player : world.getPlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR || player.isSneaking()) {
                    continue;
                }
                burnIfStanding(player);
            }
            if (PrimordialBoss.active(world)) {
                for (UUID id : PrimordialBoss.activeBosses()) {
                    Entity boss = plugin.getServer().getEntity(id);
                    if (boss != null && boss.getWorld() == world) {
                        for (Entity near : boss.getNearbyEntities(48, 48, 48)) {
                            if (near instanceof LivingEntity living && !(near instanceof Player) && !Luxtructosaurus.isBossPart(near)) {
                                burnIfStanding(living);
                            }
                        }
                    }
                }
            }
        }
    }

    private void burnIfStanding(LivingEntity entity) {
        if (entity.isInWater() || frostWalker(entity)) {
            return;
        }
        // Mod stepOn: every primal magma (lit or not), the volcanic core and the fissures burn what walks on them;
        // entityInside: lit magma and fissures also burn what stands in them.
        Block below = entity.getLocation().subtract(0, 0.2, 0).getBlock();
        Block feet = entity.getLocation().getBlock();
        int seconds = 0;
        if (below.getType() == Material.NOTE_BLOCK) {
            BlockData data = below.getBlockData();
            if (blocks.isFissure(data)) {
                seconds = 6;
            } else if (blocks.primalMagma.equals(data) || blocks.permanentMagma.equals(data) || blocks.bossMagma.equals(data)
                || blocks.volcanicCore.equals(data)) {
                seconds = 3;
            }
        }
        if (feet.getType() == Material.NOTE_BLOCK && blocks.burns(feet.getBlockData())) {
            seconds = Math.max(seconds, blocks.isFissure(feet.getBlockData()) ? 6 : 3);
        }
        if (seconds == 0) {
            return;
        }
        entity.damage(1.0, DamageSource.builder(DamageType.HOT_FLOOR).build());
        entity.setFireTicks(Math.max(entity.getFireTicks(), seconds * 20));
    }

    private static boolean frostWalker(LivingEntity entity) {
        org.bukkit.inventory.EntityEquipment equipment = entity.getEquipment();
        ItemStack boots = equipment == null ? null : equipment.getBoots();
        return boots != null && boots.getEnchantmentLevel(Enchantment.FROST_WALKER) > 0;
    }

    @Override
    public void close() {
        dirty.clear();
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                save(chunk, false);
            }
        }
        fissures.clear();
        lit.clear();
        PrimordialBoss.clearActive();
        synchronized (INSTANCES) {
            INSTANCES.remove(plugin, this);
        }
    }
}
