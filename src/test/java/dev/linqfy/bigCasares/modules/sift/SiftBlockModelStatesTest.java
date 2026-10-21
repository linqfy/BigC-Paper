package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.SiftBlockModelStates.Piece;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftBlockModelStatesTest {

    private static SiftBlockModelStates states;

    @BeforeAll
    static void load() throws IOException {
        try (InputStream input = SiftBlockModelStatesTest.class.getClassLoader()
            .getResourceAsStream(SiftBlockModelStates.RESOURCE)) {
            states = SiftBlockModelStates.read(input);
        }
    }

    @Test
    void theLookAlikeStatePicksTheModsVariant() {
        assertEquals(List.of(new Piece("the_sift:block/cobbled_siftslate_stairs", 180, 270)),
            states.pieces("cobbled_siftslate_stairs", SiftBlockModelStates.properties(
                "minecraft:cobbled_deepslate_stairs[facing=north,half=top,shape=straight,waterlogged=false]")));
        assertEquals(List.of(new Piece("the_sift:block/overgrown_willow_door_top_right_open", 0, 0)),
            states.pieces("overgrown_willow_door", SiftBlockModelStates.properties(
                "minecraft:warped_door[facing=south,half=upper,hinge=right,open=true,powered=false]")));
        assertEquals(List.of(new Piece("the_sift:block/overgrown_willow_planks", 0, 0)),
            states.pieces("overgrown_willow_slab", SiftBlockModelStates.properties(
                "minecraft:warped_slab[type=double,waterlogged=false]")));
    }

    @Test
    void multipartBlocksGetEveryMatchingPiece() {
        List<Piece> wall = states.pieces("cobbled_siftslate_wall", SiftBlockModelStates.properties(
            "minecraft:cobbled_deepslate_wall[east=none,north=low,south=tall,up=true,waterlogged=false,west=none]"));
        assertEquals(Set.of("the_sift:block/cobbled_siftslate_wall_post", "the_sift:block/cobbled_siftslate_wall_side",
            "the_sift:block/cobbled_siftslate_wall_side_tall"), Set.copyOf(wall.stream().map(Piece::model).toList()));
        assertEquals(3, wall.size());
        // The mod's vines know a "bottom" state vanilla vines lack: it reads as false, so they draw as plain vines.
        List<Piece> vines = states.pieces("overgrown_willow_vines", SiftBlockModelStates.properties(
            "minecraft:vine[east=true,north=false,south=false,up=false,west=false]"));
        assertEquals(List.of(new Piece("the_sift:block/overgrown_willow_vines", 0, 90)), vines);
        // An AND case: the shelf's front only while unpowered.
        assertTrue(states.pieces("overgrown_willow_shelf", SiftBlockModelStates.properties(
            "minecraft:warped_shelf[facing=north,powered=false,side_chain=unconnected,waterlogged=false]")).size() > 1);
    }

    @Test
    void aStateTheModDoesNotDrawGetsNoModel() {
        assertEquals(List.of(), states.pieces("overgrown_willow_sign", SiftBlockModelStates.properties(
            "minecraft:warped_wall_sign[facing=north,waterlogged=false]")));
        assertEquals(List.of(), states.pieces("not_a_look_alike", SiftBlockModelStates.properties("minecraft:stone")));
    }

    @Test
    void everyLookAlikeHasBlockstatesAndEveryModelAnItemDefinition() throws IOException {
        String source = Files.readString(Path.of("src/main/java/dev/linqfy/bigCasares/modules/sift/SiftStandIns.java"));
        Matcher placed = Pattern.compile("(?:block|plant)\\(\"([a-z_]+)\"").matcher(source);
        String data = Files.readString(Path.of("src/main/resources/" + SiftBlockModelStates.RESOURCE));
        Set<String> missing = new TreeSet<>();
        while (placed.find()) {
            if (!data.contains("\"" + placed.group(1) + "\": {")) {
                missing.add("blockstate " + placed.group(1));
            }
        }
        Matcher model = Pattern.compile("\"model\": \"the_sift:block/([a-z_0-9]+)\"").matcher(data);
        Path assets = Path.of("resourcepack/java/assets/the_sift");
        while (model.find()) {
            if (!Files.isRegularFile(assets.resolve("items/block/" + model.group(1) + ".json"))
                || !Files.isRegularFile(assets.resolve("models/block/" + model.group(1) + ".json"))
                || !Files.isRegularFile(assets.resolve("models/block/overlay/" + model.group(1) + ".json"))) {
                missing.add("model " + model.group(1));
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void packedPositionsUnpackAsVanillaPacksThem() {
        for (int[] position : new int[][]{{0, 0, 0}, {-1, -64, -1}, {29_999_999, 319, -29_999_999}, {-1234, 70, 5678}}) {
            long packed = SiftChunkMarks.pack(position[0], position[1], position[2]);
            assertEquals(position[0], SiftBlockModels.unpackX(packed));
            assertEquals(position[1], SiftBlockModels.unpackY(packed));
            assertEquals(position[2], SiftBlockModels.unpackZ(packed));
        }
    }
}
