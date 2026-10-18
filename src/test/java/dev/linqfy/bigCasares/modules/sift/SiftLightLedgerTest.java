package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.SiftLightLedger.Cell;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftLightLedgerTest {

    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SINGER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** An in-memory world: cells hold "air", "stone", "light:N" or anything else; unknown cells are air. */
    private static final class FakeWorld implements SiftLightLedger.Gateway {
        final Map<Cell, String> blocks = new HashMap<>();
        final Set<Integer> unloadedChunks = new java.util.HashSet<>();
        final List<String> log = new ArrayList<>();
        boolean failPlacing;

        String at(Cell cell) {
            return blocks.getOrDefault(cell, "air");
        }

        long lights() {
            return blocks.values().stream().filter(block -> block.startsWith("light:")).count();
        }

        @Override
        public boolean isFree(Cell cell) {
            return !unloadedChunks.contains(cell.chunkX()) && at(cell).equals("air");
        }

        @Override
        public boolean isLight(Cell cell, int level) {
            return !unloadedChunks.contains(cell.chunkX()) && at(cell).equals("light:" + level);
        }

        @Override
        public boolean place(Cell cell, int level) {
            if (failPlacing || unloadedChunks.contains(cell.chunkX()) || !(at(cell).equals("air") || at(cell).startsWith("light:"))) {
                return false;
            }
            blocks.put(cell, "light:" + level);
            log.add("place " + cell.x() + "," + cell.y() + "," + cell.z());
            return true;
        }

        @Override
        public void remove(Cell cell) {
            if (at(cell).startsWith("light:")) {
                blocks.remove(cell);
                log.add("remove " + cell.x() + "," + cell.y() + "," + cell.z());
            }
        }
    }

    private static Cell cell(int x, int y, int z) {
        return new Cell(WORLD, x, y, z);
    }

    private static List<Cell> around(double x, double y, double z) {
        return SiftLightLedger.candidates(WORLD, x, y, z);
    }

    @Test
    void candidatesAreTheHeadCellThenBelowAboveAndTwoBelow() {
        assertEquals(List.of(cell(3, 70, -5), cell(3, 69, -5), cell(3, 71, -5), cell(3, 68, -5)), around(3.4, 70.9, -4.2));
    }

    @Test
    void aSourceGetsOneLightBlockAtItsCell() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        assertEquals("light:15", world.at(cell(0, 70, 0)));
        assertEquals(1, world.lights());
        assertEquals(cell(0, 70, 0), ledger.cellOf(SINGER));
    }

    @Test
    void anUnchangedSourceCostsNoWrites() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        world.log.clear();
        for (int i = 0; i < 20; i++) {
            ledger.update(SINGER, around(0.7, 70.2, 0.1), 15);
        }
        assertEquals(List.of(), world.log);
    }

    @Test
    void aMovingSourceKeepsExactlyOneLightAndPlacesBeforeItRemoves() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(GOLEM, around(0.5, 70.5, 0.5), 14);
        world.log.clear();
        for (int x = 1; x <= 10; x++) {
            ledger.update(GOLEM, around(x + 0.5, 70.5, 0.5), 14);
            assertEquals(1, world.lights(), "one light after each step");
        }
        assertEquals("place 1,70,0", world.log.get(0));
        assertEquals("remove 0,70,0", world.log.get(1), "the new light exists before the old one goes");
        assertEquals("light:14", world.at(cell(10, 70, 0)));
        assertEquals("air", world.at(cell(0, 70, 0)));
    }

    @Test
    void aLightIsOnlyPlacedInAir() {
        FakeWorld world = new FakeWorld();
        world.blocks.put(cell(0, 70, 0), "stone");
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        assertEquals("stone", world.at(cell(0, 70, 0)), "the solid head cell is untouched");
        assertEquals("light:15", world.at(cell(0, 69, 0)), "the next candidate is used");
    }

    @Test
    void aSourceInsideSolidGroundLightsNothingAndPlacesNothing() {
        FakeWorld world = new FakeWorld();
        for (Cell candidate : around(0.5, 70.5, 0.5)) {
            world.blocks.put(candidate, "stone");
        }
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        assertEquals(0, world.lights());
        assertFalse(ledger.hasLight(SINGER));
    }

    @Test
    void aLightMovesToTheHeadCellOnceItOpens() {
        FakeWorld world = new FakeWorld();
        world.blocks.put(cell(0, 70, 0), "stone");
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        assertEquals(cell(0, 69, 0), ledger.cellOf(SINGER));
        world.blocks.remove(cell(0, 70, 0));
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        assertEquals(cell(0, 70, 0), ledger.cellOf(SINGER));
        assertEquals(1, world.lights());
        assertEquals("air", world.at(cell(0, 69, 0)));
    }

    @Test
    void anotherSourcesLightIsNotFreeAndIsNeverRemovedByThisOne() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        ledger.update(GOLEM, around(0.5, 70.5, 0.5), 14);
        assertEquals("light:15", world.at(cell(0, 70, 0)));
        assertEquals("light:14", world.at(cell(0, 69, 0)), "the golem took its next candidate");
        ledger.release(GOLEM);
        assertEquals("light:15", world.at(cell(0, 70, 0)), "the Singer's light is still there");
        assertEquals(1, world.lights());
    }

    @Test
    void aBlockPlayersPutOnTheLightIsNeverDeleted() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        world.blocks.put(cell(0, 70, 0), "planks"); // built over the light block
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        assertEquals("planks", world.at(cell(0, 70, 0)));
        assertEquals("light:15", world.at(cell(0, 69, 0)), "the light moved to a free cell");
        ledger.release(SINGER);
        assertEquals("planks", world.at(cell(0, 70, 0)));
        assertEquals(0, world.lights());
    }

    @Test
    void aLightBlockThatWasBrokenIsPlacedAgain() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        world.blocks.remove(cell(0, 70, 0));
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        assertEquals("light:15", world.at(cell(0, 70, 0)));
    }

    @Test
    void aChangedLevelReplacesTheLightInPlace() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 10);
        assertEquals("light:10", world.at(cell(0, 70, 0)));
        assertEquals(1, world.lights());
    }

    @Test
    void releasingRemovesTheLightAndForgetsTheSource() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        ledger.release(SINGER);
        ledger.release(SINGER);
        assertEquals(0, world.lights());
        assertEquals(0, ledger.size());
        assertNull(ledger.cellOf(SINGER));
    }

    @Test
    void releaseExceptKeepsOnlyTheListedSources() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        ledger.update(GOLEM, around(10.5, 70.5, 0.5), 14);
        ledger.releaseExcept(Set.of(GOLEM));
        assertFalse(ledger.hasLight(SINGER));
        assertTrue(ledger.hasLight(GOLEM));
        assertEquals(1, world.lights());
    }

    @Test
    void anUnloadingChunkLosesItsLightsOnly() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);     // chunk 0
        ledger.update(GOLEM, around(40.5, 70.5, 0.5), 14);     // chunk 2
        ledger.releaseChunk(WORLD, 0, 0);
        assertEquals("air", world.at(cell(0, 70, 0)));
        assertEquals("light:14", world.at(cell(40, 70, 0)));
        ledger.releaseChunk(UUID.randomUUID(), 2, 0);
        assertEquals("light:14", world.at(cell(40, 70, 0)), "another world's chunk is not this one");
    }

    @Test
    void releaseAllLeavesNoLightBlock() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        for (int i = 0; i < 12; i++) {
            ledger.update(UUID.nameUUIDFromBytes(new byte[]{(byte) i}), around(i * 3 + 0.5, 70.5, 0.5), 15);
        }
        assertEquals(12, world.lights());
        ledger.releaseAll();
        assertEquals(0, world.lights());
        assertEquals(0, ledger.size());
    }

    @Test
    void aSourceInAnUnloadedChunkGetsNoLight() {
        FakeWorld world = new FakeWorld();
        world.unloadedChunks.add(0);
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        assertEquals(0, world.lights());
    }

    @Test
    void aFailedPlacementLeavesNoStrayLight() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        ledger.update(SINGER, around(0.5, 70.5, 0.5), 15);
        world.failPlacing = true;
        ledger.update(SINGER, around(5.5, 70.5, 0.5), 15);
        assertEquals(0, world.lights(), "the old light was released rather than left behind");
        assertFalse(ledger.hasLight(SINGER));
    }

    @Test
    void theNearestSourcesInRangeGetTheLights() {
        Map<UUID, Double> distances = new LinkedHashMap<>();
        UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        UUID c = UUID.fromString("00000000-0000-0000-0000-00000000000c");
        UUID d = UUID.fromString("00000000-0000-0000-0000-00000000000d");
        distances.put(a, 900.0);
        distances.put(b, 100.0);
        distances.put(c, 5000.0);
        distances.put(d, 400.0);
        assertEquals(List.of(b, d, a), List.copyOf(SiftLightLedger.nearest(distances, 1000.0, 10)), "range cuts c, order is nearest first");
        assertEquals(List.of(b, d), List.copyOf(SiftLightLedger.nearest(distances, 1000.0, 2)), "the cap keeps the nearest");
        assertEquals(Set.of(), SiftLightLedger.nearest(distances, 10.0, 5));
    }

    @Test
    void aWholeSessionOfMovesNeverLeavesMoreLightsThanSources() {
        FakeWorld world = new FakeWorld();
        SiftLightLedger ledger = new SiftLightLedger(world);
        java.util.Random random = new java.util.Random(42);
        // Some solid cells to bump into.
        for (int i = 0; i < 60; i++) {
            world.blocks.put(cell(random.nextInt(40), 69 + random.nextInt(3), random.nextInt(8)), "stone");
        }
        long solid = world.blocks.size();
        for (int step = 0; step < 2000; step++) {
            for (UUID source : List.of(SINGER, GOLEM)) {
                if (random.nextInt(10) == 0) {
                    ledger.release(source);
                } else {
                    ledger.update(source, around(random.nextInt(40) + 0.5, 70.5 + random.nextInt(2), random.nextInt(8) + 0.5),
                        source == SINGER ? 15 : 14);
                }
            }
            assertTrue(world.lights() <= 2, "at most one light per source at step " + step);
            assertEquals(ledger.size(), world.lights(), "the ledger and the world agree at step " + step);
        }
        ledger.releaseAll();
        assertEquals(0, world.lights());
        assertEquals(solid, world.blocks.size(), "no other block was touched");
    }
}
