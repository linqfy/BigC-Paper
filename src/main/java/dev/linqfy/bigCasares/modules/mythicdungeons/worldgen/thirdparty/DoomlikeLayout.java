/*
 * Transcribed from Doomlike Dungeons (https://github.com/BlackJar72/DoomlikeDungeons),
 * MIT License, Copyright (c) 2014-2020 Jared Blackburn; 1.20 port by JaredBGreat and Xiroc.
 * The full licence text ships in mythicdungeons/third_party/doomlike_dungeons/LICENSE.
 */
package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty.DoomlikeTheme.Degree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The Doomlike Dungeons planner ({@code planner.Dungeon}, {@code Node}, {@code Route},
 * {@code RoomSeed}, {@code rooms.Room} and the {@code features} package) re-implemented on a 2D map
 * that is later turned into a structure template by {@link DoomlikeBuilder}:
 * <ol>
 *     <li>node (hub) rooms are seeded at random and grown from a {@link Seed} until blocked;</li>
 *     <li>nodes are connected densely or sparsely by {@link Route}s that grow rooms from both ends
 *     towards each other;</li>
 *     <li>a growth cycle sprouts side rooms from the doorways of every room until none can grow;</li>
 *     <li>rooms get features by theme probability (pillars, pools, raised platforms, depressions,
 *     island rooms), mirrored by the room's symmetry, then spawners and chests;</li>
 *     <li>doors are validated and the dungeon is made fully connected.</li>
 * </ol>
 * Differences to the original, needed to emit a template instead of editing a live world: the
 * map is at most 48 blocks per side, rooms share one level (no caves, no whole-room patterns,
 * transposed and swirled symmetries reuse the rotational and two-axis cases), Doomlike's A* door
 * walker is replaced by straight paths from each door to the room centre, and rooms that stay
 * unreachable are removed.
 */
final class DoomlikeLayout {

    static final int WALL = 1;
    static final int PILLAR = 2;
    static final int FENCE = 4;
    static final int LIQUID = 8;
    static final int DOOR = 16;

    /** Map size and growth limits ({@code themes.Sizes}, scaled to one template). */
    record Size(int width, int depth, int maxRooms, int maxRoomSize, int minNodes, int maxNodes) {
    }

    static final Size BUILDING = new Size(44, 44, 16, 12, 2, 3);
    /** Rooms never get taller than this, which keeps the template low. */
    private static final int MAX_HEIGHT = 7;
    /** Floor level of the plan: two blocks of foundation leave room for pools and depressions. */
    static final int BASE = 3;

    enum Symmetry {
        NONE(0, false, false, false), X(1, true, false, false), Z(1, false, true, false), XZ(2, true, true, false),
        TR1(1, false, false, true), TR2(1, false, false, true), R(1, false, false, true), SW(2, true, true, false);

        final int level;
        final boolean halfX;
        final boolean halfZ;
        final boolean doubler;

        Symmetry(int level, boolean halfX, boolean halfZ, boolean doubler) {
            this.level = level;
            this.halfX = halfX;
            this.halfZ = halfZ;
            this.doubler = doubler;
        }

        /** Mirror case used for doors and features; transposed/swirled reuse rotational/two-axis. */
        Symmetry mirror() {
            return switch (this) {
                case TR1, TR2 -> R;
                case SW -> XZ;
                default -> this;
            };
        }
    }

    /** A doorway cell; {@code outX/outZ} point away from the room that made it. */
    record Door(int x, int z, int outX, int outZ) {
    }

    record Spot(int x, int z, boolean treasure) {
    }

    final Size size;
    final Random random;
    final DoomlikeTheme theme;
    final int[][] room;
    final int[][] floorY;
    final int[][] ceilY;
    final int[][] bits;
    final List<Room> rooms = new ArrayList<>();
    private List<Room> planter = new ArrayList<>();
    final Degree liquids;
    final Degree subrooms;
    final Degree islands;
    final Degree pillars;
    final Degree fences;
    final Degree symmetry;
    final Degree variability;
    final Degree degeneracy;
    final Degree complexity;
    final Degree verticle;
    private boolean nextNodeIsEntrance;
    Room entrance;

    DoomlikeLayout(Size size, DoomlikeTheme theme, long seed) {
        this.size = size;
        this.theme = theme;
        this.random = new Random(seed);
        room = new int[size.width()][size.depth()];
        floorY = new int[size.width()][size.depth()];
        ceilY = new int[size.width()][size.depth()];
        bits = new int[size.width()][size.depth()];
        liquids = theme.liquids.select(random);
        subrooms = theme.subrooms.select(random);
        islands = theme.islands.select(random);
        pillars = theme.pillars.select(random);
        fences = new DoomlikeTheme.Table(new int[]{5, 15, 30, 75, 25, 0}).select(random);
        symmetry = theme.symmetry.select(random);
        variability = new DoomlikeTheme.Table(new int[]{5, 10, 25, 75, 50, 25}).select(random);
        degeneracy = theme.degeneracy.select(random);
        complexity = theme.complexity.select(random);
        verticle = theme.verticle.select(random);
    }

    /** A full multi-room dungeon level ({@code planner.Dungeon}'s constructor). */
    static DoomlikeLayout dungeon(DoomlikeTheme theme, long seed) {
        DoomlikeLayout layout = new DoomlikeLayout(BUILDING, theme, seed);
        layout.makeNodes();
        layout.connectNodes();
        layout.growthCycle();
        for (Room room : layout.rooms) {
            room.addChests();
        }
        layout.fixDoors();
        return layout;
    }

    /** One room filling a {@code width x depth} map, for side rooms; doorways are left to the caller. */
    static DoomlikeLayout singleRoom(DoomlikeTheme theme, long seed, int width, int depth) {
        DoomlikeLayout layout = new DoomlikeLayout(new Size(width, depth, 1, Math.max(width, depth), 1, 1), theme, seed);
        int height = Math.min(MAX_HEIGHT, layout.random.nextInt(layout.verticle.value / 2 + 3) + 3);
        Room room = layout.new Room(0, width - 1, 0, depth - 1, BASE, BASE + height, null, null);
        for (Door door : room.doors) {
            layout.bits[door.x()][door.z()] &= ~DOOR;
        }
        room.doors.clear();
        room.plan(null);
        room.addChests();
        return layout;
    }

    boolean is(int x, int z, int flag) {
        return inside(x, z) && (bits[x][z] & flag) != 0;
    }

    void set(int x, int z, int flag, boolean value) {
        if (value) {
            bits[x][z] |= flag;
        } else {
            bits[x][z] &= ~flag;
        }
    }

    boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < size.width() && z < size.depth();
    }

    Room roomAt(int x, int z) {
        return inside(x, z) && room[x][z] > 0 ? rooms.get(room[x][z] - 1) : null;
    }

    private Symmetry pickSymmetry() {
        int num = 0;
        if (symmetry.use(random)) {
            num++;
        }
        if (symmetry.use(random)) {
            num++;
        }
        if (num == 0) {
            return Symmetry.NONE;
        }
        int which = random.nextInt(4 / num);
        if (num == 1) {
            return switch (which) {
                case 0 -> Symmetry.X;
                case 1 -> Symmetry.Z;
                case 2 -> random.nextBoolean() ? Symmetry.TR1 : Symmetry.TR2;
                default -> Symmetry.R;
            };
        }
        return which == 0 ? Symmetry.XZ : Symmetry.SW;
    }

    private int roomHeight(int xdim, int zdim) {
        int ymod = (int) Math.sqrt(Math.min(xdim, zdim));
        return Math.min(MAX_HEIGHT, random.nextInt(verticle.value / 2 + ymod + 1) + 2);
    }

    private void makeNodes() {
        int numNodes = random.nextInt(size.maxNodes() - size.minNodes() + 1) + size.minNodes() + 1;
        int made = 0;
        List<Room> nodes = new ArrayList<>();
        for (int attempt = 0; made < numNodes && attempt < 200; attempt++) {
            int xdim = random.nextInt(size.maxRoomSize() / 2 - 3) + size.maxRoomSize() / 2 + 4;
            int zdim = random.nextInt(size.maxRoomSize() / 2 - 3) + size.maxRoomSize() / 2 + 4;
            int height = Math.max(4, roomHeight(xdim, zdim));
            nextNodeIsEntrance = made == 0;
            Room node = new Seed(random.nextInt(size.width()), BASE, random.nextInt(size.depth()))
                .grow(xdim, zdim, height, null, null);
            nextNodeIsEntrance = false;
            if (node != null) {
                nodes.add(node);
                made++;
            }
        }
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Doomlike layout produced no node room");
        }
        this.nodes = nodes;
    }

    private List<Room> nodes = List.of();

    private void connectNodes() {
        if (random.nextBoolean()) {
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    if (rooms.size() >= size.maxRooms()) {
                        return;
                    }
                    new Route(nodes.get(i), nodes.get(j)).drawConnections();
                }
            }
        } else {
            List<Room> connected = new ArrayList<>(List.of(nodes.getFirst()));
            List<Room> disconnected = new ArrayList<>(nodes.subList(1, nodes.size()));
            while (!disconnected.isEmpty()) {
                if (rooms.size() >= size.maxRooms()) {
                    return;
                }
                Room first = connected.get(random.nextInt(connected.size()));
                Room other = disconnected.get(random.nextInt(disconnected.size()));
                new Route(first, other).drawConnections();
                connected.add(other);
                disconnected.remove(other);
            }
        }
    }

    private void growthCycle() {
        boolean doMore;
        do {
            doMore = false;
            List<Room> grower = planter;
            Collections.shuffle(grower, new Random(random.nextLong()));
            planter = new ArrayList<>();
            for (Room room : grower) {
                if (rooms.size() >= size.maxRooms()) {
                    return;
                }
                if (room.plantChildren()) {
                    doMore = true;
                }
            }
        } while (doMore);
    }

    /** {@code planner.RoomSeed}: grows a rectangle from one cell until blocked or the target size. */
    final class Seed {
        private final int x;
        private final int y;
        private final int z;

        Seed(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Room grow(int xdim, int zdim, int height, Room parent, Room previous) {
            if (rooms.size() >= size.maxRooms()) {
                return null;
            }
            return random.nextBoolean() ? grow(true, xdim, zdim, height, parent, previous)
                : grow(false, xdim, zdim, height, parent, previous);
        }

        /** {@code growRoomX} when {@code xFirst}, {@code growRoomZ} otherwise. */
        Room grow(boolean xFirst, int xdim, int zdim, int height, Room parent, Room previous) {
            int container = parent == null ? 0 : parent.id;
            int w = parent == null ? 0 : 1;
            if (!inside(x, z) || room[x][z] != container) {
                return null;
            }
            int beginX = x;
            int endX = x;
            int beginZ = z;
            int endZ = z;
            int grownX = 1;
            int grownZ = 1;
            boolean canAddX = true;
            boolean canSubX = true;
            boolean canAddZ = true;
            boolean canSubZ = true;
            for (int pass = 0; pass < 2; pass++) {
                boolean alongX = (pass == 0) == xFirst;
                if (alongX) {
                    if (pass == 0) {
                        while ((canAddX || canSubX) && grownX < xdim) {
                            if (endX + 1 >= size.width()) {
                                canAddX = false;
                            }
                            if (beginX - 1 < 0) {
                                canSubX = false;
                            }
                            if (canAddX) {
                                if (room[endX + w][z] == container) {
                                    grownX++;
                                    endX++;
                                } else {
                                    canAddX = false;
                                }
                            }
                            if (canSubX) {
                                if (room[beginX - w][z] == container) {
                                    grownX++;
                                    beginX--;
                                } else {
                                    canSubX = false;
                                }
                            }
                        }
                    } else {
                        while ((canAddX || canSubX) && grownX < xdim) {
                            for (int i = beginZ + 1 - w; i <= endZ - 1 + w; i++) {
                                if (endX + 1 >= size.width()) {
                                    canAddX = false;
                                } else if (room[endX + w][i] != container) {
                                    canAddX = false;
                                }
                                if (beginX - 1 < 0) {
                                    canSubX = false;
                                } else if (room[beginX - w][i] != container) {
                                    canSubX = false;
                                }
                            }
                            if (canAddX) {
                                grownX++;
                                endX++;
                            }
                            if (canSubX) {
                                grownX++;
                                beginX--;
                            }
                        }
                    }
                } else if (pass == 0) {
                    while ((canAddZ || canSubZ) && grownZ < zdim) {
                        if (endZ + 1 >= size.depth()) {
                            canAddZ = false;
                        }
                        if (beginZ - 1 < 0) {
                            canSubZ = false;
                        }
                        if (canAddZ) {
                            if (room[x][endZ + w] == container) {
                                grownZ++;
                                endZ++;
                            } else {
                                canAddZ = false;
                            }
                        }
                        if (canSubZ) {
                            if (room[x][beginZ - w] == container) {
                                grownZ++;
                                beginZ--;
                            } else {
                                canSubZ = false;
                            }
                        }
                    }
                } else {
                    while ((canAddZ || canSubZ) && grownZ < zdim) {
                        for (int i = beginX + 1 - w; i <= endX - 1 + w; i++) {
                            if (endZ + 1 >= size.depth()) {
                                canAddZ = false;
                            } else if (room[i][endZ + w] != container) {
                                canAddZ = false;
                            }
                            if (beginZ - 1 < 0) {
                                canSubZ = false;
                            } else if (room[i][beginZ - w] != container) {
                                canSubZ = false;
                            }
                        }
                        if (canAddZ) {
                            grownZ++;
                            endZ++;
                        }
                        if (canSubZ) {
                            grownZ++;
                            beginZ--;
                        }
                    }
                }
            }
            if (grownX < 5 || grownZ < 5) {
                return null;
            }
            Room created = new Room(beginX, endX, beginZ, endZ, y, y + height, parent, previous);
            return created.plan(parent);
        }
    }

    /** {@code planner.Route}: grows two chains of rooms towards each other until they meet. */
    private final class Route {
        private Room current1;
        private Room current2;
        private final List<Room> side1 = new ArrayList<>();
        private final List<Room> side2 = new ArrayList<>();
        private boolean finishTurn;
        private boolean complete;
        private boolean comp1;
        private boolean comp2;
        private int dir1;
        private int dir2;

        Route(Room start, Room finish) {
            current1 = start;
            current2 = finish;
            side1.add(start);
            side2.add(finish);
        }

        void drawConnections() {
            int limit = size.maxRooms();
            while (!complete && limit > 0) {
                limit--;
                if (rooms.size() >= size.maxRooms()) {
                    return;
                }
                drawConnection();
            }
        }

        private void drawConnection() {
            growthDir();
            int xdim = random.nextInt(size.maxRoomSize() - 5) + 6;
            int zdim = random.nextInt(size.maxRoomSize() - 5) + 6;
            if (close(size.maxRoomSize() - 1)) {
                xdim = size.maxRoomSize();
                zdim = size.maxRoomSize();
            }
            int height = roomHeight(xdim, zdim);
            if (finishTurn) {
                dir1 = (dir1 + 2) % 4;
                dir2 = (dir2 + 2) % 4;
                Room temp = current2.connector(dir1, xdim, zdim, height);
                if (temp == null || side2.contains(temp)) {
                    temp = current2.connector(dir2, xdim, zdim, height);
                }
                if (temp == null || side2.contains(temp)) {
                    comp2 = true;
                } else if (side1.contains(temp)) {
                    complete = true;
                } else {
                    side2.add(temp);
                    current2 = temp;
                }
            } else {
                Room temp = current1.connector(dir1, xdim, zdim, height);
                if (temp == null || side1.contains(temp)) {
                    temp = current1.connector(dir2, xdim, zdim, height);
                }
                if (temp == null || side1.contains(temp)) {
                    comp1 = true;
                } else if (side2.contains(temp)) {
                    complete = true;
                } else {
                    side1.add(temp);
                    current1 = temp;
                }
            }
            if (!complete) {
                complete = comp1 && comp2;
            }
            if (comp1) {
                finishTurn = true;
            } else if (comp2) {
                finishTurn = false;
            } else {
                finishTurn = !finishTurn;
            }
        }

        private boolean xOverlap() {
            return current1.endX > current2.beginX && current2.endX > current1.beginX;
        }

        private boolean zOverlap() {
            return current1.endZ > current2.beginZ && current2.endZ > current1.beginZ;
        }

        private boolean close(int range) {
            return Math.abs(current1.beginX - current2.endX) <= range && Math.abs(current2.beginX - current1.endX) <= range
                && Math.abs(current1.beginZ - current2.endZ) <= range && Math.abs(current2.beginZ - current1.endZ) <= range;
        }

        /** Direction 0 = +x, 1 = +z, 2 = -x, 3 = -z, weighted towards the other end. */
        private void growthDir() {
            boolean posX = current1.realX < current2.realX;
            boolean posZ = current1.realZ < current2.realZ;
            float distX = Math.abs(current2.realX - current1.realX);
            float distZ = Math.abs(current2.realZ - current1.realZ);
            if (xOverlap()) {
                dir1 = posZ ? 1 : 3;
                dir2 = random.nextInt(2) * 2;
            } else if (zOverlap()) {
                dir1 = posX ? 0 : 2;
                dir2 = 1 + random.nextInt(2) * 2;
            } else if (random.nextInt((int) (distX + distZ + 1)) > (int) distX) {
                dir1 = posX ? 0 : 2;
                dir2 = posZ ? 1 : 3;
            } else {
                dir1 = posZ ? 1 : 3;
                dir2 = posX ? 0 : 2;
            }
        }
    }

    /** {@code rooms.Room}: a rectangle of the map with walls on its edge cells. */
    final class Room {
        final int id;
        final int beginX;
        final int endX;
        final int beginZ;
        final int endZ;
        final int midX;
        final int midZ;
        final float realX;
        final float realZ;
        final int floorY;
        final int ceilY;
        final boolean isNode;
        final boolean isSubroom;
        final Room parent;
        final boolean hasEntrance;
        final Symmetry sym;
        /** Material variants: 0 = main palette block, 1 = alternative (Doomlike's per-room variability). */
        final int wallVariant;
        final int floorVariant;
        final int fenceIndex;
        final int liquidIndex;
        final List<Door> doors = new ArrayList<>();
        final List<Seed> childSeeds = new ArrayList<>();
        final List<Spot> spawners = new ArrayList<>();
        final List<Spot> chests = new ArrayList<>();
        boolean removed;

        Room(int beginX, int endX, int beginZ, int endZ, int floorY, int ceilY, Room parent, Room previous) {
            if (previous != null && !variability.use(random)) {
                wallVariant = previous.wallVariant;
                floorVariant = previous.floorVariant;
                fenceIndex = previous.fenceIndex;
                liquidIndex = previous.liquidIndex;
            } else {
                wallVariant = random.nextInt(4) == 0 ? 1 : 0;
                floorVariant = random.nextInt(3) == 0 ? 1 : 0;
                fenceIndex = random.nextInt(theme.fences.size());
                liquidIndex = random.nextInt(theme.liquidBlocks.size());
            }
            rooms.add(this);
            id = rooms.size();
            planter.add(this);
            this.parent = parent;
            isNode = previous == null;
            isSubroom = parent != null;
            hasEntrance = isNode && nextNodeIsEntrance;
            if (hasEntrance) {
                entrance = this;
            }
            sym = pickSymmetry();
            this.beginX = beginX;
            this.endX = endX;
            this.beginZ = beginZ;
            this.endZ = endZ;
            this.floorY = floorY;
            this.ceilY = ceilY;
            midX = beginX + (endX - beginX) / 2;
            midZ = beginZ + (endZ - beginZ) / 2;
            realX = (endX - beginX) / 2.0f + beginX;
            realZ = (endZ - beginZ) / 2.0f + beginZ;
            for (int i = beginX + 1; i < endX; i++) {
                for (int j = beginZ + 1; j < endZ; j++) {
                    if (room[i][j] == 0) {
                        room[i][j] = id;
                    }
                    ceilY(i, j, ceilY);
                    DoomlikeLayout.this.floorY[i][j] = floorY;
                    set(i, j, LIQUID, false);
                    set(i, j, WALL, false);
                }
            }
            for (int i = beginX; i <= endX; i++) {
                assignEdge(i, beginZ);
                assignEdge(i, endZ);
            }
            for (int i = beginZ; i <= endZ; i++) {
                assignEdge(beginX, i);
                assignEdge(endX, i);
            }
            doorways();
        }

        private void ceilY(int x, int z, int value) {
            DoomlikeLayout.this.ceilY[x][z] = value;
        }

        private void assignEdge(int x, int z) {
            if (room[x][z] == 0 || isSubroom) {
                room[x][z] = id;
                set(x, z, LIQUID, false);
                set(x, z, WALL, true);
            }
            DoomlikeLayout.this.ceilY[x][z] = Math.max(DoomlikeLayout.this.ceilY[x][z], ceilY);
            DoomlikeLayout.this.floorY[x][z] = Math.max(DoomlikeLayout.this.floorY[x][z], floorY);
        }

        Room plan(Room parentRoom) {
            if (complexity.use(random) || isNode) {
                addFeatures();
            }
            if (hasEntrance) {
                for (int i = midX - 2; i <= midX + 2; i++) {
                    for (int j = midZ - 2; j <= midZ + 2; j++) {
                        if (inside(i, j) && room[i][j] == id) {
                            DoomlikeLayout.this.floorY[i][j] = floorY;
                            set(i, j, LIQUID, false);
                            set(i, j, WALL, false);
                            set(i, j, PILLAR, false);
                            set(i, j, FENCE, false);
                        }
                    }
                }
            }
            if (parentRoom == null) {
                addSpawners();
            }
            return this;
        }

        private int[] randomInterior() {
            int x = random.nextInt(Math.max(1, endX - beginX - 3)) + beginX + 2;
            int z = random.nextInt(Math.max(1, endZ - beginZ - 3)) + beginZ + 2;
            return new int[]{x, z};
        }

        private boolean occupied(int x, int z) {
            for (Spot spot : spawners) {
                if (spot.x() == x && spot.z() == z) {
                    return true;
                }
            }
            for (Spot spot : chests) {
                if (spot.x() == x && spot.z() == z) {
                    return true;
                }
            }
            return false;
        }

        /** {@code Room.addSpawners} without the difficulty settings (spawn the culture's mob). */
        private void addSpawners() {
            if (hasEntrance) {
                return;
            }
            if (isNode || random.nextInt(2) == 0) {
                int count = isNode ? random.nextInt(2 + Math.max(endX - beginX, endZ - beginZ) / 8) + 1 : 1;
                for (int i = 0; i < count; i++) {
                    for (int attempt = 0; attempt < 10; attempt++) {
                        int[] pos = randomInterior();
                        if (!occupied(pos[0], pos[1])) {
                            spawners.add(new Spot(pos[0], pos[1], false));
                            break;
                        }
                    }
                }
            }
        }

        /** {@code Room.addChests}: weak chest in quiet rooms, more chests next to spawners, treasure in nodes. */
        void addChests() {
            if (hasEntrance) {
                return;
            }
            boolean hasSpawners = !spawners.isEmpty();
            if (!hasSpawners && random.nextInt(5) > 0) {
                return;
            }
            int count;
            if (!hasSpawners || (random.nextBoolean() && !isNode)) {
                count = 1;
            } else {
                int ms = Math.max(spawners.size(), 2);
                count = isNode ? Math.min(ms, random.nextInt(2 + ms / 2) + 2) : random.nextInt(1 + ms / 2) + 1;
            }
            for (int i = 0; i < count; i++) {
                for (int attempt = 0; attempt < 10; attempt++) {
                    int[] pos = randomInterior();
                    if (!occupied(pos[0], pos[1])) {
                        chests.add(new Spot(pos[0], pos[1], false));
                        break;
                    }
                }
            }
            if (isNode && !occupied(midX, midZ)) {
                chests.add(new Spot(midX, midZ, true));
            }
        }

        private void addDoor(int x, int z, int outX, int outZ) {
            if (!inside(x, z)) {
                return;
            }
            doors.add(new Door(x, z, outX, outZ));
            set(x, z, DOOR, true);
        }

        private void doorways() {
            int num = random.nextInt(2 + (endX - beginX + endZ - beginZ) / (sym.level * 8 + 8)
                + subrooms.value / (2 + sym.level)) + 1;
            for (int i = 0; i < num; i++) {
                doorway();
            }
        }

        private void doorway() {
            int xExtend = 0;
            int zExtend = 0;
            int xDir = 0;
            int zDir = 0;
            int x;
            int z;
            int oppX;
            int oppZ;
            switch (random.nextInt(4)) {
                case 0 -> {
                    x = beginX;
                    oppX = endX;
                    z = random.nextInt(endZ - beginZ - 3) + 2;
                    oppZ = endZ - z;
                    z += beginZ;
                    xDir = -1;
                    zExtend = random.nextInt(3) - 1;
                }
                case 1 -> {
                    z = beginZ;
                    oppZ = endZ;
                    x = random.nextInt(endX - beginX - 3) + 2;
                    oppX = endX - x;
                    x += beginX;
                    zDir = -1;
                    xExtend = random.nextInt(3) - 1;
                }
                case 2 -> {
                    x = endX;
                    oppX = beginX;
                    z = random.nextInt(endZ - beginZ - 3) + 2;
                    oppZ = endZ - z;
                    z += beginZ;
                    xDir = 1;
                    zExtend = random.nextInt(3) - 1;
                }
                default -> {
                    z = endZ;
                    oppZ = beginZ;
                    x = random.nextInt(endX - beginX - 3) + 2;
                    oppX = endX - x;
                    x += beginX;
                    zDir = 1;
                    xExtend = random.nextInt(3) - 1;
                }
            }
            door(x, z, xExtend, zExtend, xDir, zDir);
            Symmetry mirror = sym.mirror();
            if (mirror == Symmetry.X || mirror == Symmetry.XZ) {
                door(oppX, z, -xExtend, zExtend, -xDir, zDir);
            }
            if (mirror == Symmetry.Z || mirror == Symmetry.XZ) {
                door(x, oppZ, xExtend, -zExtend, xDir, -zDir);
            }
            if (mirror == Symmetry.R || mirror == Symmetry.XZ) {
                door(oppX, oppZ, -xExtend, -zExtend, -xDir, -zDir);
            }
        }

        /** A door, its one-cell extension along the wall and the seed of the room beyond it. */
        private void door(int x, int z, int xExtend, int zExtend, int xDir, int zDir) {
            addDoor(x, z, xDir, zDir);
            addDoor(x + xExtend, z + zExtend, xDir, zDir);
            childSeeds.add(new Seed(x + xDir, floorY, z + zDir));
        }

        /** {@code Room.connector}: a door in direction {@code dir} and the room behind it (grown if needed). */
        Room connector(int dir, int xdim, int zdim, int height) {
            int xExtend = 0;
            int zExtend = 0;
            int xDir = 0;
            int zDir = 0;
            int x;
            int z;
            switch (dir) {
                case 2 -> {
                    x = beginX;
                    z = (int) realZ;
                    xDir = -1;
                    zExtend = random.nextInt(3) - 1;
                }
                case 3 -> {
                    z = beginZ;
                    x = (int) realX;
                    zDir = -1;
                    xExtend = random.nextInt(3) - 1;
                }
                case 0 -> {
                    x = endX;
                    z = (int) realZ;
                    xDir = 1;
                    zExtend = random.nextInt(3) - 1;
                }
                default -> {
                    z = endZ;
                    x = (int) realX;
                    zDir = 1;
                    xExtend = random.nextInt(3) - 1;
                }
            }
            addDoor(x, z, xDir, zDir);
            addDoor(x + xExtend, z + zExtend, xDir, zDir);
            int seedX = x + xDir;
            int seedZ = z + zDir;
            if (!inside(seedX, seedZ)) {
                return null;
            }
            if (room[seedX][seedZ] != 0) {
                return rooms.get(room[seedX][seedZ] - 1);
            }
            return new Seed(seedX, floorY, seedZ).grow(dir % 2 != 0, xdim, zdim, height, null, this);
        }

        boolean plantChildren() {
            boolean result = false;
            for (Seed planted : childSeeds) {
                if (rooms.size() >= size.maxRooms()) {
                    return false;
                }
                int xdim = random.nextInt(size.maxRoomSize() - 5) + 6;
                int zdim = random.nextInt(size.maxRoomSize() - 5) + 6;
                if (planted.grow(xdim, zdim, roomHeight(xdim, zdim), null, this) != null) {
                    result = true;
                }
            }
            return result;
        }

        // ---- features (planner.features) ----

        private void addFeatures() {
            List<Integer> features = new ArrayList<>(List.of(0, 1, 2, 3, 4));
            Collections.shuffle(features, new Random(random.nextLong()));
            if (complexity.value <= 0) {
                return;
            }
            int tries = (endX - beginX + endZ - beginZ) / (4 + sym.level);
            for (int i = 0; i <= tries; i++) {
                for (int feature : features) {
                    Degree chance = switch (feature) {
                        case 0, 1 -> verticle;
                        case 2 -> liquids;
                        case 3 -> pillars;
                        default -> islands;
                    };
                    if (chance.use(random)) {
                        switch (feature) {
                            case 0 -> platform(false);
                            case 1 -> platform(true);
                            case 2 -> pool();
                            case 3 -> pillar();
                            default -> islandRoom();
                        }
                        break;
                    }
                }
            }
        }

        /** {@code features.Pillar}: one pillar plus its mirror images; asymmetric rooms get none. */
        private void pillar() {
            int x1 = random.nextInt(Math.max(1, endX - beginX - 2)) + 1;
            int z1 = random.nextInt(Math.max(1, endZ - beginZ - 2)) + 1;
            if (sym.halfX) {
                x1 = (x1 - 1) / 2 + 1;
            }
            if (sym.halfZ) {
                z1 = (z1 - 1) / 2 + 1;
            }
            int x2 = endX - x1;
            int z2 = endZ - z1;
            x1 += beginX;
            z1 += beginZ;
            switch (sym.mirror()) {
                case X -> {
                    setPillar(x1, z1);
                    setPillar(x2, z1);
                }
                case Z -> {
                    setPillar(x1, z1);
                    setPillar(x1, z2);
                }
                case XZ -> {
                    setPillar(x1, z1);
                    setPillar(x1, z2);
                    setPillar(x2, z1);
                    setPillar(x2, z2);
                }
                case R -> {
                    setPillar(x1, z1);
                    setPillar(x2, z2);
                }
                default -> {
                }
            }
        }

        private void setPillar(int x, int z) {
            if (inside(x, z) && room[x][z] == id && !is(x, z, WALL)) {
                set(x, z, PILLAR, true);
            }
        }

        private float[] featureCentre() {
            float cx = random.nextInt(Math.max(1, endX - beginX - 1)) + beginX + 1;
            float cz = random.nextInt(Math.max(1, endZ - beginZ - 1)) + beginZ + 1;
            return new float[]{cx, cz};
        }

        /** Applies {@code draw} at the centre and at the room-symmetric copies (Pool/IslandPlatform). */
        private void mirrored(float dimX, float dimZ, RectAction draw) {
            float[] centre = featureCentre();
            float cx = centre[0];
            float cz = centre[1];
            float oppX = endX - (cx - beginX);
            float oppZ = endZ - (cz - beginZ);
            if (sym.halfX) {
                dimX = dimX * 2 / 3;
                oppX = endX - (cx - beginX) / 2;
                cx = (cx - beginX) / 2 + beginX;
            }
            if (sym.halfZ) {
                dimZ = dimZ * 2 / 3;
                oppZ = endZ - (cz - beginZ) / 2;
                cz = (cz - beginZ) / 2 + beginZ;
            }
            if (sym.doubler) {
                dimX *= 0.75f;
                dimZ *= 0.75f;
            }
            cx++;
            cz++;
            oppX++;
            oppZ++;
            draw.rect(cx, cz, dimX, dimZ);
            Symmetry mirror = sym.mirror();
            if (mirror == Symmetry.X || mirror == Symmetry.XZ) {
                draw.rect(oppX, cz, dimX, dimZ);
            }
            if (mirror == Symmetry.Z || mirror == Symmetry.XZ) {
                draw.rect(cx, oppZ, dimX, dimZ);
            }
            if (mirror == Symmetry.R || mirror == Symmetry.XZ) {
                draw.rect(oppX, oppZ, dimX, dimZ);
            }
        }

        /** {@code features.Pool}: sunken liquid area (two blocks, one in swampy themes), maybe fenced. */
        private void pool() {
            float dimX = (endX - beginX) * (random.nextFloat() * 0.25f + 0.15f);
            float dimZ = (endX - beginX) * (random.nextFloat() * 0.25f + 0.15f);
            int drop = theme.swampy ? 1 : 2;
            boolean fenced = fences.use(random);
            mirrored(dimX, dimZ, (sx, sz, w, d) -> {
                List<int[]> cells = rect(sx, sz, w, d);
                for (int[] cell : cells) {
                    DoomlikeLayout.this.floorY[cell[0]][cell[1]] = floorY - drop;
                    set(cell[0], cell[1], LIQUID, true);
                    set(cell[0], cell[1], PILLAR, false);
                }
                if (fenced) {
                    for (int[] cell : cells) {
                        for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int nx = cell[0] + step[0];
                            int nz = cell[1] + step[1];
                            if (inside(nx, nz) && room[nx][nz] == id && !is(nx, nz, LIQUID) && !is(nx, nz, WALL)
                                && DoomlikeLayout.this.floorY[nx][nz] == floorY) {
                                set(nx, nz, FENCE, true);
                            }
                        }
                    }
                }
            });
        }

        /** {@code features.IslandPlatform} / {@code features.Depression}. */
        private void platform(boolean depression) {
            int available = ceilY - floorY;
            if (available < 4) {
                return;
            }
            float dimX = (endX - beginX) * (random.nextFloat() * 0.25f + 0.15f);
            float dimZ = (endX - beginX) * (random.nextFloat() * 0.25f + 0.15f);
            int platY;
            if (depression) {
                platY = floorY - random.nextInt(verticle.value / 2 + 1) - 1;
            } else {
                platY = floorY + 1 + random.nextInt(2);
                if (available > 4) {
                    platY += random.nextInt(available - 3);
                }
            }
            int level = Math.max(1, Math.min(platY, ceilY - 3));
            mirrored(dimX, dimZ, (sx, sz, w, d) -> {
                for (int[] cell : rect(sx, sz, w, d)) {
                    DoomlikeLayout.this.floorY[cell[0]][cell[1]] = level;
                    set(cell[0], cell[1], LIQUID, false);
                }
            });
        }

        /** {@code features.IslandRoom}: a walled room inside this one (its interior stays this room's). */
        private void islandRoom() {
            int dimX = (int) ((endX - beginX) * (0.2f + 0.3f * random.nextFloat()));
            int dimZ = (int) ((endZ - beginZ) * (0.2f + 0.3f * random.nextFloat()));
            int cx = random.nextInt(endX - beginX) + beginX;
            int cz = random.nextInt(endZ - beginZ) + beginZ;
            if (sym.doubler) {
                dimX = (int) (dimX * 0.75f);
                dimZ = (int) (dimZ * 0.75f);
            }
            if (dimX < 5 || dimZ < 5) {
                return;
            }
            new Seed(cx, floorY, cz).grow(dimX, dimZ, Math.min(ceilY - floorY, roomHeight(dimX, dimZ)), this, this);
        }

        /** Cells of this room covered by a centred rectangle (Doomlike's "X" shape). */
        private List<int[]> rect(float sx, float sz, float dimX, float dimZ) {
            List<int[]> cells = new ArrayList<>();
            int xBegin = (int) (sx - dimX / 2.0f);
            int xEnd = (int) (sx + dimX / 2.0f);
            int zBegin = (int) (sz - dimZ / 2.0f);
            int zEnd = (int) (sz + dimZ / 2.0f);
            for (int k = zBegin; k < zEnd; k++) {
                for (int j = xBegin; j < xEnd; j++) {
                    if (inside(j, k) && room[j][k] == id && !is(j, k, WALL)) {
                        cells.add(new int[]{j, k});
                    }
                }
            }
            return cells;
        }
    }

    @FunctionalInterface
    private interface RectAction {
        void rect(float sx, float sz, float dimX, float dimZ);
    }

    // ---- door checks (planner.astar.DoorChecker, simplified) ----

    private boolean interior(int x, int z) {
        return inside(x, z) && room[x][z] > 0 && !is(x, z, WALL);
    }

    /** Owner of the walkable side of a cell: interior cells of islands belong to the outer room. */
    private Room walkableRoom(int x, int z) {
        Room owner = roomAt(x, z);
        while (owner != null && owner.isSubroom && !is(x, z, WALL)) {
            owner = owner.parent;
        }
        return owner;
    }

    /**
     * Validates doors: each door must lead from walkable floor through at most one more wall (the
     * neighbour's own edge) to walkable floor; that wall becomes part of the doorway. Invalid doors
     * are closed. Then straight paths are cleared from each doorway to the centres of the rooms it
     * joins, the dungeon is made connected and unreachable rooms are removed.
     */
    private void fixDoors() {
        List<int[]> passages = new ArrayList<>();
        for (Room source : List.copyOf(rooms)) {
            for (Door door : source.doors) {
                int x = door.x();
                int z = door.z();
                if (!is(x, z, DOOR)) {
                    continue;
                }
                int backX = x - door.outX();
                int backZ = z - door.outZ();
                int farX = x + door.outX();
                int farZ = z + door.outZ();
                if (interior(backX, backZ) && interior(farX, farZ)) {
                    passages.add(new int[]{x, z, backX, backZ, farX, farZ});
                } else if (interior(backX, backZ) && inside(farX, farZ) && is(farX, farZ, WALL)
                    && interior(farX + door.outX(), farZ + door.outZ())) {
                    set(farX, farZ, DOOR, true);
                    passages.add(new int[]{x, z, backX, backZ, farX + door.outX(), farZ + door.outZ()});
                } else {
                    set(x, z, DOOR, false);
                }
            }
        }
        // Door cells that ended up without a valid passage (extensions into corners) are closed.
        for (int x = 0; x < size.width(); x++) {
            for (int z = 0; z < size.depth(); z++) {
                if (is(x, z, DOOR) && !is(x, z, WALL)) {
                    set(x, z, DOOR, false);
                }
            }
        }
        for (int[] passage : passages) {
            clearPath(passage[2], passage[3]);
            clearPath(passage[4], passage[5]);
        }
        connect(passages);
    }

    /** Opens a 3-wide doorway in the north wall of a single-room layout, centred on {@code x}. */
    void openNorthDoor(int x) {
        for (int dx = -1; dx <= 1; dx++) {
            set(x + dx, 0, DOOR, true);
        }
        clearPath(x, 1);
    }

    /** Straight L-shaped walk from a cell to its room's centre: no pillars, liquids or fences on it. */
    private void clearPath(int x, int z) {
        Room owner = walkableRoom(x, z);
        if (owner == null) {
            return;
        }
        int cx = x;
        int cz = z;
        int guard = 0;
        while ((cx != owner.midX || cz != owner.midZ) && guard++ < 128) {
            if (inside(cx, cz) && room[cx][cz] > 0) {
                set(cx, cz, PILLAR, false);
                set(cx, cz, FENCE, false);
                if (is(cx, cz, LIQUID)) {
                    set(cx, cz, LIQUID, false);
                    floorY[cx][cz] = owner.floorY;
                }
                if (is(cx, cz, WALL) && walkableRoom(cx, cz) != null && walkableRoom(cx, cz).isSubroom) {
                    set(cx, cz, DOOR, true);
                }
            }
            if (cx != owner.midX) {
                cx += Integer.signum(owner.midX - cx);
            } else {
                cz += Integer.signum(owner.midZ - cz);
            }
        }
    }

    private void connect(List<int[]> passages) {
        Room start = entrance != null ? entrance : rooms.getFirst();
        Set<Room> reached = reach(start, passages);
        boolean changed = true;
        while (changed && reached.size() < topLevelRooms()) {
            changed = false;
            outer:
            for (int x = 0; x < size.width(); x++) {
                for (int z = 0; z < size.depth(); z++) {
                    Room from = interior(x, z) ? walkableRoom(x, z) : null;
                    if (from == null || !reached.contains(from)) {
                        continue;
                    }
                    for (int[] step : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                        int[] passage = tunnel(x, z, step[0], step[1], reached);
                        if (passage != null) {
                            passages.add(passage);
                            clearPath(passage[4], passage[5]);
                            reached = reach(start, passages);
                            changed = true;
                            break outer;
                        }
                    }
                }
            }
        }
        for (Room room : rooms) {
            Room top = room;
            while (top.isSubroom) {
                top = top.parent;
            }
            if (!reached.contains(top)) {
                room.removed = true;
            }
        }
        removeRooms();
    }

    private int topLevelRooms() {
        return (int) rooms.stream().filter(room -> !room.isSubroom).count();
    }

    /**
     * From walkable {@code (x, z)}, crosses one or two wall cells in direction {@code (dx, dz)} into an
     * unreached room; the wall cells and their lateral neighbours must be walls so the door is clean.
     */
    private int[] tunnel(int x, int z, int dx, int dz, Set<Room> reached) {
        for (int thickness = 1; thickness <= 2; thickness++) {
            int tx = x + dx * (thickness + 1);
            int tz = z + dz * (thickness + 1);
            if (!interior(tx, tz)) {
                continue;
            }
            Room target = walkableRoom(tx, tz);
            if (target == null || reached.contains(target)) {
                continue;
            }
            boolean walls = true;
            for (int step = 1; step <= thickness; step++) {
                int wx = x + dx * step;
                int wz = z + dz * step;
                walls &= inside(wx, wz) && is(wx, wz, WALL) && is(wx + dz, wz + dx, WALL) && is(wx - dz, wz - dx, WALL);
            }
            if (!walls) {
                continue;
            }
            for (int step = 1; step <= thickness; step++) {
                set(x + dx * step, z + dz * step, DOOR, true);
            }
            return new int[]{x + dx, z + dz, x, z, tx, tz};
        }
        return null;
    }

    private Set<Room> reach(Room start, List<int[]> passages) {
        Set<Room> reached = new HashSet<>();
        Deque<Room> queue = new ArrayDeque<>(List.of(start));
        reached.add(start);
        while (!queue.isEmpty()) {
            Room current = queue.removeFirst();
            for (int[] passage : passages) {
                Room a = walkableRoom(passage[2], passage[3]);
                Room b = walkableRoom(passage[4], passage[5]);
                Room next = a == current ? b : b == current ? a : null;
                if (next != null && reached.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return reached;
    }

    /** Clears removed rooms; their edge cells that close off a kept room stay as its wall. */
    private void removeRooms() {
        for (int x = 0; x < size.width(); x++) {
            for (int z = 0; z < size.depth(); z++) {
                Room owner = roomAt(x, z);
                if (owner == null || !owner.removed) {
                    continue;
                }
                Room keeper = null;
                for (int dx = -1; dx <= 1 && keeper == null; dx++) {
                    for (int dz = -1; dz <= 1 && keeper == null; dz++) {
                        Room neighbour = roomAt(x + dx, z + dz);
                        if (neighbour != null && !neighbour.removed && interior(x + dx, z + dz)) {
                            keeper = neighbour;
                        }
                    }
                }
                if (keeper != null && is(x, z, WALL)) {
                    room[x][z] = keeper.id;
                    set(x, z, DOOR, false);
                    floorY[x][z] = keeper.floorY;
                    ceilY[x][z] = Math.max(ceilY[x][z], keeper.ceilY);
                } else {
                    room[x][z] = 0;
                    bits[x][z] = 0;
                }
            }
        }
        // Kept cells that now face the outside become wall, so the plan stays closed.
        for (int x = 0; x < size.width(); x++) {
            for (int z = 0; z < size.depth(); z++) {
                if (room[x][z] == 0 || is(x, z, WALL)) {
                    continue;
                }
                for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
                    int nx = x + step[0];
                    int nz = z + step[1];
                    if (!inside(nx, nz) || room[nx][nz] == 0) {
                        set(x, z, WALL, true);
                        set(x, z, DOOR, false);
                        break;
                    }
                }
            }
        }
    }
}
