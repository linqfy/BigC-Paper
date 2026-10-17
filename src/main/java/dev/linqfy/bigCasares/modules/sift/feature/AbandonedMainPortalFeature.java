package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Mirror;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Rotation;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Template;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.TemplateBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of the mod's {@code AbandonedMainPortalFeature}: once per 20x20-chunk cell (in the chunk
 * {@link SiftLandmarkPlacement} selects for the world seed) a ruined portal is raised on the
 * ground. One of five authored templates for the biome family ({@code abandoned_portal_overgrown_*}
 * in overgrown biomes and Ichor peaks, {@code abandoned_portal_wastes_*} elsewhere) is placed
 * with a random mirror; of the four rotations the one that needs the least ground levelling
 * (smallest height difference across the footprint, at most 18 blocks) wins, every block it
 * overwrites must be air, Sift ground or a replaceable plant, and the ground inside and up to
 * 12 blocks around the footprint is blended into the structure floor with at most 8 blocks of
 * earthwork. Plants are then scattered over the ruin and the reshaped ground, and the template's
 * chests are filled from their {@code the_sift:chests/abandoned_portal} loot table.
 *
 * <p>Like the mod's {@code BlockIgnoreProcessor.STRUCTURE_AND_AIR}, template air (and structure
 * blocks and voids) is not placed, so the terrain shows through the ruin's open volume.</p>
 *
 * <p>Differences:</p>
 * <ul>
 *   <li>{@code SiftFeaturePlacementGuard} and {@code SiftLandmarkTracker} are not ported. The guard is global
 *       state filled in generation order, so the ruin's presence depended on which chunks happened to generate
 *       first: it kept the ruin 28 blocks away from the dimension's arrival portal ({@code SiftPortalStructure},
 *       reserved at run time) and reserved the ruin's 46x46 footprint so that monoliths, dry spikes, lakes and
 *       canyons generated before or after it do not overlap. The tracker is a persistent registry of landmark
 *       positions (to locate them); the positions are a pure function of the world seed here
 *       ({@link SiftLandmarkPlacement}). Selected chunks of neighbouring cells are at least 13 chunks apart, so
 *       two ruins never collide, but overlaps with the arrival portal and with other solid features are
 *       possible.</li>
 *   <li>Chests are filled at generation time from the generation {@link Random}, not when first
 *       opened. The loot table is evaluated by {@link SiftLootTable} (all of this table's features
 *       are supported) and its items mapped by {@link SiftLootItems}: Sift plants become their
 *       vanilla stand-in items and {@code the_sift:music_disc_rift} a vanilla disc; the Siftite upgrade
 *       template is a content item of the port.</li>
 *   <li>Template chests carry the mod's extra {@code ichorlogged} property; it is folded into
 *       {@code waterlogged} (Ichor is water here). Chest facing is rotated/mirrored from the
 *       template's own value.</li>
 *   <li>The mod's {@code canBeReplaced()} is approximated by {@link SiftLandmarkTerrain}; the
 *       foundation and chunk-bounds checks are identical.</li>
 * </ul>
 */
final class AbandonedMainPortalFeature implements SiftFeature {

    static final String LOOT_TABLE = "the_sift:chests/abandoned_portal";

    private static final String[] OVERGROWN_TEMPLATES = templateIds("abandoned_portal_overgrown_");
    private static final String[] WASTES_TEMPLATES = templateIds("abandoned_portal_wastes_");
    private static final int MAX_RELIEF = 18;
    private static final int FOUNDATION_BLEND_RADIUS = 12;
    private static final int MAX_EARTHWORK = 8;
    private static final int CHEST_SLOTS = 27;

    /** The block types the mod's {@code naturalBlocks} collects, in its order. */
    private static final List<String> NATURAL_BLOCKS = List.of("the_sift:siftslate", "the_sift:siftslate_growth",
        "the_sift:healthy_sculk", "the_sift:dry_healthy_sculk", "the_sift:dry_healthy_sculk_growth");
    /** Template blocks that are never placed ({@code BlockIgnoreProcessor.STRUCTURE_AND_AIR}, structure voids). */
    private static final Set<String> IGNORED_BLOCKS = Set.of("minecraft:air", "minecraft:structure_block",
        "minecraft:structure_void");
    private static final String[] FACINGS = {"north", "east", "south", "west"};

    private record TerrainColumn(int x, int z, int naturalTop, int targetTop, BlockStateSpec surface) {
    }

    /** The chosen placement: every template block in world space (in template order), its terrain blocks and the earthwork. */
    private record PortalPlan(Rotation rotation, Mirror mirror, List<TemplateBlock> blocks, List<TemplateBlock> naturalBlocks,
                              List<TerrainColumn> terrain, int relief) {
    }

    private final SiftTemplates templates;
    private final Map<String, Optional<SiftLootTable>> lootTables = new ConcurrentHashMap<>();

    AbandonedMainPortalFeature(SiftTemplates templates) {
        this.templates = templates;
    }

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        if (!SiftLandmarkPlacement.isSelectedChunk(region.seed(), x, z, SiftLandmarkPlacement.ABANDONED_PORTAL_CELL_CHUNKS,
            SiftLandmarkPlacement.ABANDONED_PORTAL_SALT, SiftLandmarkPlacement.ABANDONED_PORTAL_CANDIDATES)) {
            return false;
        }
        SiftWorldgenBounds bounds = SiftWorldgenBounds.aroundWithNeighbourMargin(x, z);
        int centerX = (x & ~15) + 8;
        int centerZ = (z & ~15) + 8;
        int surfaceY = SiftLandmarkTerrain.surfaceBlockY(region, centerX, centerZ);
        if (surfaceY <= region.minY() + 4) {
            return false;
        }
        String path = templatePath(region.biome(centerX, surfaceY, centerZ), random);
        Optional<Template> found = templates.template(path);
        if (found.isEmpty()) {
            return false;
        }
        Template template = found.get();
        Rotation[] rotations = Rotation.values();
        int firstRotation = random.nextInt(rotations.length);
        Mirror mirror = random.nextBoolean() ? Mirror.NONE : Mirror.LEFT_RIGHT;
        PortalPlan plan = null;
        for (int attempt = 0; attempt < rotations.length; attempt++) {
            PortalPlan candidate = planPlacement(region, bounds, template, rotations[(firstRotation + attempt) % rotations.length],
                mirror, centerX, centerZ);
            if (candidate == null || plan != null && candidate.relief() >= plan.relief()) {
                continue;
            }
            plan = candidate;
        }
        if (plan == null) {
            return false;
        }
        List<Pos> foundationSurfaces = applyFoundation(region, plan.terrain());
        placeBlocks(region, template, plan.blocks(), plan.rotation(), plan.mirror());
        for (TemplateBlock info : plan.naturalBlocks()) {
            SiftSurfaceDecorator.decorateStructureSurface(region, random, new Pos(info.x(), info.y(), info.z()), 0.96F);
        }
        for (Pos surface : foundationSurfaces) {
            SiftSurfaceDecorator.decorate(region, random, surface, 2.85F);
        }
        fillChests(region, random, templates.lootTables(path), plan.blocks());
        return true;
    }

    private static String[] templateIds(String prefix) {
        String[] ids = new String[5];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = prefix + (index + 1);
        }
        return ids;
    }

    /** {@code chooseTemplate}: overgrown biomes (and Ichor peaks) get the overgrown ruins, everything else the wastes ones. */
    static String templatePath(String biome, Random random) {
        String biomePath = biome.substring(biome.indexOf(':') + 1);
        boolean overgrown = biomePath.startsWith("overgrown_") || biomePath.equals("ichor_snowy_peaks");
        String[] candidates = overgrown ? OVERGROWN_TEMPLATES : WASTES_TEMPLATES;
        return candidates[random.nextInt(candidates.length)];
    }

    private static PortalPlan planPlacement(WorldRegion region, SiftWorldgenBounds bounds, Template template, Rotation rotation,
                                            Mirror mirror, int centerX, int centerZ) {
        List<TemplateBlock> relative = naturalBlocks(template, SiftTemplates.transform(template, 0, 0, 0, 0, 0, rotation, mirror));
        if (relative.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TemplateBlock info : relative) {
            minX = Math.min(minX, info.x());
            maxX = Math.max(maxX, info.x());
            minZ = Math.min(minZ, info.z());
            maxZ = Math.max(maxZ, info.z());
        }
        int baseY = relative.getFirst().y();
        int originX = centerX - Math.floorDiv(minX + maxX + 1, 2);
        int originZ = centerZ - Math.floorDiv(minZ + maxZ + 1, 2);
        int[] heights = new int[(maxX - minX + 1) * (maxZ - minZ + 1)];
        int count = 0;
        for (int x = originX + minX; x <= originX + maxX; x++) {
            for (int z = originZ + minZ; z <= originZ + maxZ; z++) {
                if (!bounds.contains(x, z)) {
                    return null;
                }
                int height = SiftLandmarkTerrain.surfaceBlockY(region, x, z);
                if (height <= region.minY() + 4) {
                    return null;
                }
                heights[count++] = height;
            }
        }
        Arrays.sort(heights);
        int relief = heights[heights.length - 1] - heights[0];
        if (relief > MAX_RELIEF) {
            return null;
        }
        int groundY = heights[heights.length / 2] + 1;
        int originY = groundY - baseY;
        List<TemplateBlock> blocks = SiftTemplates.transform(template, originX, originY, originZ, 0, 0, rotation, mirror);
        List<TemplateBlock> natural = naturalBlocks(template, blocks);
        List<TemplateBlock> all = new ArrayList<>(natural);
        for (TemplateBlock block : blocks) {
            if (block.block().equals("minecraft:chest")) {
                all.add(block);
            }
        }
        if (!allBlocksWritable(region, bounds, all)) {
            return null;
        }
        List<TerrainColumn> terrain = planFoundation(region, bounds, originX + minX, originX + maxX, originZ + minZ,
            originZ + maxZ, groundY - 1, region.seed() ^ packBlockPos(originX, originY, originZ));
        return terrain == null ? null : new PortalPlan(rotation, mirror, blocks, natural, terrain, relief);
    }

    /**
     * The terrain blocks among a transformed template ({@code StructureTemplate.filterBlocks}), grouped by
     * block type in the mod's order (each group in the template's y/x/z order, as vanilla stores them) and
     * stably sorted by height, so the lowest block comes first.
     */
    private static List<TemplateBlock> naturalBlocks(Template template, List<TemplateBlock> transformed) {
        List<TemplateBlock> blocks = new ArrayList<>();
        for (String natural : NATURAL_BLOCKS) {
            List<Integer> indices = new ArrayList<>();
            for (int index = 0; index < transformed.size(); index++) {
                if (template.blocks().get(index).block().equals(natural)) {
                    indices.add(index);
                }
            }
            indices.sort(Comparator.<Integer>comparingInt(index -> template.blocks().get(index).y())
                .thenComparingInt(index -> template.blocks().get(index).x())
                .thenComparingInt(index -> template.blocks().get(index).z()));
            for (int index : indices) {
                blocks.add(transformed.get(index));
            }
        }
        blocks.sort(Comparator.comparingInt(TemplateBlock::y));
        return blocks;
    }

    private static boolean allBlocksWritable(WorldRegion region, SiftWorldgenBounds bounds, List<TemplateBlock> blocks) {
        for (TemplateBlock info : blocks) {
            if (bounds.contains(info.x(), info.z()) && region.isWritable(info.x(), info.y(), info.z())
                && SiftLandmarkTerrain.canReplace(region, info.x(), info.y(), info.z())) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static List<TerrainColumn> planFoundation(WorldRegion region, SiftWorldgenBounds bounds, int minX, int maxX, int minZ,
                                                      int maxZ, int floorY, long noiseSalt) {
        List<TerrainColumn> terrain = new ArrayList<>();
        for (int x = minX - FOUNDATION_BLEND_RADIUS; x <= maxX + FOUNDATION_BLEND_RADIUS; x++) {
            for (int z = minZ - FOUNDATION_BLEND_RADIUS; z <= maxZ + FOUNDATION_BLEND_RADIUS; z++) {
                if (!bounds.contains(x, z)) {
                    continue;
                }
                int dx = Math.max(0, Math.max(minX - x, x - maxX));
                int dz = Math.max(0, Math.max(minZ - z, z - maxZ));
                double distance = Math.sqrt(dx * dx + dz * dz);
                double edge = FOUNDATION_BLEND_RADIUS + blendNoise(x, z, noiseSalt) * 1.35;
                if (distance > edge) {
                    continue;
                }
                boolean core = dx == 0 && dz == 0;
                int naturalTop = SiftLandmarkTerrain.surfaceBlockY(region, x, z);
                if (naturalTop == Integer.MIN_VALUE) {
                    if (!core) {
                        continue;
                    }
                    return null;
                }
                if (core && Math.abs(floorY - naturalTop) > MAX_EARTHWORK) {
                    return null;
                }
                double t = Math.max(0.0, 1.0 - distance / edge);
                double weight = t * t * (3.0 - 2.0 * t);
                if (!core) {
                    weight *= Math.min(1.0, (double) bounds.distanceToEdge(x, z) / 3.0);
                }
                int adjustment = Math.max(-MAX_EARTHWORK, Math.min(MAX_EARTHWORK, floorY - naturalTop));
                int targetTop = core ? floorY : naturalTop + (int) Math.round(adjustment * weight);
                if (!core && naturalTop == targetTop) {
                    continue;
                }
                BlockStateSpec originalTop = region.state(x, naturalTop, z);
                int startY = Math.min(naturalTop, targetTop) - 2;
                int endY = Math.max(naturalTop, targetTop) + 3;
                for (int y = startY; y <= endY; y++) {
                    if (region.isWritable(x, y, z) && SiftLandmarkTerrain.canReplace(region, x, y, z)) {
                        continue;
                    }
                    return null;
                }
                BlockStateSpec surface = originalTop.equals(SiftTerrain.SIFT_RUBBLE) ? SiftTerrain.SIFTSLATE : originalTop;
                terrain.add(new TerrainColumn(x, z, naturalTop, targetTop, surface));
            }
        }
        return terrain;
    }

    private static List<Pos> applyFoundation(WorldRegion region, List<TerrainColumn> terrain) {
        List<Pos> surfaces = new ArrayList<>();
        for (TerrainColumn column : terrain) {
            int floorY = column.targetTop();
            for (int y = Math.min(column.naturalTop() + 1, floorY - 2); y <= floorY; y++) {
                BlockStateSpec current = region.state(column.x(), y, column.z());
                if (y <= column.naturalTop() && SiftLandmarkTerrain.isGround(current) && !current.equals(SiftTerrain.SIFT_RUBBLE)) {
                    continue;
                }
                region.set(column.x(), y, column.z(), SiftTerrain.SIFTSLATE);
            }
            for (int y = floorY + 1; y <= Math.max(column.naturalTop(), floorY) + 3; y++) {
                region.set(column.x(), y, column.z(), SiftTerrain.AIR);
            }
            region.set(column.x(), floorY, column.z(), column.surface());
            surfaces.add(new Pos(column.x(), floorY, column.z()));
        }
        return surfaces;
    }

    private static double blendNoise(int x, int z, long salt) {
        double phase = (double) (salt & 0xFFFFL) * 1.91E-4;
        double broad = Math.sin((double) x * 0.087 + (double) z * 0.039 + phase);
        double cross = Math.cos((double) x * 0.043 - (double) z * 0.074 - phase * 0.71);
        double detail = Math.sin((double) (x + z) * 0.023 + phase * 1.83);
        return broad * 0.5 + cross * 0.33 + detail * 0.17;
    }

    /** {@code BlockPos.asLong}. */
    private static long packBlockPos(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    /** {@code StructureTemplate.placeInWorld}: every block that survives the ignore processor, inside the writable area. */
    private static void placeBlocks(WorldRegion region, Template template, List<TemplateBlock> placed, Rotation rotation,
                                    Mirror mirror) {
        BlockStateSpec[] states = new BlockStateSpec[placed.size()];
        for (int index = 0; index < states.length; index++) {
            TemplateBlock block = placed.get(index);
            if (!IGNORED_BLOCKS.contains(block.block())) {
                states[index] = stateOf(template.blocks().get(index), block, rotation, mirror);
            }
        }
        for (int index = 0; index < states.length; index++) {
            TemplateBlock block = placed.get(index);
            if (states[index] != null && region.isWritable(block.x(), block.y(), block.z())) {
                region.set(block.x(), block.y(), block.z(), states[index]);
            }
        }
    }

    private static BlockStateSpec stateOf(TemplateBlock original, TemplateBlock placed, Rotation rotation, Mirror mirror) {
        String id = placed.block();
        if (id.startsWith("the_sift:")) {
            return SiftBlocks.remap(id.substring("the_sift:".length()), placed.properties()).orElseThrow(
                () -> new IllegalStateException("Unmapped template block " + id));
        }
        if (id.equals("minecraft:chest")) {
            return chestState(original, rotation, mirror);
        }
        return BlockStateSpec.of(id, placed.properties());
    }

    /**
     * A chest as the template stores it, rotated and mirrored like {@code ChestBlock.rotate/mirror} (taken
     * from the untransformed template block, so it does not depend on how {@link SiftTemplates} treats
     * {@code facing}); the mod's {@code ichorlogged} becomes {@code waterlogged}.
     */
    private static BlockStateSpec chestState(TemplateBlock original, Rotation rotation, Mirror mirror) {
        Map<String, String> properties = new TreeMap<>(original.properties());
        if ("true".equals(properties.remove("ichorlogged"))) {
            properties.put("waterlogged", "true");
        }
        String facing = properties.get("facing");
        if (facing != null) {
            properties.put("facing", transformFacing(facing, rotation, mirror));
        }
        if (mirror != Mirror.NONE) {
            String type = properties.get("type");
            if ("left".equals(type)) {
                properties.put("type", "right");
            } else if ("right".equals(type)) {
                properties.put("type", "left");
            }
        }
        return BlockStateSpec.of("minecraft:chest", properties);
    }

    /** Mirror (z axis for {@code LEFT_RIGHT}, x axis for {@code FRONT_BACK}) then rotate clockwise. */
    static String transformFacing(String facing, Rotation rotation, Mirror mirror) {
        int index = Arrays.asList(FACINGS).indexOf(facing);
        if (index < 0) {
            return facing;
        }
        if (mirror == Mirror.LEFT_RIGHT && index % 2 == 0 || mirror == Mirror.FRONT_BACK && index % 2 == 1) {
            index += 2;
        }
        int turns = switch (rotation) {
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
            default -> 0;
        };
        return FACINGS[(index + turns) % 4];
    }

    /** Fills the template's loot-table blocks, each from its own random drawn from the generation random. */
    private void fillChests(WorldRegion region, Random random, Map<Integer, String> loot, List<TemplateBlock> placed) {
        for (Map.Entry<Integer, String> entry : loot.entrySet()) {
            TemplateBlock block = placed.get(entry.getKey());
            Random lootRandom = new Random(random.nextLong());
            List<WorldRegion.ItemSpec> items = rollLoot(entry.getValue(), lootRandom);
            if (!items.isEmpty() && region.isWritable(block.x(), block.y(), block.z())) {
                region.fillContainer(block.x(), block.y(), block.z(), items);
            }
        }
    }

    /** One chest's contents: the loot table rolled, its items mapped (see {@link SiftLootItems}) and spread over the slots. */
    List<WorldRegion.ItemSpec> rollLoot(String tableId, Random random) {
        Optional<SiftLootTable> table = lootTables.computeIfAbsent(tableId, id -> templates.lootTableJson(id).map(SiftLootTable::parse));
        if (table.isEmpty()) {
            return List.of();
        }
        List<SiftLootTable.Stack> stacks = new ArrayList<>();
        for (SiftLootTable.Stack stack : table.get().roll(random)) {
            SiftLootItems.resolve(stack.item()).ifPresent(item -> stacks.add(new SiftLootTable.Stack(item, stack.count())));
        }
        return SiftLootTable.distribute(stacks, CHEST_SLOTS, random);
    }
}
