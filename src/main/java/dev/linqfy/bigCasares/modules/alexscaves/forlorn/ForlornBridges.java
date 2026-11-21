package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeResolver;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SeededHash;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.IntPredicate;

/**
 * Bridges across the forlorn canyon ({@code ForlornBridgeStructure}, {@code random_spread} 8/7/22): from a start in
 * the biome, a 4-wide plank bridge at a random height of the biome, as long as the biome continues both ways, in
 * 6-block sections ({@code ForlornBridgeStructurePiece}): top slabs over the gap with upside-down stair rails,
 * stripped dark oak supports every few blocks with thornwood branches, fence posts with chains up to the ceiling,
 * beams down to the ground and forsaken idols at both ends. Like the mod's pieces, a section writes its whole box in
 * every chunk it touches (inside the chunks this chunk may write) and only fills air and liquids.
 */
final class ForlornBridges implements ChunkFeature {
    static final int SECTION_LENGTH = 6;
    static final int SECTION_WIDTH = 4;
    private static final int ASSUMED_SURFACE = 72;
    private static final int REACH_CHUNKS = 9;
    private static final int CACHE = 256;

    private final ForlornFeatures.Blocks b;
    private final Map<Long, Optional<List<Section>>> plans = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Optional<List<Section>>> eldest) {
            return size() > CACHE;
        }
    };

    ForlornBridges(ForlornFeatures.Blocks b) {
        this.b = b;
    }

    /** One piece: its first block before the walk, section number, last section and direction index. */
    record Section(int x, int y, int z, int id, int max, int direction, long seed) {
        /** Mod {@code createBoundingBox}: {@code {minX, minZ, maxX, maxZ}}. */
        int[] box() {
            int half = SECTION_WIDTH / 2;
            int rest = SECTION_LENGTH - half;
            int[] d = ForlornFeatures.DIRECTIONS[direction];
            int dx = half + rest * Math.abs(d[0]);
            int dz = half + rest * Math.abs(d[2]);
            return new int[] {x - dx, z - dz, x + dx, z + dz};
        }
    }

    @Override
    public String id() {
        return "forlorn_bridge";
    }

    @Override
    public CaveBiomeId biome() {
        return ForlornFeatures.BIOME;
    }

    @Override
    public boolean placesAnywhere() {
        return true;
    }

    @Override
    public void place(CaveChunk chunk) {
        for (int[] origin : CaveBowl.origins(chunk.seed(), chunk.chunkX(), chunk.chunkZ(), REACH_CHUNKS, 8, 7, 22)) {
            for (Section section : plan(chunk, origin[0], origin[1]).orElse(List.of())) {
                int[] box = section.box();
                if (box[2] >= chunk.minBlockX() && box[0] < chunk.minBlockX() + 16 && box[3] >= chunk.minBlockZ()
                    && box[1] < chunk.minBlockZ() + 16) {
                    new Piece(chunk, section).place();
                }
            }
        }
    }

    private Optional<List<Section>> plan(CaveChunk chunk, int chunkX, int chunkZ) {
        long key = SeededHash.hash(chunk.seed(), chunkX, chunkZ);
        synchronized (plans) {
            Optional<List<Section>> known = plans.get(key);
            if (known != null) {
                return known;
            }
        }
        Optional<List<Section>> planned = plan(chunk.seed(), chunkX, chunkZ,
            (x, z) -> chunk.columnBiomeAt(x, z).filter(found -> found == ForlornFeatures.BIOME).isPresent());
        synchronized (plans) {
            plans.put(key, planned);
        }
        return planned;
    }

    /** Biome test of a column, for planning. */
    interface ColumnTest {
        boolean holds(int x, int z);
    }

    /** Mod {@code ForlornBridgeStructure.generatePieces}. Pure logic. */
    static Optional<List<Section>> plan(long seed, int chunkX, int chunkZ, ColumnTest column) {
        int x = chunkX << 4;
        int z = chunkZ << 4;
        IntPredicate inBand = y -> CaveBiomeResolver.inDepthBand(ForlornFeatures.BIOME, y, ASSUMED_SURFACE);
        // Structure biome check at the stub, the chunk's middle (mod atYCaveBiomePoint: getMiddleBlockX/Z, -10).
        if (!column.holds(x + 8, z + 8) || !inBand.test(-10)) {
            return Optional.empty();
        }
        Random random = ModPlacement.largeFeatureRandom(seed, chunkX, chunkZ);
        int up = continues(32, step -> inBand.test(-10 + step));
        int down = continues(32, step -> inBand.test(-10 - step));
        int y = -10 - down + random.nextInt(Math.max(up, 10));
        int direction = ForlornFeatures.HORIZONTAL[random.nextInt(4)];
        int[] d = ForlornFeatures.DIRECTIONS[direction];
        int forwards = continues(32 + random.nextInt(6) * 16,
            step -> column.holds(x + d[0] * step, z + d[2] * step) && inBand.test(y));
        int backwards = continues(32 + random.nextInt(6) * 16,
            step -> column.holds(x - d[0] * step, z - d[2] * step) && inBand.test(y));
        int maxSections = (backwards + forwards) / SECTION_LENGTH;
        List<Section> sections = new ArrayList<>();
        for (int section = 0; section <= maxSections; section++) {
            int along = section * SECTION_LENGTH - SECTION_LENGTH / 2 - backwards;
            sections.add(new Section(x + d[0] * along, y, z + d[2] * along, section, maxSections, direction,
                SeededHash.hash(seed ^ 0x466F726CL, chunkX * 64 + section, chunkZ)));
        }
        return Optional.of(List.copyOf(sections));
    }

    /** Mod {@code biomeContinuesInDirectionFor}: steps of 16 until the biome stops, at most {@code cutoff}. */
    static int continues(int cutoff, IntPredicate holds) {
        int step;
        for (step = 0; step < cutoff; step += 16) {
            if (!holds.test(step)) {
                break;
            }
        }
        return Math.min(step, cutoff);
    }

    /** Mod {@code ForlornBridgeStructurePiece.postProcess} in one chunk. */
    private final class Piece {
        private final CaveChunk chunk;
        private final CaveChunk.BlockSink blocks;
        private final Section s;
        private final int[] box;

        Piece(CaveChunk chunk, Section s) {
            this.chunk = chunk;
            this.blocks = chunk.blocks();
            this.s = s;
            this.box = s.box();
        }

        void place() {
            SplittableRandom random = new SplittableRandom(s.seed());
            int[] d = ForlornFeatures.DIRECTIONS[s.direction()];
            int cw = ForlornFeatures.clockWise(s.direction());
            int[] side = ForlornFeatures.DIRECTIONS[cw];
            Set<Long> supports = new LinkedHashSet<>();
            Set<Long> special = new LinkedHashSet<>();
            int half = SECTION_WIDTH / 2;
            for (int width = -half; width < half; width++) {
                int px = s.x() + side[0] * width;
                int pz = s.z() + side[2] * width;
                int startLength = 0;
                int endLength = SECTION_LENGTH;
                if (s.id() == 0) {
                    startLength = random.nextInt(SECTION_LENGTH - 1);
                }
                if (s.id() == s.max()) {
                    endLength = random.nextInt(SECTION_LENGTH);
                }
                // As in the mod the walk always starts at the piece's position, whatever startLength is.
                for (int length = startLength; length < endLength; length++) {
                    px += d[0];
                    pz += d[2];
                    // Deck spots already filled by this bridge (from another chunk's pass) count as placed, so
                    // the supports are the same whatever order the chunks generate in.
                    boolean open = open(px, s.y(), pz);
                    if (!open && !ours(px, s.y(), pz)) {
                        continue;
                    }
                    BlockData state = b.bridgeSlab;
                    if (width == -half || width == half - 1) {
                        // Mod: length % 3 != startLength (not length - startLength).
                        if (length % 3 != startLength) {
                            boolean left = width == half - 1;
                            state = b.bridgeStairs[left ? ForlornFeatures.counterClockWise(s.direction()) : cw];
                        } else {
                            state = b.strippedDarkOakLog;
                            long at = pack(px, pz);
                            supports.add(at);
                            if (s.id() == 0 && length == startLength || s.id() == s.max() && length != startLength) {
                                special.add(at);
                            }
                        }
                    }
                    if (open) {
                        set(px, s.y(), pz, state);
                    }
                }
            }
            for (long at : supports) {
                int x = (int) (at >> 32);
                int z = (int) at;
                int y = s.y();
                set(x, y + 1, z, b.strippedDarkOakLog);
                set(x + d[0], y + 1, z + d[2], b.branch[s.direction()]);
                set(x - d[0], y + 1, z - d[2], b.branch[ForlornFeatures.opposite(s.direction())]);
                if (special.contains(at)) {
                    boolean end = s.max() == s.id();
                    set(x, y + 2, z, b.idol[end ? s.direction() : ForlornFeatures.opposite(s.direction())]);
                } else {
                    set(x, y + 2, z, b.strippedDarkOakLog);
                    set(x, y + 3, z, b.fence);
                    if (coin(x, z, 1)) {
                        chain(x, y + 4, z);
                    }
                }
                if (coin(x, z, 2) || s.id() == 0 || s.id() == s.max()) {
                    beam(x, y - 1, z);
                }
            }
        }

        /** Per-support coin flip (mod: the piece's random), fixed by the section and the column. */
        private boolean coin(int x, int z, int salt) {
            return (SeededHash.hash(s.seed() + salt, x, z) & 1L) == 0L;
        }

        /** A deck block this bridge places, already there. */
        private boolean ours(int x, int y, int z) {
            if (!inBox(x, z) || !blocks.isInRegion(x, y, z)) {
                return false;
            }
            BlockData data = blocks.getBlockData(x, y, z);
            if (data.equals(b.bridgeSlab) || data.equals(b.strippedDarkOakLog)) {
                return true;
            }
            for (BlockData stairs : b.bridgeStairs) {
                if (data.equals(stairs)) {
                    return true;
                }
            }
            return false;
        }

        private boolean open(int x, int y, int z) {
            if (!inBox(x, z) || !blocks.isInRegion(x, y, z)) {
                return false;
            }
            return blocks.getType(x, y, z).isAir() || ForlornFeatures.Blocks.hasFluid(blocks, x, y, z);
        }

        /** Mod {@code buildBeam}: stripped dark oak down to the first solid block. */
        private void beam(int x, int y, int z) {
            while (inBox(x, z) && blocks.isInRegion(x, y, z) && !blocks.getType(x, y, z).isSolid() && y > chunk.minY()) {
                set(x, y, z, b.strippedDarkOakLog);
                y--;
            }
        }

        /** Mod {@code buildChain}: a chain up to the ceiling, when there is one. */
        private void chain(int x, int y, int z) {
            int sky = ForlornFeatures.knownSurface(chunk, x, z);
            while (inBox(x, z) && blocks.isInRegion(x, y, z) && !blocks.getType(x, y, z).isSolid() && y <= sky) {
                set(x, y, z, b.chain);
                y++;
            }
        }

        private boolean inBox(int x, int z) {
            return x >= box[0] && x <= box[2] && z >= box[1] && z <= box[3];
        }

        /** Mod {@code checkedSetBlock}: inside the piece's box. */
        private void set(int x, int y, int z, BlockData data) {
            if (inBox(x, z) && blocks.isInRegion(x, y, z) && y >= -64 && y <= 256) {
                blocks.setBlock(x, y, z, data);
            }
        }
    }

    static long pack(int x, int z) {
        return (long) x << 32 | z & 0xFFFFFFFFL;
    }
}
