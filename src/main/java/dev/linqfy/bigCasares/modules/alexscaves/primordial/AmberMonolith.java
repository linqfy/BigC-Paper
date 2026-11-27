package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.Entry;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.BiomeSpawnTable.SpawnCategory;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.ModSpawnTables;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Amber Monolith (mod {@code AmberMonolithBlock} and {@code AmberMonolithBlockEntity}): a block that, now and then,
 * conjures a group of the local cave creatures around itself when a player is near. It picks the mob a while before and
 * only picks one that has no live kin within 64 blocks (up to 4 to 11 tries); the mean wait is 32000 ticks. For a day
 * after the Luxtructosaurus first falls it brings the Atlatitans (the "migration").
 *
 * <p>The mod's block entity is a chunk-saved record here (the mob, the group size and the countdown). Differences: the
 * mod shows the next mob inside the amber; here it only spawns. The Primordial Caves' creature list is the module's
 * Primordial table plus the frog, as in the mod's biome; elsewhere a monolith conjures the usual farm animals.
 */
public final class AmberMonolith implements Listener, Runnable, AutoCloseable {
    static final NamespacedKey CHUNK_KEY = new NamespacedKey("bigcasares", "ac_amber_monoliths");
    /** Mod config {@code amber_monolith_mean_time}. */
    static final int MEAN_TIME = 32000;
    static final int NEAR_PLAYER = 28;
    static final int CHECK_RANGE = 64;
    /** The update runs this many ticks at a time (the mod's block entity ticks every tick). */
    static final int STEP = 10;

    private final ModMobService mobs;
    private final Plugin plugin;
    private final PrimordialBlocks blocks;
    private final Random random = new Random();
    private final Map<Key, Monolith> monoliths = new LinkedHashMap<>();
    private long tick;

    record Key(UUID world, int x, int y, int z) {
    }

    /** The block entity's saved fields. */
    static final class Monolith {
        String spawnType;
        int spawnCount;
        int spawnsMobIn;
        int findSpawnsCooldown;
        boolean donePostBossSpawn;
    }

    public AmberMonolith(ModMobService mobs, PrimordialBlocks blocks) {
        this.mobs = mobs;
        this.plugin = mobs.plugin();
        this.blocks = blocks;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                load(chunk);
            }
        }
    }

    // --- pure rules ---

    /** Mod {@code generateSpawnData}: ticks until the next conjuring, {@code i / 2 + nextInt(i)}. */
    static int nextDelay(int meanTime, Random random) {
        int i = Math.max(1000, meanTime);
        return i / 2 + random.nextInt(i);
    }

    /** Mod: group size between the entry's minimum and maximum. */
    static int groupSize(Entry entry, Random random) {
        int range = Math.max(entry.maxCount() - entry.minCount(), 0);
        return range <= 0 ? entry.minCount() : random.nextInt(range) + entry.minCount();
    }

    /**
     * Mod {@code getDepopulatedEntitySpawnData}: roll up to {@code rolls} times and keep the first pick that is not blocked;
     * the last pick is kept even when it is (a quirk of the mod).
     */
    static Entry depopulated(java.util.function.Supplier<Entry> pick, java.util.function.Predicate<Entry> blocked, int rolls) {
        Entry chosen = null;
        for (int roll = 0; roll < rolls; roll++) {
            if (chosen != null && !blocked.test(chosen)) {
                return chosen;
            }
            chosen = pick.get();
        }
        return chosen;
    }

    // --- registry ---

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        load(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        save(chunk);
        monoliths.keySet().removeIf(key -> key.world().equals(chunk.getWorld().getUID()) && key.x() >> 4 == chunk.getX()
            && key.z() >> 4 == chunk.getZ());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.NOTE_BLOCK && blocks.amberMonolith.equals(block.getBlockData())) {
            monoliths.computeIfAbsent(keyOf(block), ignored -> new Monolith());
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), "alexscaves:amber_monolith_place",
                SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static Key keyOf(Block block) {
        return new Key(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private void load(Chunk chunk) {
        String text = chunk.getPersistentDataContainer().get(CHUNK_KEY, PersistentDataType.STRING);
        if (text != null) {
            for (String line : text.split("\n")) {
                String[] parts = line.split(";");
                if (parts.length < 7) {
                    continue;
                }
                try {
                    Key key = new Key(chunk.getWorld().getUID(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]));
                    Monolith monolith = new Monolith();
                    monolith.spawnType = "-".equals(parts[3]) ? null : parts[3];
                    monolith.spawnCount = Integer.parseInt(parts[4]);
                    monolith.spawnsMobIn = Integer.parseInt(parts[5]);
                    monolith.donePostBossSpawn = Boolean.parseBoolean(parts[6]);
                    monoliths.put(key, monolith);
                } catch (RuntimeException invalid) {
                    plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Monolito de ámbar con datos inválidos en el chunk "
                        + chunk.getX() + "," + chunk.getZ(), invalid);
                }
            }
        }
        // Generated monoliths: the world generation marked where they stand.
        for (int[] box : StructureMarks.boxes(chunk, StructureMarks.Kind.AMBER_MONOLITH)) {
            Key key = new Key(chunk.getWorld().getUID(), box[0], box[1], box[2]);
            if (!monoliths.containsKey(key)) {
                Block block = chunk.getWorld().getBlockAt(box[0], box[1], box[2]);
                if (block.getType() == Material.NOTE_BLOCK && blocks.amberMonolith.equals(block.getBlockData())) {
                    monoliths.put(key, new Monolith());
                }
            }
        }
    }

    private void save(Chunk chunk) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<Key, Monolith> entry : monoliths.entrySet()) {
            Key key = entry.getKey();
            if (key.world().equals(chunk.getWorld().getUID()) && key.x() >> 4 == chunk.getX() && key.z() >> 4 == chunk.getZ()) {
                Monolith monolith = entry.getValue();
                text.append(key.x()).append(';').append(key.y()).append(';').append(key.z()).append(';')
                    .append(monolith.spawnType == null ? "-" : monolith.spawnType).append(';').append(monolith.spawnCount)
                    .append(';').append(monolith.spawnsMobIn).append(';').append(monolith.donePostBossSpawn).append('\n');
            }
        }
        if (text.isEmpty()) {
            chunk.getPersistentDataContainer().remove(CHUNK_KEY);
        } else {
            chunk.getPersistentDataContainer().set(CHUNK_KEY, PersistentDataType.STRING, text.toString());
        }
    }

    // --- tick ---

    @Override
    public void run() {
        tick++;
        if (tick % STEP != 0 || monoliths.isEmpty()) {
            return;
        }
        for (Map.Entry<Key, Monolith> entry : new ArrayList<>(monoliths.entrySet())) {
            World world = plugin.getServer().getWorld(entry.getKey().world());
            Key key = entry.getKey();
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (block.getType() != Material.NOTE_BLOCK || !blocks.amberMonolith.equals(block.getBlockData())) {
                monoliths.remove(key);
                save(world.getChunkAt(key.x() >> 4, key.z() >> 4));
                continue;
            }
            try {
                update(world, block, entry.getValue());
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "[AlexsCaves] Falló un monolito de ámbar", failure);
            }
        }
    }

    /** Mod {@code AmberMonolithBlockEntity.tick}, {@link #STEP} ticks at a time. */
    private void update(World world, Block block, Monolith monolith) {
        boolean migration = PrimordialBoss.migration(world);
        if (monolith.spawnType == null && (monolith.findSpawnsCooldown -= STEP) <= 0
            || !monolith.donePostBossSpawn && migration) {
            monolith.findSpawnsCooldown = 40 + random.nextInt(50);
            generate(world, block, monolith, migration);
            if (!monolith.donePostBossSpawn && migration) {
                monolith.donePostBossSpawn = true;
                monolith.spawnsMobIn = (int) Math.ceil(monolith.spawnsMobIn * 0.25F) + 100;
            }
        }
        if (monolith.spawnsMobIn <= 0) {
            if (monolith.spawnType == null) {
                generate(world, block, monolith, migration);
            } else if (playerNear(block) && conjure(world, block, monolith)) {
                world.playSound(block.getLocation().add(0.5, 0.5, 0.5), "alexscaves:amber_monolith_summon",
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
                generate(world, block, monolith, migration);
            }
        }
        if (monolith.spawnsMobIn > 0) {
            monolith.spawnsMobIn -= STEP;
        }
    }

    private boolean playerNear(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Player player : block.getWorld().getPlayers()) {
            if (player.getGameMode() != GameMode.SPECTATOR && player.getLocation().distanceSquared(center)
                <= NEAR_PLAYER * NEAR_PLAYER) {
                return true;
            }
        }
        return false;
    }

    // --- choosing ---

    private List<Entry> creatures(Block block) {
        NamespacedKey biome = block.getBiome().getKey();
        boolean primordial = biome.getNamespace().equals(CaveBiomeId.NAMESPACE) && biome.getKey().equals("primordial_caves");
        List<Entry> list = new ArrayList<>();
        if (primordial || StructureMarks.inside(block.getLocation(), StructureMarks.Kind.AMBER_MONOLITH)) {
            for (Entry entry : ModSpawnTables.all(CaveBiomeId.PRIMORDIAL_CAVES)) {
                if (entry.category() == SpawnCategory.CAVE_CREATURE && mobs.knownIds().contains(entry.mobId())) {
                    list.add(entry);
                }
            }
            list.add(new Entry("minecraft:frog", SpawnCategory.CAVE_CREATURE, 7, 1, 2));
        } else {
            list.add(new Entry("minecraft:cow", SpawnCategory.CAVE_CREATURE, 8, 4, 4));
            list.add(new Entry("minecraft:sheep", SpawnCategory.CAVE_CREATURE, 12, 4, 4));
            list.add(new Entry("minecraft:pig", SpawnCategory.CAVE_CREATURE, 10, 4, 4));
            list.add(new Entry("minecraft:chicken", SpawnCategory.CAVE_CREATURE, 10, 4, 4));
        }
        return list;
    }

    /** Mod {@code isEntitySpawnBlocked}. */
    private boolean blocked(Entry entry, Block block, boolean defeated) {
        if (entry.mobId().equals(Atlatitan.ATLATITAN.id()) && !defeated) {
            return true;
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (Entity near : block.getWorld().getNearbyEntities(center, CHECK_RANGE, CHECK_RANGE, CHECK_RANGE)) {
            if (near instanceof LivingEntity living && living.isValid() && !living.isDead() && matches(entry, living)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(Entry entry, LivingEntity living) {
        if (entry.mobId().startsWith("minecraft:")) {
            return living.getType().getKey().toString().equals(entry.mobId());
        }
        return mobs.typeOf(living).map(type -> type.id().equals(entry.mobId())).orElse(false);
    }

    private void generate(World world, Block block, Monolith monolith, boolean migration) {
        List<Entry> list = creatures(block);
        if (migration && !monolith.donePostBossSpawn) {
            List<Entry> forced = list.stream().filter(entry -> entry.mobId().equals(Atlatitan.ATLATITAN.id())).toList();
            if (!forced.isEmpty()) {
                list = forced;
            }
        }
        BiomeSpawnTable table = new BiomeSpawnTable(list);
        boolean defeated = PrimordialBoss.defeatedOnce();
        Entry chosen = depopulated(() -> table.pick(random).orElse(null), entry -> blocked(entry, block, defeated),
            4 + random.nextInt(8));
        if (chosen != null) {
            monolith.spawnType = chosen.mobId();
            monolith.spawnCount = groupSize(chosen, random);
        }
        monolith.spawnsMobIn = nextDelay(MEAN_TIME, random);
        save(world.getChunkAt(block.getX() >> 4, block.getZ() >> 4));
    }

    // --- conjuring ---

    /** Mod {@code spawnMobs}: each member of the group tries 6 spots around the monolith. */
    private boolean conjure(World world, Block block, Monolith monolith) {
        boolean spawned = false;
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (int i = 0; i < monolith.spawnCount; i++) {
            for (int attempt = 0; attempt < 6; attempt++) {
                Location spot = randomSpot(world, block, monolith.spawnType);
                if (spot == null || !fits(spot, monolith.spawnType)) {
                    continue;
                }
                Entity entity = spawn(monolith.spawnType, spot);
                if (entity == null) {
                    continue;
                }
                spawned = true;
                Vector line = entity.getLocation().add(0, entity.getHeight() * 0.8, 0).toVector().subtract(center.toVector());
                int steps = Math.min(80, (int) (line.length() * 1.5));
                for (int step = 0; step < steps; step++) {
                    Location at = center.clone().add(line.clone().multiply((double) step / steps)).add(random.nextFloat() - 0.5,
                        random.nextFloat() - 0.5, random.nextFloat() - 0.5);
                    world.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(255, 176, 40), 1.0F));
                }
                world.spawnParticle(Particle.DUST, entity.getLocation().add(0, entity.getHeight() * 0.5, 0), 5, 0.5, 0.5, 0.5, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 200, 80), 1.5F));
                break;
            }
        }
        return spawned;
    }

    private Location randomSpot(World world, Block monolith, String type) {
        boolean caveCreature = !type.startsWith("minecraft:cow") && !type.startsWith("minecraft:sheep")
            && !type.startsWith("minecraft:pig") && !type.startsWith("minecraft:chicken");
        for (int i = 0; i < 20; i++) {
            int x = monolith.getX() + random.nextInt(20) - 10;
            int z = monolith.getZ() + random.nextInt(20) - 10;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = monolith.getY() + 1;
            while ((world.getBlockAt(x, y, z).getType().isAir() || world.getBlockAt(x, y, z).isReplaceable())
                && y > world.getMinHeight()) {
                y--;
            }
            if (Math.abs(y - monolith.getY()) < 20 && (!caveCreature || world.getHighestBlockYAt(x, z) > y + 1)) {
                return new Location(world, x + 0.5, y + 1, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
            }
        }
        return null;
    }

    /** Mod {@code isFree} and the spawn rules, simplified to a free box on firm ground. */
    private boolean fits(Location spot, String type) {
        double width = type.equals(Atlatitan.ATLATITAN.id()) ? Atlatitan.WIDTH : 1.0;
        double height = type.equals(Atlatitan.ATLATITAN.id()) ? Atlatitan.HEIGHT : 2.0;
        BoundingBox box = BoundingBox.of(spot.toVector().add(new Vector(0, height * 0.5, 0)), width * 0.5, height * 0.5,
            width * 0.5);
        return spot.getBlock().getRelative(0, -1, 0).getType().isSolid() && !spot.getWorld().hasCollisionsIn(box);
    }

    private Entity spawn(String type, Location at) {
        if (type.startsWith("minecraft:")) {
            NamespacedKey key = NamespacedKey.minecraft(type.substring("minecraft:".length()));
            EntityType vanilla = Registry.ENTITY_TYPE.get(key);
            return vanilla == null ? null : at.getWorld().spawnEntity(at, vanilla);
        }
        return mobs.spawnNatural(type, at).orElse(null);
    }

    @Override
    public void close() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                save(chunk);
            }
        }
        monoliths.clear();
    }
}
