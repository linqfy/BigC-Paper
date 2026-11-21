package dev.linqfy.bigCasares.modules.mythicdungeons.boss.pachamama;

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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PachamamaFloorTest {

    @TempDir
    Path dir;

    @Test
    void aBlockThePlayerChangedIsNotOverwritten() {
        PachamamaFloor floor = floor(dir.resolve("journal.txt"));
        FakeBlock painted = new FakeBlock(0, Material.STONE);
        FakeBlock mined = new FakeBlock(1, Material.STONE);
        FakeBlock water = new FakeBlock(2, Material.AIR);
        FakeBlock blocked = new FakeBlock(3, Material.AIR);
        assertTrue(floor.change(Map.of(painted.block, data(Material.BLUE_CONCRETE),
            mined.block, data(Material.BLUE_CONCRETE))));
        assertTrue(floor.change(Map.of(water.block, data(Material.WATER), blocked.block, data(Material.WATER))));

        mined.type = Material.AIR;               // a player mined the painted concrete
        water.data = data(Material.WATER);      // the source now flows: still water
        blocked.type = Material.CHEST;          // a player put a chest in the water
        floor.restore(List.of(water.block, blocked.block));
        floor.restoreAll();

        assertEquals(Material.STONE, painted.type);
        assertEquals(Material.AIR, mined.type);
        assertEquals(1, mined.writes);
        assertEquals(Material.AIR, water.type);
        assertEquals(Material.CHEST, blocked.type);
        assertEquals(1, blocked.writes);
    }

    @Test
    void stillPlacedComparesTheBlockType() {
        assertTrue(PachamamaFloor.stillPlaced(Material.WATER, Material.WATER));
        assertFalse(PachamamaFloor.stillPlaced(Material.COBBLESTONE, Material.WATER));
        assertFalse(PachamamaFloor.stillPlaced(Material.AIR, Material.BLUE_CONCRETE));
    }

    @Test
    void theJournalIsReplacedWholeBeforeTheWorldChanges() throws IOException {
        Path journal = dir.resolve("mythicdungeons").resolve("journal.txt");
        PachamamaFloor floor = floor(journal);
        FakeBlock block = new FakeBlock(0, Material.STONE);
        block.onWrite = () -> assertTrue(Files.readString(journal, StandardCharsets.UTF_8)
            .contains("BLUE_CONCRETE"), "journal written first");

        assertTrue(floor.change(Map.of(block.block, data(Material.BLUE_CONCRETE))));

        assertEquals(Material.BLUE_CONCRETE, block.type);
        assertEquals("world 0 64 0 BLUE_CONCRETE minecraft:stone\n", Files.readString(journal, StandardCharsets.UTF_8));
        assertFalse(Files.exists(journal.resolveSibling("journal.txt.tmp")));
        floor.restoreAll();
        assertFalse(Files.exists(journal));
    }

    @Test
    void nothingChangesWhenTheJournalCannotBeWritten() throws IOException {
        Path notAFolder = dir.resolve("mythicdungeons");
        Files.writeString(notAFolder, "a file where the folder should be");
        PachamamaFloor floor = floor(notAFolder.resolve("journal.txt"));
        FakeBlock block = new FakeBlock(0, Material.STONE);

        assertFalse(floor.change(Map.of(block.block, data(Material.BLUE_CONCRETE))));

        assertEquals(Material.STONE, block.type);
        assertEquals(0, block.writes);
        floor.restoreAll();
        assertEquals(0, block.writes);
    }

    // ----------------------------------------------------------------------- fakes

    private static PachamamaFloor floor(Path journal) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return new PachamamaFloor(journal, logger, UUID.randomUUID());
    }

    private static BlockData data(Material material) {
        return (BlockData) Proxy.newProxyInstance(PachamamaFloorTest.class.getClassLoader(),
            new Class<?>[]{BlockData.class}, (self, method, args) -> switch (method.getName()) {
                case "getMaterial" -> material;
                case "getAsString" -> "minecraft:" + material.name().toLowerCase(Locale.ROOT);
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                default -> null;
            });
    }

    private interface Check {
        void run() throws IOException;
    }

    private static final class FakeBlock {

        final Block block;
        Material type;
        BlockData data;
        int writes;
        Check onWrite;

        FakeBlock(int x, Material type) {
            this.type = type;
            this.data = data(type);
            World world = (World) Proxy.newProxyInstance(PachamamaFloorTest.class.getClassLoader(),
                new Class<?>[]{World.class}, (self, method, args) -> "getName".equals(method.getName()) ? "world"
                    : null);
            this.block = (Block) Proxy.newProxyInstance(PachamamaFloorTest.class.getClassLoader(),
                new Class<?>[]{Block.class}, (self, method, args) -> switch (method.getName()) {
                    case "getType" -> this.type;
                    case "getBlockData" -> this.data;
                    case "setBlockData" -> {
                        if (onWrite != null) {
                            onWrite.run();
                        }
                        writes++;
                        this.data = (BlockData) args[0];
                        this.type = this.data.getMaterial();
                        yield null;
                    }
                    case "getWorld" -> world;
                    case "getX" -> x;
                    case "getY" -> 64;
                    case "getZ" -> 0;
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    case "toString" -> "block " + x;
                    default -> null;
                });
        }
    }
}
