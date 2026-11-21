package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El pozo: rareza, huella, construccion y anotacion, todo sin servidor. */
class PitLayoutTest {

    private static final long SEED = 123456789L;
    private static final int RIM = 70;

    /** Guarda cada bloque emitido (y avisa si algun lugar se emite dos veces). */
    private static class Recorder implements PitLayout.Sink {
        final Map<String, Material> blocks = new HashMap<>();
        final Map<String, BlockFace> facings = new HashMap<>();
        int emitted;

        @Override
        public void set(int x, int y, int z, Material material) {
            emitted++;
            blocks.put(key(x, y, z), material);
        }

        @Override
        public void stairs(int x, int y, int z, Material material, BlockFace facing) {
            set(x, y, z, material);
            facings.put(key(x, y, z), facing);
        }

        Material at(int x, int y, int z) {
            return blocks.get(key(x, y, z));
        }

        private static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
    }

    private static PitLayout.Pit pit(int orientation) {
        // un chunk por orientacion: se busca uno que la de
        for (int cx = -50; cx < 50; cx++) {
            if (PitLayout.orientationOf(SEED, cx, 7) == orientation) {
                return PitLayout.create(SEED, cx, 7, RIM);
            }
        }
        throw new AssertionError("no hay chunk con la orientacion " + orientation);
    }

    private static Recorder build(PitLayout.Pit pit) {
        Recorder recorder = new Recorder();
        PitLayout.build(pit, recorder);
        return recorder;
    }

    @Test
    void everyRegionHasExactlyOneDeterministicCandidate() {
        for (int rx = -3; rx <= 3; rx++) {
            for (int rz = -3; rz <= 3; rz++) {
                int[] candidate = PitLayout.candidateChunk(SEED, rx, rz);
                assertEquals(rx, Math.floorDiv(candidate[0], PitLayout.REGION_CHUNKS));
                assertEquals(rz, Math.floorDiv(candidate[1], PitLayout.REGION_CHUNKS));
                assertEquals(candidate[0], PitLayout.candidateChunk(SEED, rx, rz)[0]);
                int count = 0;
                for (int cx = rx * PitLayout.REGION_CHUNKS; cx < (rx + 1) * PitLayout.REGION_CHUNKS; cx++) {
                    for (int cz = rz * PitLayout.REGION_CHUNKS; cz < (rz + 1) * PitLayout.REGION_CHUNKS; cz++) {
                        if (PitLayout.isCandidate(SEED, cx, cz)) {
                            count++;
                            assertEquals(candidate[0], cx);
                            assertEquals(candidate[1], cz);
                        }
                    }
                }
                assertEquals(1, count);
            }
        }
    }

    @Test
    void candidatesDependOnTheSeed() {
        boolean differs = false;
        for (int rx = 0; rx < 6; rx++) {
            differs |= PitLayout.candidateChunk(SEED, rx, 0)[0] != PitLayout.candidateChunk(SEED + 1, rx, 0)[0];
        }
        assertTrue(differs);
    }

    @Test
    void footprintFitsInsideItsChunkForEveryOrientation() {
        for (int orientation = 0; orientation < PitLayout.ORIENTATIONS; orientation++) {
            PitLayout.Pit pit = pit(orientation);
            assertEquals(orientation, pit.orientation());
            int cx = Math.floorDiv(pit.footprintMinX(), 16);
            int cz = Math.floorDiv(pit.footprintMinZ(), 16);
            assertEquals(cx, Math.floorDiv(pit.footprintMaxX(), 16));
            assertEquals(cz, Math.floorDiv(pit.footprintMaxZ(), 16));
            Recorder recorder = build(pit);
            assertTrue(recorder.emitted > 0);
        }
    }

    @Test
    void negativeChunksFitToo() {
        PitLayout.Pit pit = PitLayout.create(SEED, -5, -9, RIM);
        assertEquals(-5, Math.floorDiv(pit.footprintMinX(), 16));
        assertEquals(-5, Math.floorDiv(pit.footprintMaxX(), 16));
        assertEquals(-9, Math.floorDiv(pit.footprintMinZ(), 16));
        assertEquals(-9, Math.floorDiv(pit.footprintMaxZ(), 16));
    }

    @Test
    void footprintSizeIsFourteenByTenOrTenByFourteen() {
        for (int orientation = 0; orientation < PitLayout.ORIENTATIONS; orientation++) {
            PitLayout.Pit pit = pit(orientation);
            int width = pit.footprintMaxX() - pit.footprintMinX() + 1;
            int depth = pit.footprintMaxZ() - pit.footprintMinZ() + 1;
            assertEquals(orientation % 2 == 0 ? PitLayout.LENGTH : PitLayout.WIDTH, width);
            assertEquals(orientation % 2 == 0 ? PitLayout.WIDTH : PitLayout.LENGTH, depth);
        }
    }

    @Test
    void floorIsBlackEverywhereInside() {
        for (int orientation = 0; orientation < PitLayout.ORIENTATIONS; orientation++) {
            PitLayout.Pit pit = pit(orientation);
            Recorder recorder = build(pit);
            int count = 0;
            for (int x = pit.interiorMinX(); x <= pit.interiorMaxX(); x++) {
                for (int z = pit.interiorMinZ(); z <= pit.interiorMaxZ(); z++) {
                    assertEquals(Material.BLACK_CONCRETE, recorder.at(x, pit.floorY(), z));
                    count++;
                }
            }
            assertEquals(12 * 8, count);
        }
    }

    @Test
    void airAboveTheFloorExceptTheStairs() {
        for (int orientation = 0; orientation < PitLayout.ORIENTATIONS; orientation++) {
            PitLayout.Pit pit = pit(orientation);
            Recorder recorder = build(pit);
            int stairCount = 0;
            for (int x = pit.interiorMinX(); x <= pit.interiorMaxX(); x++) {
                for (int z = pit.interiorMinZ(); z <= pit.interiorMaxZ(); z++) {
                    for (int y = pit.floorY() + 1; y <= pit.rimY() + PitLayout.CLEAR_ABOVE; y++) {
                        Material material = recorder.at(x, y, z);
                        assertNotNull(material);
                        if (material == Material.AIR) {
                            continue;
                        }
                        // lo unico que no es aire sobre el piso es la escalera y su relleno de piedra
                        assertTrue(material == PitLayout.STAIRS || material == PitLayout.STONE,
                            "bloque inesperado " + material + " en " + x + "," + y + "," + z);
                        if (material == PitLayout.STAIRS) {
                            stairCount++;
                        }
                    }
                }
            }
            assertEquals(PitLayout.STEPS * PitLayout.STAIR_WIDTH, stairCount);
        }
    }

    @Test
    void stairsDescendOneBlockPerStepFromTheRimToTheFloor() {
        for (int orientation = 0; orientation < PitLayout.ORIENTATIONS; orientation++) {
            PitLayout.Pit pit = pit(orientation);
            Recorder recorder = build(pit);
            BlockFace down = pit.descent();
            int[] edge = {pit.stairX(), pit.stairZ()};
            for (int lane = 0; lane < PitLayout.STAIR_WIDTH; lane++) {
                // el carril lateral: perpendicular a la bajada, hacia adentro del pozo
                int sideX = lane * Math.abs(down.getModZ()) * sideSign(pit, true);
                int sideZ = lane * Math.abs(down.getModX()) * sideSign(pit, false);
                int previousY = pit.rimY() + 1;
                for (int step = 1; step <= PitLayout.STEPS; step++) {
                    int x = edge[0] + down.getModX() * step + sideX;
                    int z = edge[1] + down.getModZ() * step + sideZ;
                    int y = pit.rimY() - (step - 1);
                    assertEquals(PitLayout.STAIRS, recorder.at(x, y, z));
                    assertEquals(down.getOppositeFace(), recorder.facings.get(x + "," + y + "," + z));
                    assertEquals(previousY - 1, y);
                    previousY = y;
                    // cada escalon apoya en piedra maciza hasta el piso
                    for (int below = pit.floorY() + 1; below < y; below++) {
                        assertEquals(PitLayout.STONE, recorder.at(x, below, z));
                    }
                    assertEquals(PitLayout.FLOOR, recorder.at(x, pit.floorY(), z));
                }
                // el ultimo escalon queda a nivel del piso: al lado hay piso libre
                assertEquals(pit.floorY() + 1, previousY);
            }
        }
    }

    /** Hacia que lado (en el eje) se corre el segundo carril: siempre hacia adentro del pozo. */
    private static int sideSign(PitLayout.Pit pit, boolean xAxis) {
        int stair = xAxis ? pit.stairX() : pit.stairZ();
        int min = xAxis ? pit.interiorMinX() : pit.interiorMinZ();
        int max = xAxis ? pit.interiorMaxX() : pit.interiorMaxZ();
        return Math.abs(stair - min) <= Math.abs(max - stair) ? 1 : -1;
    }

    @Test
    void emitsOnlyInsideTheFootprint() {
        for (int orientation = 0; orientation < PitLayout.ORIENTATIONS; orientation++) {
            PitLayout.Pit pit = pit(orientation);
            Recorder recorder = new Recorder() {
                @Override
                public void set(int x, int y, int z, Material material) {
                    assertTrue(x >= pit.footprintMinX() && x <= pit.footprintMaxX()
                        && z >= pit.footprintMinZ() && z <= pit.footprintMaxZ(), "fuera de la huella: " + x + "," + z);
                    assertTrue(y >= pit.floorY() - 1 && y <= pit.rimY() + PitLayout.CLEAR_ABOVE);
                    super.set(x, y, z, material);
                }
            };
            PitLayout.build(pit, recorder);
            int footprint = PitLayout.LENGTH * PitLayout.WIDTH;
            assertTrue(recorder.blocks.size() > footprint);
        }
    }

    @Test
    void wallsAndRimAreStoneAndMarble() {
        PitLayout.Pit pit = pit(0);
        Recorder recorder = build(pit);
        for (int x = pit.footprintMinX(); x <= pit.footprintMaxX(); x++) {
            Material rim = recorder.at(x, pit.rimY(), pit.footprintMinZ());
            assertTrue(rim == PitLayout.MARBLE || rim == PitLayout.MARBLE_LIGHT);
            Material wall = recorder.at(x, pit.rimY() - 1, pit.footprintMinZ());
            assertTrue(wall == PitLayout.STONE || wall == PitLayout.CRACKED || wall == PitLayout.MOSSY);
            assertEquals(PitLayout.MARBLE_LIGHT, recorder.at(x, pit.rimY() - 3, pit.footprintMinZ()));
        }
        // lo despejado arriba del borde
        assertEquals(Material.AIR, recorder.at(pit.footprintMinX(), pit.rimY() + PitLayout.CLEAR_ABOVE,
            pit.footprintMinZ()));
    }

    @Test
    void buildIsDeterministic() {
        PitLayout.Pit pit = pit(2);
        assertEquals(build(pit).blocks, build(pit).blocks);
    }

    @Test
    void encodeDecodeRoundTrips() {
        for (int orientation = 0; orientation < PitLayout.ORIENTATIONS; orientation++) {
            PitLayout.Pit pit = pit(orientation);
            assertEquals(Optional.of(pit), PitLayout.Pit.decode(pit.encode()));
        }
        PitLayout.Pit negative = PitLayout.create(SEED, -5, -9, 64);
        assertEquals(Optional.of(negative), PitLayout.Pit.decode(negative.encode()));
    }

    @Test
    void decodeToleratesGarbage() {
        for (String raw : new String[] {null, "", "x", "1,2,3,4,5", "1,2,3,4,5,6,7", "1,2,3,9,5,6", "1,2,3,-1,5,6",
            "a,b,c,d,e,f", "1,2,3,0,5,", ",,,,,", "1;2;3;0;5;6", "99999999999,2,3,0,5,6", " 1,2,3,0,5,6"}) {
            assertEquals(Optional.empty(), PitLayout.Pit.decode(raw), "debia ser vacio: " + raw);
        }
    }

    @Test
    void floorAndInteriorBounds() {
        PitLayout.Pit pit = pit(0);
        int x0 = pit.interiorMinX();
        int z0 = pit.interiorMinZ();
        int x1 = pit.interiorMaxX();
        int z1 = pit.interiorMaxZ();
        int y = pit.floorY();
        assertTrue(pit.floorContains(x0, y, z0));
        assertTrue(pit.floorContains(x1, y, z1));
        assertFalse(pit.floorContains(x0 - 1, y, z0));
        assertFalse(pit.floorContains(x1 + 1, y, z1));
        assertFalse(pit.floorContains(x0, y, z0 - 1));
        assertFalse(pit.floorContains(x0, y, z1 + 1));
        assertFalse(pit.floorContains(x0, y + 1, z0));
        assertFalse(pit.floorContains(x0, y - 1, z0));

        assertTrue(pit.interiorContains(x0, y + 1, z0));
        assertTrue(pit.interiorContains(x1, pit.rimY() + PitLayout.HOLLOW_ABOVE, z1));
        assertFalse(pit.interiorContains(x0, y, z0));
        assertFalse(pit.interiorContains(x0, pit.rimY() + PitLayout.HOLLOW_ABOVE + 1, z0));
        assertFalse(pit.interiorContains(x0 - 1, y + 1, z0));
        assertFalse(pit.interiorContains(x1 + 1, y + 1, z1));
        assertFalse(pit.interiorContains(x1, y + 1, z1 + 1));
    }

    @Test
    void returnPointIsOnTheRimOutsideTheHollowFacingAway() {
        for (int orientation = 0; orientation < PitLayout.ORIENTATIONS; orientation++) {
            PitLayout.Pit pit = pit(orientation);
            int x = (int) Math.floor(pit.returnX());
            int z = (int) Math.floor(pit.returnZ());
            assertEquals(pit.rimY() + 1, pit.returnY());
            assertFalse(pit.interiorContains(x, pit.returnY(), z));
            assertFalse(pit.floorContains(x, pit.returnY() - 1, z));
            assertEquals(pit.descent().getOppositeFace(), pit.outward());
            // el bloque de abajo es del borde de marmol
            Recorder recorder = build(pit);
            Material rim = recorder.at(x, pit.rimY(), z);
            assertTrue(rim == PitLayout.MARBLE || rim == PitLayout.MARBLE_LIGHT);
            // y un paso hacia adentro empieza la escalera
            assertEquals(PitLayout.STAIRS, recorder.at(x + pit.descent().getModX(), pit.rimY(),
                z + pit.descent().getModZ()));
        }
    }
}
