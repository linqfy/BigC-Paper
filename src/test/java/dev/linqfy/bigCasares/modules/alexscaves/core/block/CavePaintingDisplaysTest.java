package dev.linqfy.bigCasares.modules.alexscaves.core.block;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.block.BlockFace;
import org.bukkit.block.structure.StructureRotation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CavePaintingDisplaysTest {
    private static final float EPSILON = 1.0e-5f;

    /** What vanilla draws: the display transformation, then its half turn around Y, then the model. */
    private static Vector3f drawn(BlockFace facing, Vector3f modelVector) {
        Vector3f turned = new Quaternionf().rotationY((float) Math.PI).transform(new Vector3f(modelVector));
        return FacingDisplays.leftRotation(facing).transform(turned);
    }

    @Test
    void theModelsNorthSideEndsUpOnTheFacing() {
        for (BlockFace facing : FacingDisplays.FACINGS) {
            Vector3f normal = drawn(facing, new Vector3f(0, 0, -1));
            assertEquals(facing.getModX(), normal.x, EPSILON, facing.name());
            assertEquals(facing.getModY(), normal.y, EPSILON, facing.name());
            assertEquals(facing.getModZ(), normal.z, EPSILON, facing.name());
        }
    }

    @Test
    void sidePaintingsStayUpright() {
        for (BlockFace facing : new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            Vector3f up = drawn(facing, new Vector3f(0, 1, 0));
            assertEquals(1.0f, up.y, EPSILON, facing.name());
        }
    }

    @Test
    void blockstateRotationsAreTheMods() {
        assertArrayEquals(new int[] {90, 0}, FacingDisplays.blockstateRotation(BlockFace.DOWN));
        assertArrayEquals(new int[] {270, 0}, FacingDisplays.blockstateRotation(BlockFace.UP));
        assertArrayEquals(new int[] {0, 90}, FacingDisplays.blockstateRotation(BlockFace.EAST));
        assertArrayEquals(new int[] {0, 180}, FacingDisplays.blockstateRotation(BlockFace.SOUTH));
        assertArrayEquals(new int[] {0, 270}, FacingDisplays.blockstateRotation(BlockFace.WEST));
    }

    @Test
    void placedPaintingsFaceThePlayer() {
        assertEquals(BlockFace.SOUTH, FacingDisplays.placedFacing(0.1, -0.2, -0.9), "looking north");
        assertEquals(BlockFace.WEST, FacingDisplays.placedFacing(0.9, 0.3, 0.2), "looking east");
        assertEquals(BlockFace.UP, FacingDisplays.placedFacing(0.2, -0.9, 0.3), "looking down");
        assertEquals(BlockFace.DOWN, FacingDisplays.placedFacing(0.0, 1.0, 0.0), "looking up");
    }

    @Test
    void templateRotationTurnsHorizontalFacingsOnly() {
        assertEquals(BlockFace.EAST, FacingDisplays.rotate(BlockFace.NORTH, StructureRotation.CLOCKWISE_90));
        assertEquals(BlockFace.WEST, FacingDisplays.rotate(BlockFace.EAST, StructureRotation.CLOCKWISE_180));
        assertEquals(BlockFace.EAST, FacingDisplays.rotate(BlockFace.SOUTH, StructureRotation.COUNTERCLOCKWISE_90));
        assertEquals(BlockFace.UP, FacingDisplays.rotate(BlockFace.UP, StructureRotation.CLOCKWISE_90));
        assertEquals(BlockFace.NORTH, FacingDisplays.rotate(BlockFace.NORTH, StructureRotation.NONE));
        assertEquals(BlockFace.DOWN, FacingDisplays.parse("down"));
        assertNull(FacingDisplays.parse("sideways"));
    }

    @Test
    void displayStandsInTheBlockInFrontOfThePaintedSide() {
        assertArrayEquals(new double[] {10.5, 64.5, -4.5}, CavePaintings.anchor(10, 64, -4, BlockFace.NORTH), 1.0e-9);
        assertArrayEquals(new double[] {11.5, 64.5, -3.5}, CavePaintings.anchor(10, 64, -4, BlockFace.EAST), 1.0e-9);
        assertArrayEquals(new double[] {10.5, 63.5, -3.5}, CavePaintings.anchor(10, 64, -4, BlockFace.DOWN), 1.0e-9);
    }

    @Test
    void paintingIdsAndModels() {
        assertEquals("dark", CavePaintings.paintingOf("ac_cave_painting_dark"));
        assertNull(CavePaintings.paintingOf("ac_cave_painting_"));
        assertNull(CavePaintings.paintingOf("ac_dinosaur_chop"));
        assertNull(CavePaintings.paintingOf(null));
        assertEquals("display/cave_painting_mystery_3", CavePaintings.displayModel("mystery_3"));
        assertEquals("bigcasares:ac_cave_painting", CavePaintings.PAINTING.toString());
        assertEquals("bigcasares:ac_cave_painting_host", CavePaintings.HOST.toString());
    }

    @Test
    void everyStructurePaintingHasItsDisplayModelAndItem() throws IOException {
        JsonObject marks = JsonParser.parseString(Files.readString(
            Path.of("src/main/resources/alexscaves/structures/paintings.json"))).getAsJsonObject();
        assertEquals(7, marks.size());
        int total = 0;
        for (var template : marks.entrySet()) {
            for (var mark : template.getValue().getAsJsonArray()) {
                String painting = mark.getAsJsonArray().get(3).getAsString();
                assertTrue(Files.isRegularFile(Path.of("resourcepack/java/assets/alexscaves/items",
                    CavePaintings.displayModel(painting) + ".json")), painting);
                assertTrue(Files.isRegularFile(Path.of("resourcepack/java/assets/alexscaves/models",
                    CavePaintings.displayModel(painting) + ".json")), painting);
                assertTrue(Files.isRegularFile(Path.of("src/main/resources/content/items",
                    CavePaintings.ITEM_PREFIX + painting + ".yml")), painting);
                total++;
            }
        }
        assertEquals(138, total, "the mod's caveman houses hold 138 paintings");
    }
}
