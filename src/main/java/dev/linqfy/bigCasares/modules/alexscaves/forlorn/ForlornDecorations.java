package dev.linqfy.bigCasares.modules.alexscaves.forlorn;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.NoteBlockService;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.VanillaOre;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.data.BlockData;

import java.util.SplittableRandom;

/**
 * Features of the Forlorn Hollows, ported from the mod's feature classes with their placed-feature numbers:
 * thornwood trees with fallen branches, peering coprolith, guano piles, thornwood roots hanging from ceilings and
 * brown mushroom patches. Blocks outside the chunks this chunk may write read as void air and are not written.
 */
final class ForlornDecorations {
    static final CaveBiomeId BIOME = ForlornFeatures.BIOME;

    private ForlornDecorations() {
    }

    /** Block access over the writable region, as the mod's {@code WorldGenLevel}. */
    static final class Level {
        final CaveChunk chunk;
        final CaveChunk.BlockSink blocks;
        final ForlornFeatures.Blocks b;

        Level(CaveChunk chunk, ForlornFeatures.Blocks b) {
            this.chunk = chunk;
            this.blocks = chunk.blocks();
            this.b = b;
        }

        Material type(int x, int y, int z) {
            return blocks.isInRegion(x, y, z) ? blocks.getType(x, y, z) : Material.VOID_AIR;
        }

        BlockData data(int x, int y, int z) {
            return blocks.isInRegion(x, y, z) ? blocks.getBlockData(x, y, z) : null;
        }

        void set(int x, int y, int z, BlockData data) {
            if (blocks.isInRegion(x, y, z)) {
                blocks.setBlock(x, y, z, data);
            }
        }

        boolean canBeReplaced(int x, int y, int z) {
            return ForlornFeatures.Blocks.canBeReplaced(type(x, y, z));
        }

        boolean solidRender(int x, int y, int z) {
            return blocks.isInRegion(x, y, z) && b.solidRender(blocks, x, y, z);
        }

        boolean water(int x, int y, int z) {
            return blocks.isInRegion(x, y, z) && ForlornFeatures.Blocks.water(blocks, x, y, z);
        }

        /** Vanilla {@code canSeeSky}, approximated by the column's generation surface (unknown: never). */
        boolean canSeeSky(int x, int y, int z) {
            return y > ForlornFeatures.knownSurface(chunk, x, z);
        }

        int minY() {
            return chunk.minY();
        }

        int maxY() {
            return 320;
        }
    }

    /**
     * Thornwood trees ({@code ThornwoodTreeWithBranchesFeature}; placement {@code noise_based_count} 15/24/0.2, a
     * random height, then the first non-air block within 32 below). The tree grows from that block when the block
     * under it is dirt, guanostone, coprolith or packed mud and the trunk and crown space is free.
     */
    static final class Trees implements ChunkFeature {
        private final ForlornFeatures.Blocks b;

        Trees(ForlornFeatures.Blocks b) {
            this.b = b;
        }

        @Override
        public String id() {
            return "forlorn_thornwood_trees";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            Level level = new Level(chunk, b);
            int count = ModPlacement.noiseCount(chunk, 24.0, 0.2, 15.0);
            for (int i = 0; i < count; i++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int y = ModPlacement.uniform(random, chunk.minY(), ModPlacement.TOP);
                int ground = ModPlacement.scanDown(chunk, x, y, z, 32, ModPlacement::notAir);
                if (ground == Integer.MIN_VALUE || !chunk.isBiome(x, ground, z, BIOME)) {
                    continue;
                }
                if (ThornwoodTree.grow(level, random, x, ground, z)) {
                    fallenBranches(level, random, x, ground, z);
                }
            }
        }

        /** Mod {@code ThornwoodTreeWithBranchesFeature}: 5 to 14 branches on the ground around the tree. */
        private void fallenBranches(Level level, SplittableRandom random, int x, int y, int z) {
            for (int i = 0; i < 5 + random.nextInt(10); i++) {
                int bx = x + random.nextInt(12) - 6;
                int by = y + random.nextInt(12);
                int bz = z + random.nextInt(12) - 6;
                while (level.type(bx, by, bz).isAir() && by > level.minY()) {
                    by--;
                }
                if (level.solidRender(bx, by, bz)) {
                    by++;
                    if (level.canBeReplaced(bx, by, bz)) {
                        level.set(bx, by, bz, b.branch[ForlornFeatures.UP]);
                    }
                }
            }
        }
    }

    /** Mod {@code ThornwoodTreeFeature}. */
    static final class ThornwoodTree {
        private ThornwoodTree() {
        }

        static boolean grow(Level level, SplittableRandom random, int x, int y, int z) {
            ForlornFeatures.Blocks b = level.b;
            int centerAboveGround = random.nextInt(5);
            int height = centerAboveGround + 4 + random.nextInt(5);
            if (!canPlace(level, x, y, z, height)) {
                return false;
            }
            int cy = y + centerAboveGround;
            if (centerAboveGround > 0) {
                int rootCount = 0;
                for (int direction : ForlornFeatures.HORIZONTAL) {
                    if (rootCount <= 3 + random.nextInt(1)) {
                        int[] d = ForlornFeatures.DIRECTIONS[direction];
                        root(level, x + d[0], cy, z + d[2], 0.25F, random, direction,
                            centerAboveGround + 1 + random.nextInt(6));
                        rootCount++;
                    }
                }
            }
            int tx = x;
            int ty = cy - 1;
            int tz = z;
            int tallPart = height - centerAboveGround;
            for (int i = 0; i < tallPart; ) {
                i++;
                ty++;
                if (random.nextInt(5) == 0) {
                    level.set(tx, ty, tz, b.wood(ForlornFeatures.UP));
                    int[] d = ForlornFeatures.DIRECTIONS[ForlornFeatures.HORIZONTAL[random.nextInt(4)]];
                    tx += d[0];
                    tz += d[2];
                    level.set(tx, ty, tz, b.wood(ForlornFeatures.UP));
                } else {
                    level.set(tx, ty, tz, i == tallPart ? b.wood(ForlornFeatures.UP) : b.log(ForlornFeatures.UP));
                }
                decorateLog(level, tx, ty, tz, random, true);
            }
            for (int direction : ForlornFeatures.HORIZONTAL) {
                int[] d = ForlornFeatures.DIRECTIONS[direction];
                int cx = tx;
                int cyy = ty;
                int cz = tz;
                int canopyLength = 1 + random.nextInt(3);
                for (int j = 1; j <= canopyLength; j++) {
                    boolean upFlag = false;
                    cx += d[0];
                    cz += d[2];
                    if (random.nextInt(2) != 0) {
                        upFlag = true;
                        level.set(cx, cyy, cz, b.wood(direction));
                        cyy++;
                    }
                    if (j == canopyLength && level.canBeReplaced(cx, cyy, cz)) {
                        level.set(cx, cyy, cz, b.branch[upFlag ? ForlornFeatures.UP : direction]);
                    } else {
                        level.set(cx, cyy, cz, j != canopyLength - 1 && !upFlag ? b.log(direction) : b.wood(direction));
                        // Mod: decorates around the trunk top, not the crown log.
                        decorateLog(level, tx, ty, tz, random, true);
                    }
                }
            }
            return true;
        }

        private static boolean canPlace(Level level, int x, int y, int z, int height) {
            Material below = level.type(x, y - 1, z);
            BlockData belowData = level.data(x, y - 1, z);
            boolean ground = Tag.DIRT.isTagged(below) || below == Material.PACKED_MUD
                || below == Material.NOTE_BLOCK && (level.b.guanostone.equals(belowData) || level.b.coprolith.equals(belowData));
            if (!ground) {
                return false;
            }
            for (int i = 0; i < height; i++) {
                if (!canReplace(level, x, y + i, z)) {
                    return false;
                }
            }
            int top = y + height;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (!canReplace(level, x + dx, top + dy, z + dz)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private static boolean canReplace(Level level, int x, int y, int z) {
            Material type = level.type(x, y, z);
            if (ForlornFeatures.Blocks.unmoveable(type)) {
                return false;
            }
            return ForlornFeatures.Blocks.canBeReplaced(type) || Tag.DIRT.isTagged(type) || type == Material.PACKED_MUD
                || NoteBlockService.isBase(type) && level.b.isBranch(level.data(x, y, z));
        }

        /** Mod {@code decorateLog}: sometimes a branch (and a short log) on a random side. */
        static void decorateLog(Level level, int x, int y, int z, SplittableRandom random, boolean logBranches) {
            if (random.nextFloat() < 0.65F) {
                int direction = random.nextInt(6);
                int[] d = ForlornFeatures.DIRECTIONS[direction];
                int bx = x + d[0];
                int by = y + d[1];
                int bz = z + d[2];
                if (level.canBeReplaced(bx, by, bz)) {
                    if (logBranches && random.nextFloat() < 0.4F) {
                        int length = 1 + random.nextInt(1);
                        for (int i = 0; i < length; i++) {
                            level.set(bx, by, bz, i == length - 1 ? level.b.wood(direction) : level.b.log(direction));
                            bx += d[0];
                            by += d[1];
                            bz += d[2];
                        }
                    }
                    level.set(bx, by, bz, level.b.branch[direction]);
                }
            }
        }

        /** Mod {@code generateRoot}: a root going down and out, bending at random, ending in a branch. */
        static void root(Level level, int x, int y, int z, float bendChance, SplittableRandom random, int direction,
                         int length) {
            ForlornFeatures.Blocks b = level.b;
            int[] d = ForlornFeatures.DIRECTIONS[direction];
            for (int i = 0; i < length; i++) {
                if (ForlornFeatures.Blocks.unmoveable(level.type(x, y, z))) {
                    return;
                }
                if (random.nextFloat() < bendChance) {
                    BlockData here = level.data(x, y, z);
                    if (here == null || !b.isWood(here)) {
                        level.set(x, y, z, b.wood(direction));
                    }
                    y--;
                    x += d[0];
                    y += d[1];
                    z += d[2];
                    level.set(x, y, z, b.wood(ForlornFeatures.UP));
                } else {
                    y--;
                    level.set(x, y, z, i == 0 ? b.wood(ForlornFeatures.UP) : b.log(ForlornFeatures.UP));
                }
                decorateLog(level, x, y, z, random, false);
            }
            if (level.canBeReplaced(x, y - 1, z)) {
                level.set(x, y - 1, z, b.branch[ForlornFeatures.DOWN]);
            }
        }
    }

    /**
     * Thornwood roots ({@code ThornwoodRootsFeature}; 1 chunk in 2, below y=60 and the surface): from an open spot,
     * up to the ceiling, a thornwood block with a root down and four roots out.
     */
    static final class Roots implements ChunkFeature {
        private final ForlornFeatures.Blocks b;

        Roots(ForlornFeatures.Blocks b) {
            this.b = b;
        }

        @Override
        public String id() {
            return "forlorn_thornwood_roots";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            if (!ModPlacement.rarity(random, 2)) {
                return;
            }
            int y = ModPlacement.uniform(random, chunk.minY(), 60);
            int x = chunk.minBlockX() + random.nextInt(16);
            int z = chunk.minBlockZ() + random.nextInt(16);
            if (!ModPlacement.belowSurface(chunk, x, y, z, -1) || !chunk.isBiome(x, y, z, BIOME)) {
                return;
            }
            Level level = new Level(chunk, b);
            if (!level.water(x, y, z) && !level.type(x, y, z).isAir()) {
                return;
            }
            while ((level.water(x, y, z) || !level.solidRender(x, y, z)) && y < level.maxY()) {
                y++;
            }
            if (level.type(x, y, z).isAir()) {
                return;
            }
            level.set(x, y, z, b.wood(ForlornFeatures.UP));
            ThornwoodTree.root(level, x, y, z, 0.0F, random, ForlornFeatures.DOWN, 1 + random.nextInt(2));
            for (int direction : ForlornFeatures.HORIZONTAL) {
                ThornwoodTree.root(level, x, y, z, 0.4F, random, direction, 2 + random.nextInt(5));
            }
        }
    }

    /**
     * Peering coprolith ({@code PeeringCoprolithFeature}; 70-100 per chunk, 10 below the surface): an ore-shaped vein
     * of 18 that turns exposed coprolith and guanostone into peering or porous coprolith.
     */
    static final class PeeringCoprolith implements ChunkFeature {
        private final ForlornFeatures.Blocks b;

        PeeringCoprolith(ForlornFeatures.Blocks b) {
            this.b = b;
        }

        @Override
        public String id() {
            return "forlorn_peering_coprolith";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            Level level = new Level(chunk, b);
            int count = ModPlacement.uniform(random, 70, 100);
            for (int i = 0; i < count; i++) {
                int y = ModPlacement.uniform(random, chunk.minY(), ModPlacement.TOP);
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                if (!ModPlacement.belowSurface(chunk, x, y, z, -10) || !chunk.isBiome(x, y, z, BIOME)) {
                    continue;
                }
                VanillaOre.placeBreaking(random, x, y, z, 18, (bx, by, bz) -> {
                    if (!level.blocks.isInRegion(bx, by, bz) || level.type(bx, by, bz) != Material.NOTE_BLOCK) {
                        return false;
                    }
                    BlockData here = level.data(bx, by, bz);
                    if (!b.coprolith.equals(here) && !b.guanostone.equals(here) || !nextToAir(level, bx, by, bz)) {
                        return false;
                    }
                    level.set(bx, by, bz, random.nextBoolean() ? b.peering : b.porous);
                    return true;
                });
            }
        }

        private static boolean nextToAir(Level level, int x, int y, int z) {
            for (int[] d : ForlornFeatures.DIRECTIONS) {
                if (level.type(x + d[0], y + d[1], z + d[2]).isAir()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Guano piles ({@code GuanoPileFeature}; {@code noise_based_count} 12/4/0.15 in the lower 160 blocks): a round
     * mound of guano layers on the floor, 3 to 6 wide and 1 to 3 high, ragged by a noise.
     */
    static final class GuanoPiles implements ChunkFeature {
        private final ForlornFeatures.Blocks b;

        GuanoPiles(ForlornFeatures.Blocks b) {
            this.b = b;
        }

        @Override
        public String id() {
            return "forlorn_guano_piles";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            Level level = new Level(chunk, b);
            int count = ModPlacement.noiseCount(chunk, 4.0, 0.15, 12.0);
            for (int i = 0; i < count; i++) {
                int y = ModPlacement.uniform(random, chunk.minY(), chunk.minY() + 160) + 1;
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                if (chunk.isBiome(x, y, z, BIOME)) {
                    pile(level, random, x, y, z);
                }
            }
        }

        private void pile(Level level, SplittableRandom random, int px, int py, int pz) {
            if (!level.type(px, py, pz).isAir() || level.canSeeSky(px, py, pz)) {
                return;
            }
            while (py > level.minY() && level.canBeReplaced(px, py, pz)) {
                py--;
            }
            int centerLayerHeight = 1 + random.nextInt(3);
            int radius = 3 + random.nextInt(4);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = px + dx;
                    int z = pz + dz;
                    int y = py;
                    // Above the surface it is open air, where the mod's climb stops too.
                    int climbTo = Math.min(level.maxY(), ForlornFeatures.knownSurface(level.chunk, x, z) + 1);
                    while (!level.canBeReplaced(x, y, z) && y < climbTo) {
                        y++;
                    }
                    while (level.canBeReplaced(x, y, z) && y > level.minY()) {
                        y--;
                    }
                    double ox = x + 0.5 - px;
                    double oz = z + 0.5 - pz;
                    double dist = ox * ox + 0.25 + oz * oz;
                    float seaFloorExtra = (1.0F + SimplexNoise3D.sample2D(x, z, 6.0F)) * 2.0F;
                    double radiusSq = radius * (radius - seaFloorExtra);
                    if (dist <= radiusSq) {
                        double invDist = 1.0 - dist / (float) radiusSq;
                        for (int layer = 0; layer < centerLayerHeight * invDist; y++) {
                            BlockData state;
                            if (++layer < centerLayerHeight) {
                                state = b.guano[8];
                            } else {
                                int layers = (int) Math.round(8.0 * invDist * invDist) - random.nextInt(2);
                                state = b.guano[Math.max(1, Math.min(8, layers))];
                            }
                            if (canReplace(level, x, y, z) && !level.canSeeSky(x, y + 1, z) && level.solidRender(x, y - 1, z)) {
                                level.set(x, y, z, state);
                            }
                        }
                    }
                    if (level.canSeeSky(x, y, z)) {
                        break;
                    }
                }
            }
        }

        private static boolean canReplace(Level level, int x, int y, int z) {
            Material type = level.type(x, y, z);
            return (type.isAir() || ForlornFeatures.Blocks.canBeReplaced(type)) && !ForlornFeatures.Blocks.unmoveable(type)
                && !ForlornFeatures.Blocks.hasFluid(level.blocks, x, y, z);
        }
    }

    /**
     * Brown mushroom patches ({@code forlorn_hollows_brown_mushroom}: 20-40 per chunk in the lower 160 blocks, each a
     * {@code random_patch} of 20 tries spread 5 and 3) on any opaque block in the dark.
     */
    static final class BrownMushrooms implements ChunkFeature {
        private final ForlornFeatures.Blocks b;

        BrownMushrooms(ForlornFeatures.Blocks b) {
            this.b = b;
        }

        @Override
        public String id() {
            return "forlorn_brown_mushrooms";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            Level level = new Level(chunk, b);
            int count = ModPlacement.uniform(random, 20, 40);
            for (int i = 0; i < count; i++) {
                int y = ModPlacement.uniform(random, chunk.minY(), chunk.minY() + 160) + 1;
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                if (!chunk.isBiome(x, y, z, BIOME)) {
                    continue;
                }
                for (int attempt = 0; attempt < 20; attempt++) {
                    int bx = x + random.nextInt(6) - random.nextInt(6);
                    int by = y + random.nextInt(4) - random.nextInt(4);
                    int bz = z + random.nextInt(6) - random.nextInt(6);
                    if (level.blocks.isInRegion(bx, by, bz) && level.type(bx, by, bz).isAir()
                        && level.solidRender(bx, by - 1, bz) && !level.canSeeSky(bx, by, bz)) {
                        level.set(bx, by, bz, b.brownMushroom);
                    }
                }
            }
        }
    }
}
