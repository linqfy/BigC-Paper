package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Carves the biome's cave as the mod does ({@link CaveBowl}): caves placed by the structure set's
 * {@code random_spread} and sized by how far the biome continues. A chunk carves every cave
 * that reaches it, so the result agrees across chunk borders. Only rock, dirt and ores are
 * carved, and liquids next to the cave are walled, as in the mod.
 *
 * <p>The acid pit's lake (below {@link CaveBowl#ACID_LEVEL}) is filled with water: the plugin cannot add
 * the mod's acid fluid, so water in the Toxic Caves stands in for it ({@code ToxicAcid}). As in the mod,
 * the sides and bottom of the lake are walled so it does not drain into nearby caves.
 */
public final class BowlFeature implements ChunkFeature {
    /** The surface above a start is unknown while chunks generate; the mod's overworld sits near sea level. */
    private static final int ASSUMED_SURFACE = 72;
    private static final int PLAN_CACHE = 512;
    private static final int[][] AROUND = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final String id;
    private final CaveBiomeId biome;
    private final CaveBowl.Kind kind;
    private final BlockData air;
    private final BlockData wall;
    private final BlockData pillar;
    private final BlockData acid;
    private final BiPredicate<Integer, Integer> wallLiquidAt;
    private final Map<PlanKey, Optional<CaveBowl>> plans = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<PlanKey, Optional<CaveBowl>> eldest) {
            return size() > PLAN_CACHE;
        }
    };

    /**
     * @param air    cave air
     * @param wall   block that replaces liquids next to the cave (mod: stone, galena or mud)
     * @param pillar block of the acid pit's pillars where they cross a liquid (mod: radrock); unused otherwise
     * @param acid   the acid pit's lake, or {@code null} to leave it solid
     */
    public BowlFeature(String id, CaveBiomeId biome, CaveBowl.Kind kind, BlockData air, BlockData wall, BlockData pillar,
                       BlockData acid) {
        this(id, biome, kind, air, wall, pillar, acid, null);
    }

    /**
     * @param wallLiquidAt when set, liquids are walled only inside the chunk being carved (mod: the piece's box) and
     *                     only in the columns it accepts (mod cake cave: away from the soda rivers)
     */
    public BowlFeature(String id, CaveBiomeId biome, CaveBowl.Kind kind, BlockData air, BlockData wall, BlockData pillar,
                       BlockData acid, BiPredicate<Integer, Integer> wallLiquidAt) {
        this.wallLiquidAt = wallLiquidAt;
        this.id = id;
        this.biome = biome;
        this.kind = kind;
        this.air = air;
        this.wall = wall;
        this.pillar = pillar;
        this.acid = acid;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public CaveBiomeId biome() {
        return biome;
    }

    @Override
    public void place(CaveChunk chunk) {
        for (CaveBowl cave : cavesReaching(chunk)) {
            carve(chunk, cave);
        }
    }

    /** The caves of this feature that reach the chunk (the same ones {@link #place} carves). */
    public List<CaveBowl> cavesReaching(CaveChunk chunk) {
        return cavesReaching(chunk, chunk.chunkX(), chunk.chunkZ());
    }

    /** The caves of this feature that reach any chunk of the world (planned with the chunk's world). */
    public List<CaveBowl> cavesReaching(CaveChunk chunk, int chunkX, int chunkZ) {
        long seed = chunk.worldSeed(id);
        List<CaveBowl> caves = new ArrayList<>();
        for (int[] origin : CaveBowl.origins(seed, chunkX, chunkZ, kind.reachChunks(), kind.spacing(),
            kind.separation(), kind.salt())) {
            Optional<CaveBowl> cave = plan(chunk, seed, origin[0], origin[1]);
            if (cave.isPresent() && cave.get().reaches(chunkX - origin[0], chunkZ - origin[1])) {
                caves.add(cave.get());
            }
        }
        return caves;
    }

    /** True when one of the caves carves air at that block (the biome is checked by the caller). */
    public static boolean carvesAir(List<CaveBowl> caves, int x, int y, int z) {
        for (CaveBowl cave : caves) {
            if (cave.columnMayCarve(x, z) && cave.carve(x, y, z) == CaveBowl.Carve.AIR) {
                return true;
            }
        }
        return false;
    }

    private Optional<CaveBowl> plan(CaveChunk chunk, long seed, int originX, int originZ) {
        PlanKey key = new PlanKey(seed, originX, originZ);
        synchronized (plans) {
            Optional<CaveBowl> known = plans.get(key);
            if (known != null) {
                return known;
            }
        }
        Optional<CaveBowl> planned = CaveBowl.plan(kind, CaveBowl.startRandom(seed, originX, originZ),
            originX << 4, originZ << 4,
            y -> CaveBiomeResolver.inDepthBand(biome, y, ASSUMED_SURFACE),
            (x, z) -> chunk.columnBiomeAt(x, z).filter(found -> found == biome).isPresent());
        synchronized (plans) {
            plans.put(key, planned);
        }
        return planned;
    }

    private void carve(CaveChunk chunk, CaveBowl cave) {
        CaveChunk.BlockSink blocks = chunk.blocks();
        int fromY = Math.max(cave.minY(chunk.minY()), cave.carveMinY());
        int toY = Math.min(cave.maxY(), cave.carveMaxY());
        for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
            for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                if (!cave.columnMayCarve(x, z)) {
                    continue;
                }
                for (int y = toY; y >= fromY; y--) {
                    if (!chunk.isBiome(x, y, z, biome)) {
                        continue;
                    }
                    CaveBowl.Carve carve = cave.carve(x, y, z);
                    if (carve == CaveBowl.Carve.NONE || carve == CaveBowl.Carve.ACID && acid == null) {
                        continue;
                    }
                    Material type = blocks.getType(x, y, z);
                    if (carve == CaveBowl.Carve.ACID) {
                        if (CavernFeature.carvable(type) || isLiquid(type) || isWall(blocks, x, y, z, type)
                            || type.isAir()) {
                            blocks.setBlock(x, y, z, acid);
                            wallLake(chunk, blocks, x, y, z);
                        }
                    } else if (carve == CaveBowl.Carve.PILLAR) {
                        if (isLiquid(type)) {
                            blocks.setBlock(x, y, z, pillar);
                        }
                    } else if (CavernFeature.carvable(type) || isLiquid(type) || isWall(blocks, x, y, z, type)) {
                        // As in the mod, liquids inside the shape are carved too; walls placed for an earlier
                        // block of this cave are carved again when they turn out to be inside it.
                        blocks.setBlock(x, y, z, air);
                        wallLiquids(chunk, blocks, x, y, z);
                    }
                }
            }
        }
    }

    /** Mod {@code surroundCornerLiquid}: sides and bottom of the lake that are not lake become wall. */
    private void wallLake(CaveChunk chunk, CaveChunk.BlockSink blocks, int x, int y, int z) {
        for (int[] d : AROUND) {
            if (d[1] > 0) {
                continue;
            }
            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];
            if (!acid.equals(blocks.getBlockData(nx, ny, nz)) && blocks.isInRegion(nx, ny, nz)
                && chunk.isBiome(nx, ny, nz, biome)) {
                blocks.setBlock(nx, ny, nz, wall);
            }
        }
    }

    private boolean isWall(CaveChunk.BlockSink blocks, int x, int y, int z, Material type) {
        return type == wall.getMaterial() && wall.equals(blocks.getBlockData(x, y, z));
    }

    private void wallLiquids(CaveChunk chunk, CaveChunk.BlockSink blocks, int x, int y, int z) {
        for (int[] d : AROUND) {
            int nx = x + d[0];
            int nz = z + d[2];
            if (wallLiquidAt != null && (nx >> 4 != chunk.chunkX() || nz >> 4 != chunk.chunkZ()
                || !wallLiquidAt.test(nx, nz))) {
                continue;
            }
            if (isLiquid(blocks.getType(nx, y + d[1], nz))) {
                blocks.setBlock(nx, y + d[1], nz, wall);
            }
        }
    }

    private static boolean isLiquid(Material type) {
        return type == Material.WATER || type == Material.LAVA;
    }

    private record PlanKey(long seed, int chunkX, int chunkZ) {
    }
}
