package dev.linqfy.bigCasares.modules.mythicdungeons.boss.enlil;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The flood and its journal on disk, with a fake world: a 10x10 room (walls around it) on stone at y=63. */
class EnlilFloodTest {

    private static final Logger LOGGER = Logger.getAnonymousLogger();
    private static final EnlilArena ARENA = new EnlilArena(0, 9, 0, 9, 64);

    @TempDir
    Path dir;

    private final Map<String, Material> types = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();
    private final World world = (World) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {World.class},
        (proxy, method, args) -> switch (method.getName()) {
            case "getBlockAt" -> block((int) args[0], (int) args[1], (int) args[2]);
            case "getName" -> "dungeon";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "FakeWorld";
            default -> throw new UnsupportedOperationException(method.getName());
        });

    private final EnlilFloodJournal.Cells cells = new EnlilFloodJournal.Cells() {
        @Override
        public EnlilFloodJournal.State state(EnlilFloodJournal.Cell cell) {
            if (!cell.world().equals("dungeon")) {
                return EnlilFloodJournal.State.NO_WORLD;
            }
            return type(cell.x(), cell.y(), cell.z()) == Material.WATER
                ? EnlilFloodJournal.State.WATER : EnlilFloodJournal.State.OTHER;
        }

        @Override
        public void dry(EnlilFloodJournal.Cell cell) {
            types.put(key(cell.x(), cell.y(), cell.z()), Material.AIR);
        }
    };

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private Material type(int x, int y, int z) {
        Material set = types.get(key(x, y, z));
        if (set != null) {
            return set;
        }
        boolean inside = x >= ARENA.minX() && x <= ARENA.maxX() && z >= ARENA.minZ() && z <= ARENA.maxZ();
        return y >= 64 && inside ? Material.AIR : Material.STONE;
    }

    private Block block(int x, int y, int z) {
        return blocks.computeIfAbsent(key(x, y, z), k -> {
            BlockData data = (BlockData) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {BlockData.class}, (p, m, a) -> switch (m.getName()) {
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    default -> throw new UnsupportedOperationException(m.getName());
                });
            return (Block) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Block.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getType" -> type(x, y, z);
                    case "isEmpty" -> type(x, y, z) == Material.AIR;
                    case "getBlockData" -> data;
                    case "setType" -> types.put(k, (Material) a[0]);
                    case "setBlockData" -> types.put(k, Material.AIR);
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    case "toString" -> "Block(" + k + ")";
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        });
    }

    private long water() {
        return types.values().stream().filter(type -> type == Material.WATER).count();
    }

    private List<String> journalLines(Path path) throws IOException {
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    private List<Path> files() throws IOException {
        try (Stream<Path> list = Files.list(dir)) {
            return list.toList();
        }
    }

    @Test
    void journalListsOnlyTheCellsAlreadyPlacedAndLeavesPlayerWaterAlone() throws IOException {
        UUID boss = UUID.randomUUID();
        EnlilFlood flood = new EnlilFlood(dir, LOGGER, boss, world, ARENA, 5, 5);
        assertTrue(flood.raiseTo(1));
        flood.tick();
        assertEquals(40, water());
        Path journal = dir.resolve("enlil-flood-" + boss + ".txt");
        assertEquals(40, journalLines(journal).size());
        assertFalse(Files.exists(dir.resolve("enlil-flood-" + boss + ".txt.tmp")));

        // crash: the fight never restores; a player pours water on a cell the flood never reached
        int px = -1;
        int pz = -1;
        for (int x = 0; x <= 9 && px < 0; x++) {
            for (int z = 0; z <= 9; z++) {
                if (type(x, 64, z) == Material.AIR) {
                    px = x;
                    pz = z;
                    break;
                }
            }
        }
        types.put(key(px, 64, pz), Material.WATER);

        EnlilFloodJournal.Recovery recovery = EnlilFloodJournal.recover(dir, Set.of(), new HashSet<>(), cells);
        assertEquals(40, recovery.dried());
        assertEquals(1, water());
        assertEquals(Material.WATER, types.get(key(px, 64, pz)));
    }

    @Test
    void cleanRestoreKeepsTheJournalUntilALaterStartSeesTheCellsDry() throws IOException {
        UUID boss = UUID.randomUUID();
        EnlilFlood flood = new EnlilFlood(dir, LOGGER, boss, world, ARENA, 5, 5);
        flood.raiseTo(2);
        for (int tick = 0; tick < 10; tick++) {
            flood.tick();
        }
        assertEquals(200, water());
        flood.restore();
        assertEquals(0, water());
        assertFalse(Files.exists(dir.resolve("enlil-flood-" + boss + ".txt")));
        List<Path> left = files();
        assertEquals(1, left.size());
        assertTrue(left.getFirst().getFileName().toString().endsWith(".restored"));
        assertEquals(200, journalLines(left.getFirst()).size());

        // a new fight in the same run: the air may not be saved yet, so the journal stays
        EnlilFlood.recoverOrphans(dir, LOGGER, cells);
        assertEquals(1, files().size());

        // crash before the chunks were saved: on the next start the water is back and gets dried again
        types.replaceAll((k, type) -> Material.WATER);
        Set<Path> nextRun = new HashSet<>();
        EnlilFloodJournal.Recovery dried = EnlilFloodJournal.recover(dir, Set.of(), nextRun, cells);
        assertTrue(dried.dried() > 0);
        assertEquals(0, water());
        assertEquals(1, files().size());

        // a later start sees every cell dry: now it is deleted
        EnlilFloodJournal.Recovery clean = EnlilFloodJournal.recover(dir, Set.of(), new HashSet<>(), cells);
        assertEquals(0, clean.dried());
        assertEquals(1, clean.deleted());
        assertTrue(files().isEmpty());
    }

    @Test
    void anOrphanActiveJournalBecomesRestoredInsteadOfBeingDeletedRightAway() throws IOException {
        UUID boss = UUID.randomUUID();
        EnlilFloodJournal journal = new EnlilFloodJournal(dir, boss);
        assertTrue(journal.append(List.of(new EnlilFloodJournal.Cell("dungeon", 1, 64, 1))));
        types.put(key(1, 64, 1), Material.WATER);
        Set<Path> run = new HashSet<>();
        EnlilFloodJournal.recover(dir, Set.of(), run, cells);
        assertEquals(0, water());
        assertFalse(Files.exists(journal.path()));
        assertEquals(1, files().size());
        assertEquals(1, run.size());
        // same run: kept; next run: deleted
        EnlilFloodJournal.recover(dir, Set.of(), run, cells);
        assertEquals(1, files().size());
        EnlilFloodJournal.recover(dir, Set.of(), new HashSet<>(), cells);
        assertTrue(files().isEmpty());
    }

    @Test
    void liveBossesAndUnloadedWorldsAreLeftAlone() throws IOException {
        UUID live = UUID.randomUUID();
        UUID elsewhere = UUID.randomUUID();
        new EnlilFloodJournal(dir, live).append(List.of(new EnlilFloodJournal.Cell("dungeon", 2, 64, 2)));
        new EnlilFloodJournal(dir, elsewhere).append(List.of(new EnlilFloodJournal.Cell("gone", 2, 64, 2)));
        types.put(key(2, 64, 2), Material.WATER);
        EnlilFloodJournal.Recovery recovery = EnlilFloodJournal.recover(dir, Set.of(live), new HashSet<>(), cells);
        assertEquals(0, recovery.dried());
        assertEquals(0, recovery.deleted());
        assertEquals(1, water());
        assertEquals(2, files().size());
    }

    @Test
    void doesNotFloodWhenTheJournalCannotBeWritten() throws IOException {
        Path blocked = dir.resolve("not-a-folder");
        Files.writeString(blocked, "x");
        EnlilFlood flood = new EnlilFlood(blocked.resolve("mythicdungeons"), LOGGER, UUID.randomUUID(), world, ARENA,
            5, 5);
        assertTrue(flood.raiseTo(1));
        flood.tick();
        flood.tick();
        assertEquals(0, water());
        assertTrue(flood.leaked());
        assertFalse(flood.raiseTo(2));
        flood.restore();
    }
}
