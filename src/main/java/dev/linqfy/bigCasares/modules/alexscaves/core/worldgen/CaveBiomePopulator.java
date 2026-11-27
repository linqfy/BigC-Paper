package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockStates;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.remap.WaterloggedBlocks;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeResolver.SurfaceKind;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Assigns cave biomes to the underground volume of newly generated chunks and
 * runs the biome features. Added to {@code World#getPopulators()} at enable and
 * removed by the module scope, so it only ever touches chunks generated while
 * the module is active.
 */
public final class CaveBiomePopulator extends BlockPopulator {
    private static final int QUART = 4;

    private final Map<CaveBiomeId, Biome> biomes;
    private final Map<CaveBiomeId, List<ChunkFeature>> features;
    /** Structures of the enabled biomes, run in chunks without their biome too. */
    private final List<ChunkFeature> anywhere;
    private final ResolverCache resolvers;
    private final BooleanSupplier active;
    private final Logger logger;

    public CaveBiomePopulator(
        Map<CaveBiomeId, Biome> biomes,
        List<ChunkFeature> features,
        ResolverCache resolvers,
        BooleanSupplier active,
        Logger logger
    ) {
        this.biomes = Map.copyOf(biomes);
        EnumMap<CaveBiomeId, List<ChunkFeature>> byBiome = new EnumMap<>(CaveBiomeId.class);
        for (ChunkFeature feature : features) {
            byBiome.computeIfAbsent(feature.biome(), ignored -> new ArrayList<>()).add(feature);
        }
        this.features = Map.copyOf(byBiome);
        List<ChunkFeature> anywhere = new ArrayList<>();
        for (ChunkFeature feature : features) {
            if (feature.placesAnywhere() && this.biomes.containsKey(feature.biome())) {
                anywhere.add(feature);
            }
        }
        this.anywhere = List.copyOf(anywhere);
        this.resolvers = resolvers;
        this.active = active;
        this.logger = logger;
    }

    private final LogThrottle throttle = new LogThrottle(30_000L);

    @Override
    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion region) {
        try {
            populateChunk(worldInfo, chunkX, chunkZ, region);
        } catch (RuntimeException | LinkageError failure) {
            // A broken populator must not abort chunk generation for the whole server.
            long suppressed = throttle.tryLog("populate");
            if (suppressed >= 0) {
                logger.log(Level.WARNING, "[AlexsCaves] La generación de cuevas falló en el chunk " + chunkX + ","
                    + chunkZ + (suppressed > 0 ? " (" + suppressed + " fallos más sin registrar)" : ""), failure);
            }
        }
    }

    private void populateChunk(WorldInfo worldInfo, int chunkX, int chunkZ, LimitedRegion region) {
        if (!active.getAsBoolean()) {
            return;
        }
        CaveBiomeResolver resolver = resolvers.forWorld(worldInfo);
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int minY = worldInfo.getMinHeight();
        int[][] surface = new int[QUART][QUART];
        @SuppressWarnings("unchecked")
        Optional<CaveBiomeId>[][] columns = new Optional[QUART][QUART];
        Set<CaveBiomeId> present = EnumSet.noneOf(CaveBiomeId.class);
        for (int qx = 0; qx < QUART; qx++) {
            for (int qz = 0; qz < QUART; qz++) {
                int x = baseX + qx * QUART + 2;
                int z = baseZ + qz * QUART + 2;
                Optional<CaveBiomeId> column = resolver.columnBiome(x, z);
                columns[qx][qz] = column;
                if (column.isEmpty() || !biomes.containsKey(column.get())) {
                    columns[qx][qz] = Optional.empty();
                    continue;
                }
                int top = region.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR_WG);
                surface[qx][qz] = top;
                Biome biome = biomes.get(column.get());
                for (int y = minY + 2; y < top; y += QUART) {
                    if (CaveBiomeResolver.inDepthBand(column.get(), y, top)) {
                        region.setBiome(x, y, z, biome);
                        present.add(column.get());
                    }
                }
            }
        }
        if (present.isEmpty() && anywhere.isEmpty()) {
            return;
        }
        CaveChunk.BiomeMask mask = new CaveChunk.BiomeMask() {
            @Override
            public Optional<CaveBiomeId> biomeAt(int x, int y, int z) {
                int qx = Math.floorMod(x - baseX, 16) / QUART;
                int qz = Math.floorMod(z - baseZ, 16) / QUART;
                if (x - baseX < 0 || x - baseX > 15 || z - baseZ < 0 || z - baseZ > 15) {
                    return Optional.empty();
                }
                Optional<CaveBiomeId> column = columns[qx][qz];
                if (column.isEmpty() || !CaveBiomeResolver.inDepthBand(column.get(), y, surface[qx][qz])) {
                    return Optional.empty();
                }
                return column;
            }

            @Override
            public Optional<CaveBiomeId> columnBiomeAt(int x, int z) {
                return resolver.columnBiome(x, z).filter(biomes::containsKey);
            }

            @Override
            public int surfaceY(int x, int z) {
                int qx = Math.floorMod(x - baseX, 16) / QUART;
                int qz = Math.floorMod(z - baseZ, 16) / QUART;
                return surface[qx][qz];
            }
        };
        CaveChunk chunk = new CaveChunk(worldInfo.getSeed(), chunkX, chunkZ, minY, mask, new RegionSink(region, worldInfo.getUID(), chunkX, chunkZ, biomes));
        for (CaveBiomeId biome : present) {
            for (ChunkFeature feature : features.getOrDefault(biome, List.of())) {
                place(feature, chunk, chunkX, chunkZ);
            }
        }
        for (ChunkFeature feature : anywhere) {
            if (!present.contains(feature.biome())) {
                place(feature, chunk, chunkX, chunkZ);
            }
        }
    }

    private void place(ChunkFeature feature, CaveChunk chunk, int chunkX, int chunkZ) {
        try {
            feature.place(chunk);
        } catch (RuntimeException failure) {
            // A broken feature must not abort chunk generation for the whole server.
            long suppressed = throttle.tryLog("feature:" + feature.id());
            if (suppressed >= 0) {
                logger.log(Level.WARNING, "[AlexsCaves] Feature " + feature.id() + " falló en el chunk "
                    + chunkX + "," + chunkZ + (suppressed > 0 ? " (" + suppressed + " fallos más sin registrar)" : ""),
                    failure);
            }
        }
    }

    /** Classifies a vanilla surface biome key for {@link CaveBiomeResolver}. */
    public static SurfaceKind classify(NamespacedKey biome) {
        String path = biome.getKey().toLowerCase(Locale.ROOT);
        if (path.contains("ocean")) {
            return path.contains("warm") ? SurfaceKind.OTHER : SurfaceKind.OCEAN;
        }
        if (path.contains("river") || path.contains("beach") || path.contains("shore")) {
            return SurfaceKind.OTHER;
        }
        return SurfaceKind.LAND;
    }

    /** Containers placed by the module's structures: the loot table they were generated with. */
    public static final org.bukkit.NamespacedKey STRUCTURE_CONTAINER = new org.bukkit.NamespacedKey("bigcasares", "ac_structure_container");

    private static final class RegionSink implements CaveChunk.BlockSink {
        private final LimitedRegion region;

        private java.util.UUID world;
        private int chunkX;
        private int chunkZ;

        private Map<CaveBiomeId, Biome> biomeMap = Map.of();

        private RegionSink(LimitedRegion region, java.util.UUID world, int chunkX, int chunkZ, Map<CaveBiomeId, Biome> biomeMap) {
            this(region);
            this.biomeMap = biomeMap;
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public void setCaveBiome(int x, int y, int z, CaveBiomeId biome) {
            Biome target = biomeMap.get(biome);
            if (target != null && region.isInRegion(x, y, z)) {
                region.setBiome(x, y, z, target);
            }
        }

        @Override
        public int oceanFloorWG(int x, int z) {
            if (!region.isInRegion(x, 0, z)) {
                return Integer.MIN_VALUE;
            }
            return region.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR_WG) + 1;
        }

        @Override
        public void markStructure(StructureMarks.Kind kind, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (world == null) {
                return;
            }
            // The box clipped to each chunk of the 3x3 region it covers (a piece may be written into the neighbours).
            for (int cx = chunkX - 1; cx <= chunkX + 1; cx++) {
                for (int cz = chunkZ - 1; cz <= chunkZ + 1; cz++) {
                    int fromX = Math.max(minX, cx << 4);
                    int toX = Math.min(maxX, (cx << 4) + 15);
                    int fromZ = Math.max(minZ, cz << 4);
                    int toZ = Math.min(maxZ, (cz << 4) + 15);
                    if (fromX <= toX && fromZ <= toZ) {
                        StructureMarks.queue(world, cx, cz, kind, fromX, minY, fromZ, toX, maxY, toZ);
                    }
                }
            }
        }

        private RegionSink(LimitedRegion region) {
            this.region = region;
        }

        @Override
        public boolean isInRegion(int x, int y, int z) {
            return region.isInRegion(x, y, z);
        }

        @Override
        public boolean placeStructure(org.bukkit.structure.Structure template, int x, int y, int z,
                                      org.bukkit.block.structure.StructureRotation rotation, Random random,
                                      NoteBlockRotations turn) {
            if (!region.isInRegion(x, y, z) || template.getPalettes().isEmpty()) {
                return false;
            }
            // Placed block by block instead of Structure#place: that runs neighbour shape updates, which would
            // retune the module's note blocks into other blocks. Containers keep their loot table.
            for (org.bukkit.block.BlockState state : template.getPalettes().get(0).getBlocks()) {
                int[] at = StructureRotations.rotate(state.getX(), state.getZ(), rotation);
                int bx = x + at[0];
                int by = y + state.getY();
                int bz = z + at[1];
                if (!region.isInRegion(bx, by, bz)) {
                    continue;
                }
                BlockData data;
                BlockData templated = ModBlockStates.modernize(state.getBlockData());
                if (turn != null) {
                    data = turn.rotate(templated, rotation);
                } else if (ModBlockStates.isModuleShaped(templated)) {
                    // Bukkit would turn the links of a tripwire / chorus plant state into another module block.
                    data = templated;
                } else {
                    data = templated.clone();
                    data.rotate(rotation);
                }
                put(bx, by, bz, data);
                if (state instanceof org.bukkit.loot.Lootable lootable && lootable.getLootTable() != null) {
                    // setBlockState needs the block already in place; it then copies the container's loot table.
                    org.bukkit.block.BlockState placed = region.getBlockState(bx, by, bz);
                    if (placed instanceof org.bukkit.loot.Lootable container) {
                        container.setLootTable(lootable.getLootTable());
                        region.setBlockState(bx, by, bz, placed);
                    }
                }
            }
            return true;
        }

        @Override
        public void addCavePainting(int x, int y, int z, String painting, org.bukkit.block.BlockFace facing,
                                    BlockData limestone) {
            if (!region.isInRegion(x, y, z)) {
                return;
            }
            put(x, y, z, limestone);
            double[] at = dev.linqfy.bigCasares.modules.alexscaves.core.block.CavePaintings.anchor(x, y, z, facing);
            if (!region.isInRegion((int) Math.floor(at[0]), (int) Math.floor(at[1]), (int) Math.floor(at[2]))) {
                return;
            }
            region.spawn(new org.bukkit.Location(null, at[0], at[1], at[2]), org.bukkit.entity.ItemDisplay.class,
                display -> dev.linqfy.bigCasares.modules.alexscaves.core.block.CavePaintings.configure(display, painting,
                    facing, x, y, z));
        }

        @Override
        public void setContainer(int x, int y, int z, BlockData data, org.bukkit.loot.LootTable lootTable) {
            if (!region.isInRegion(x, y, z)) {
                return;
            }
            region.setBlockData(x, y, z, data);
            // setBlockState needs the block already in place; it then copies the container's loot table.
            org.bukkit.block.BlockState placed = region.getBlockState(x, y, z);
            if (lootTable != null && placed instanceof org.bukkit.loot.Lootable container) {
                container.setLootTable(lootTable);
                if (placed instanceof org.bukkit.block.TileState tile) {
                    // Remembers which structure placed the container (its loot table), after it is looted too.
                    tile.getPersistentDataContainer().set(STRUCTURE_CONTAINER, org.bukkit.persistence.PersistentDataType.STRING,
                        lootTable.getKey().toString());
                }
                region.setBlockState(x, y, z, placed);
            }
        }

        @Override
        public Material getType(int x, int y, int z) {
            return region.isInRegion(x, y, z) ? region.getType(x, y, z) : Material.VOID_AIR;
        }

        @Override
        public BlockData getBlockData(int x, int y, int z) {
            return region.isInRegion(x, y, z) ? region.getBlockData(x, y, z) : null;
        }

        @Override
        public void setBlock(int x, int y, int z, BlockData data) {
            if (region.isInRegion(x, y, z)) {
                put(x, y, z, data);
            }
        }

        /** A waterloggable module block placed where there was water is waterlogged, as the mod's features do. */
        private void put(int x, int y, int z, BlockData data) {
            if (world != null && WaterloggedBlocks.tracksGeneration(data)
                && WaterloggedBlocks.isWater(region.getBlockData(x, y, z))) {
                WaterloggedBlocks.generated(world, x, y, z);
            }
            region.setBlockData(x, y, z, data);
        }
    }

    /** One resolver per world seed; the surface probe needs the world's vanilla biome provider. */
    public interface ResolverCache {
        CaveBiomeResolver forWorld(WorldInfo worldInfo);
    }
}
