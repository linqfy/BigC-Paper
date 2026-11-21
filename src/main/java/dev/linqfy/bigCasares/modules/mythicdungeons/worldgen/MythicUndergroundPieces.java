package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicCanvas.Face;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicPieces.Exit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pieces of the {@link MythicDungeonTheme.Layout#UNDERGROUND} family that are not shared with
 * {@link MythicPieces}: the spine hub and gallery, the branch tunnels and junctions and the culture
 * cavern. Floors are at y = 0, every doorway at walking level {@link MythicPieces#STAND}.
 *
 * <p>Wing heads (pieces that may hang directly off a spine piece) are at most 9 blocks wide along
 * their entry face, entered at its centre: spine side doorways are at least 4 blocks from both ends
 * of their face, so a wing head never reaches into the strip of the next spine piece.</p>
 */
final class MythicUndergroundPieces {

    private static final int STAND = MythicPieces.STAND;

    private MythicUndergroundPieces() {
    }

    /** 19x12x19 crossing hall: spine through north/south, two wings east and two west, culture set-piece. */
    static StructureTemplate hub(MythicDungeonTheme theme, NamespacedId next, NamespacedId wing) {
        MythicCanvas canvas = new MythicCanvas(MythicPieces.template(theme, "hub"), theme, 19, 12, 19);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.room(0, 0, 0, 18, 11, 18);
        for (int x = 1; x < 18; x++) {
            for (int z = 1; z < 18; z++) {
                double distance = Math.hypot(x - 9, z - 9);
                if (distance >= 6.5 && distance < 7.5) {
                    canvas.set(x, 0, z, palette.accent2());
                }
            }
        }
        // Pilasters in the walls keep the floor free around the set-piece.
        for (int i : new int[]{4, 14}) {
            for (int y = 1; y <= 10; y++) {
                canvas.set(i, y, 0, palette.pillar()).set(i, y, 18, palette.pillar());
            }
        }
        canvas.ceilingLights(3, 3, 15, 15, 11, 6);
        canvas.set(9, 10, 4, palette.hangingLight()).set(9, 10, 14, palette.hangingLight());
        setPiece(canvas, theme, 9, 9, 0);
        canvas.chest(17, 1, 17, "west", theme.commonLoot());
        MythicPieces.entry(canvas, theme, 9, STAND);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.spine(Face.SOUTH, 9, next),
            Exit.branch(Face.WEST, 4, wing), Exit.branch(Face.WEST, 14, wing),
            Exit.branch(Face.EAST, 4, wing), Exit.branch(Face.EAST, 14, wing)));
        return canvas.build();
    }

    /** 15x9x13 pillared gallery before the arena; wings east and west. */
    static StructureTemplate gallery(MythicDungeonTheme theme, NamespacedId next, NamespacedId wing) {
        MythicCanvas canvas = new MythicCanvas(MythicPieces.template(theme, "gallery"), theme, 15, 9, 13);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.room(0, 0, 0, 14, 8, 12);
        for (int x : new int[]{3, 11}) {
            for (int y = 1; y <= 7; y++) {
                canvas.set(x, y, 0, palette.pillar()).set(x, y, 12, palette.pillar());
            }
        }
        for (int z = 1; z <= 11; z++) {
            canvas.set(7, 0, z, palette.accent2());
        }
        canvas.ceilingLights(3, 2, 11, 10, 8, 4);
        canvas.spawner(1, 1, 2, theme.spawnerMob());
        canvas.spawner(13, 1, 10, theme.spawnerMob());
        canvas.chest(13, 1, 1, "west", theme.treasureLoot());
        MythicPieces.entry(canvas, theme, 7, STAND);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.spine(Face.SOUTH, 7, next),
            Exit.branch(Face.WEST, 6, wing), Exit.branch(Face.EAST, 6, wing)));
        return canvas.build();
    }

    /** 7x7x11 straight tunnel with stone ribs. */
    static StructureTemplate tunnel(MythicDungeonTheme theme, NamespacedId branch) {
        MythicCanvas canvas = new MythicCanvas(MythicPieces.template(theme, "tunnel"), theme, 7, 7, 11);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.room(0, 0, 0, 6, 6, 10);
        for (int z : new int[]{3, 7}) {
            for (int y = 1; y <= 5; y++) {
                canvas.set(0, y, z, palette.accent2()).set(6, y, z, palette.accent2());
            }
            canvas.fill(1, 6, z, 5, 6, z, palette.accent2());
        }
        canvas.set(3, 6, 5, palette.light());
        MythicPieces.entry(canvas, theme, 3, STAND);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.branch(Face.SOUTH, 3, branch)));
        return canvas.build();
    }

    /** 9x7x9 junction entered from the north; {@code faces} are its exits. */
    static StructureTemplate junction(MythicDungeonTheme theme, String name, List<Face> faces, NamespacedId branch) {
        MythicCanvas canvas = new MythicCanvas(MythicPieces.template(theme, name), theme, 9, 7, 9);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.room(0, 0, 0, 8, 6, 8);
        for (int[] corner : new int[][]{{1, 1}, {7, 1}, {1, 7}, {7, 7}}) {
            canvas.pillar(corner[0], 1, corner[1], 5);
        }
        canvas.set(4, 6, 4, palette.light());
        canvas.set(4, 0, 4, palette.accent2());
        List<Exit> exits = new ArrayList<>();
        for (Face face : faces) {
            exits.add(Exit.branch(face, 4, branch));
        }
        MythicPieces.entry(canvas, theme, 4, STAND);
        MythicPieces.exits(canvas, theme, STAND, exits);
        return canvas.build();
    }

    /**
     * 21x12x25 culture cavern (Helheim hall, throne of Irkalla, Yomi grove, Salamanca) with the set-piece
     * at the back and an open floor in front; exits east and west.
     */
    static StructureTemplate cavern(MythicDungeonTheme theme, NamespacedId branch) {
        MythicCanvas canvas = new MythicCanvas(MythicPieces.template(theme, "cavern"), theme, 21, 12, 25);
        MythicDungeonTheme.Palette palette = theme.palette();
        canvas.room(0, 0, 0, 20, 11, 24);
        // Rough rock bulging from the walls above head height, clear of the doorways.
        Random random = canvas.random();
        for (int i = 1; i < 24; i++) {
            for (int y = 3; y <= 9; y++) {
                if (random.nextInt(3) == 0) {
                    if (i < 4 || i > 10) {
                        canvas.set(1, y, i, canvas.wallBlock()).set(19, y, i, canvas.wallBlock());
                    }
                    if (i < 20 && (i < 7 || i > 13)) {
                        canvas.set(i, y, 1, canvas.wallBlock()).set(i, y, 23, canvas.wallBlock());
                    }
                }
            }
        }
        canvas.ceilingLights(4, 4, 16, 22, 11, 6);
        setPiece(canvas, theme, 10, 19, 0);
        switch (theme.entrance()) {
            case LONGHOUSE -> {
                for (int z = 17; z <= 21; z++) {
                    canvas.set(4, 1, z, palette.slab("top")).set(16, 1, z, palette.slab("top"));
                    canvas.set(3, 1, z, palette.stairs("east", false)).set(17, 1, z, palette.stairs("west", false));
                }
            }
            case ZIGGURAT -> {
                canvas.fill(3, 1, 21, 5, 1, 22, palette.accent());
                canvas.set(4, 2, 21, palette.stairs("north", false));
                canvas.fill(3, 2, 22, 5, 4, 22, palette.accent2());
            }
            case TORII_SHRINE -> {
                for (int[] spot : new int[][]{{3, 17}, {17, 17}, {3, 21}, {17, 21}}) {
                    canvas.set(spot[0], 1, spot[1], BlockStateSpec.of("minecraft:stone_bricks"));
                    canvas.set(spot[0], 2, spot[1], BlockStateSpec.of("minecraft:lantern", Map.of("hanging", "false")));
                }
            }
            case CAVE_MOUTH -> {
                for (int[] spot : new int[][]{{3, 18}, {17, 18}, {4, 22}, {16, 22}}) {
                    canvas.set(spot[0], 1, spot[1], palette.accent());
                    canvas.set(spot[0], 2, spot[1], palette.clutter());
                }
            }
            default -> canvas.set(4, 1, 21, palette.accent());
        }
        canvas.spawner(1, 1, 12, theme.spawnerMob());
        canvas.spawner(19, 1, 12, theme.spawnerMob());
        canvas.chest(10, 1, 23, "north", theme.treasureLoot());
        MythicPieces.entry(canvas, theme, 10, STAND);
        MythicPieces.exits(canvas, theme, STAND, List.of(Exit.branch(Face.WEST, 7, branch),
            Exit.branch(Face.EAST, 7, branch)));
        return canvas.build();
    }

    /** The culture's centrepiece on a 7x7 footprint around {@code (cx, cz)}, standing on floor layer {@code floor}. */
    static void setPiece(MythicCanvas canvas, MythicDungeonTheme theme, int cx, int cz, int floor) {
        MythicDungeonTheme.Palette palette = theme.palette();
        int y = floor + 1;
        switch (theme.entrance()) {
            case LONGHOUSE -> {
                // Root of Yggdrasil reaching into the ceiling, frost at its feet.
                BlockStateSpec trunk = BlockStateSpec.of("minecraft:spruce_log", Map.of("axis", "y"));
                canvas.fill(cx - 1, y, cz - 1, cx + 1, y + 8, cz + 1, trunk);
                for (int d = 2; d <= 3; d++) {
                    canvas.set(cx - d, y, cz, BlockStateSpec.of("minecraft:spruce_log", Map.of("axis", "x")));
                    canvas.set(cx + d, y, cz, BlockStateSpec.of("minecraft:spruce_log", Map.of("axis", "x")));
                    canvas.set(cx, y, cz - d, BlockStateSpec.of("minecraft:spruce_log", Map.of("axis", "z")));
                    canvas.set(cx, y, cz + d, BlockStateSpec.of("minecraft:spruce_log", Map.of("axis", "z")));
                }
                for (int[] corner : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                    canvas.set(cx + corner[0], floor, cz + corner[1], palette.accent());
                    canvas.set(cx + corner[0], y, cz + corner[1], palette.light());
                }
            }
            case ZIGGURAT -> {
                // Stepped altar of the underworld gates with a lapis band.
                canvas.fill(cx - 3, y, cz - 3, cx + 3, y, cz + 3, palette.wall());
                canvas.fill(cx - 2, y + 1, cz - 2, cx + 2, y + 1, cz + 2, palette.wallAlt());
                canvas.fill(cx - 1, y + 2, cz - 1, cx + 1, y + 2, cz + 1, palette.accent());
                canvas.set(cx, y + 3, cz, palette.light());
                for (int i = -3; i <= 3; i++) {
                    canvas.set(cx + i, y, cz - 3, palette.stairs("south", false));
                    canvas.set(cx + i, y, cz + 3, palette.stairs("north", false));
                }
            }
            case TORII_SHRINE -> {
                // Vermilion torii with stone lanterns.
                canvas.fill(cx - 2, y, cz, cx - 2, y + 4, cz, palette.accent());
                canvas.fill(cx + 2, y, cz, cx + 2, y + 4, cz, palette.accent());
                canvas.fill(cx - 3, y + 5, cz, cx + 3, y + 5, cz, palette.ceiling());
                canvas.fill(cx - 3, y + 6, cz, cx + 3, y + 6, cz, palette.slab("bottom"));
                for (int[] corner : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                    canvas.set(cx + corner[0], y, cz + corner[1], BlockStateSpec.of("minecraft:stone_bricks"));
                    canvas.set(cx + corner[0], y + 1, cz + corner[1],
                        BlockStateSpec.of("minecraft:lantern", Map.of("hanging", "false")));
                }
            }
            case CAVE_MOUTH -> {
                // Salamanca pool with an amethyst idol.
                for (int x = cx - 3; x <= cx + 3; x++) {
                    for (int z = cz - 3; z <= cz + 3; z++) {
                        boolean rim = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3;
                        canvas.set(x, y, z, rim ? palette.wallAlt() : BlockStateSpec.of("minecraft:water"));
                    }
                }
                canvas.set(cx, y, cz, palette.accent());
                canvas.set(cx, y + 1, cz, palette.clutter());
                canvas.set(cx - 3, y + 1, cz - 3, palette.light()).set(cx + 3, y + 1, cz + 3, palette.light());
            }
            default -> {
                canvas.pillar(cx, y, cz, y + 4);
                canvas.set(cx, y + 5, cz, palette.light());
            }
        }
    }
}
