package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockStates;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.NoteBlockService;
import dev.linqfy.bigCasares.modules.alexscaves.core.block.ModBlockPalette;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ChunkFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.ModPlacement;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.NoteBlockRotations;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.RuinsFeature;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureMarks;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureRotations;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.StructureTemplates;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.structure.Structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Structures of the Abyssal Chasm, after the mod: whalefalls ({@code WhalefallFeature}, 1 chunk in 19: a whale
 * skeleton of skull, body and tail on the muck of the trench, the body and tail settling on the ground block by block,
 * with bone worms and mussels on it), abyssal ruins ({@code AbyssalRuinsStructure}, {@code random_spread} 5/3/45:
 * sunken submarines and wrecks on the trench floor at least 40 under the sea, with their loot) and Deep One ruins
 * ({@code DeepOnesRuinsFeature}, 1 chunk in 14 on muck, on abyssmarine brick supports).
 *
 * <p>The templates are small (a whale is about 30 blocks long), so each one is placed whole by the chunk it starts in,
 * inside the 3x3 chunks it may write. Where it reaches into a neighbouring chunk whose trench is not dug yet, it sits on
 * the floor that trench will leave ({@code Trench.predictFloor}), and that trench leaves the structure's box alone
 * ({@code Trench.guard}).
 */
final class AbyssalStructures {
    static final CaveBiomeId BIOME = CaveBiomeId.ABYSSAL_CHASM;
    static final StructureRotation[] ROTATIONS = StructureRotation.values();

    private AbyssalStructures() {
    }

    /** Made on the main thread, in the mod's step order: whalefalls and abyssal ruins, then the Deep One ruins. */
    static List<ChunkFeature> create(ModBlockPalette palette, AbyssalFeatures.Blocks b, AbyssalFeatures.Trench trench) {
        NoteBlockRotations rotations = NoteBlockRotations.of(palette);
        return List.of(
            new Whalefall(palette, b, trench, rotations),
            new AbyssalRuins(b, trench, rotations, templates("abyssal_ruins")),
            new DeepOneRuins(palette, b));
    }

    static List<Template> templates(String group) {
        return StructureTemplates.load(group).stream().map(Template::of).toList();
    }

    /** A structure template read once on the main thread. */
    record Template(int sizeX, int sizeY, int sizeZ, List<Entry> blocks) {
        record Entry(int x, int y, int z, BlockData data, LootTable lootTable) {
        }

        static Template of(Structure structure) {
            List<Entry> entries = new ArrayList<>();
            for (BlockState state : structure.getPalettes().get(0).getBlocks()) {
                LootTable loot = state instanceof Lootable lootable ? lootable.getLootTable() : null;
                entries.add(new Entry(state.getX(), state.getY(), state.getZ(), ModBlockStates.modernize(state.getBlockData()), loot));
            }
            return new Template(structure.getSize().getBlockX(), structure.getSize().getBlockY(), structure.getSize().getBlockZ(),
                List.copyOf(entries));
        }

        /** The rotated footprint from the zero corner: {@code {minX, minZ, maxX, maxZ}}. */
        int[] footprint(int zeroX, int zeroZ, StructureRotation rotation) {
            int[] far = StructureRotations.rotate(sizeX - 1, sizeZ - 1, rotation);
            return new int[] {Math.min(zeroX, zeroX + far[0]), Math.min(zeroZ, zeroZ + far[1]), Math.max(zeroX, zeroX + far[0]),
                Math.max(zeroZ, zeroZ + far[1])};
        }
    }

    static boolean inChunk(CaveChunk chunk, int x, int z) {
        return x >> 4 == chunk.chunkX() && z >> 4 == chunk.chunkZ();
    }

    /** Water or a block the mod has waterlogged (the module's sea blocks). */
    static boolean fluid(AbyssalFeatures.Blocks b, CaveChunk chunk, int x, int y, int z) {
        Material material = chunk.blocks().getType(x, y, z);
        if (material == Material.WATER || material == Material.BUBBLE_COLUMN) {
            return true;
        }
        return NoteBlockService.isBase(material) && b.soft.contains(chunk.blocks().getBlockData(x, y, z));
    }

    /** Vanilla templates keep liquids: a waterloggable block placed in water is waterlogged. */
    static BlockData keepWater(CaveChunk chunk, int x, int y, int z, BlockData data) {
        if (data instanceof Waterlogged waterlogged && !waterlogged.isWaterlogged()
            && chunk.blocks().getType(x, y, z) == Material.WATER) {
            Waterlogged copy = (Waterlogged) data.clone();
            copy.setWaterlogged(true);
            return copy;
        }
        return data;
    }

    /** Vanilla {@code Rotation.rotate(Direction.SOUTH)} as {@code {dx, dz}}. */
    static int[] south(StructureRotation rotation) {
        return switch (rotation) {
            case NONE -> new int[] {0, 1};
            case CLOCKWISE_90 -> new int[] {-1, 0};
            case CLOCKWISE_180 -> new int[] {0, -1};
            case COUNTERCLOCKWISE_90 -> new int[] {1, 0};
        };
    }

    static final class Whalefall implements ChunkFeature {
        private final AbyssalFeatures.Blocks b;
        private final AbyssalFeatures.Trench trench;
        private final NoteBlockRotations rotations;
        private final List<Template> heads;
        private final List<Template> bodies;
        private final List<Template> tails;
        private final BlockData[] boneWorms = new BlockData[6];
        private final Set<BlockData> wormStates = new HashSet<>();
        /** Mod tag {@code whalefall_ignores}: the whale's bones, which the falling pieces sink through. */
        private final Set<BlockData> bones = new HashSet<>();
        private final BlockData baleenX;
        private final BlockData baleenZ;
        private final BlockData thinBoneX;
        private final BlockData thinBoneZ;

        Whalefall(ModBlockPalette palette, AbyssalFeatures.Blocks b, AbyssalFeatures.Trench trench, NoteBlockRotations rotations) {
            this.b = b;
            this.trench = trench;
            this.rotations = rotations;
            this.heads = templates("whalefall_skull");
            this.bodies = templates("whalefall_body");
            this.tails = templates("whalefall_tail");
            String[] facings = {"down", "up", "north", "south", "west", "east"};
            for (int i = 0; i < 6; i++) {
                boneWorms[i] = palette.get("bone_worms", facings[i]);
                wormStates.add(boneWorms[i]);
            }
            for (String block : new String[] {"smooth_bone", "baleen_bone", "bone_nodule", "bone_ribs", "hollow_bone", "thin_bone"}) {
                for (String variant : palette.registry().variants(block)) {
                    bones.add(palette.get(block, variant));
                }
            }
            baleenX = palette.get("baleen_bone", "x");
            baleenZ = palette.get("baleen_bone");
            thinBoneX = palette.get("thin_bone", "x_0");
            thinBoneZ = palette.get("thin_bone", "z_0");
        }

        @Override
        public String id() {
            return "abyssal_whalefall";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public boolean placesAnywhere() {
            return true;
        }

        private boolean bone(CaveChunk chunk, int x, int y, int z) {
            Material material = chunk.blocks().getType(x, y, z);
            if (material == Material.BONE_BLOCK || material == Material.SMOOTH_QUARTZ_STAIRS || material == Material.SMOOTH_QUARTZ_SLAB
                || material == Material.DIORITE_WALL) {
                return true;
            }
            return NoteBlockService.isBase(material) && bones.contains(chunk.blocks().getBlockData(x, y, z));
        }

        /** Mod {@code WhalefallProcessor.sinkThrough}: fluid (bone worms are waterlogged too), air or bones. */
        private boolean sinks(CaveChunk chunk, int x, int y, int z) {
            Material material = chunk.blocks().getType(x, y, z);
            if (material.isAir() || fluid(b, chunk, x, y, z) || bone(chunk, x, y, z)) {
                return true;
            }
            return NoteBlockService.isBase(material) && wormStates.contains(chunk.blocks().getBlockData(x, y, z));
        }

        @Override
        public void place(CaveChunk chunk) {
            if (!chunk.mayHave(BIOME)) {
                return;
            }
            SplittableRandom random = chunk.random(id());
            if (!ModPlacement.rarity(random, 19)) {
                return;
            }
            int x = chunk.minBlockX() + 8;
            int z = chunk.minBlockZ() + 8;
            // Mod: from the sea floor height, down through the water to the trench bottom, which stands on muck.
            int bottom = chunk.oceanFloor(x, z);
            while (bottom > chunk.minY() && (fluid(b, chunk, x, bottom, z) || chunk.blocks().getType(x, bottom, z).isAir())) {
                bottom--;
            }
            if (!chunk.isBiome(x, bottom, z, BIOME) || !b.muck.equals(chunk.blocks().getBlockData(x, bottom - 1, z))) {
                return;
            }
            StructureRotation rotation = ROTATIONS[random.nextInt(4)];
            Template head = heads.get(random.nextInt(heads.size()));
            Template body = bodies.get(random.nextInt(bodies.size()));
            Template tail = tails.get(random.nextInt(tails.size()));
            int[] direction = south(rotation);
            // Direction.getClockWise / getCounterClockWise.
            int[] bend = random.nextBoolean() ? new int[] {-direction[1], direction[0]} : new int[] {direction[1], -direction[0]};
            int sx = x - direction[0] * 5;
            int sz = z - direction[1] * 5;
            Map<Long, Integer> floors = new HashMap<>();
            int i = piece(chunk, rotation, random, head, sx, bottom, sz, false, floors);
            int bendDist = random.nextInt(2);
            int along = i + random.nextInt(1);
            int j = piece(chunk, rotation, random, body, sx + direction[0] * along + bend[0] * bendDist, bottom,
                sz + direction[1] * along + bend[1] * bendDist, true, floors) + 1;
            along = i + j + random.nextInt(1);
            int tailBend = bendDist + random.nextInt(2);
            piece(chunk, rotation, random, tail, sx + direction[0] * along + bend[0] * tailBend, bottom,
                sz + direction[1] * along + bend[1] * tailBend, true, floors);
        }

        /** Mod {@code generateStructurePiece}: the template centred on the position; returns its length (size z). */
        private int piece(CaveChunk chunk, StructureRotation rotation, SplittableRandom random, Template template, int px, int py,
                          int pz, boolean gravity, Map<Long, Integer> floors) {
            boolean quarter = rotation == StructureRotation.CLOCKWISE_90 || rotation == StructureRotation.COUNTERCLOCKWISE_90;
            int rx = quarter ? template.sizeZ() : template.sizeX();
            int rz = quarter ? template.sizeX() : template.sizeZ();
            int[] zero = RuinsFeature.zeroCorner(px - rx / 2, pz - rz / 2, template.sizeX() - 1, template.sizeZ() - 1, rotation);
            CaveChunk.BlockSink blocks = chunk.blocks();
            List<Template.Entry> entries = template.blocks();
            int[] heights = new int[entries.size()];
            for (int n = 0; n < entries.size(); n++) {
                Template.Entry entry = entries.get(n);
                int[] at = StructureRotations.rotate(entry.x(), entry.z(), rotation);
                heights[n] = py + entry.y();
                if (gravity) {
                    // Mod WhalefallProcessor, for every block before any is placed: down through water, air and bones,
                    // then the block's template height + 1.
                    heights[n] = ground(chunk, zero[0] + at[0], heights[n], zero[1] + at[1], floors) + entry.y() + 1;
                }
            }
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (int n = 0; n < entries.size(); n++) {
                Template.Entry entry = entries.get(n);
                int[] at = StructureRotations.rotate(entry.x(), entry.z(), rotation);
                int wx = zero[0] + at[0];
                int wz = zero[1] + at[1];
                int wy = heights[n];
                minY = Math.min(minY, wy);
                maxY = Math.max(maxY, wy);
                BlockData data = entry.data();
                if ((data.equals(baleenX) || data.equals(baleenZ))
                    && new SplittableRandom(((long) wx * 3129871L) ^ (long) wz * 116129781L ^ wy).nextFloat() < 0.2F) {
                    // Mod: 1 in 5 baleen bones become thin bones along the same axis.
                    data = data.equals(baleenX) ? thinBoneX : thinBoneZ;
                }
                if (blocks.isInRegion(wx, wy, wz)) {
                    blocks.setBlock(wx, wy, wz, keepWater(chunk, wx, wy, wz, rotations.rotate(data, rotation)));
                }
            }
            int[] box = template.footprint(zero[0], zero[1], rotation);
            decorate(chunk, random, box, py, py + template.sizeY() - 1);
            if (minY <= maxY) {
                // Bone worms and mussels go up to 1 block around the box.
                trench.guard(chunk, box[0] - 1, Math.min(minY, py - 1), box[1] - 1, box[2] + 1, Math.max(maxY, py + template.sizeY()),
                    box[3] + 1);
            }
            return Math.abs(template.sizeZ());
        }

        /** The first block under (or at) a position the piece does not sink through. */
        private int ground(CaveChunk chunk, int x, int y, int z, Map<Long, Integer> floors) {
            if (!inChunk(chunk, x, z)) {
                // Not dug yet, perhaps: where the trench will leave the floor (solid ground below it).
                return Math.min(y, trench.predictFloor(chunk, x, z, floors));
            }
            while (y > chunk.minY() && sinks(chunk, x, y, z)) {
                y--;
            }
            return y;
        }

        /**
         * Mod {@code decorateWhalefallPos} over the piece's box grown by 1: one random side of each water or air block;
         * if that side is a bone (or 1 in 5 anyway) with a sturdy face, bone worms or 1-4 mussels on it.
         */
        private void decorate(CaveChunk chunk, SplittableRandom random, int[] box, int minY, int maxY) {
            CaveChunk.BlockSink blocks = chunk.blocks();
            // BlockPos.betweenClosed: x fastest, then y, then z.
            for (int z = box[1] - 1; z <= box[3] + 1; z++) {
                for (int y = minY - 1; y <= maxY + 1; y++) {
                    for (int x = box[0] - 1; x <= box[2] + 1; x++) {
                        int dir = random.nextInt(6);
                        int[] d = AbyssalFeatures.DIRECTIONS[dir];
                        int nx = x + d[0];
                        int ny = y + d[1];
                        int nz = z + d[2];
                        if (!blocks.isInRegion(x, y, z) || !blocks.isInRegion(nx, ny, nz)) {
                            continue;
                        }
                        if (!bone(chunk, nx, ny, nz) && random.nextInt(5) != 0) {
                            continue;
                        }
                        Material here = blocks.getType(x, y, z);
                        Material there = blocks.getType(nx, ny, nz);
                        if ((here == Material.WATER || here.isAir()) && there.isSolid() && there.isOccluding()
                            && !fluid(b, chunk, nx, ny, nz)) {
                            // The block faces away from the side it hangs on (Direction values: down, up, north, south, west, east).
                            int facing = dir ^ 1;
                            if (random.nextBoolean()) {
                                blocks.setBlock(x, y, z, boneWorms[facing]);
                            } else {
                                blocks.setBlock(x, y, z, b.mussels[facing][random.nextInt(4)]);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Mod {@code AbyssalRuinsStructure} and {@code AbyssalRuinsStructurePiece}: one of four sunken ruins at the start
     * chunk's corner, turned at random, on the floor (the lowest under its footprint when most of it is lower), dropped
     * when that is less than 40 under the sea. The ruin is marked ({@code ABYSSAL_RUINS}) for the mine guardians' spawn
     * rule, which comes with the mobs.
     */
    static final class AbyssalRuins implements ChunkFeature {
        private final AbyssalFeatures.Blocks b;
        private final AbyssalFeatures.Trench trench;
        private final NoteBlockRotations rotations;
        private final List<Template> templates;

        AbyssalRuins(AbyssalFeatures.Blocks b, AbyssalFeatures.Trench trench, NoteBlockRotations rotations, List<Template> templates) {
            this.b = b;
            this.trench = trench;
            this.rotations = rotations;
            this.templates = templates;
        }

        @Override
        public String id() {
            return "abyssal_ruins";
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public boolean placesAnywhere() {
            return true;
        }

        @Override
        public void place(CaveChunk chunk) {
            if (!chunk.mayHave(BIOME)) {
                return;
            }
            for (int[] start : CaveBowl.origins(chunk.seed(), chunk.chunkX(), chunk.chunkZ(), 0, 5, 3, 45L)) {
                if (start[0] == chunk.chunkX() && start[1] == chunk.chunkZ()) {
                    ruin(chunk);
                }
            }
        }

        private void ruin(CaveChunk chunk) {
            int x = chunk.minBlockX();
            int z = chunk.minBlockZ();
            // Vanilla isValidBiome: the biome source at the stub position (the chunk corner at the world bottom + 15).
            if (!chunk.biomeAt(x, chunk.minY() + 15, z).filter(found -> found == BIOME).isPresent()) {
                return;
            }
            Random random = ModPlacement.largeFeatureRandom(chunk.seed(), chunk.chunkX(), chunk.chunkZ());
            StructureRotation rotation = ROTATIONS[random.nextInt(4)];
            int start = chunk.oceanFloor(x, z);
            if (start > AbyssalFeatures.SEA_LEVEL) {
                return;
            }
            Template template = templates.get(random.nextInt(templates.size()));
            int[] box = template.footprint(x, z, rotation);
            int y = height(chunk, box, start, Math.abs(box[2] - box[0]), new HashMap<>());
            if (y > AbyssalFeatures.SEA_LEVEL - 40) {
                // Mod: moved to y -128, under the world.
                return;
            }
            CaveChunk.BlockSink blocks = chunk.blocks();
            for (Template.Entry entry : template.blocks()) {
                int[] at = StructureRotations.rotate(entry.x(), entry.z(), rotation);
                int wx = x + at[0];
                int wy = y + entry.y();
                int wz = z + at[1];
                if (!blocks.isInRegion(wx, wy, wz)) {
                    continue;
                }
                BlockData data = keepWater(chunk, wx, wy, wz, rotations.rotate(entry.data(), rotation));
                if (entry.lootTable() != null) {
                    blocks.setContainer(wx, wy, wz, data, entry.lootTable());
                } else {
                    blocks.setBlock(wx, wy, wz, data);
                }
            }
            blocks.markStructure(StructureMarks.Kind.ABYSSAL_RUINS, box[0], y, box[1], box[2], y + template.sizeY() - 1, box[3]);
            trench.guard(chunk, box[0], y, box[1], box[2], y + template.sizeY() - 1, box[3]);
        }

        /**
         * Mod {@code getHeight} over the footprint: each column from under the start height down through water, air and
         * leaves; the ruin goes on the lowest of them when it is more than 2 lower and most columns are too.
         */
        private int height(CaveChunk chunk, int[] box, int start, int span, Map<Long, Integer> floors) {
            int lowest = 512;
            int top = start - 1;
            int lower = 0;
            for (int cx = box[0]; cx <= box[2]; cx++) {
                for (int cz = box[1]; cz <= box[3]; cz++) {
                    int floor = floorBelow(chunk, cx, top, cz, floors);
                    lowest = Math.min(lowest, floor);
                    if (floor < top - 2) {
                        lower++;
                    }
                }
            }
            return top - lowest > 2 && lower > span - 2 ? lowest + 1 : start;
        }

        private int floorBelow(CaveChunk chunk, int x, int y, int z, Map<Long, Integer> floors) {
            if (!inChunk(chunk, x, z)) {
                return Math.min(y, trench.predictFloor(chunk, x, z, floors));
            }
            CaveChunk.BlockSink blocks = chunk.blocks();
            while (y > chunk.minY() + 1) {
                Material material = blocks.getType(x, y, z);
                if (!material.isAir() && !fluid(b, chunk, x, y, z) && !Tag.LEAVES.isTagged(material)) {
                    break;
                }
                y--;
            }
            return y;
        }
    }

    /**
     * Mod {@code DeepOnesRuinsFeature}: 1 chunk in 14, on muck, sunk 1, abyssmarine brick supports under it. It runs
     * wherever the trench made the chunk Abyssal Chasm, not only in the biome's depth band.
     */
    static final class DeepOneRuins implements ChunkFeature {
        private final RuinsFeature ruins;

        DeepOneRuins(ModBlockPalette palette, AbyssalFeatures.Blocks b) {
            ruins = new RuinsFeature("abyssal_deep_one_ruins", BIOME, 14, -1, StructureTemplates.load("deep_one_ruins"), false,
                palette.get("abyssmarine_bricks"), Set.of(b.muck), false);
        }

        @Override
        public String id() {
            return ruins.id();
        }

        @Override
        public CaveBiomeId biome() {
            return BIOME;
        }

        @Override
        public boolean placesAnywhere() {
            return true;
        }

        @Override
        public void place(CaveChunk chunk) {
            if (chunk.mayHave(BIOME)) {
                ruins.place(chunk);
            }
        }
    }
}
