/*
 * Transcribed from Doomlike Dungeons (https://github.com/BlackJar72/DoomlikeDungeons),
 * MIT License, Copyright (c) 2014-2020 Jared Blackburn; 1.20 port by JaredBGreat and Xiroc.
 * The full licence text ships in mythicdungeons/third_party/doomlike_dungeons/LICENSE.
 */
package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonTheme;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty.DoomlikeLayout.Room;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty.DoomlikeLayout.Spot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Doomlike's {@code MapMatrix.buildInChunk} turned into a template writer: every map column gets
 * foundation, floor, walls or pillars or air up to its ceiling, the ceiling, doors (3 tall),
 * liquids in sunken cells and fences, then spawners and chests. Blocks come from the culture
 * palette (walls, floors, ceiling, pillars, light) and the Doomlike theme (fences, liquids).
 */
final class DoomlikeBuilder {

    private static final int MAX_SIDE_DOORS = 3;
    private static final int MAX_GATES = 2;
    private static final int MIN_OPENING_GAP = 6;
    /** Footprint side reserved for the boss arena. */
    static final int ARENA_SIDE = 35;

    private final DoomlikeLayout layout;
    private final MythicDungeonTheme theme;
    private final Random random;
    private final int minX;
    private final int minZ;
    private final TemplateGrid grid;

    private DoomlikeBuilder(DoomlikeLayout layout, MythicDungeonTheme theme, boolean crop) {
        this.layout = layout;
        this.theme = theme;
        this.random = new Random(layout.random.nextLong());
        int loX = layout.size.width();
        int loZ = layout.size.depth();
        int hiX = -1;
        int hiZ = -1;
        int top = 0;
        for (int x = 0; x < layout.size.width(); x++) {
            for (int z = 0; z < layout.size.depth(); z++) {
                if (layout.room[x][z] > 0) {
                    loX = Math.min(loX, x);
                    loZ = Math.min(loZ, z);
                    hiX = Math.max(hiX, x);
                    hiZ = Math.max(hiZ, z);
                    top = Math.max(top, layout.ceilY[x][z]);
                }
            }
        }
        if (hiX < 0) {
            throw new IllegalStateException("Empty Doomlike layout");
        }
        if (!crop) {
            loX = 0;
            loZ = 0;
            hiX = layout.size.width() - 1;
            hiZ = layout.size.depth() - 1;
        }
        minX = loX;
        minZ = loZ;
        int width = hiX - loX + 1;
        int depth = hiZ - loZ + 1;
        if (width == ARENA_SIDE && depth == ARENA_SIDE) {
            // 35x35 is reserved for the boss arena (the runtime finds the arena by that footprint): one more
            // column of structure void.
            width++;
        }
        grid = new TemplateGrid(width, top + 2, depth);
    }

    /**
     * A full dungeon: cropped to its rooms, with the descent connector in the entrance node and
     * side-room connectors and plain gates in outer walls.
     */
    static TemplateGrid dungeon(DoomlikeLayout layout, MythicDungeonTheme theme) {
        DoomlikeBuilder builder = new DoomlikeBuilder(layout, theme, true);
        builder.columns();
        builder.contents();
        builder.openings();
        Room entrance = layout.entrance;
        builder.stepsToDescent(entrance.midX, entrance.midZ);
        ThirdPartyBlocks.jigsaw(builder.grid, entrance.midX - builder.minX, 0, entrance.midZ - builder.minZ,
            "down_south", ThirdPartyBlocks.DESCENT);
        return builder.grid;
    }

    /**
     * The descent hole is cut through the bottom layer (y = 0) while the plan's floor is at
     * {@link DoomlikeLayout#BASE} - 1: two steps north of the hole bring the floor down to the hole's rim,
     * so players walk into the shaft (and back out) instead of dropping three blocks. Only cut where the
     * plan has open floor; otherwise the piece is left to the module's walkability check.
     */
    private void stepsToDescent(int midX, int midZ) {
        MythicDungeonTheme.Palette palette = theme.palette();
        for (int mx = midX - 1; mx <= midX + 1; mx++) {
            for (int mz = midZ - 3; mz <= midZ - 2; mz++) {
                if (!free(mx, mz) || layout.floorY[mx][mz] != DoomlikeLayout.BASE) {
                    return;
                }
            }
        }
        for (int mx = midX - 1; mx <= midX + 1; mx++) {
            int x = mx - minX;
            int rim = midZ - 2 - minZ;
            int step = midZ - 3 - minZ;
            grid.set(x, 0, rim, palette.floor());
            for (int y = 1; y <= DoomlikeLayout.BASE + 1; y++) {
                grid.set(x, y, rim, TemplateGrid.AIR);
            }
            grid.set(x, 1, step, palette.stairs("north", false));
            for (int y = 2; y <= DoomlikeLayout.BASE + 1; y++) {
                grid.set(x, y, step, TemplateGrid.AIR);
            }
        }
    }

    /** Chebyshev distance of the descent connector from the template centre (on the horizontal axes). */
    static double descentOffset(TemplateGrid grid) {
        double offset = Double.MAX_VALUE;
        for (TemplateGrid.Cell cell : grid.cells()) {
            if (cell.name().equals("minecraft:jigsaw") && ThirdPartyBlocks.DESCENT.equals(cell.nbt().getString("name"))) {
                offset = Math.max(Math.abs(cell.x() - (grid.sizeX() - 1) / 2.0), Math.abs(cell.z() - (grid.sizeZ() - 1) / 2.0));
            }
        }
        return offset;
    }

    /** A single room whose north doorway (opened by {@link DoomlikeLayout#openNorthDoor}) holds the entry. */
    static TemplateGrid sideRoom(DoomlikeLayout layout, MythicDungeonTheme theme, int doorX) {
        DoomlikeBuilder builder = new DoomlikeBuilder(layout, theme, false);
        builder.columns();
        builder.contents();
        ThirdPartyBlocks.jigsaw(builder.grid, doorX, DoomlikeLayout.BASE, 0, "north_up", ThirdPartyBlocks.ENTRY);
        return builder.grid;
    }

    private BlockStateSpec wall(Room room) {
        MythicDungeonTheme.Palette palette = theme.palette();
        boolean alternative = room.wallVariant == 1;
        // Degeneracy (crumbling walls in Doomlike) shows as weathered blocks here.
        if (random.nextInt(20) < layout.degeneracy.value) {
            alternative = !alternative;
        }
        return alternative ? palette.wallAlt() : palette.wall();
    }

    private BlockStateSpec floor(Room room) {
        return room.floorVariant == 1 ? theme.palette().floorAlt() : theme.palette().floor();
    }

    private void columns() {
        MythicDungeonTheme.Palette palette = theme.palette();
        for (int mx = 0; mx < layout.size.width(); mx++) {
            for (int mz = 0; mz < layout.size.depth(); mz++) {
                Room room = layout.roomAt(mx, mz);
                if (room == null) {
                    continue;
                }
                int x = mx - minX;
                int z = mz - minZ;
                int floor = Math.max(1, layout.floorY[mx][mz]);
                int ceiling = Math.max(floor + 1, layout.ceilY[mx][mz]);
                boolean door = layout.is(mx, mz, DoomlikeLayout.DOOR);
                boolean wall = layout.is(mx, mz, DoomlikeLayout.WALL);
                for (int y = 0; y < floor - 1; y++) {
                    grid.set(x, y, z, wall(room));
                }
                grid.set(x, floor - 1, z, wall && !door ? wall(room) : floor(room));
                for (int y = floor; y <= ceiling; y++) {
                    BlockStateSpec state;
                    if (wall) {
                        state = door && y <= floor + 2 ? TemplateGrid.AIR : wall(room);
                    } else if (layout.is(mx, mz, DoomlikeLayout.PILLAR)) {
                        state = palette.pillar();
                    } else {
                        state = TemplateGrid.AIR;
                    }
                    grid.set(x, y, z, state);
                }
                boolean lamp = !wall && Math.floorMod(mx, 5) == 2 && Math.floorMod(mz, 5) == 2;
                grid.set(x, ceiling + 1, z, lamp ? palette.light() : palette.ceiling());
                if (!wall && layout.is(mx, mz, DoomlikeLayout.LIQUID) && floor < DoomlikeLayout.BASE) {
                    String liquid = layout.theme.liquidBlocks.get(room.liquidIndex);
                    if (!liquid.equals("minecraft:air")) {
                        grid.set(x, floor, z, BlockStateSpec.of(liquid));
                    }
                } else if (!wall && layout.is(mx, mz, DoomlikeLayout.FENCE)) {
                    grid.set(x, floor, z, BlockStateSpec.of(layout.theme.fences.get(room.fenceIndex)));
                }
            }
        }
    }

    private boolean free(int mx, int mz) {
        return layout.roomAt(mx, mz) != null && !layout.is(mx, mz, DoomlikeLayout.WALL)
            && !layout.is(mx, mz, DoomlikeLayout.PILLAR) && !layout.is(mx, mz, DoomlikeLayout.LIQUID)
            && !layout.is(mx, mz, DoomlikeLayout.DOOR) && !layout.is(mx, mz, DoomlikeLayout.FENCE);
    }

    private void contents() {
        for (Room room : layout.rooms) {
            if (room.removed) {
                continue;
            }
            for (Spot spot : room.spawners) {
                if (free(spot.x(), spot.z())) {
                    ThirdPartyBlocks.spawner(grid, spot.x() - minX, layout.floorY[spot.x()][spot.z()], spot.z() - minZ,
                        theme.spawnerMob());
                }
            }
            for (Spot spot : room.chests) {
                if (free(spot.x(), spot.z())) {
                    ThirdPartyBlocks.chest(grid, spot.x() - minX, layout.floorY[spot.x()][spot.z()], spot.z() - minZ,
                        "north", spot.treasure() ? theme.treasureLoot() : theme.commonLoot());
                }
            }
        }
    }

    private record Opening(int x, int z, int outX, int outZ, String front) {
    }

    /** Outer wall cells with level floor behind them and walls beside them. */
    private List<Opening> openingCandidates() {
        List<Opening> candidates = new ArrayList<>();
        int sizeX = grid.sizeX();
        int sizeZ = grid.sizeZ();
        for (int x = 1; x < sizeX - 1; x++) {
            candidates.add(new Opening(x, 0, 0, -1, "north"));
            candidates.add(new Opening(x, sizeZ - 1, 0, 1, "south"));
        }
        for (int z = 1; z < sizeZ - 1; z++) {
            candidates.add(new Opening(0, z, -1, 0, "west"));
            candidates.add(new Opening(sizeX - 1, z, 1, 0, "east"));
        }
        List<Opening> usable = new ArrayList<>();
        for (Opening opening : candidates) {
            boolean ok = true;
            for (int lateral = -1; lateral <= 1 && ok; lateral++) {
                int mx = opening.x() + minX + lateral * Math.abs(opening.outZ());
                int mz = opening.z() + minZ + lateral * Math.abs(opening.outX());
                ok = layout.is(mx, mz, DoomlikeLayout.WALL) && !layout.is(mx, mz, DoomlikeLayout.DOOR)
                    && free(mx - opening.outX(), mz - opening.outZ())
                    && layout.floorY[mx - opening.outX()][mz - opening.outZ()] == DoomlikeLayout.BASE;
            }
            if (ok) {
                usable.add(opening);
            }
        }
        return usable;
    }

    /** Plain gates (2 wide) and side-room connectors (3 wide) through the outer walls. */
    private void openings() {
        List<Opening> candidates = openingCandidates();
        Collections.shuffle(candidates, random);
        List<Opening> chosen = new ArrayList<>();
        for (Opening candidate : candidates) {
            if (chosen.size() >= MAX_SIDE_DOORS + MAX_GATES) {
                break;
            }
            boolean apart = chosen.stream().allMatch(other ->
                Math.abs(other.x() - candidate.x()) + Math.abs(other.z() - candidate.z()) >= MIN_OPENING_GAP);
            if (apart) {
                chosen.add(candidate);
            }
        }
        for (int index = 0; index < chosen.size(); index++) {
            Opening opening = chosen.get(index);
            // Gates first: side rooms and seals close their doorway, so the complex needs open gates.
            boolean side = index >= MAX_GATES;
            for (int lateral = side ? -1 : 0; lateral <= 1; lateral++) {
                int x = opening.x() + lateral * Math.abs(opening.outZ());
                int z = opening.z() + lateral * Math.abs(opening.outX());
                for (int y = DoomlikeLayout.BASE; y <= DoomlikeLayout.BASE + 2; y++) {
                    grid.set(x, y, z, TemplateGrid.AIR);
                }
                grid.set(x, DoomlikeLayout.BASE - 1, z, theme.palette().floor());
            }
            if (side) {
                ThirdPartyBlocks.jigsaw(grid, opening.x(), DoomlikeLayout.BASE, opening.z(), opening.front() + "_up",
                    ThirdPartyBlocks.SIDE);
            }
        }
    }
}
