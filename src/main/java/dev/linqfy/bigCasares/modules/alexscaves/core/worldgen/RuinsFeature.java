package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.CavePaintings;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.FacingDisplays;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * The mod's ruins and huts (mod {@code UndergroundRuinsFeature}, and {@code ForlornRuinsFeature} for the caveman
 * houses): 1 chunk in {@code rarity}, from the middle of the chunk, one of the cave floors of that column is picked,
 * and a random template is placed there, centred and rotated at random, sunk {@code sinkBy} blocks (raised when
 * negative). Caveman houses need packed mud under them and get packed mud supports down to solid ground. Candy ruins
 * ({@code CandyRuinsFeature}) need chocolate under them and sink 1 to {@code sinkBy + 1} blocks.
 */
public final class RuinsFeature implements ChunkFeature {
    private static final StructureRotation[] ROTATIONS = StructureRotation.values();

    private final String id;
    private final CaveBiomeId biome;
    private final int rarity;
    private final int sinkBy;
    private final List<Structure> templates;
    private final boolean caveman;
    private final BlockData packedMud;
    private final java.util.Set<BlockData> floorBlocks;
    private final boolean randomSink;
    private final NoteBlockRotations rotations;
    private List<List<CavePaintings.Mark>> paintings = List.of();
    private BlockData limestone;

    /**
     * @param caveman  the mod's ForlornRuinsFeature rules (packed mud floor, supports)
     * @param packedMud supports of caveman houses, created on the main thread; for other ruins with a support block
     *                  (the Deep One ruins' abyssmarine bricks), supports over water
     */
    public RuinsFeature(String id, CaveBiomeId biome, int rarity, int sinkBy, List<Structure> templates, boolean caveman,
                        BlockData packedMud) {
        this(id, biome, rarity, sinkBy, templates, caveman, packedMud, null, false);
    }

    /**
     * @param floorBlocks blocks the ruin must stand on (null: any)
     * @param randomSink  sink 1 to {@code sinkBy + 1} blocks at random instead of exactly {@code sinkBy}
     */
    public RuinsFeature(String id, CaveBiomeId biome, int rarity, int sinkBy, List<Structure> templates, boolean caveman,
                        BlockData packedMud, java.util.Set<BlockData> floorBlocks, boolean randomSink) {
        this(id, biome, rarity, sinkBy, templates, caveman, packedMud, floorBlocks, randomSink, null);
    }

    /** @param rotations turns the module blocks of the templates with them (null: they keep their variant) */
    public RuinsFeature(String id, CaveBiomeId biome, int rarity, int sinkBy, List<Structure> templates, boolean caveman,
                        BlockData packedMud, java.util.Set<BlockData> floorBlocks, boolean randomSink,
                        NoteBlockRotations rotations) {
        this.rotations = rotations;
        this.id = id;
        this.biome = biome;
        this.rarity = rarity;
        this.sinkBy = sinkBy;
        this.templates = List.copyOf(templates);
        this.caveman = caveman;
        this.packedMud = packedMud;
        this.floorBlocks = floorBlocks == null ? null : java.util.Set.copyOf(floorBlocks);
        this.randomSink = randomSink;
    }

    /**
     * The cave paintings of each template (same order as the templates) and the limestone under them: the mod's
     * caveman houses have them, the converted templates hold plain limestone there. Set once, before generating.
     */
    public RuinsFeature withPaintings(List<List<CavePaintings.Mark>> paintings, BlockData limestone) {
        if (paintings.size() != templates.size()) {
            throw new IllegalArgumentException("Pinturas para " + paintings.size() + " de " + templates.size()
                + " plantillas de " + id);
        }
        this.paintings = List.copyOf(paintings);
        this.limestone = limestone;
        return this;
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
        SplittableRandom random = chunk.random(id);
        // Mod placement: rarity filter, then the middle of the chunk, biome checked at y=0.
        if (!ModPlacement.rarity(random, rarity)) {
            return;
        }
        int x = chunk.minBlockX() + 8;
        int z = chunk.minBlockZ() + 8;
        if (!chunk.isBiome(x, 0, z, biome)) {
            return;
        }
        CaveChunk.BlockSink blocks = chunk.blocks();
        List<Integer> floors = new ArrayList<>();
        int surface = chunk.surfaceY(x, z) - 5;
        int step = 0;
        for (int y = chunk.minY() + 3; y < surface; y++, step++) {
            if (!replaceable(blocks.getType(x, y, z), step) && replaceable(blocks.getType(x, y + 1, z), step + 1)) {
                floors.add(y);
            }
        }
        if (floors.isEmpty()) {
            return;
        }
        int y = floors.size() <= 1 ? floors.get(0) : floors.get(random.nextInt(floors.size() - 1));
        if (caveman && blocks.getType(x, y, z) != Material.PACKED_MUD) {
            return;
        }
        if (floorBlocks != null && !floorBlocks.contains(blocks.getBlockData(x, y, z))) {
            return;
        }
        StructureRotation rotation = ROTATIONS[random.nextInt(ROTATIONS.length)];
        int templateIndex = random.nextInt(templates.size());
        Structure template = templates.get(templateIndex);
        BlockVector size = template.getSize();
        boolean quarter = rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90;
        int sizeX = quarter ? size.getBlockZ() : size.getBlockX();
        int sizeZ = quarter ? size.getBlockX() : size.getBlockZ();
        int cornerX = x - sizeX / 2;
        int cornerZ = z - sizeZ / 2;
        int cornerY = y - (randomSink ? 1 + random.nextInt(sinkBy + 1) : sinkBy);
        int[] zero = zeroCorner(cornerX, cornerZ, size.getBlockX() - 1, size.getBlockZ() - 1, rotation);
        if (!blocks.placeStructure(template, zero[0], cornerY, zero[1], rotation, new Random(random.nextLong()),
            rotations)) {
            return;
        }
        if (!paintings.isEmpty()) {
            for (CavePaintings.Mark mark : paintings.get(templateIndex)) {
                int[] at = StructureRotations.rotate(mark.x(), mark.z(), rotation);
                blocks.addCavePainting(zero[0] + at[0], cornerY + mark.y(), zero[1] + at[1], mark.painting(),
                    FacingDisplays.rotate(mark.facing(), rotation), limestone);
            }
        }
        if (caveman) {
            supports(chunk, cornerX, cornerY, cornerZ, sizeX, sizeZ);
        } else if (packedMud != null) {
            waterSupports(chunk, cornerX, cornerY, cornerZ, sizeX, sizeZ);
        }
    }

    /**
     * Mod {@code DeepOnesRuinsFeature.processBoundingBox}: under the bottom two layers, wherever the ruin is not on the
     * floor (its floor block, water, or a block that is not full), a column of the support block down to the ground.
     */
    private void waterSupports(CaveChunk chunk, int cornerX, int cornerY, int cornerZ, int sizeX, int sizeZ) {
        CaveChunk.BlockSink blocks = chunk.blocks();
        for (int x = cornerX; x < cornerX + sizeX; x++) {
            for (int z = cornerZ; z < cornerZ + sizeZ; z++) {
                for (int y = cornerY; y <= cornerY + 1; y++) {
                    if (!blocks.isInRegion(x, y, z)) {
                        continue;
                    }
                    Material type = blocks.getType(x, y, z);
                    BlockData data = blocks.getBlockData(x, y, z);
                    if (type == Material.WATER || floorBlocks != null && floorBlocks.contains(data)) {
                        continue;
                    }
                    Material below = blocks.getType(x, y - 1, z);
                    if (below != Material.WATER && below.isSolid() && below.isOccluding()) {
                        continue;
                    }
                    for (int ground = y - 1; ground > chunk.minY() && blocks.isInRegion(x, ground, z); ground--) {
                        Material at = blocks.getType(x, ground, z);
                        if (at != Material.WATER && at.isSolid() && at.isOccluding()) {
                            break;
                        }
                        blocks.setBlock(x, ground, z, packedMud);
                    }
                }
            }
        }
    }

    /** Vanilla {@code getZeroPositionWithTransform} without mirroring: the rotated template keeps its footprint. */
    public static int[] zeroCorner(int x, int z, int lastX, int lastZ, StructureRotation rotation) {
        return switch (rotation) {
            case COUNTERCLOCKWISE_90 -> new int[] {x, z + lastX};
            case CLOCKWISE_90 -> new int[] {x + lastZ, z};
            case CLOCKWISE_180 -> new int[] {x + lastX, z + lastZ};
            case NONE -> new int[] {x, z};
        };
    }

    /** Mod {@code ForlornRuinsFeature.processBoundingBox}: packed mud under the bottom of the house down to ground. */
    private void supports(CaveChunk chunk, int cornerX, int cornerY, int cornerZ, int sizeX, int sizeZ) {
        CaveChunk.BlockSink blocks = chunk.blocks();
        for (int x = cornerX; x < cornerX + sizeX; x++) {
            for (int z = cornerZ; z < cornerZ + sizeZ; z++) {
                for (int y = cornerY; y <= cornerY + 1; y++) {
                    if (!blocks.isInRegion(x, y, z) || blocks.getType(x, y, z).isAir()) {
                        continue;
                    }
                    Material below = blocks.getType(x, y - 1, z);
                    if (below.isSolid() && below != Material.WATER) {
                        continue;
                    }
                    for (int ground = y - 1; ground > chunk.minY() && blocks.isInRegion(x, ground, z); ground--) {
                        Material type = blocks.getType(x, ground, z);
                        if (type.isSolid() && type != Material.WATER) {
                            break;
                        }
                        blocks.setBlock(x, ground, z, packedMud);
                    }
                }
            }
        }
    }

    /** Mod {@code canReplace}: air or replaceable; acid (water of the Toxic Caves) only near the bottom. */
    private boolean replaceable(Material type, int step) {
        if (type == Material.WATER) {
            return biome != CaveBiomeId.TOXIC_CAVES || step < 3;
        }
        return type.isAir() || type == Material.SHORT_GRASS || type == Material.TALL_GRASS || type == Material.FERN
            || type == Material.LARGE_FERN || type == Material.MOSS_CARPET || type == Material.LAVA
            || type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT || type == Material.GLOW_LICHEN;
    }
}
