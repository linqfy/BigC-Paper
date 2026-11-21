package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicPassability.Voxels;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicTemplateRewriter.Jigsaw;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes an extra piece (after its connectors were rewritten) follow the playtest rules of the built-in
 * pieces, or rejects it:
 * <ol>
 *     <li>every active horizontal connector gets a 3-wide, 4-tall opening in its face;</li>
 *     <li>spawners without a free 5x5x4 volume beside them in an 11x11 room, and spawners or chests a player
 *     cannot walk up to, are removed; at most {@code maxChests} reachable chests are kept;</li>
 *     <li>the piece is rejected ({@code null}) when a player cannot walk between all its connectors (or,
 *     for a start, from its edge to its descent), or a 5x5x5 mob cannot travel between them.</li>
 * </ol>
 */
final class MythicPieceSanitizer {

    private MythicPieceSanitizer() {
    }

    private static boolean active(Jigsaw jigsaw) {
        return !jigsaw.name().equals("minecraft:empty") || !jigsaw.pool().equals("minecraft:empty");
    }

    static StructureTemplate sanitize(StructureTemplate template, int maxChests) {
        int[] size = MythicTemplateRewriter.size(template);
        List<Jigsaw> connectors = MythicTemplateRewriter.jigsaws(template).stream()
            .filter(jigsaw -> jigsaw.horizontal() && active(jigsaw)).toList();
        Set<List<Integer>> jigsawCells = new HashSet<>();
        for (Jigsaw jigsaw : MythicTemplateRewriter.jigsaws(template)) {
            jigsawCells.add(List.of(jigsaw.x(), jigsaw.y(), jigsaw.z()));
        }
        Set<List<Integer>> opening = new HashSet<>();
        for (Jigsaw jigsaw : connectors) {
            boolean alongX = jigsaw.front().equals("north") || jigsaw.front().equals("south");
            for (int lateral = -1; lateral <= 1; lateral++) {
                for (int dy = 0; dy < MythicPassability.DOOR_HEIGHT; dy++) {
                    int x = alongX ? jigsaw.x() + lateral : jigsaw.x();
                    int z = alongX ? jigsaw.z() : jigsaw.z() + lateral;
                    int y = jigsaw.y() + dy;
                    List<Integer> cell = List.of(x, y, z);
                    if (x >= 0 && z >= 0 && y < size[1] && x < size[0] && z < size[2] && !jigsawCells.contains(cell)) {
                        opening.add(cell);
                    }
                }
            }
        }
        StructureTemplate opened = opening.isEmpty() ? template
            : MythicTemplateRewriter.rewrite(template, template.id(), Jigsaw::nbt, opening);

        Voxels voxels = Voxels.of(opened);
        Set<Long> reached = MythicPassability.walk(voxels, starts(voxels, connectors));
        Set<List<Integer>> removed = new HashSet<>();
        int chests = 0;
        for (int y = 0; y < voxels.sizeY(); y++) {
            for (int z = 0; z < voxels.sizeZ(); z++) {
                for (int x = 0; x < voxels.sizeX(); x++) {
                    String name = voxels.name(x, y, z);
                    boolean reachable = MythicPassability.besideReached(reached, x, y, z);
                    if ("minecraft:spawner".equals(name) && (!reachable || !MythicPassability.spawnerOk(voxels, x, y, z))) {
                        removed.add(List.of(x, y, z));
                    } else if ("minecraft:chest".equals(name)) {
                        if (reachable && chests < maxChests) {
                            chests++;
                        } else {
                            removed.add(List.of(x, y, z));
                        }
                    }
                }
            }
        }
        StructureTemplate cleaned = removed.isEmpty() ? opened
            : MythicTemplateRewriter.rewrite(opened, opened.id(), Jigsaw::nbt, removed);

        Voxels result = Voxels.of(cleaned);
        if (!connectors.isEmpty()) {
            Set<Long> walked = MythicPassability.walk(result, starts(result, connectors));
            for (Jigsaw jigsaw : connectors) {
                if (!walked.contains(MythicPassability.key(jigsaw.x(), jigsaw.y(), jigsaw.z()))) {
                    return null;
                }
            }
        } else {
            Jigsaw descent = MythicTemplateRewriter.jigsaws(cleaned).stream()
                .filter(jigsaw -> jigsaw.front().equals("down") && active(jigsaw)).findFirst().orElse(null);
            if (descent != null && !nearDescent(MythicPassability.walk(result, starts(result, connectors)), descent)) {
                return null;
            }
        }
        if (connectors.size() >= 2) {
            Jigsaw first = connectors.getFirst();
            Set<Long> boxes = MythicPassability.mobWalk(result,
                List.of(MythicPassability.inward(first.x(), first.y(), first.z(), first.front())));
            for (Jigsaw jigsaw : connectors.subList(1, connectors.size())) {
                int[] inner = MythicPassability.inward(jigsaw.x(), jigsaw.y(), jigsaw.z(), jigsaw.front());
                if (!MythicPassability.mobCovers(boxes, inner[0], inner[1], inner[2])) {
                    return null;
                }
            }
        }
        return cleaned;
    }

    /** The first connector, or every cell on the piece's outer edge when it has none (a start). */
    private static List<int[]> starts(Voxels voxels, List<Jigsaw> connectors) {
        List<int[]> starts = new ArrayList<>();
        if (!connectors.isEmpty()) {
            Jigsaw first = connectors.getFirst();
            starts.add(new int[]{first.x(), first.y(), first.z()});
            return starts;
        }
        for (int x = 0; x < voxels.sizeX(); x++) {
            for (int z = 0; z < voxels.sizeZ(); z++) {
                if (x == 0 || z == 0 || x == voxels.sizeX() - 1 || z == voxels.sizeZ() - 1) {
                    for (int y = 1; y < voxels.sizeY(); y++) {
                        starts.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return starts;
    }

    private static boolean nearDescent(Set<Long> reached, Jigsaw descent) {
        // On the rim of the 3x3 hole, standing on the bottom layer: one step down onto the shaft's first stair.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (rim && reached.contains(MythicPassability.key(descent.x() + dx, descent.y() + 1, descent.z() + dz))) {
                    return true;
                }
            }
        }
        return false;
    }
}
