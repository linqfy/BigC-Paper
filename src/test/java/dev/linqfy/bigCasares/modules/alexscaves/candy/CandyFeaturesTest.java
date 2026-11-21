package dev.linqfy.bigCasares.modules.alexscaves.candy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl.Carve;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBowl.Kind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandyFeaturesTest {

    @Test
    void chocolateBandsFollowTheModSurfaceRule() {
        // createBands(20, 2, 10): bottoms at 52, 42, ...; every third band one block thicker.
        assertTrue(CandyFeatures.Terrain.isBand(52) && CandyFeatures.Terrain.isBand(53));
        assertTrue(!CandyFeatures.Terrain.isBand(54) && !CandyFeatures.Terrain.isBand(51));
        assertTrue(CandyFeatures.Terrain.isBand(34), "third band (bottom 32) is three thick");
        assertTrue(!CandyFeatures.Terrain.isBand(35));
        assertTrue(!CandyFeatures.Terrain.isBand(-139), "only 20 bands");
    }

    @Test
    void cakeCaveIsADomeOverPlateaus() {
        CaveBowl cave = new CaveBowl(Kind.CAKE_CAVE, 0, -48, 0, 105, 100);
        int air = 0;
        for (int x = -100; x <= 100; x += 4) {
            for (int z = -100; z <= 100; z += 4) {
                int floor = (int) CaveBowl.plateauHeight(x, z, 7, true);
                assertTrue(cave.carve(x, floor - 1, z) == Carve.NONE, "the plateau stays solid");
                for (int y = floor; y < 40; y += 3) {
                    if (cave.carve(x, y, z) == Carve.AIR) {
                        air++;
                    }
                }
            }
        }
        assertTrue(air > 1000, "open cave: " + air);
        assertTrue(cave.carve(0, 90, 0) == Carve.NONE, "closed above the dome");
    }

    @Test
    void plateausStepInWholeLevels() {
        for (int x = -300; x <= 300; x += 37) {
            for (int z = -300; z <= 300; z += 41) {
                double flat = CaveBowl.plateauHeight(x, z, 0, false);
                assertTrue(flat >= -48.0 && flat <= 0.0, "between the bottom and one step up: " + flat);
                double steps = (flat + 48.0) / 9.6;
                assertEquals(Math.round(steps), steps, 1e-3, "canyonStep(…, 5) moves in fifths of 48");
            }
        }
    }

    @Test
    void riversAreRareAndWideBanksSurroundThem() {
        int river = 0;
        int bank = 0;
        int total = 0;
        for (int x = -2000; x <= 2000; x += 16) {
            for (int z = -2000; z <= 2000; z += 16) {
                double r = CandyFeatures.CakeFloor.riveriness(x, z);
                total++;
                if (r < 1000.0) {
                    river++;
                } else if (r < 3000.0) {
                    bank++;
                }
            }
        }
        assertTrue(river > 0 && river < total / 10, "rivers: " + river + "/" + total);
        assertTrue(bank > river / 2, "banks: " + bank);
    }

    @Test
    void scoopOffsetUsesTheVanillaPositionSeed() {
        // Mth.getSeed(0, 0, 0) is 0; the offsets stay within three blocks.
        assertEquals(0L, CandyDecorations.IceCreamScoop.mthSeed(0, 0, 0));
        assertEquals(CandyDecorations.IceCreamScoop.mthSeed(12, 0, -7), CandyDecorations.IceCreamScoop.mthSeed(12, 0, -7));
    }

    @Test
    void coneStairsFaceAwayFromTheMiddle() {
        // Direction.fromYRot: 0 south, 90 west, 180 north, 270 east (indices of north, east, south, west).
        assertEquals(2, CandyDecorations.CeilingIceCreamCone.fromYRot(0.0F));
        assertEquals(3, CandyDecorations.CeilingIceCreamCone.fromYRot(90.0F));
        assertEquals(0, CandyDecorations.CeilingIceCreamCone.fromYRot(180.0F));
        assertEquals(1, CandyDecorations.CeilingIceCreamCone.fromYRot(270.0F));
        assertEquals(2, CandyDecorations.CeilingIceCreamCone.fromYRot(359.0F));
        assertEquals(10.0F, CandyDecorations.CeilingIceCreamCone.degreesDifference(35.0F, 45.0F), 1e-6);
        assertEquals(20.0F, CandyDecorations.CeilingIceCreamCone.degreesDifference(350.0F, 10.0F), 1e-6);
    }

    @Test
    void floorWritesStayInTheModPiece() {
        // Pieces start at y = -48 + 16k; the floor's piece holds the block above the floor.
        assertEquals(-48, CandyFeatures.CakeFloor.Clipped.corner(-49));
        assertEquals(-32, CandyFeatures.CakeFloor.Clipped.corner(-33));
        assertEquals(-48, CandyFeatures.CakeFloor.Clipped.corner(-34));
        assertEquals(-64, CandyFeatures.CakeFloor.Clipped.corner(-50));
    }

    @Test
    void sunkGummyRingsHaveTheirOwnState() throws IOException {
        String blocks = Files.readString(Path.of("src/main/resources/alexscaves/blocks.yml"));
        for (String colour : new String[] {"red", "green", "yellow", "blue"}) {
            int at = blocks.indexOf("gummy_ring_" + colour + ":");
            assertTrue(at >= 0 && blocks.indexOf("  sunk:", at) > at, colour);
        }
    }

    @Test
    void candyTemplatesArePackaged() throws IOException {
        JsonObject index = JsonParser.parseString(Files.readString(Path.of("src/main/resources/alexscaves/structures/index.json")))
            .getAsJsonObject();
        assertTrue(index.getAsJsonArray("big_lollipop_top").size() >= 4, "the mod uses big tops 0-3");
        assertEquals(4, index.getAsJsonArray("small_lollipop_top").size());
        assertEquals(2, index.getAsJsonArray("chocolate_bar").size());
    }
}
