package dev.linqfy.bigCasares.modules.alexscaves.primordial;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SimplexNoise3D;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * Volcanoes of the Primordial Caves (mod {@code VolcanoStructure} and its piece): placed by {@code random_spread}
 * (spacing 14, separation 8, salt 9129) where the biome holds 30 blocks all around the start at y=-34; a cone
 * 30-44 blocks wide and 8-12 taller than wide, of flood basalt with primal magma veins, a lava-filled caldera
 * over a core of active magma, a volcanic core in the middle and a limestone foot. Also the mod's volcano
 * boulders: smooth basalt lumps on the flood basalt near the bottom of the world.
 */
final class Volcano {
    static final int CENTER_Y = -34;
    private static final int SPACING = 14;
    private static final int SEPARATION = 8;
    private static final int SALT = 9129;
    /** Mod: 30; here enough for the widest cone (radius 44 around the chunk corner) seen from block 9. */
    private static final int CHECK_RADIUS = 44 + 13;

    /** Tallest cone: radius 44 plus 8 plus 4 more. */
    private static final int MAX_CONE_HEIGHT = 44 + 8 + 4;

    private Volcano() {
    }

    /**
     * The heights ({@code {min, max}}, inclusive) where a volcano's core can stand: from the base to the top of the
     * tallest cone, clipped to the world. The core is in the corner column of the start chunk ({@code centerX} and
     * {@code centerZ} are chunk origins), so volcanoes generated before the core was marked can be found by looking at
     * that one column of a chunk.
     */
    static int[] coreColumnRange(int minHeight, int maxHeight) {
        return new int[] {Math.max(minHeight, CENTER_Y), Math.min(maxHeight - 1, CENTER_Y + MAX_CONE_HEIGHT)};
    }

    /** One volcano: its start column and size. */
    record Cone(int centerX, int centerZ, int radius, int height) {
        /** Mod {@code calcVolcanoCurve}. */
        static double curve(double invDist) {
            double off = 2.0 * invDist - 1.0;
            double d0 = 0.5 * (0.85F * Math.sin(off * (float) Math.PI) + Math.pow(off, 3.0) + 0.5);
            return invDist > 0.85F ? d0 : Math.max(d0, invDist * invDist);
        }

        int reachChunks() {
            return (int) Math.ceil(radius / 16.0F);
        }
    }

    /** Mod {@code generatePieces} sizes, from the start's random. */
    static Cone cone(int startX, int startZ, Random random) {
        int radius = 30 + random.nextInt(15);
        int height = radius + 8 + random.nextInt(5);
        return new Cone(startX, startZ, radius, height);
    }

    static final class Feature implements ChunkFeature {
        private static final int PLAN_CACHE = 256;
        private final BlockData floodBasalt;
        private final BlockData primalMagma;
        private final BlockData activeMagma;
        private final BlockData volcanicCore;
        private final BlockData limestone;
        private final BlockData lava = Bukkit.createBlockData(Material.LAVA);
        private final BlockData smoothBasalt = Bukkit.createBlockData(Material.SMOOTH_BASALT);
        private final Map<Long, Optional<Cone>> plans = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Optional<Cone>> eldest) {
                return size() > PLAN_CACHE;
            }
        };

        Feature(ModBlockPalette palette) {
            this.floodBasalt = palette.get("flood_basalt", "y");
            this.primalMagma = palette.get("primal_magma");
            this.activeMagma = palette.get("primal_magma", "active");
            this.volcanicCore = palette.get("volcanic_core");
            this.limestone = palette.get("limestone");
        }

        @Override
        public String id() {
            return "primordial_volcano";
        }

        @Override
        public CaveBiomeId biome() {
            return CaveBiomeId.PRIMORDIAL_CAVES;
        }

        @Override
        public void place(CaveChunk chunk) {
            long seed = chunk.worldSeed(id());
            int reach = (int) Math.ceil((30 + 14) / 16.0F);
            for (int[] origin : CaveBowl.origins(seed, chunk.chunkX(), chunk.chunkZ(), reach, SPACING, SEPARATION, SALT)) {
                Optional<Cone> cone = plan(chunk, seed, origin[0], origin[1]);
                if (cone.isPresent() && Math.abs(chunk.chunkX() - origin[0]) <= cone.get().reachChunks()
                    && Math.abs(chunk.chunkZ() - origin[1]) <= cone.get().reachChunks()) {
                    build(chunk, cone.get());
                }
            }
        }

        private Optional<Cone> plan(CaveChunk chunk, long seed, int originX, int originZ) {
            long key = ((long) originX << 32) ^ (originZ & 0xffffffffL) ^ seed * 31;
            synchronized (plans) {
                Optional<Cone> known = plans.get(key);
                if (known != null) {
                    return known;
                }
            }
            int x = originX << 4;
            int z = originZ << 4;
            Optional<Cone> planned = Optional.empty();
            // Mod: every biome sample within 30 blocks of block 9 of the start chunk, at y=-34, must be the
            // Primordial Caves. The plugin only builds in chunks of the biome, so the check also covers the widest
            // cone: otherwise the cone would be cut off where the biome ends.
            boolean all = true;
            for (int dx = -CHECK_RADIUS; dx <= CHECK_RADIUS && all; dx += 4) {
                for (int dz = -CHECK_RADIUS; dz <= CHECK_RADIUS && all; dz += 4) {
                    if (dx * dx + dz * dz <= CHECK_RADIUS * CHECK_RADIUS) {
                        all = chunk.columnBiomeAt(x + 9 + dx, z + 9 + dz).filter(b -> b == CaveBiomeId.PRIMORDIAL_CAVES)
                            .isPresent();
                    }
                }
            }
            if (all) {
                planned = Optional.of(cone(x, z, CaveBowl.startRandom(seed, originX, originZ)));
            }
            synchronized (plans) {
                plans.put(key, planned);
            }
            return planned;
        }

        /** Mod {@code VolcanoStructurePiece.postProcess} for the columns of this chunk. */
        private void build(CaveChunk chunk, Cone cone) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            SplittableRandom random = chunk.random("primordial_volcano_piece");
            int coreHeight = (int) Math.floor(cone.height() * 0.35);
            for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
                for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                    double dx = x + 0.5 - cone.centerX();
                    double dz = z + 0.5 - cone.centerZ();
                    double dist = Math.sqrt(dx * dx + 0.25 + dz * dz) / cone.radius();
                    if (dist >= 1.0) {
                        continue;
                    }
                    double invDist = 1.0 - dist;
                    double curve = Cone.curve(invDist);
                    int lavaSink = curve < 1.0 && dist < 0.15F ? 2 : 0;
                    int top = (int) (CENTER_Y + cone.height() * curve - lavaSink);
                    float caldera = (float) Math.sin(invDist * Math.PI);
                    for (int y = CENTER_Y; y < top; y++) {
                        if (!blocks.isInRegion(x, y, z) || blocks.getType(x, y, z) == Material.BEDROCK) {
                            break;
                        }
                        int beneath = top - y;
                        int above = y - CENTER_Y;
                        BlockData set;
                        if (beneath == coreHeight && x == cone.centerX() && z == cone.centerZ()) {
                            set = volcanicCore;
                            // Where the mod's volcanic core block entity would stand (the boss summon and the eruptions).
                            blocks.markStructure(dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks.Kind
                                .VOLCANIC_CORE, x, y, z, x, y, z);
                        } else if (beneath > 3.0F + caldera * 20.0F && above > 3.0F + caldera * 10.0F) {
                            set = lava;
                        } else if (lavaSink <= 0 && (beneath <= 6 || above <= 3)) {
                            float vein = SimplexNoise3D.sample(x - 19000, y * 0.5F + 120.0F, z + 19000, 20.0F);
                            if (curve < 0.035F) {
                                set = random.nextFloat() * 0.13F < curve ? smoothBasalt : limestone;
                            } else if (vein < 0.075F && vein > -0.075F) {
                                set = primalMagma;
                            } else {
                                set = floodBasalt;
                            }
                        } else {
                            set = activeMagma;
                        }
                        blocks.setBlock(x, y, z, set);
                    }
                    // Limestone foot: at least 3 blocks, more while there is air or dirt below.
                    for (int y = CENTER_Y - 1, j = 0; y > chunk.minY() && blocks.isInRegion(x, y, z); y--, j++) {
                        Material below = blocks.getType(x, y, z);
                        if (!(below.isAir() || isDirt(below)) && j >= 3) {
                            break;
                        }
                        blocks.setBlock(x, y, z, limestone);
                    }
                }
            }
        }

        private static boolean isDirt(Material material) {
            return material == Material.DIRT || material == Material.GRASS_BLOCK || material == Material.PACKED_MUD
                || material == Material.MUD || material == Material.MOSS_BLOCK || material == Material.PODZOL
                || material == Material.COARSE_DIRT || material == Material.ROOTED_DIRT;
        }
    }

    /**
     * Mod {@code volcano_boulder} (20-35 tries per chunk): from a floor spot, down to flood basalt within 40 blocks
     * of the bottom of the world, three small overlapping lumps of smooth basalt.
     */
    static final class Boulders implements ChunkFeature {
        private final BlockData floodBasalt;
        private final BlockData smoothBasalt = Bukkit.createBlockData(Material.SMOOTH_BASALT);

        Boulders(ModBlockPalette palette) {
            this.floodBasalt = palette.get("flood_basalt", "y");
        }

        @Override
        public String id() {
            return "primordial_volcano_boulder";
        }

        @Override
        public CaveBiomeId biome() {
            return CaveBiomeId.PRIMORDIAL_CAVES;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            CaveChunk.BlockSink blocks = chunk.blocks();
            int count = ModPlacement.uniform(random, 20, 35);
            for (int i = 0; i < count; i++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int y = ModPlacement.uniform(random, chunk.minY(), ModPlacement.TOP);
                y = ModPlacement.scanDown(chunk, x, y, z, 32, ModPlacement::notAir);
                if (y == Integer.MIN_VALUE || !ModPlacement.belowSurface(chunk, x, y, z, -5)
                    || !chunk.isBiome(x, y, z, CaveBiomeId.PRIMORDIAL_CAVES)) {
                    continue;
                }
                // Mod: walk down to flood basalt.
                while (y > chunk.minY() + 1 && !floodBasalt.equals(blocks.getBlockData(x, y - 1, z))) {
                    y--;
                }
                if (y <= chunk.minY() + 1 || y > chunk.minY() + 40) {
                    continue;
                }
                y += 1 + random.nextInt(1);
                for (int lump = 0; lump < 3; lump++) {
                    int rx = random.nextInt(3);
                    int ry = random.nextInt(3);
                    int rz = random.nextInt(3);
                    float f = (rx + ry + rz) * 0.333F + 0.5F;
                    double radius = f * f;
                    for (int dx = -rx; dx <= rx; dx++) {
                        for (int dy = -ry; dy <= ry; dy++) {
                            for (int dz = -rz; dz <= rz; dz++) {
                                if (dx * dx + dy * dy + dz * dz <= radius && blocks.isInRegion(x + dx, y + dy, z + dz)
                                    && blocks.getType(x + dx, y + dy, z + dz) != Material.BEDROCK) {
                                    blocks.setBlock(x + dx, y + dy, z + dz, smoothBasalt);
                                }
                            }
                        }
                    }
                    x += -2 + random.nextInt(4);
                    y -= random.nextInt(2);
                    z += -2 + random.nextInt(4);
                }
            }
        }
    }
}
