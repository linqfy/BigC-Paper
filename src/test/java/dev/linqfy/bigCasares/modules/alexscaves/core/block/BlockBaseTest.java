package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockBaseTest {

    @Test
    void everyBaseRoundTripsThroughItsBlockDataString() {
        for (BlockBase base : BlockBase.values()) {
            for (int index = 0; index < base.count(); index++) {
                CustomBlockState state = new CustomBlockState(base, index);
                assertEquals(state, BlockBase.parse(state.blockDataString()).orElseThrow(), state.blockDataString());
            }
        }
        assertEquals("minecraft:tripwire[attached=false,disarmed=true,east=false,north=true,powered=false,south=false,west=true]",
            BlockBase.TRIPWIRE.blockDataString(32 + 8 + 1));
        assertEquals("minecraft:chorus_plant[down=false,east=true,north=true,south=false,up=false,west=false]",
            BlockBase.CHORUS_PLANT.blockDataString(8 + 4));
        assertTrue(BlockBase.parse("minecraft:stone").isEmpty());
        assertTrue(BlockBase.parse("minecraft:tripwire[attached=false]").isEmpty(), "missing properties");
    }

    @Test
    void onlyStatesVanillaNeverMakesAreAllocatable() {
        assertEquals(750, IntStream.range(0, 800).filter(BlockBase.NOTE_BLOCK::allocatable).count());
        assertEquals(64, IntStream.range(0, 128).filter(BlockBase.TRIPWIRE::allocatable).count());
        assertEquals(23, IntStream.range(0, 64).filter(BlockBase.CHORUS_PLANT::allocatable).count());
        assertFalse(BlockBase.NOTE_BLOCK.allocatable(49), "harp stays vanilla");
        for (int index = 0; index < 128; index++) {
            Map<String, String> state = BlockBase.TRIPWIRE.properties(index);
            if (state.get("disarmed").equals("false")) {
                assertFalse(BlockBase.TRIPWIRE.allocatable(index), "player and temple string: " + state);
            }
        }
        for (int index = 0; index < 64; index++) {
            Map<String, String> state = BlockBase.CHORUS_PLANT.properties(index);
            long horizontal = Map.of("n", state.get("north"), "e", state.get("east"), "s", state.get("south"), "w", state.get("west"))
                .values().stream().filter("true"::equals).count();
            boolean endGeneration = state.get("down").equals("true") || horizontal == 1;
            if (endGeneration || index == 0) {
                assertFalse(BlockBase.CHORUS_PLANT.allocatable(index), "End chorus and the default state: " + state);
            }
        }
    }

    @Test
    void registryReadsBasesAndLegacyStates() {
        ModBlockRegistry registry = ModBlockRegistry.parse("""
            mussel:
              up_1: tripwire 32 legacy 600
            galena:
              default: 60
            """);
        CustomBlockState mussel = registry.state("mussel", "up_1").orElseThrow();
        assertEquals(new CustomBlockState(BlockBase.TRIPWIRE, 32), mussel);
        assertEquals(registry.at(mussel), registry.at(CustomBlockState.note(600)), "legacy state still draws the block");
        assertEquals(Map.of(CustomBlockState.note(600), mussel), registry.legacy());
        assertEquals(3, registry.usedStates().size());
        // Vanilla states, reused legacy states and unknown bases fail the enable.
        assertThrows(IllegalStateException.class, () -> ModBlockRegistry.parse("a:\n  default: tripwire 0\n"));
        assertThrows(IllegalStateException.class, () -> ModBlockRegistry.parse("a:\n  default: chorus_plant 32\n"));
        assertThrows(IllegalStateException.class, () -> ModBlockRegistry.parse("a:\n  x: tripwire 32 legacy 60\n  y: 60\n"));
        assertThrows(IllegalStateException.class, () -> ModBlockRegistry.parse("a:\n  default: leaves 3\n"));
    }

    @Test
    void shippedAllocationKeepsEveryOldStateInPlace() throws Exception {
        // Append-only: every note block index of the previous allocation is still used, by the same block.
        ModBlockRegistry registry = ModBlockRegistry.parse(Files.readString(Path.of("src/main/resources/alexscaves/blocks.yml")));
        assertEquals(new ModBlockRegistry.Placed("galena", "default"), registry.at(registry.state("galena", "default").orElseThrow()).orElseThrow());
        assertEquals(63, registry.usedStates().stream().filter(s -> s.base() == BlockBase.TRIPWIRE).count());
        assertEquals(22, registry.usedStates().stream().filter(s -> s.base() == BlockBase.CHORUS_PLANT).count());
        assertEquals(85, registry.legacy().size(), "every moved variant and new plant keeps a note block state");
    }

    @Test
    void migrationMovesLegacyStatesOnceAndOnlyThose() {
        LegacyBlockMigration<String> migration = new LegacyBlockMigration<>(Map.of("note:600", "tripwire:32"));
        Map<Integer, String> grid = new HashMap<>();
        grid.put(0, "note:600");
        grid.put(5, "note:601");
        grid.put(4095, "note:600");
        grid.put(7, "stone");
        LegacyBlockMigration.Section<String> section = new LegacyBlockMigration.Section<>() {
            @Override
            public boolean candidate(int x, int y, int z) {
                return get(x, y, z).startsWith("note:");
            }

            @Override
            public String get(int x, int y, int z) {
                return grid.getOrDefault(x + z * 16 + y * 256, "air");
            }

            @Override
            public void set(int x, int y, int z, String state) {
                grid.put(x + z * 16 + y * 256, state);
            }
        };
        assertEquals(2, migration.migrate(section));
        assertEquals("tripwire:32", grid.get(0));
        assertEquals("tripwire:32", grid.get(4095));
        assertEquals("note:601", grid.get(5), "a current note block state is left alone");
        assertEquals("stone", grid.get(7));
        assertEquals(0, migration.migrate(section), "idempotent");
    }
}
