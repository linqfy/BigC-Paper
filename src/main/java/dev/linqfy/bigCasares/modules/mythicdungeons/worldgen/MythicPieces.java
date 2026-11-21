package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicCanvas.Face;

import java.util.List;
import java.util.Locale;

/**
 * Pieces and connector rules shared by every culture, plus the underground rooms reused by
 * {@link MythicUndergroundPieces}. Every piece is authored facing the same way: it is entered
 * through its north face (z = 0); its other doorways are {@link Exit}s.
 *
 * <p>Jigsaw contract: every doorway is 3 wide and 4 tall with its jigsaw at the bottom-centre
 * cell (the walking level). A child's entry jigsaw is named {@code <culture>/door} (pool and target
 * {@code minecraft:empty}); exits target that name. A <em>spine</em> exit continues the guaranteed
 * path to the arena: selection priority {@link #SPINE_SELECTION} makes it the first doorway of its
 * piece to get a child, placement priority {@link #SPINE_PLACEMENT} makes vanilla process that child
 * before any branch piece, so the whole spine is laid before the branches grow. Branch exits use
 * priority 0 and fall back to the seal.</p>
 */
final class MythicPieces {

    static final NamespacedId EMPTY = NamespacedId.minecraft("empty");
    /** Walking level of every horizontal doorway of an underground piece, one above the floor layer. */
    static final int STAND = 1;
    static final int SPINE_SELECTION = 2;
    static final int SPINE_PLACEMENT = 1;

    enum RoomVariant { CRYPT, SHRINE, PILLARED, LIBRARY }

    enum SideVariant { TREASURY, DEN, OSSUARY }

    /** A doorway on a face other than the entry: {@code along} is x on north/south faces, z on east/west. */
    record Exit(Face face, int along, NamespacedId pool, boolean spine) {
        static Exit spine(Face face, int along, NamespacedId pool) {
            return new Exit(face, along, pool, true);
        }

        static Exit branch(Face face, int along, NamespacedId pool) {
            return new Exit(face, along, pool, false);
        }
    }

    private MythicPieces() {
    }

    static NamespacedId door(MythicDungeonTheme theme) {
        return theme.id("door");
    }

    static NamespacedId shaftTop(MythicDungeonTheme theme) {
        return theme.id("shaft_top");
    }

    static NamespacedId template(MythicDungeonTheme theme, String name) {
        return theme.id("pieces/" + name);
    }

    /** The entry doorway in the north face (z = 0). */
    static void entry(MythicCanvas canvas, MythicDungeonTheme theme, int x, int y) {
        canvas.doorway(Face.NORTH, x, y, 0);
        canvas.jigsaw(x, y, 0, Face.NORTH.orientation(), door(theme), EMPTY, EMPTY, MythicCanvas.AIR, 0);
    }

    /** Carves every exit at walking level {@code stand} and places its jigsaw. */
    static void exits(MythicCanvas canvas, MythicDungeonTheme theme, int stand, List<Exit> exits) {
        for (Exit exit : exits) {
            int x = switch (exit.face()) {
                case WEST -> 0;
                case EAST -> canvas.sizeX() - 1;
                default -> exit.along();
            };
            int z = switch (exit.face()) {
                case NORTH -> 0;
                case SOUTH -> canvas.sizeZ() - 1;
                default -> exit.along();
            };
            canvas.doorway(exit.face(), x, stand, z);
            canvas.jigsaw(x, stand, z, exit.face().orientation(), EMPTY, door(theme), exit.pool(), MythicCanvas.AIR,
                exit.spine() ? SPINE_SELECTION : 0, exit.spine() ? SPINE_PLACEMENT : 0);
        }
    }

    /** 5x6x1 solid plug used as fallback for any doorway that could not get a piece. */
    static StructureTemplate seal(MythicDungeonTheme theme) {
        MythicCanvas canvas = new MythicCanvas(template(theme, "seal"), theme, 5, 6, 1);
        canvas.wall(0, 0, 0, 4, 5, 0);
        canvas.jigsaw(2, 1, 0, Face.NORTH.orientation(), door(theme), EMPTY, EMPTY, theme.palette().wall(), 0);
        return canvas.build();
    }

    /**
     * Straight stair under the entrance: entered from above through a 3x3 opening in its
     * top layer (vertical jigsaw), descends 12 blocks southwards to a doorway.
     */
    static StructureTemplate shaft(MythicDungeonTheme theme, String name, NamespacedId next) {
        MythicCanvas canvas = new MythicCanvas(template(theme, name), theme, 7, 13, 15);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.room(0, 0, 0, 6, 12, 14);
        // Open under the entrance hole and one row further, so every step keeps three free blocks above it.
        canvas.air(2, 12, 1, 4, 12, 4);
        for (int z = 1; z <= 12; z++) {
            int top = 13 - z;
            int fromX = z == 1 ? 2 : 1;
            int toX = z == 1 ? 4 : 5;
            for (int x = fromX; x <= toX; x++) {
                if (top > 1) {
                    canvas.wall(x, 1, z, x, top - 1, z);
                }
                canvas.set(x, top, z, palette.stairs("north", false));
            }
            if (z == 1) {
                canvas.wall(1, 1, 1, 1, 11, 1);
                canvas.wall(5, 1, 1, 5, 11, 1);
            }
            if (z % 3 == 0) {
                int lightY = Math.min(11, top + 2);
                canvas.set(0, lightY, z, palette.light());
                canvas.set(6, lightY, z, palette.light());
            }
        }
        canvas.jigsaw(3, 12, 2, "up_south", shaftTop(theme), EMPTY, EMPTY, MythicCanvas.AIR, 0);
        exits(canvas, theme, STAND, List.of(Exit.spine(Face.SOUTH, 3, next)));
        return canvas.build();
    }

    /**
     * 15x9x15 room entered at x = 7; the variant decides furniture and spawners. Furniture and spawners
     * stand only in the ring of cells along the walls (never in front of a doorway), so the 11x11 floor
     * in the middle stays open for large mobs and every spawner has free space beside it.
     */
    static StructureTemplate room(MythicDungeonTheme theme, String name, RoomVariant variant, List<Exit> roomExits) {
        MythicCanvas canvas = new MythicCanvas(template(theme, name), theme, 15, 9, 15);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.room(0, 0, 0, 14, 8, 14);
        canvas.ceilingLights(3, 3, 11, 11, 8, 8);
        canvas.set(7, 8, 7, palette.light());
        for (int x = 1; x <= 13; x++) {
            canvas.set(x, 0, 7, palette.accent2());
        }
        canvas.set(7, 7, 3, palette.hangingLight()).set(7, 7, 11, palette.hangingLight());
        switch (variant) {
            case CRYPT -> {
                for (int z : new int[]{2, 3, 4, 10, 11, 12}) {
                    for (int x : new int[]{1, 13}) {
                        canvas.set(x, 1, z, palette.accent2());
                        canvas.set(x, 2, z, palette.slab("bottom"));
                    }
                }
                canvas.spawner(3, 1, 1, theme.spawnerMob());
                canvas.spawner(11, 1, 13, theme.spawnerMob());
                canvas.set(1, 1, 13, palette.clutter()).set(13, 1, 1, palette.clutter());
            }
            case SHRINE -> {
                canvas.set(1, 1, 7, palette.accent());
                canvas.set(1, 2, 7, palette.clutter());
                canvas.pillar(1, 1, 5, 6).pillar(1, 1, 9, 6);
                canvas.set(1, 0, 6, palette.accent()).set(1, 0, 8, palette.accent());
                canvas.spawner(13, 1, 3, theme.spawnerMob());
            }
            case PILLARED -> {
                for (int i : new int[]{2, 4, 10, 12}) {
                    canvas.pillar(1, 1, i, 7).pillar(13, 1, i, 7).pillar(i, 1, 13, 7);
                }
                canvas.spawner(3, 1, 1, theme.spawnerMob());
                canvas.spawner(11, 1, 1, theme.spawnerMob());
            }
            case LIBRARY -> {
                BlockStateSpec shelf = BlockStateSpec.of("minecraft:bookshelf");
                canvas.fill(1, 1, 1, 1, 3, 13, shelf);
                canvas.fill(2, 1, 13, 12, 3, 13, shelf);
                canvas.set(13, 1, 13, palette.accent2());
                canvas.spawner(13, 1, 2, theme.spawnerMob());
            }
        }
        entry(canvas, theme, 7, STAND);
        exits(canvas, theme, STAND, roomExits);
        return canvas.build();
    }

    /**
     * 7x15x15 grand stair: enters at walking level 9 on a landing, eight steps down, then a five-block
     * landing at walking level 1 before the exit. The ceiling stays high above every step.
     */
    static StructureTemplate stairs(MythicDungeonTheme theme, String name, NamespacedId next, boolean spine) {
        MythicCanvas canvas = new MythicCanvas(template(theme, name), theme, 7, 15, 15);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.room(0, 0, 0, 6, 14, 14);
        canvas.wall(1, 1, 1, 5, 7, 1);
        canvas.fill(1, 8, 1, 5, 8, 1, palette.floor());
        for (int z = 2; z <= 8; z++) {
            int top = 9 - z;
            if (top > 1) {
                canvas.wall(1, 1, z, 5, top - 1, z);
            }
            for (int x = 1; x <= 5; x++) {
                canvas.set(x, top, z, palette.stairs("north", false));
            }
        }
        for (int z : new int[]{2, 5, 8, 11}) {
            int lightY = Math.max(9 - z, 0) + 4;
            canvas.set(0, lightY, z, palette.light()).set(6, lightY, z, palette.light());
        }
        canvas.ceilingLights(3, 4, 3, 12, 14, 4);
        entry(canvas, theme, 3, 9);
        exits(canvas, theme, STAND, List.of(spine ? Exit.spine(Face.SOUTH, 3, next) : Exit.branch(Face.SOUTH, 3, next)));
        return canvas.build();
    }

    /** Arena size and altar position; tests and runtime-facing checks use these. */
    static final int ARENA_SIZE = 35;
    static final int ARENA_HEIGHT = 18;
    static final int ALTAR_X = 17;
    static final int ALTAR_Z = 20;
    /** The arena doorway is raised so ~2 blocks of flood water stay inside. */
    static final int ARENA_ENTRY_STAND = 4;

    /**
     * Boss arena: 33x33 interior with 16 blocks of headroom, watertight shell, raised
     * entrance with a short stair, and the lodestone altar on the floor near the centre.
     */
    static StructureTemplate arena(MythicDungeonTheme theme) {
        int max = ARENA_SIZE - 1;
        int top = ARENA_HEIGHT - 1;
        MythicCanvas canvas = new MythicCanvas(template(theme, "arena"), theme, ARENA_SIZE, ARENA_HEIGHT, ARENA_SIZE);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.room(0, 0, 0, max, top, max);
        for (int x = 1; x < max; x++) {
            for (int z = 1; z < max; z++) {
                double distance = Math.hypot(x - ALTAR_X, z - ALTAR_Z);
                if ((distance >= 5.5 && distance < 6.5) || (distance >= 10.5 && distance < 11.5)) {
                    canvas.set(x, 0, z, palette.accent());
                } else if (distance < 2.5) {
                    canvas.set(x, 0, z, palette.accent2());
                }
            }
        }
        for (int i = 0; i <= max; i++) {
            canvas.set(i, 12, 0, palette.accent2()).set(i, 12, max, palette.accent2());
            canvas.set(0, 12, i, palette.accent2()).set(max, 12, i, palette.accent2());
        }
        for (int i = 3; i < max; i += 6) {
            canvas.set(i, 6, max, palette.light()).set(0, 6, i, palette.light()).set(max, 6, i, palette.light());
        }
        canvas.ceilingLights(2, 2, 32, 32, top, 5);
        for (int z = 8; z < max; z += 6) {
            canvas.pillar(1, 1, z, top - 1);
            canvas.pillar(max - 1, 1, z, top - 1);
        }
        int door = ALTAR_X;
        canvas.wall(door - 2, 1, 1, door + 2, 2, 3);
        canvas.fill(door - 2, 3, 1, door + 2, 3, 3, palette.floor());
        for (int x = door - 2; x <= door + 2; x++) {
            canvas.set(x, 1, 4, palette.wall());
            canvas.set(x, 2, 4, palette.stairs("north", false));
            canvas.set(x, 1, 5, palette.stairs("north", false));
        }
        canvas.set(ALTAR_X, 1, ALTAR_Z, BlockStateSpec.of("minecraft:lodestone"));
        entry(canvas, theme, door, ARENA_ENTRY_STAND);
        return canvas.build();
    }

    /**
     * Dead ends entered from the north. Treasury and ossuary are 9 wide (they may hang off a spine
     * doorway) and hold no spawner, the treasury one chest; the den is a 15x15 lair with two spawners.
     */
    static StructureTemplate sideRoom(MythicDungeonTheme theme, SideVariant variant) {
        boolean den = variant == SideVariant.DEN;
        int width = den ? 15 : 9;
        int depth = den ? 15 : 9;
        int height = den ? 9 : 7;
        MythicCanvas canvas = new MythicCanvas(template(theme, "side_" + variant.name().toLowerCase(Locale.ROOT)),
            theme, width, height, depth);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.room(0, 0, 0, width - 1, height - 1, depth - 1);
        canvas.set(width / 2, height - 1, depth / 2, palette.light());
        switch (variant) {
            case TREASURY -> {
                for (int x = 2; x <= 6; x += 2) {
                    canvas.set(x, 0, 7, palette.accent());
                }
                canvas.chest(4, 1, 7, "north", theme.treasureLoot());
                canvas.pillar(1, 1, 7, 5);
                canvas.pillar(7, 1, 7, 5);
                canvas.set(4, 6, 2, palette.light());
            }
            case DEN -> {
                canvas.ceilingLights(3, 3, 11, 11, 8, 8);
                canvas.spawner(1, 1, 7, theme.spawnerMob());
                canvas.spawner(13, 1, 7, theme.spawnerMob());
                canvas.set(1, 1, 1, palette.clutter()).set(13, 1, 1, palette.clutter());
                canvas.set(1, 1, 13, palette.clutter()).set(13, 1, 13, palette.clutter());
            }
            case OSSUARY -> {
                for (int z = 2; z <= 6; z += 2) {
                    canvas.set(1, 1, z, palette.clutter()).set(7, 1, z, palette.clutter());
                    canvas.set(1, 2, z, palette.accent2()).set(7, 2, z, palette.accent2());
                }
                canvas.set(4, 1, 7, palette.accent2());
                canvas.set(4, 2, 7, palette.clutter());
                canvas.set(4, 5, 3, palette.hangingLight());
            }
        }
        entry(canvas, theme, width / 2, STAND);
        return canvas.build();
    }
}
