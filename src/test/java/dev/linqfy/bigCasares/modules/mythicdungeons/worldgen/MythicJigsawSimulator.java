package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.nbt.Nbt.IntTag;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicTemplateRewriter.Jigsaw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * In-memory model of vanilla {@code JigsawPlacement} for rigid pieces: pieces are processed by placement
 * priority (higher first, in placement order within a priority; the priority of the parent jigsaw a piece
 * was attached through), each piece's jigsaws by selection priority (higher first, ties shuffled), shuffled
 * weighted pool candidates followed by the fallback pool, all four rotations, jigsaw matching by
 * facing/top/name, and placement only inside the max-distance box without overlapping any earlier piece.
 */
final class MythicJigsawSimulator {

    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean intersects(Box other) {
            return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
        }

        boolean within(Box other) {
            return minX >= other.minX && maxX <= other.maxX && minY >= other.minY && maxY <= other.maxY
                && minZ >= other.minZ && maxZ <= other.maxZ;
        }

        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    /**
     * A placed piece. {@code parentCell}/{@code childCell} are the world cells of the two jigsaws that joined
     * it to its parent ({@code null} for the start).
     */
    record Placed(StructureTemplate template, int rotation, int originX, int originY, int originZ, Box box, int depth,
                  int[] parentCell, int[] childCell) {
        String path() {
            return template.id().path();
        }

        /** World position of a template-local cell. */
        int[] world(int x, int y, int z) {
            int[] local = rotatePos(x, z, rotation);
            return new int[]{originX + local[0], originY + y, originZ + local[1]};
        }

        /** Template-local position of a world cell. */
        int[] local(int x, int y, int z) {
            int[] local = rotatePos(x - originX, z - originZ, (4 - rotation) % 4);
            return new int[]{local[0], y - originY, local[1]};
        }
    }

    record Result(List<Placed> pieces, Box limit, int startCentreX, int startCentreZ) {
    }

    private static final String[] HORIZONTAL = {"north", "east", "south", "west"};

    private final MythicPackIndex index;
    private final int maxDepth;
    private final int maxDistance;
    private final Map<StructureTemplate, List<Jigsaw>> jigsaws = new IdentityHashMap<>();
    private final Map<StructureTemplate, int[]> sizes = new IdentityHashMap<>();

    MythicJigsawSimulator(MythicPackIndex index, int maxDepth, int maxDistance) {
        this.index = index;
        this.maxDepth = maxDepth;
        this.maxDistance = maxDistance;
    }

    List<Jigsaw> jigsaws(StructureTemplate template) {
        return jigsaws.computeIfAbsent(template, MythicTemplateRewriter::jigsaws);
    }

    private int[] size(StructureTemplate template) {
        return sizes.computeIfAbsent(template, MythicTemplateRewriter::size);
    }

    List<Placed> assemble(String startPool, long seed, int surfaceY, int worldMinY) {
        return run(startPool, seed, surfaceY, worldMinY).pieces();
    }

    private record Pending(Placed piece, int priority, long order) {
    }

    Result run(String startPool, long seed, int surfaceY, int worldMinY) {
        Random random = new Random(seed);
        StructureTemplate start = shuffled(startPool, random).getFirst();
        int rotation = random.nextInt(4);
        Placed first = place(start, rotation, 0, surfaceY, 0, 0, null, null);
        int centerX = (first.box().minX() + first.box().maxX()) / 2;
        int centerZ = (first.box().minZ() + first.box().maxZ()) / 2;
        Box limit = new Box(centerX - maxDistance, Math.max(worldMinY, surfaceY - maxDistance), centerZ - maxDistance,
            centerX + maxDistance, surfaceY + maxDistance, centerZ + maxDistance);
        List<Placed> placed = new ArrayList<>(List.of(first));
        PriorityQueue<Pending> queue = new PriorityQueue<>(Comparator.comparingInt(Pending::priority).reversed()
            .thenComparingLong(Pending::order));
        long order = 0;
        queue.add(new Pending(first, 0, order++));
        while (!queue.isEmpty()) {
            Placed parent = queue.poll().piece();
            List<Jigsaw> parentJigsaws = new ArrayList<>(jigsaws(parent.template()));
            Collections.shuffle(parentJigsaws, random);
            parentJigsaws.sort(Comparator.comparingInt((Jigsaw jigsaw) -> intValue(jigsaw, "selection_priority")).reversed());
            for (Jigsaw jigsaw : parentJigsaws) {
                if (jigsaw.pool().equals("minecraft:empty")) {
                    continue;
                }
                String front = rotate(jigsaw.front(), parent.rotation());
                String top = rotate(jigsaw.top(), parent.rotation());
                int[] world = parent.world(jigsaw.x(), jigsaw.y(), jigsaw.z());
                int[] step = step(front);
                int targetX = world[0] + step[0];
                int targetY = world[1] + step[1];
                int targetZ = world[2] + step[2];
                List<StructureTemplate> candidates = new ArrayList<>();
                MythicPackIndex.Pool pool = index.pools.get(jigsaw.pool());
                if (parent.depth() != maxDepth) {
                    candidates.addAll(shuffled(pool.id(), random));
                }
                candidates.addAll(shuffled(pool.fallback(), random));
                Placed child = null;
                Set<StructureTemplate> failed = Collections.newSetFromMap(new IdentityHashMap<>());
                for (int c = 0; c < candidates.size() && child == null; c++) {
                    StructureTemplate candidate = candidates.get(c);
                    if (failed.contains(candidate)) {
                        continue;
                    }
                    List<Integer> rotations = new ArrayList<>(List.of(0, 1, 2, 3));
                    Collections.shuffle(rotations, random);
                    for (int r = 0; r < rotations.size() && child == null; r++) {
                        int childRotation = rotations.get(r);
                        List<Jigsaw> childJigsaws = new ArrayList<>(jigsaws(candidate));
                        Collections.shuffle(childJigsaws, random);
                        for (Jigsaw childJigsaw : childJigsaws) {
                            String childFront = rotate(childJigsaw.front(), childRotation);
                            String childTop = rotate(childJigsaw.top(), childRotation);
                            boolean attach = childFront.equals(opposite(front))
                                && ("rollable".equals(joint(jigsaw)) || top.equals(childTop))
                                && jigsaw.target().equals(childJigsaw.name());
                            if (!attach) {
                                continue;
                            }
                            int[] local = rotatePos(childJigsaw.x(), childJigsaw.z(), childRotation);
                            Placed option = place(candidate, childRotation, targetX - local[0], targetY - childJigsaw.y(),
                                targetZ - local[1], parent.depth() + 1, world, new int[]{targetX, targetY, targetZ});
                            if (option.box().within(limit) && placed.stream().noneMatch(p -> p.box().intersects(option.box()))) {
                                child = option;
                                break;
                            }
                        }
                    }
                    if (child == null) {
                        failed.add(candidate);
                    }
                }
                if (child != null) {
                    placed.add(child);
                    if (child.depth() <= maxDepth) {
                        queue.add(new Pending(child, intValue(jigsaw, "placement_priority"), order++));
                    }
                }
            }
        }
        return new Result(placed, limit, centerX, centerZ);
    }

    static int intValue(Jigsaw jigsaw, String key) {
        return jigsaw.nbt() == null || !(jigsaw.nbt().get(key) instanceof IntTag tag) ? 0 : tag.value();
    }

    private static String joint(Jigsaw jigsaw) {
        return jigsaw.nbt() == null ? "aligned" : String.valueOf(jigsaw.nbt().getString("joint"));
    }

    private List<StructureTemplate> shuffled(String pool, Random random) {
        MythicPackIndex.Pool definition = index.pools.get(pool);
        List<StructureTemplate> result = new ArrayList<>();
        if (definition == null) {
            return result;
        }
        for (MythicPackIndex.Element element : definition.elements()) {
            for (int copy = 0; copy < element.weight(); copy++) {
                result.add(index.templates.get(element.location()));
            }
        }
        Collections.shuffle(result, random);
        return result;
    }

    private Placed place(StructureTemplate template, int rotation, int x, int y, int z, int depth, int[] parentCell,
                         int[] childCell) {
        int[] size = size(template);
        int[] a = rotatePos(0, 0, rotation);
        int[] b = rotatePos(size[0] - 1, size[2] - 1, rotation);
        Box box = new Box(x + Math.min(a[0], b[0]), y, z + Math.min(a[1], b[1]),
            x + Math.max(a[0], b[0]), y + size[1] - 1, z + Math.max(a[1], b[1]));
        return new Placed(template, rotation, x, y, z, box, depth, parentCell, childCell);
    }

    /** Vanilla rotation about the template origin: clockwise 90 maps (x, z) to (-z, x). */
    static int[] rotatePos(int x, int z, int rotation) {
        return switch (rotation) {
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    static String rotate(String direction, int rotation) {
        for (int index = 0; index < HORIZONTAL.length; index++) {
            if (HORIZONTAL[index].equals(direction)) {
                return HORIZONTAL[(index + rotation) % 4];
            }
        }
        return direction;
    }

    private static String opposite(String direction) {
        return switch (direction) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            case "west" -> "east";
            case "up" -> "down";
            default -> "up";
        };
    }

    private static int[] step(String direction) {
        return switch (direction) {
            case "north" -> new int[]{0, 0, -1};
            case "south" -> new int[]{0, 0, 1};
            case "east" -> new int[]{1, 0, 0};
            case "west" -> new int[]{-1, 0, 0};
            case "up" -> new int[]{0, 1, 0};
            default -> new int[]{0, -1, 0};
        };
    }
}
