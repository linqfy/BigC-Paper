package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobService;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The volcano's core (mod {@code VolcanicCoreBlock} and {@code VolcanicCoreBlockEntity}). An ominous catalyst thrown
 * within 20 blocks of it (and 100 up or down) is pulled to it and, when it arrives, is consumed to summon the
 * Luxtructosaurus at the top of the volcano, once a day. While a boss erupts, the core shoots tephra: a big one at the
 * nearest player (within 400 blocks) and a few small ones, every 120 to 239 ticks.
 *
 * <p>The mod's block entity is a record here: volcano generation marks where the core is ({@link StructureMarks}) and the
 * summon cooldown is saved in the chunk. Volcanoes generated before the marks existed are recognised lazily: a volcano's
 * core always stands in the corner column of its start chunk ({@link Volcano#coreColumnRange}), so when a chunk loads
 * without a mark that one column is looked at (about sixty blocks, once per load) and a core found there is saved in the
 * chunk, and the old volcano then works like a new one.
 */
public final class VolcanicCores implements Listener, Runnable, AutoCloseable {
    static final NamespacedKey COOLDOWN = new NamespacedKey("bigcasares", "ac_volcanic_core_until");
    /** Cores found in chunks generated before {@link StructureMarks} marked them, as x, y, z triples. */
    static final NamespacedKey FOUND = new NamespacedKey("bigcasares", "ac_volcanic_core_found");
    static final String CATALYST = "ac_ominous_catalyst";
    private static final int PULL_RANGE = 20;
    private static final int PULL_HEIGHT = 100;

    private final ModMobService mobs;
    private final Plugin plugin;
    private final PrimordialBlocks blocks;
    private final Random random = new Random();
    private final Map<Key, Core> cores = new HashMap<>();
    private final Set<UUID> catalysts = new HashSet<>();

    record Key(UUID world, int x, int y, int z) {
    }

    private static final class Core {
        /** Ticks left before it can summon again; counted only while its chunk is loaded, like the block entity. */
        long cooldown;
        int eruptionCooldown;
    }

    public VolcanicCores(ModMobService mobs, PrimordialBlocks blocks) {
        this.mobs = mobs;
        this.plugin = mobs.plugin();
        this.blocks = blocks;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                load(chunk);
                for (Entity entity : chunk.getEntities()) {
                    note(entity);
                }
            }
        }
    }

    // --- registry ---

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        load(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        for (Key key : new ArrayList<>(cores.keySet())) {
            if (key.world().equals(chunk.getWorld().getUID()) && key.x() >> 4 == chunk.getX() && key.z() >> 4 == chunk.getZ()) {
                save(key, chunk);
                cores.remove(key);
            }
        }
    }

    @EventHandler
    public void onItem(ItemSpawnEvent event) {
        note(event.getEntity());
    }

    @EventHandler
    public void onEntities(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            note(entity);
        }
    }

    /** Mod {@code fireResistant}: the catalyst and the tectonic shard neither burn nor melt in lava or fire. */
    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Item item)) {
            return;
        }
        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> {
                if (mobs.itemId(item.getItemStack()).filter(id -> id.equals(CATALYST) || id.equals("ac_tectonic_shard")).isPresent()) {
                    event.setCancelled(true);
                    item.setFireTicks(0);
                }
            }
            default -> {
            }
        }
    }

    private void note(Entity entity) {
        if (entity instanceof Item item && isCatalyst(item.getItemStack())) {
            catalysts.add(item.getUniqueId());
        }
    }

    private boolean isCatalyst(ItemStack stack) {
        return mobs.itemId(stack).filter(CATALYST::equals).isPresent();
    }

    /** Where the cores of a chunk stand: the generation marks, else the ones found before, else a look at its corner column. */
    private List<int[]> coresIn(Chunk chunk) {
        List<int[]> positions = new ArrayList<>();
        for (int[] box : StructureMarks.boxes(chunk, StructureMarks.Kind.VOLCANIC_CORE)) {
            positions.add(new int[] {box[0], box[1], box[2]});
        }
        if (!positions.isEmpty()) {
            return positions;
        }
        int[] known = chunk.getPersistentDataContainer().get(FOUND, PersistentDataType.INTEGER_ARRAY);
        if (known != null) {
            for (int i = 0; i + 2 < known.length; i += 3) {
                positions.add(new int[] {known[i], known[i + 1], known[i + 2]});
            }
            return positions;
        }
        World world = chunk.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return positions;
        }
        int[] range = Volcano.coreColumnRange(world.getMinHeight(), world.getMaxHeight());
        int x = chunk.getX() << 4;
        int z = chunk.getZ() << 4;
        // Only where the module's caves are: the biome at the foot of the cone is one of Alex's Caves.
        int baseY = Math.max(range[0], world.getMinHeight());
        if (!"alexscaves".equals(world.getBiome(x, baseY, z).getKey().getNamespace())) {
            return positions;
        }
        for (int y = range[0]; y <= range[1]; y++) {
            Block block = chunk.getBlock(0, y, 0);
            if (block.getType() == Material.NOTE_BLOCK && blocks.volcanicCore.equals(block.getBlockData())) {
                positions.add(new int[] {x, y, z});
            }
        }
        // Saved even when empty, so the column is never looked at again.
        int[] saved = new int[positions.size() * 3];
        for (int i = 0; i < positions.size(); i++) {
            System.arraycopy(positions.get(i), 0, saved, i * 3, 3);
        }
        chunk.getPersistentDataContainer().set(FOUND, PersistentDataType.INTEGER_ARRAY, saved);
        return positions;
    }

    private void load(Chunk chunk) {
        for (int[] box : coresIn(chunk)) {
            Block block = chunk.getWorld().getBlockAt(box[0], box[1], box[2]);
            if (block.getType() != Material.NOTE_BLOCK || !blocks.volcanicCore.equals(block.getBlockData())) {
                continue;
            }
            Key key = new Key(chunk.getWorld().getUID(), box[0], box[1], box[2]);
            Core core = cores.computeIfAbsent(key, ignored -> new Core());
            core.cooldown = chunk.getPersistentDataContainer().getOrDefault(COOLDOWN, PersistentDataType.LONG, 0L);
            core.eruptionCooldown = 0;
        }
    }

    private void save(Key key, Chunk chunk) {
        Core core = cores.get(key);
        if (core != null) {
            if (core.cooldown > 0) {
                chunk.getPersistentDataContainer().set(COOLDOWN, PersistentDataType.LONG, core.cooldown);
            } else {
                chunk.getPersistentDataContainer().remove(COOLDOWN);
            }
        }
    }

    // --- tick ---

    @Override
    public void run() {
        if (!catalysts.isEmpty()) {
            for (UUID id : new ArrayList<>(catalysts)) {
                Entity entity = plugin.getServer().getEntity(id);
                if (!(entity instanceof Item item) || !item.isValid() || !isCatalyst(item.getItemStack())) {
                    catalysts.remove(id);
                    continue;
                }
                attract(item);
            }
        }
        for (Map.Entry<Key, Core> entry : cores.entrySet()) {
            World world = plugin.getServer().getWorld(entry.getKey().world());
            if (world == null) {
                continue;
            }
            Core core = entry.getValue();
            if (core.cooldown > 0) {
                core.cooldown--;
            }
            if (PrimordialBoss.active(world)) {
                if (core.eruptionCooldown-- <= 0) {
                    erupt(world, entry.getKey());
                    core.eruptionCooldown = SauropodRules.eruptionCooldown(random);
                }
            } else {
                core.eruptionCooldown = 0;
            }
        }
    }

    /** Mod tick: ready cores pull a catalyst that is within reach and take it when it is under 0.66 blocks away. */
    private void attract(Item item) {
        Location at = item.getLocation();
        for (Map.Entry<Key, Core> entry : cores.entrySet()) {
            Key key = entry.getKey();
            if (!key.world().equals(item.getWorld().getUID()) || Math.abs(at.getX() - key.x() - 0.5) > PULL_RANGE
                || Math.abs(at.getZ() - key.z() - 0.5) > PULL_RANGE || Math.abs(at.getY() - key.y() - 0.5) > PULL_HEIGHT) {
                continue;
            }
            Core core = entry.getValue();
            World world = item.getWorld();
            if (core.cooldown > 0) {
                continue;
            }
            Vector center = new Vector(key.x() + 0.5, key.y() + 0.5, key.z() + 0.5);
            Vector offset = center.clone().subtract(at.toVector());
            double distance = offset.length();
            if (distance < 100.0 && distance > 1.0E-6) {
                item.setVelocity(offset.normalize().multiply(0.2).add(item.getVelocity().multiply(0.8)));
            }
            if (distance < 0.66 && spawnBoss(world, key, core)) {
                ItemStack stack = item.getItemStack();
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() <= 0) {
                    item.remove();
                } else {
                    item.setItemStack(stack);
                }
            }
            return;
        }
    }

    /** Mod {@code spawnBoss}. */
    private boolean spawnBoss(World world, Key key, Core core) {
        Block coreBlock = world.getBlockAt(key.x(), key.y(), key.z());
        // Mod quirk kept: it only looks at the column 5 blocks away on both axes, and keeps the core's own column.
        Block top = topOfVolcano(world.getBlockAt(key.x() - 5, key.y(), key.z() - 5));
        int topY = Math.max(coreBlock.getY(), top.getY());
        Location at = new Location(world, key.x() + 0.5, topY + 2.0, key.z() + 0.5);
        if (Luxtructosaurus.summon(mobs, at).isEmpty()) {
            return false;
        }
        core.cooldown = SauropodRules.BOSS_SPAWN_COOLDOWN;
        Chunk chunk = world.getChunkAt(key.x() >> 4, key.z() >> 4);
        save(key, chunk);
        return true;
    }

    Block topOfVolcano(Block start) {
        World world = start.getWorld();
        Block top = start;
        while (blocks.volcano(top) && top.getY() < world.getMaxHeight() - 1) {
            top = top.getRelative(0, 1, 0);
        }
        return top.getRelative(0, -1, 0);
    }

    /** Mod {@code spawnTephra(true)}: a big rock toward the nearest player and some small ones around. */
    private void erupt(World world, Key key) {
        Tephra.get(plugin).ifPresent(tephra -> {
            Block top = topOfVolcano(world.getBlockAt(key.x(), key.y(), key.z()));
            boolean ok = blocks.erupts(top);
            if (!ok) {
                return;
            }
            Location from = new Location(world, top.getX() + 0.5, top.getY() + 3.0, top.getZ() + 0.5);
            Player nearest = null;
            double best = 400.0 * 400.0;
            for (Player player : world.getPlayers()) {
                double distance = player.getLocation().distanceSquared(from);
                if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR && distance < best) {
                    best = distance;
                    nearest = player;
                }
            }
            Vector target;
            double d5 = nearest == null ? random.nextFloat() : 0.0;
            if (nearest == null) {
                target = new Vector(random.nextFloat() - 0.5, 0.0, random.nextFloat() - 0.5).normalize()
                    .multiply(random.nextInt(50) + 20);
            } else {
                target = nearest.getLocation().toVector().subtract(from.toVector());
            }
            double d4 = Math.sqrt(target.getX() * target.getX() + target.getZ() * target.getZ());
            tephra.launch(from, Tephra.shoot(target.getX(), target.getY() + 0.5 + d4 * 0.75 + d5, target.getZ(),
                (float) (d4 * 0.1F + d5), 1.0F + random.nextFloat() * 0.5F, random), 2.0F + random.nextFloat(), null,
                nearest == null ? null : nearest.getUniqueId());
            for (int smalls = 0; smalls < 3 + random.nextInt(3); smalls++) {
                Vector small = new Vector(random.nextFloat() - 0.5, 0.0, random.nextFloat() - 0.5).normalize()
                    .multiply(random.nextInt(30) + 30);
                double d = Math.sqrt(small.getX() * small.getX() + small.getZ() * small.getZ());
                tephra.launch(from, Tephra.shoot(small.getX(), small.getY() + 0.5 + d * 0.75 + random.nextFloat(), small.getZ(),
                    (float) (d * 0.1F + random.nextFloat()), 1.0F, random), 0.6F + 0.6F * random.nextFloat(), null, null);
            }
        });
    }

    @Override
    public void close() {
        for (Key key : new ArrayList<>(cores.keySet())) {
            World world = plugin.getServer().getWorld(key.world());
            if (world != null && world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                save(key, world.getChunkAt(key.x() >> 4, key.z() >> 4));
            }
        }
        cores.clear();
        catalysts.clear();
    }

    List<Key> coresForTest() {
        return List.copyOf(cores.keySet());
    }
}
