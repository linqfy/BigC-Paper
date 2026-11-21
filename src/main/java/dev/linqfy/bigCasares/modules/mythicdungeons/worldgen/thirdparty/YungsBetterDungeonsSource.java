package dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.thirdparty;

import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.nbt.Nbt;
import dev.linqfy.bigCasares.dimension.nbt.Nbt.CompoundTag;
import dev.linqfy.bigCasares.dimension.worldgen.BlockStateSpec;
import dev.linqfy.bigCasares.dimension.worldgen.StructureTemplate;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicDungeonTheme;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicExtraPiece;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicExtraPiece.Role;
import dev.linqfy.bigCasares.modules.mythicdungeons.worldgen.MythicTemplateSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * Rooms from YUNG's Better Dungeons (LGPLv3, YUNGNICKYOUNG &amp; Acarii), read unmodified from
 * {@code mythicdungeons/third_party/yungs_better_dungeons/} and adapted at pack-build time:
 * <ul>
 *     <li>The mod's placeholder blocks, which only its own structure processors understand, are
 *     resolved here with the same rules and probabilities as those processors (see
 *     {@link YungsPlaceholders}), from a {@link Random} seeded by the template path.</li>
 *     <li>Sub-pool jigsaws (loot piles, cubbies, tombstones, props) become their final state; only
 *     the connectors of the module contract are written back. Zombie-dungeon doorways are 2 wide with
 *     paired jigsaws: one cell of each pair carries the connector, the other stays open air, so a
 *     3-wide parent doorway or seal fully covers the opening.</li>
 *     <li>Small dungeons have no doorway: a 3x3 opening is carved in the middle of one face.</li>
 *     <li>Blocks are re-themed per culture with {@link StructureTemplate#remapPalette}.</li>
 * </ul>
 * The module then applies its walkability and clearance rules (see {@code MythicExtraPiece}): the
 * crypt rooms offered as corridors are only 4 blocks high inside and are left out, the small rooms lose
 * their spawners (no room for large mobs) and their loot.
 */
public final class YungsBetterDungeonsSource implements MythicTemplateSource {

    static final String RESOURCE_ROOT = "/mythicdungeons/third_party/yungs_better_dungeons/";

    enum Kind { SMALL, NETHER, ZOMBIE }

    /**
     * One shipped template. {@code entryFace}/{@code exitFace} pick doorways (or, for small dungeons,
     * where the doorway is carved); unused for nether shells, whose entrance is in the template.
     */
    record Spec(String path, Kind kind, Role role, String entryFace, String exitFace, int weight) {
        String pieceName() {
            return "yungs/" + path.substring(path.lastIndexOf('/') + 1);
        }
    }

    static final Spec SMALL_5X5 = new Spec("small_dungeon/shells/small_shell_5x5", Kind.SMALL, Role.SIDE_ROOM, "north", null, 1);
    static final Spec SMALL_7X5 = new Spec("small_dungeon/shells/small_shell_7x5", Kind.SMALL, Role.SIDE_ROOM, "north", null, 1);
    static final Spec SMALL_7X7 = new Spec("small_dungeon/shells/small_shell_7x7", Kind.SMALL, Role.SIDE_ROOM, "north", null, 1);
    static final Spec SMALL_9X5 = new Spec("small_dungeon/shells/small_shell_9x5", Kind.SMALL, Role.SIDE_ROOM, "west", null, 1);
    static final Spec NETHER_9X9 = new Spec("small_nether_dungeon/shells/nether_shell_9x9", Kind.NETHER, Role.SIDE_ROOM, null, null, 1);
    static final Spec NETHER_9X11 = new Spec("small_nether_dungeon/shells/nether_shell_9x11", Kind.NETHER, Role.SIDE_ROOM, null, null, 1);
    static final Spec NETHER_9X13 = new Spec("small_nether_dungeon/shells/nether_shell_9x13", Kind.NETHER, Role.SIDE_ROOM, null, null, 1);
    static final Spec CRYPT_0 = new Spec("zombie_dungeon/floor2/room/floor2_room_0", Kind.ZOMBIE, Role.CORRIDOR, "north", "south", 2);
    static final Spec CRYPT_1 = new Spec("zombie_dungeon/floor2/room/floor2_room_1", Kind.ZOMBIE, Role.CORRIDOR, "north", "south", 2);
    static final Spec CRYPT_2 = new Spec("zombie_dungeon/floor2/room/floor2_room_2", Kind.ZOMBIE, Role.CORRIDOR, "north", "south", 2);
    static final Spec CRYPT_3 = new Spec("zombie_dungeon/floor2/room/floor2_room_3", Kind.ZOMBIE, Role.CORRIDOR, "west", "east", 1);
    static final Spec CRYPT_START = new Spec("zombie_dungeon/zombie_start", Kind.ZOMBIE, Role.FULL_DUNGEON, null, null, 1);

    /** Every template that ships in the resources. */
    static final List<Spec> ALL = List.of(SMALL_5X5, SMALL_7X5, SMALL_7X7, SMALL_9X5, NETHER_9X9, NETHER_9X11, NETHER_9X13,
        CRYPT_0, CRYPT_1, CRYPT_2, CRYPT_3, CRYPT_START);

    /**
     * Which rooms suit which culture: the zombie crypt (catacombs) for the cultures with
     * burial halls, nether shells (lava, basalt-dark bricks) for the volcanic/fire ones, the small
     * stone dungeons for the rest.
     */
    static final Map<String, List<Spec>> BY_CULTURE = Map.of(
        "greek", List.of(SMALL_5X5, SMALL_7X7),
        "norse", List.of(SMALL_7X5, SMALL_9X5, CRYPT_0, CRYPT_2, CRYPT_START),
        "medieval", List.of(SMALL_5X5, SMALL_7X5, SMALL_7X7, SMALL_9X5, CRYPT_0, CRYPT_1, CRYPT_2, CRYPT_3, CRYPT_START),
        "egyptian", List.of(SMALL_7X7, CRYPT_0, CRYPT_1, CRYPT_START),
        "mesopotamian", List.of(NETHER_9X9, NETHER_9X11),
        "japanese", List.of(SMALL_5X5, SMALL_7X5),
        "mesoamerican", List.of(SMALL_7X7, SMALL_9X5, CRYPT_2),
        "arabian", List.of(SMALL_5X5, SMALL_9X5),
        "andean", List.of(NETHER_9X9, NETHER_9X11, NETHER_9X13));

    /** Descent of the crypt start: on the floor, clear of the central altar. */
    private static final int[] START_DESCENT = {12, 0, 3};

    private final Function<String, byte[]> loader;
    private final Map<String, StructureTemplate> originals = new HashMap<>();

    public YungsBetterDungeonsSource() {
        this(YungsBetterDungeonsSource::readResource);
    }

    /** {@code loader} returns the original {@code .nbt} bytes of a template path. */
    YungsBetterDungeonsSource(Function<String, byte[]> loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    @Override
    public Map<String, byte[]> licenseFiles() {
        return ThirdPartySources.licenseFiles("yungs_better_dungeons", List.of("COPYING", "LICENSE", "NOTICE"));
    }

    @Override
    public List<MythicExtraPiece> piecesFor(String culture) {
        MythicDungeonTheme theme = MythicDungeonTheme.all().stream()
            .filter(candidate -> candidate.culture().equals(culture)).findFirst().orElse(null);
        if (theme == null) {
            return List.of();
        }
        List<MythicExtraPiece> pieces = new ArrayList<>();
        for (Spec spec : BY_CULTURE.getOrDefault(culture, List.of())) {
            StructureTemplate template = resolved(spec, theme).build(NamespacedId.of("bigc_myth", "third_party/" + spec.path()))
                .remapPalette(ThirdPartyRetheme.mapper(theme));
            String exit = spec.role() == Role.CORRIDOR ? ThirdPartyBlocks.EXIT : null;
            String entry = spec.role() == Role.FULL_DUNGEON ? ThirdPartyBlocks.DESCENT : ThirdPartyBlocks.ENTRY;
            pieces.add(new MythicExtraPiece(spec.pieceName(), template, spec.role(), entry, exit, spec.weight()));
        }
        return pieces;
    }

    synchronized StructureTemplate original(String path) {
        return originals.computeIfAbsent(path, key -> {
            try {
                return StructureTemplate.of(NamespacedId.of("betterdungeons", key), Nbt.readGzip(loader.apply(key)));
            } catch (IOException exception) {
                throw new UncheckedIOException("Cannot read YUNG's template " + key, exception);
            }
        });
    }

    /** The template with placeholders resolved and connectors placed, before re-theming. */
    TemplateGrid resolved(Spec spec, MythicDungeonTheme theme) {
        TemplateGrid grid = TemplateGrid.read(original(spec.path()));
        List<Original> jigsaws = originalJigsaws(grid);
        Random random = new Random(spec.path().hashCode());
        List<int[]> netherStairs = spec.kind() == Kind.NETHER ? entranceStairs(grid) : List.of();
        YungsPlaceholders.resolve(grid, spec.kind(), random, theme);
        switch (spec.kind()) {
            case SMALL -> carveDoorway(grid, spec.entryFace());
            case NETHER -> netherEntry(grid, spec, netherStairs);
            case ZOMBIE -> zombieConnectors(grid, spec, jigsaws);
        }
        return grid;
    }

    private record Original(int x, int y, int z, String front, String pool, String name) {
        boolean doorway() {
            return pool.contains("floor2_hall") || name.contains("floor2_hall") || pool.contains("floor2_room")
                || name.contains("floor2_room") || pool.contains("big_stairs");
        }
    }

    /** Records the original jigsaws and replaces each with its final state. */
    private static List<Original> originalJigsaws(TemplateGrid grid) {
        List<Original> result = new ArrayList<>();
        grid.forEach(cell -> {
            if (!cell.name().equals("minecraft:jigsaw")) {
                return;
            }
            CompoundTag nbt = cell.nbt();
            String orientation = cell.state().properties().getOrDefault("orientation", "north_up");
            result.add(new Original(cell.x(), cell.y(), cell.z(), orientation.substring(0, orientation.indexOf('_')),
                text(nbt, "pool"), text(nbt, "name")));
            String finalState = text(nbt, "final_state");
            grid.set(cell.x(), cell.y(), cell.z(), finalState.isBlank() ? TemplateGrid.AIR : BlockStateSpec.parse(finalState));
        });
        return result;
    }

    private static String text(CompoundTag nbt, String key) {
        String value = nbt == null ? null : nbt.getString(key);
        return value == null ? "" : value;
    }

    /** Small dungeons: 3 wide, 3 tall opening in the middle of a face, connector at the bottom centre. */
    private static void carveDoorway(TemplateGrid grid, String face) {
        boolean north = face.equals("north");
        int centre = ((north ? grid.sizeX() : grid.sizeZ()) - 1) / 2;
        for (int offset = -1; offset <= 1; offset++) {
            for (int y = 1; y <= 3; y++) {
                if (north) {
                    grid.set(centre + offset, y, 0, TemplateGrid.AIR);
                } else {
                    grid.set(0, y, centre + offset, TemplateGrid.AIR);
                }
            }
        }
        for (int y = 1; y <= 2; y++) {
            if (north) {
                grid.set(centre, y, 1, TemplateGrid.AIR);
            } else {
                grid.set(1, y, centre, TemplateGrid.AIR);
            }
        }
        if (north) {
            ThirdPartyBlocks.jigsaw(grid, centre, 1, 0, "north_up", ThirdPartyBlocks.ENTRY);
        } else {
            ThirdPartyBlocks.jigsaw(grid, 0, 1, centre, "west_up", ThirdPartyBlocks.ENTRY);
        }
    }

    /** Positions and facing of the brick-stair placeholders that mark a nether shell's entrance. */
    private static List<int[]> entranceStairs(TemplateGrid grid) {
        List<int[]> stairs = new ArrayList<>();
        for (TemplateGrid.Cell cell : grid.cells()) {
            if (cell.name().equals("minecraft:brick_stairs")) {
                String facing = cell.state().properties().getOrDefault("facing", "south");
                stairs.add(new int[]{cell.x(), cell.y(), cell.z(), List.of("north", "east", "south", "west").indexOf(facing)});
            }
        }
        return stairs;
    }

    private static void netherEntry(TemplateGrid grid, Spec spec, List<int[]> stairs) {
        if (stairs.isEmpty()) {
            throw new IllegalStateException("No entrance in " + spec.path());
        }
        stairs.sort(Comparator.<int[]>comparingInt(pos -> pos[0]).thenComparingInt(pos -> pos[2]));
        int[] middle = stairs.get(stairs.size() / 2);
        String facing = List.of("north", "east", "south", "west").get(middle[3]);
        ThirdPartyBlocks.jigsaw(grid, middle[0], middle[1] + 1, middle[2], facing + "_up", ThirdPartyBlocks.ENTRY);
    }

    private record Doorway(String front, List<Original> cells) {
        Original connector() {
            return cells.getFirst();
        }
    }

    /** Groups adjacent doorway jigsaws on the same face (YUNG's 2-wide doorways). */
    private static List<Doorway> doorways(List<Original> jigsaws) {
        List<Original> candidates = new ArrayList<>(jigsaws.stream().filter(Original::doorway).toList());
        candidates.sort(Comparator.comparing(Original::front).thenComparingInt(Original::y)
            .thenComparingInt(Original::x).thenComparingInt(Original::z));
        List<Doorway> doorways = new ArrayList<>();
        for (Original jigsaw : candidates) {
            Doorway match = null;
            for (Doorway doorway : doorways) {
                Original last = doorway.cells().getLast();
                if (doorway.front().equals(jigsaw.front()) && last.y() == jigsaw.y()
                    && Math.abs(last.x() - jigsaw.x()) + Math.abs(last.z() - jigsaw.z()) == 1) {
                    match = doorway;
                }
            }
            if (match == null) {
                doorways.add(new Doorway(jigsaw.front(), new ArrayList<>(List.of(jigsaw))));
            } else {
                match.cells().add(jigsaw);
            }
        }
        return doorways;
    }

    private static void zombieConnectors(TemplateGrid grid, Spec spec, List<Original> jigsaws) {
        List<Doorway> doorways = doorways(jigsaws);
        if (spec.role() == Role.FULL_DUNGEON) {
            for (Doorway doorway : doorways) {
                Original cell = doorway.connector();
                ThirdPartyBlocks.jigsaw(grid, cell.x(), cell.y(), cell.z(), cell.front() + "_up", ThirdPartyBlocks.SIDE);
            }
            ThirdPartyBlocks.jigsaw(grid, START_DESCENT[0], START_DESCENT[1], START_DESCENT[2], "down_south",
                ThirdPartyBlocks.DESCENT);
            return;
        }
        Doorway entry = doorways.stream().filter(doorway -> doorway.front().equals(spec.entryFace())).findFirst()
            .orElseThrow(() -> new IllegalStateException("No " + spec.entryFace() + " doorway in " + spec.path()));
        Doorway exit = doorways.stream().filter(doorway -> doorway.front().equals(spec.exitFace())).findFirst()
            .orElseThrow(() -> new IllegalStateException("No " + spec.exitFace() + " doorway in " + spec.path()));
        for (Doorway doorway : doorways) {
            Original cell = doorway.connector();
            String name = doorway == entry ? ThirdPartyBlocks.ENTRY
                : doorway == exit ? ThirdPartyBlocks.EXIT : ThirdPartyBlocks.SEAL;
            ThirdPartyBlocks.jigsaw(grid, cell.x(), cell.y(), cell.z(), cell.front() + "_up", name);
        }
    }

    private static byte[] readResource(String path) {
        try (InputStream input = YungsBetterDungeonsSource.class.getResourceAsStream(RESOURCE_ROOT + path + ".nbt")) {
            if (input == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE_ROOT + path + ".nbt");
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Ids the mod uses as placeholders; none may survive {@link #resolved}. */
    static Set<String> placeholderIds() {
        return YungsPlaceholders.PLACEHOLDERS;
    }
}
