package dev.linqfy.bigCasares.modules.sift.feature;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.sift.SiftBlocks;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Mirror;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.Rotation;
import dev.linqfy.bigCasares.modules.sift.feature.SiftTemplates.TemplateBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Port of the mod's {@code OvergrownWillowTreeFeature}: places one of two
 * authored willow templates (small 38%/big) with random rotation and mirror,
 * validates the trunk footprint, adds surface roots and lengthens hanging vines.
 *
 * <p>Differences: willow vines are vanilla vines (no bottom-face variants); the
 * mod's global "nearby portal" reservation is not ported because it depends on
 * generation order, which would make output non-deterministic.</p>
 */
final class OvergrownWillowTreeFeature implements SiftFeature {

    private record Choice(String path, int rootX, int rootZ) {
    }

    private static final Choice SMALL = new Choice("overgrown_willow/small_01", 3, 6);
    private static final Choice BIG = new Choice("overgrown_willow/big_01", 7, 6);
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final String[] FACES = {"north", "east", "south", "west"};

    private final SiftTemplates templates;
    private final BlockStateSpec log;
    private final BlockStateSpec logX;
    private final BlockStateSpec logZ;
    private final BlockStateSpec wood;
    private final Set<BlockStateSpec> trunk;
    private final Set<BlockStateSpec> ground;
    private final Set<String> replaceableTypes;
    private final BlockStateSpec foliage;

    OvergrownWillowTreeFeature(SiftTemplates templates) {
        this.templates = templates;
        this.log = remap("overgrown_willow_log", Map.of("axis", "y"));
        this.logX = remap("overgrown_willow_log", Map.of("axis", "x"));
        this.logZ = remap("overgrown_willow_log", Map.of("axis", "z"));
        this.wood = remap("overgrown_willow_wood", Map.of());
        this.foliage = remap("overgrown_willow_foliage", Map.of());
        this.trunk = Set.of(log, logX, logZ, wood);
        this.ground = Set.of(remap("siftslate_growth", Map.of()), remap("healthy_sculk", Map.of()),
            remap("dry_healthy_sculk", Map.of()), remap("dry_healthy_sculk_growth", Map.of()));
        this.replaceableTypes = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:vine", "minecraft:fern",
            "minecraft:short_grass", "minecraft:bush", "minecraft:crimson_roots", "minecraft:short_dry_grass");
    }

    private static BlockStateSpec remap(String path, Map<String, String> properties) {
        return SiftBlocks.remap(path, properties).orElseThrow();
    }

    @Override
    public boolean place(WorldRegion region, Random random, int x, int y, int z) {
        // Like the mod: re-pick a root inside the chunk, away from its edges.
        int minX = x & ~15;
        int minZ = z & ~15;
        int rootX = minX + 4 + random.nextInt(8);
        int rootZ = minZ + 4 + random.nextInt(8);
        int rootY = region.height(WorldRegion.Heightmap.WORLD_SURFACE_WG, rootX, rootZ);
        if (!isGround(region, rootX, rootY - 1, rootZ)) {
            return false;
        }
        Choice choice = random.nextFloat() < 0.38F ? BIG : SMALL;
        return grow(region, random, x, z, rootX, rootY, rootZ, choice);
    }

    /**
     * The mod's {@code placeAt(level, random, root, bounds, large)}: grows a willow of the given size at an
     * explicit root (used by the Ichor lake's willow islands). {@code originX/originZ} select the chunk
     * whose neighbour ring bounds the tree, like the {@code bounds} argument of the mod.
     */
    boolean placeAt(WorldRegion region, Random random, int originX, int originZ, int rootX, int rootY, int rootZ,
                    boolean large) {
        if (!isGround(region, rootX, rootY - 1, rootZ)) {
            return false;
        }
        return grow(region, random, originX, originZ, rootX, rootY, rootZ, large ? BIG : SMALL);
    }

    /** The mod's {@code growFromSapling}: a big willow in 38 % of cases, bounded by the sapling's chunk ring. */
    boolean growFromSapling(WorldRegion region, Random random, int x, int y, int z) {
        boolean large = random.nextFloat() < 0.38F;
        return placeAt(region, random, x, z, x, y, z, large);
    }

    private boolean grow(WorldRegion region, Random random, int x, int z, int rootX, int rootY, int rootZ, Choice choice) {
        Optional<SiftTemplates.Template> template = templates.template(choice.path());
        if (template.isEmpty()) {
            return false;
        }
        Rotation rotation = Rotation.random(random);
        Mirror mirror = random.nextBoolean() ? Mirror.NONE : Mirror.LEFT_RIGHT;
        List<TemplateBlock> blocks = SiftTemplates.transform(template.get(),
            rootX - choice.rootX(), rootY, rootZ - choice.rootZ(), choice.rootX(), choice.rootZ(), rotation, mirror);

        List<TemplateBlock> logs = filter(blocks, "the_sift:overgrown_willow_log");
        List<TemplateBlock> woods = filter(blocks, "the_sift:overgrown_willow_wood");
        List<TemplateBlock> leaves = filter(blocks, "the_sift:overgrown_willow_foliage");
        List<TemplateBlock> vines = filter(blocks, "the_sift:overgrown_willow_vines", "the_sift:overgrown_willow_vines_plant");
        for (List<TemplateBlock> group : List.of(logs, woods, leaves, vines)) {
            for (TemplateBlock block : group) {
                if (!inBounds(region, block, x, z)) {
                    return false;
                }
            }
        }
        TemplateBlock base = lowestNearest(logs, rootX, rootZ);
        if (base == null || !isGround(region, base.x(), base.y() - 1, base.z()) || hasNearbyTrunk(region, base, 5)
            || !fits(region, logs) || !fits(region, woods)) {
            return false;
        }
        int placed = placeAll(region, logs) + placeAll(region, woods) + placeAll(region, leaves);
        placed += placeVines(region, vines);
        addSurfaceRoots(region, random, base);
        lengthenVines(region, random, vines);
        return placed > 0;
    }

    /** Same footprint limit as the mod: the generating chunk plus one neighbour ring, minus one block. */
    private static boolean inBounds(WorldRegion region, TemplateBlock block, int originX, int originZ) {
        int chunkX = Math.floorDiv(originX, 16);
        int chunkZ = Math.floorDiv(originZ, 16);
        int minX = ((chunkX - 1) << 4) + 1;
        int maxX = ((chunkX + 2) << 4) - 2;
        int minZ = ((chunkZ - 1) << 4) + 1;
        int maxZ = ((chunkZ + 2) << 4) - 2;
        return block.x() >= minX && block.x() <= maxX && block.z() >= minZ && block.z() <= maxZ
            && region.isWritable(block.x(), block.y(), block.z());
    }

    private static List<TemplateBlock> filter(List<TemplateBlock> blocks, String... ids) {
        List<TemplateBlock> out = new ArrayList<>();
        for (TemplateBlock block : blocks) {
            for (String id : ids) {
                if (block.block().equals(id)) {
                    out.add(block);
                }
            }
        }
        return out;
    }

    private static TemplateBlock lowestNearest(List<TemplateBlock> logs, int rootX, int rootZ) {
        TemplateBlock best = null;
        int lowest = Integer.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        for (TemplateBlock block : logs) {
            int dx = block.x() - rootX;
            int dz = block.z() - rootZ;
            int distance = dx * dx + dz * dz;
            if (block.y() < lowest || block.y() == lowest && distance < bestDistance) {
                lowest = block.y();
                bestDistance = distance;
                best = block;
            }
        }
        return best;
    }

    private boolean isGround(WorldRegion region, int x, int y, int z) {
        BlockStateSpec state = region.state(x, y, z);
        return ground.contains(state) || state.block().path().equals("grass_block") || state.block().path().equals("moss_block");
    }

    private boolean canReplace(WorldRegion region, int x, int y, int z) {
        return replaceableTypes.contains(region.blockId(x, y, z)) || region.is(x, y, z, foliage);
    }

    private boolean fits(WorldRegion region, List<TemplateBlock> blocks) {
        for (TemplateBlock block : blocks) {
            if (!canReplace(region, block.x(), block.y(), block.z())) {
                return false;
            }
        }
        return true;
    }

    private boolean hasNearbyTrunk(WorldRegion region, TemplateBlock root, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 10; dy++) {
                    int x = root.x() + dx;
                    int y = root.y() + dy;
                    int z = root.z() + dz;
                    if (region.isWritable(x, y, z) && trunk.contains(region.state(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int placeAll(WorldRegion region, List<TemplateBlock> blocks) {
        int placed = 0;
        for (TemplateBlock block : blocks) {
            if (canReplace(region, block.x(), block.y(), block.z())
                && region.set(block.x(), block.y(), block.z(), stateOf(block))) {
                placed++;
            }
        }
        return placed;
    }

    private static BlockStateSpec stateOf(TemplateBlock block) {
        String path = block.block().substring(block.block().indexOf(':') + 1);
        return SiftBlocks.remap(path, block.properties()).orElseThrow(
            () -> new IllegalStateException("Unmapped template block " + block.block()));
    }

    /**
     * Places vines top-down, keeping faces that are supported by a solid neighbour
     * or by the vine above (vanilla vine survival rules), dropping unsupported ones.
     */
    private int placeVines(WorldRegion region, List<TemplateBlock> vines) {
        List<TemplateBlock> topDown = new ArrayList<>(vines);
        topDown.sort((a, b) -> Integer.compare(b.y(), a.y()));
        int placed = 0;
        for (TemplateBlock block : topDown) {
            if (!canReplace(region, block.x(), block.y(), block.z())) {
                continue;
            }
            BlockStateSpec above = region.state(block.x(), block.y() + 1, block.z());
            boolean upSupport = region.isSolid(block.x(), block.y() + 1, block.z());
            BlockStateSpec vine = BlockStateSpec.parse("minecraft:vine");
            boolean anyFace = false;
            for (int index = 0; index < 4; index++) {
                boolean direct = region.isSolid(block.x() + HORIZONTAL[index][0], block.y(), block.z() + HORIZONTAL[index][1]);
                boolean inherited = above.block().path().equals("vine") && "true".equals(above.properties().get(FACES[index]));
                boolean authored = "true".equals(block.property(FACES[index])) && upSupport && !direct;
                boolean active = direct || inherited || authored && upSupport;
                vine = vine.with(FACES[index], Boolean.toString(active));
                anyFace |= active;
            }
            vine = vine.with("up", Boolean.toString(upSupport));
            if ((anyFace || upSupport) && region.set(block.x(), block.y(), block.z(), vine)) {
                placed++;
            }
        }
        return placed;
    }

    private void addSurfaceRoots(WorldRegion region, Random random, TemplateBlock root) {
        BlockStateSpec base = region.state(root.x(), root.y(), root.z());
        if (!trunk.contains(base) || !isGround(region, root.x(), root.y() - 1, root.z())) {
            return;
        }
        int first = random.nextInt(4);
        int count = 2 + random.nextInt(3);
        for (int index = 0; index < count; index++) {
            int direction = (first + index) % 4;
            int length = 1 + random.nextInt(3);
            BlockStateSpec pillar = direction % 2 == 0 ? logZ : logX;
            for (int step = 1; step <= length; step++) {
                int x = root.x() + HORIZONTAL[direction][0] * step;
                int z = root.z() + HORIZONTAL[direction][1] * step;
                if (!region.isWritable(x, root.y(), z) || !canReplace(region, x, root.y(), z)
                    || !isGround(region, x, root.y() - 1, z)) {
                    break;
                }
                region.set(x, root.y(), z, pillar);
            }
        }
    }

    /** Extends some vine tips downward by 1–3 blocks (34% per hanging face). */
    private static void lengthenVines(WorldRegion region, Random random, List<TemplateBlock> vines) {
        for (TemplateBlock block : vines) {
            BlockStateSpec tip = region.state(block.x(), block.y(), block.z());
            if (!tip.block().path().equals("vine") || !region.isAir(block.x(), block.y() - 1, block.z())) {
                continue;
            }
            for (String face : FACES) {
                if (!"true".equals(tip.properties().get(face)) || random.nextFloat() >= 0.34F) {
                    continue;
                }
                int extension = 1 + random.nextInt(3);
                for (int step = 1; step <= extension; step++) {
                    int y = block.y() - step;
                    if (!region.isWritable(block.x(), y, block.z()) || !region.isAir(block.x(), y, block.z())) {
                        break;
                    }
                    region.set(block.x(), y, block.z(), BlockStateSpec.parse("minecraft:vine[" + face + "=true]")
                        .with("up", "false"));
                }
            }
        }
    }
}
