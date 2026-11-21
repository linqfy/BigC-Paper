package dev.linqfy.bigCasares.modules.alexscaves.core.block.remap;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Server side of the waterlogged module blocks (packet remap only, see {@link WaterloggedIndex}). The server state
 * stays dry; the position is marked in its chunk's persistent data when a waterloggable module block (one with a wet
 * client state) ends up in water:
 * <ul>
 *   <li>a player places it into a water source, or empties a water bucket on it (an empty bucket takes the water
 *   back, like the mod);</li>
 *   <li>worldgen places it where there was water ({@link #generated}, queued from the generation threads and saved
 *   when the chunk loads);</li>
 *   <li>chunks saved before this existed are checked once when they load: a module block next to water counts as
 *   waterlogged (the mod's features waterlog what they place in water).</li>
 * </ul>
 * Breaking a marked block (or an explosion taking it) leaves a water source, as in the mod.
 */
public final class WaterloggedBlocks implements Listener {
    static final String KEY = "alexscaves_waterlogged";
    static final String SCAN_KEY = "alexscaves_waterlogged_scan";
    private static final Map<String, Set<Integer>> PENDING = new ConcurrentHashMap<>();
    private static volatile Predicate<BlockData> generationFilter;

    private final Plugin plugin;
    private final WaterloggedIndex index;
    private final Predicate<BlockData> wetCapable;
    private final ServerInternals.ChunkProbe capableProbe;
    private final NamespacedKey key;
    private final NamespacedKey scanKey;

    WaterloggedBlocks(Plugin plugin, WaterloggedIndex index, Predicate<BlockData> wetCapable,
                      ServerInternals.ChunkProbe capableProbe) {
        this.plugin = plugin;
        this.index = index;
        this.wetCapable = wetCapable;
        this.capableProbe = capableProbe;
        this.key = new NamespacedKey(plugin, KEY);
        this.scanKey = new NamespacedKey(plugin, SCAN_KEY);
    }

    void start() {
        generationFilter = wetCapable;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                load(chunk, false);
            }
        }
    }

    void stop() {
        generationFilter = null;
        PENDING.clear();
        index.clear();
    }

    public boolean isMarked(Block block) {
        return index.contains(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    // --- worldgen ---------------------------------------------------------------------------------------------

    /** Whether worldgen should report placing {@code data} (only while the remap tracks water). */
    public static boolean tracksGeneration(BlockData data) {
        Predicate<BlockData> filter = generationFilter;
        return filter != null && filter.test(data);
    }

    /** Worldgen (any thread): a waterloggable module block was placed where there was water. */
    public static void generated(UUID world, int x, int y, int z) {
        PENDING.computeIfAbsent(pendingKey(world, x >> 4, z >> 4), ignored -> ConcurrentHashMap.newKeySet())
            .add(WaterloggedIndex.pack(x, y, z));
    }

    private static String pendingKey(UUID world, int chunkX, int chunkZ) {
        return world + ":" + chunkX + ":" + chunkZ;
    }

    /** Water, or a block that holds water (what the mod's waterlogging checks see as a water fluid). */
    public static boolean isWater(BlockData data) {
        Material type = data.getMaterial();
        return type == Material.WATER || type == Material.BUBBLE_COLUMN || type == Material.KELP
            || type == Material.KELP_PLANT || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS
            || data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
    }

    // --- chunks -----------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        load(event.getChunk(), event.isNewChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        index.unload(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        UUID world = event.getWorld().getUID();
        index.clearWorld(world);
        PENDING.keySet().removeIf(key -> key.startsWith(world + ":"));
    }

    private void load(Chunk chunk, boolean newChunk) {
        UUID world = chunk.getWorld().getUID();
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        int[] saved = WaterloggedIndex.normalize(data.get(key, PersistentDataType.INTEGER_ARRAY));
        int[] found = saved;
        Set<Integer> generated = PENDING.remove(pendingKey(world, chunk.getX(), chunk.getZ()));
        if (generated != null) {
            found = merge(found, generated.stream().mapToInt(Integer::intValue).toArray());
        }
        if (!data.has(scanKey, PersistentDataType.BYTE)) {
            if (!newChunk && capableProbe.mayHold(chunk)) {
                found = merge(found, scan(chunk.getChunkSnapshot(false, false, false), chunk.getWorld().getMinHeight(),
                    chunk.getWorld().getMaxHeight()));
            }
            data.set(scanKey, PersistentDataType.BYTE, (byte) 1);
        }
        found = keepWetCapable(chunk, found);
        if (!java.util.Arrays.equals(found, saved)) {
            save(data, found);
        }
        index.load(world, chunk.getX(), chunk.getZ(), found);
    }

    /** Drops marks whose block is no longer a waterloggable module block (changed by something the module missed). */
    private int[] keepWetCapable(Chunk chunk, int[] positions) {
        int kept = 0;
        int[] out = new int[positions.length];
        for (int packed : positions) {
            int y = WaterloggedIndex.y(packed);
            if (y >= chunk.getWorld().getMinHeight() && y < chunk.getWorld().getMaxHeight()
                && wetCapable.test(chunk.getBlock(WaterloggedIndex.localX(packed), y, WaterloggedIndex.localZ(packed)).getBlockData())) {
                out[kept++] = packed;
            }
        }
        return kept == positions.length ? positions : java.util.Arrays.copyOf(out, kept);
    }

    /** Module blocks with a wet client state next to water, in a chunk saved before positions were tracked. */
    private int[] scan(ChunkSnapshot snapshot, int minY, int maxY) {
        java.util.List<Integer> found = new java.util.ArrayList<>();
        for (int section = 0; section < (maxY - minY) >> 4; section++) {
            if (snapshot.isSectionEmpty(section)) {
                continue;
            }
            for (int y = minY + section * 16; y < minY + section * 16 + 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Material type = snapshot.getBlockType(x, y, z);
                        if ((type == Material.NOTE_BLOCK || type == Material.TRIPWIRE || type == Material.CHORUS_PLANT)
                            && wetCapable.test(snapshot.getBlockData(x, y, z))
                            && nextToWater(snapshot, x, y, z, minY, maxY)) {
                            found.add(WaterloggedIndex.pack(snapshot.getX() * 16 + x, y, snapshot.getZ() * 16 + z));
                        }
                    }
                }
            }
        }
        return found.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean nextToWater(ChunkSnapshot snapshot, int x, int y, int z, int minY, int maxY) {
        int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
        for (int[] side : sides) {
            int nx = x + side[0];
            int ny = y + side[1];
            int nz = z + side[2];
            if (nx < 0 || nx > 15 || nz < 0 || nz > 15 || ny < minY || ny >= maxY) {
                continue;
            }
            if (isWater(snapshot.getBlockData(nx, ny, nz))) {
                return true;
            }
        }
        return false;
    }

    static int[] merge(int[] a, int[] b) {
        int[] all = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return WaterloggedIndex.normalize(all);
    }

    private void save(PersistentDataContainer data, int[] positions) {
        if (positions.length == 0) {
            data.remove(key);
        } else {
            data.set(key, PersistentDataType.INTEGER_ARRAY, positions);
        }
    }

    // --- marks --------------------------------------------------------------------------------------------------

    /**
     * The chunk's saved positions are the source of truth (the index may have been cleared): the change is applied to
     * them and the index reloaded from the result. Chunk PDC writes mark the chunk unsaved (Paper's
     * DirtyCraftPersistentDataContainer), so the change survives a restart.
     */
    private void mark(Block block) {
        change(block, true);
    }

    /** @return whether the block was marked */
    private boolean unmark(Block block) {
        return change(block, false);
    }

    private boolean change(Block block, boolean wet) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        int[] saved = WaterloggedIndex.normalize(data.get(key, PersistentDataType.INTEGER_ARRAY));
        int packed = WaterloggedIndex.pack(block.getX(), block.getY(), block.getZ());
        boolean present = java.util.Arrays.binarySearch(saved, packed) >= 0;
        if (present == wet) {
            index.load(block.getWorld().getUID(), chunk.getX(), chunk.getZ(), saved);
            return false;
        }
        int[] updated = wet ? merge(saved, new int[] {packed})
            : java.util.Arrays.stream(saved).filter(value -> value != packed).toArray();
        save(data, updated);
        index.load(block.getWorld().getUID(), chunk.getX(), chunk.getZ(), updated);
        return true;
    }

    /** Resends a block whose mark changed while its server state did not (the packet listener adds the water). */
    private static void refresh(Block block) {
        Location location = block.getLocation();
        BlockData data = block.getBlockData();
        for (Player player : block.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) < 160 * 160) {
                player.sendBlockChange(location, data);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        BlockState replaced = event.getBlockReplacedState();
        boolean source = replaced.getType() == Material.WATER && replaced.getBlockData() instanceof Levelled water
            && water.getLevel() == 0;
        if (source && wetCapable.test(block.getBlockData())) {
            mark(block);
        } else if (isMarked(block)) {
            unmark(block);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        removed(event.getBlock());
    }

    /**
     * A marked waterloggable block is gone (player break, explosion): a water source takes its place next tick, as in
     * the mod. The mark stays until then, so the break effect is still sent with the wet state.
     */
    public void removed(Block block) {
        if (!isMarked(block) || !wetCapable.test(block.getBlockData())) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) && unmark(block) && block.getType().isAir()) {
                block.setType(Material.WATER);
            }
        });
    }

    /**
     * Water bucket on a dry waterloggable module block waterlogs it; an empty bucket takes the water back. Runs at
     * MONITOR so protection plugins have decided (a denied item use is left alone); adventure and spectator players
     * cannot change blocks.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucket(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || item == null
            || event.useItemInHand() == Event.Result.DENY
            || item.getType() != Material.WATER_BUCKET && item.getType() != Material.BUCKET) {
            return;
        }
        if (!wetCapable.test(block.getBlockData())) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.ADVENTURE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean marked = isMarked(block);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        if (item.getType() == Material.WATER_BUCKET && !marked) {
            mark(block);
            if (!creative) {
                setHeld(player, event.getHand(), item, new ItemStack(Material.BUCKET));
            }
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.0f);
        } else if (item.getType() == Material.BUCKET && marked) {
            unmark(block);
            if (!creative) {
                setHeld(player, event.getHand(), item, new ItemStack(Material.WATER_BUCKET));
            }
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_BUCKET_FILL, 1.0f, 1.0f);
        } else {
            return;
        }
        event.setCancelled(true);
        refresh(block);
    }

    private static void setHeld(Player player, EquipmentSlot hand, ItemStack held, ItemStack result) {
        EquipmentSlot slot = hand == null ? EquipmentSlot.HAND : hand;
        if (held.getAmount() <= 1) {
            player.getInventory().setItem(slot, result);
            return;
        }
        held.setAmount(held.getAmount() - 1);
        player.getInventory().setItem(slot, held);
        for (ItemStack left : player.getInventory().addItem(result).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }
}
