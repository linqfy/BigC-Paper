package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.BowlFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CavernFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.RuinsFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureTemplates;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.FeatureShapes;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.LakeFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.SpringFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.OreFeature;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Terrain of the Toxic Caves, after the mod's surface rule (everything radrock) and ore
 * features: the mod's acid pit, radrock in place of stone, uranium ore veins (mod: size 17) and
 * unrefined waste blobs. The acid lake itself, vents, sulfur stacks and ruins come in a later part.
 */
public final class ToxicFeatures {
    static final CaveBiomeId BIOME = CaveBiomeId.TOXIC_CAVES;
    /** Depth band of the biome below the surface (0.3 to 1.5 depth units of 128 blocks). */
    static final int BAND_TOP = (int) Math.floor(BIOME.minDepth() * 128.0);
    static final int BAND_DEPTH = (int) Math.ceil((BIOME.maxDepth() - BIOME.minDepth()) * 128.0);

    private ToxicFeatures() {
    }

    public static List<ChunkFeature> create(ModBlockPalette palette) {
        BlockData air = Bukkit.createBlockData(Material.CAVE_AIR);
        BlockData mud = Bukkit.createBlockData(Material.MUD);
        // Acid is water in this biome (see ToxicAcid).
        BlockData acid = Bukkit.createBlockData(Material.WATER);
        // Mod acid_pit: liquids next to the pit become mud, its pillars radrock.
        return List.of(new BowlFeature("toxic_acid_pit", BIOME, CaveBowl.Kind.ACID_PIT, air, mud, palette.get("radrock"), acid),
            new Terrain(palette),
            // Mod acid_lake: 1-5 tries, each 1 in 7, from 20 above the bottom.
            new LakeFeature("toxic_acid_lake", BIOME, 1, 5, 7, 20, ModPlacement::notAir, acid, mud, air),
            // Mod toxic_ruins: 1 chunk in 10.
            new RuinsFeature("toxic_ruins", BIOME, 10, 1, StructureTemplates.load("toxic_ruins"), false, null),
            // Mod ore features: uranium in radrock; metal swarf and unrefined waste in the mud around acid.
            new OreFeature("toxic_radrock_uranium_ore", BIOME, 30, 17, 0, true, ModPlacement.TOP, false,
                OreFeature.Target.of(palette.get("radrock")), palette.get("radrock_uranium_ore")),
            new OreFeature("toxic_metal_swarf_replace_mud", BIOME, 20, 20, -100, false, 100, true,
                OreFeature.Target.of(Material.MUD), palette.get("metal_swarf")),
            new OreFeature("toxic_unrefined_waste_replace_mud", BIOME, 5, 40, -100, false, 100, true,
                OreFeature.Target.of(Material.MUD), palette.get("unrefined_waste")),
            // Mod spring_acid: 2-10 tries, acid leaking out of radrock walls.
            new SpringFeature("toxic_spring_acid", BIOME, 2, 10, palette.get("radrock"), acid),
            new Vents(palette, air), new SulfurStacks(palette));
    }

    /**
     * Sulfur stacks (mod {@code SulfurStackFeature}, 1-4 per chunk on radrock floors): a sulfur pillar of 3-5
     * blocks with 2-3 shorter ones around it, sulfur crystals on top and on the floor nearby, and acidic
     * radrock dripping from the ceiling above each crystal.
     */
    static final class SulfurStacks implements ChunkFeature {
        private final BlockData radrock;
        private final BlockData sulfur;
        private final BlockData acidicRadrock;
        private final BlockData[] crystals;

        SulfurStacks(ModBlockPalette palette) {
            this.radrock = palette.get("radrock");
            this.sulfur = palette.get("sulfur");
            this.acidicRadrock = palette.get("acidic_radrock");
            this.crystals = new BlockData[] {palette.get("sulfur_bud_small", "up"), palette.get("sulfur_bud_medium", "up"),
                palette.get("sulfur_bud_large", "up")};
        }

        @Override
        public String id() {
            return "toxic_sulfur_stack";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            CaveChunk.BlockSink blocks = chunk.blocks();
            int count = 1 + random.nextInt(4);
            for (int i = 0; i < count; i++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int top = chunk.surfaceY(x, z) - BAND_TOP;
                int bottom = Math.max(chunk.minY() + 5, top - BAND_DEPTH);
                if (top <= bottom) {
                    continue;
                }
                // Mod environment scan: from a random height down, the first block that is not air or acid.
                int y = bottom + random.nextInt(top - bottom);
                for (int step = 0; step < 32 && replaceable(blocks.getType(x, y, z)); step++) {
                    y--;
                }
                if (!chunk.isBiome(x, y, z, BIOME) || !radrock.equals(blocks.getBlockData(x, y - 1, z))) {
                    continue;
                }
                stack(chunk, x, y, z, random);
            }
        }

        private void stack(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            int centerHeight = 3 + random.nextInt(3);
            pillar(chunk, x, y, z, centerHeight, random);
            for (int i = 0; i < 2 + random.nextInt(2); i++) {
                int dx = random.nextInt(4) - 2;
                int dy = -random.nextInt(2);
                int dz = random.nextInt(4) - 2;
                int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                pillar(chunk, x + dx, y + dy, z + dz, centerHeight - distance + random.nextInt(2), random);
            }
            for (int i = 0; i < 4 + random.nextInt(6); i++) {
                int cx = x + random.nextInt(6) - 3;
                int cz = z + random.nextInt(6) - 3;
                int cy = y + 4;
                while (FeatureShapes.isAir(blocks.getType(cx, cy, cz).name()) && cy > chunk.minY()) {
                    cy--;
                }
                if (blocks.getType(cx, cy, cz).isSolid() && FeatureShapes.isAir(blocks.getType(cx, cy + 1, cz).name())) {
                    crystal(chunk, cx, cy + 1, cz, random);
                }
            }
        }

        private void pillar(CaveChunk chunk, int x, int y, int z, int height, SplittableRandom random) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int py = y - 2; py <= y + height; py++) {
                if (replaceable(blocks.getType(x, py, z))) {
                    blocks.setBlock(x, py, z, sulfur);
                }
            }
            if (replaceable(blocks.getType(x, y + height + 1, z)) && !isCrystal(blocks.getBlockData(x, y + height, z))) {
                crystal(chunk, x, y + height + 1, z, random);
            }
        }

        private void crystal(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            if (!blocks.isInRegion(x, y, z)) {
                return;
            }
            // Mod: one of the three bud sizes (its cluster branch is never reached).
            blocks.setBlock(x, y, z, crystals[random.nextInt(3)]);
            int drip = y + 1;
            while (FeatureShapes.isAir(blocks.getType(x, drip, z).name()) && drip < y + 64) {
                drip++;
            }
            if (blocks.isInRegion(x, drip, z) && blocks.getType(x, drip, z).isSolid()) {
                blocks.setBlock(x, drip, z, acidicRadrock);
            }
        }

        private boolean isCrystal(BlockData data) {
            for (BlockData crystal : crystals) {
                if (crystal.equals(data)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean replaceable(Material type) {
            return FeatureShapes.isAir(type.name()) || type == Material.WATER;
        }
    }

    /**
     * Geothermal vents rising out of the acid lakes (mod {@code AcidVentFeature}, placed 100-150 times per
     * chunk between 20 above the bottom and y=30, where the block below is mud). Each successful spot gets
     * one vent and one or two more within 4 blocks.
     */
    static final class Vents implements ChunkFeature {
        private final BlockData vent;
        private final BlockData medium;
        private final BlockData thin;
        private final BlockData mud = Bukkit.createBlockData(Material.MUD);
        private final BlockData acid = Bukkit.createBlockData(Material.WATER);
        private final BlockData air;

        Vents(ModBlockPalette palette, BlockData air) {
            this.vent = palette.get("geothermal_vent");
            this.medium = palette.get("geothermal_vent_medium");
            this.thin = palette.get("geothermal_vent_thin");
            this.air = air;
        }

        @Override
        public String id() {
            return "toxic_acid_vent";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            SplittableRandom random = chunk.random(id());
            CaveChunk.BlockSink blocks = chunk.blocks();
            int minY = chunk.minY() + 20;
            if (minY > 30) {
                return;
            }
            int attempts = 100 + random.nextInt(51);
            for (int i = 0; i < attempts; i++) {
                int x = chunk.minBlockX() + random.nextInt(16);
                int z = chunk.minBlockZ() + random.nextInt(16);
                int y = minY + random.nextInt(30 - minY + 1);
                if (!chunk.isBiome(x, y, z, BIOME) || blocks.getType(x, y, z) == Material.MUD
                    || blocks.getType(x, y - 1, z) != Material.MUD) {
                    continue;
                }
                drawVent(chunk, x, y, z, random);
                for (int extra = 0; extra < 1 + random.nextInt(2); extra++) {
                    drawVent(chunk, x + random.nextInt(8) - 4, y, z + random.nextInt(8) - 4, random);
                }
            }
        }

        private void drawVent(CaveChunk chunk, int x, int y, int z, SplittableRandom random) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            if (!blocks.isInRegion(x, y, z)) {
                return;
            }
            int height = random.nextInt(4) + 2;
            int steps = 0;
            // Climb out of the acid (or rock): at most two blocks.
            while (!FeatureShapes.isAir(blocks.getType(x, y, z).name())) {
                y++;
                if (++steps >= 3) {
                    return;
                }
            }
            for (int i = 0; i < height; i++) {
                if (!FeatureShapes.isAir(blocks.getType(x, y + 1 + i, z).name())) {
                    return;
                }
            }
            int bottom = y - 1;
            mudOrb(blocks, x, bottom, z, 2 + random.nextInt(3), random);
            for (int[] side : new int[][] {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, -1, 0}}) {
                blocks.setBlock(x + side[0], bottom + side[1], z + side[2], mud);
            }
            blocks.setBlock(x, bottom, z, acid);
            int middleStart = Math.max(1, height / 3);
            int middleTop = middleStart * 2;
            for (int i = 1; i <= height; i++) {
                blocks.setBlock(x, bottom + i, z, i <= middleStart ? vent : i > middleTop ? thin : medium);
            }
            blocks.setBlock(x, bottom + height + 1, z, air);
        }

        /** Mod {@code drawOrb}: a mud lump under the vent, radius 1 sideways and {@code radiusY} down. */
        private void mudOrb(CaveChunk.BlockSink blocks, int cx, int cy, int cz, int radiusY, SplittableRandom random) {
            double equal = (1 + radiusY + 1) / 3.0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -radiusY; dy <= 0; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        double distance = (dx + 0.5) * (dx + 0.5) + (dy + 0.5) * (dy + 0.5) + (dz + 0.5) * (dz + 0.5);
                        Material type = blocks.getType(cx + dx, cy + dy, cz + dz);
                        if (distance <= equal * equal + random.nextDouble() * 2.0 && !FeatureShapes.isAir(type.name())
                            && type != Material.BEDROCK) {
                            blocks.setBlock(cx + dx, cy + dy, cz + dz, mud);
                        }
                    }
                }
            }
        }
    }

    /** Mod surface rule: radrock everywhere stone was. */
    static final class Terrain implements ChunkFeature {
        private final ModBlockPalette palette;

        private final BlockData air = Bukkit.createBlockData(Material.CAVE_AIR);

        Terrain(ModBlockPalette palette) {
            this.palette = palette;
        }

        @Override
        public String id() {
            return "toxic_terrain";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public void place(CaveChunk chunk) {
            BlockData radrock = palette.get("radrock");
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (int x = chunk.minBlockX(); x < chunk.minBlockX() + 16; x++) {
                for (int z = chunk.minBlockZ(); z < chunk.minBlockZ() + 16; z++) {
                    int top = chunk.surfaceY(x, z) - BAND_TOP;
                    for (int y = top; y >= Math.max(chunk.minY() + 5, top - BAND_DEPTH); y--) {
                        if (!chunk.isBiome(x, y, z, BIOME)) {
                            continue;
                        }
                        Material type = blocks.getType(x, y, z);
                        if (CavernFeature.vanillaCaveDecoration(type)) {
                            blocks.setBlock(x, y, z, air);
                        } else if (CavernFeature.surfaceRock(type)) {
                            blocks.setBlock(x, y, z, radrock);
                        }
                    }
                }
            }
        }
    }
}
