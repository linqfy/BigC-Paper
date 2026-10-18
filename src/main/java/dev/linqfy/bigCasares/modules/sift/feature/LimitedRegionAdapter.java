package dev.linqfy.bigCasares.modules.sift.feature;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.RegionAccessor;
import org.bukkit.Registry;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import org.bukkit.HeightMap;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.LimitedRegion;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link WorldRegion} over a Bukkit {@link RegionAccessor}: the generation-time
 * {@link LimitedRegion}, or a loaded world when a feature runs at play time (a
 * willow sapling growing).
 */
final class LimitedRegionAdapter implements WorldRegion {

    /** The positions the adapter may read and write. */
    interface Bounds {
        boolean contains(int x, int y, int z);
    }

    /** Parsed block data is immutable in practice; cache by state string. */
    private static final Map<String, BlockData> DATA_CACHE = new ConcurrentHashMap<>();

    private final Server server;
    private final RegionAccessor region;
    private final Bounds bounds;
    private final int minY;
    private final int maxY;
    private final long seed;
    private final BiFunction<String, Integer, Optional<ItemStack>> items;

    LimitedRegionAdapter(Server server, LimitedRegion region, int minY, int maxY, long seed,
                         BiFunction<String, Integer, Optional<ItemStack>> items) {
        this(server, region, region::isInRegion, minY, maxY, seed, items);
    }

    LimitedRegionAdapter(Server server, RegionAccessor region, Bounds bounds, int minY, int maxY, long seed,
                         BiFunction<String, Integer, Optional<ItemStack>> items) {
        this.server = server;
        this.region = region;
        this.bounds = bounds;
        this.minY = minY;
        this.maxY = maxY;
        this.seed = seed;
        this.items = items;
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public boolean spawn(String entityType, double x, double y, double z) {
        return spawn(entityType, x, y, z, null);
    }

    @Override
    public boolean spawn(String entityType, double x, double y, double z, String marker) {
        int blockX = (int) Math.floor(x);
        int blockY = (int) Math.floor(y);
        int blockZ = (int) Math.floor(z);
        NamespacedKey key = NamespacedKey.fromString(entityType);
        EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
        if (type == null || !type.isSpawnable() || !isWritable(blockX, blockY, blockZ)) {
            return false;
        }
        Location at = new Location(null, x, y, z);
        NamespacedKey markerKey = marker == null ? null : NamespacedKey.fromString(marker);
        if (markerKey == null) {
            region.spawnEntity(at, type);
        } else {
            // The consumer runs before the entity joins the world, so the mark is there when it loads.
            spawnMarked(at, type.getEntityClass(), markerKey);
        }
        return true;
    }

    private <T extends Entity> void spawnMarked(Location at, Class<T> type, NamespacedKey marker) {
        region.spawn(at, type, entity -> entity.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1));
    }

    @Override
    public boolean fillContainer(int x, int y, int z, List<ItemSpec> specs) {
        if (!isWritable(x, y, z) || !(region.getBlockState(x, y, z) instanceof Container container)) {
            return false;
        }
        Inventory inventory = container.getSnapshotInventory();
        for (ItemSpec spec : specs) {
            if (spec.slot() < inventory.getSize()) {
                items.apply(spec.item(), spec.count()).ifPresent(stack -> inventory.setItem(spec.slot(), stack));
            }
        }
        if (region instanceof LimitedRegion limited) {
            limited.setBlockState(x, y, z, container);
        } else {
            container.update(true, false);
        }
        return true;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return maxY;
    }

    @Override
    public boolean isWritable(int x, int y, int z) {
        return y >= minY && y < maxY && bounds.contains(x, y, z);
    }

    @Override
    public String blockId(int x, int y, int z) {
        if (!isWritable(x, y, z)) {
            return "minecraft:air";
        }
        return region.getType(x, y, z).getKey().asString();
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return isWritable(x, y, z) && region.getType(x, y, z).isSolid() && region.getType(x, y, z).isOccluding();
    }

    @Override
    public BlockStateSpec state(int x, int y, int z) {
        if (!isWritable(x, y, z)) {
            return BlockStateSpec.of("minecraft:air");
        }
        return BlockStateSpec.parse(region.getBlockData(x, y, z).getAsString());
    }

    @Override
    public boolean set(int x, int y, int z, BlockStateSpec state) {
        if (!isWritable(x, y, z)) {
            return false;
        }
        BlockData data = DATA_CACHE.computeIfAbsent(state.asString(), server::createBlockData);
        region.setBlockData(x, y, z, data);
        return true;
    }

    @Override
    public int height(Heightmap heightmap, int x, int z) {
        HeightMap bukkit = switch (heightmap) {
            case WORLD_SURFACE_WG -> HeightMap.WORLD_SURFACE_WG;
            case OCEAN_FLOOR_WG -> HeightMap.OCEAN_FLOOR_WG;
            case MOTION_BLOCKING -> HeightMap.MOTION_BLOCKING;
            case MOTION_BLOCKING_NO_LEAVES -> HeightMap.MOTION_BLOCKING_NO_LEAVES;
        };
        if (!bounds.contains(x, minY, z)) {
            return minY;
        }
        // Bukkit returns the highest block; vanilla placement uses the first free block above it.
        return region.getHighestBlockYAt(x, z, bukkit) + 1;
    }

    @Override
    public String biome(int x, int y, int z) {
        if (!bounds.contains(x, Math.max(minY, Math.min(maxY - 1, y)), z)) {
            return "minecraft:plains";
        }
        return region.getBiome(x, Math.max(minY, Math.min(maxY - 1, y)), z).getKey().asString();
    }
}
