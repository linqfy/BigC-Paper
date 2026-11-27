package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NuclearVisualsTest {
    private static final Path CLOUD = Path.of("src/main/resources/bettermodel/models/" + MushroomCloud.MODEL + ".bbmodel");

    @Test
    void theCloudLastsAndScalesAsTheModsParticle() {
        assertEquals(134, MushroomCloud.lifetime(NukeMath.NUCLEEPER_SIZE));
        assertEquals(234, MushroomCloud.lifetime(NukeMath.CHARGED_NUCLEEPER_SIZE));
        assertEquals(1200, MushroomCloud.lifetime(NukeMath.BOMB_SIZE), "a minute for the bomb");
        assertEquals(9.2F, MushroomCloud.scale(NukeMath.BOMB_SIZE), 1e-6);
    }

    @Test
    void theModelHasAnAnimationForEverySizeAndEveryBoneIsLit() throws IOException {
        JsonObject model = JsonParser.parseString(Files.readString(CLOUD)).getAsJsonObject();
        Set<String> animations = new HashSet<>();
        for (JsonElement animation : model.getAsJsonArray("animations")) {
            animations.add(animation.getAsJsonObject().get("name").getAsString());
        }
        for (float size : new float[] {NukeMath.NUCLEEPER_SIZE, NukeMath.CHARGED_NUCLEEPER_SIZE, NukeMath.BOMB_SIZE}) {
            assertTrue(animations.contains(MushroomCloud.animation(size)), MushroomCloud.animation(size));
        }
        Set<String> bones = new HashSet<>();
        for (JsonElement group : model.getAsJsonArray("groups")) {
            bones.add(group.getAsJsonObject().get("name").getAsString());
        }
        assertEquals(bones, Set.copyOf(MushroomCloud.BONES));
        assertEquals(18, model.getAsJsonArray("elements").size(), "the 18 boxes of MushroomCloudModel");
    }

    @Test
    void theBombStandsStillWhenLitAndSwellsThenSettles() {
        Vector3f corner = new Matrix4f(NuclearBombPose.matrix(0)).transformPosition(new Vector3f(1, 1, 1));
        assertEquals(1.0F, corner.x, 1e-5);
        assertEquals(1.0F, corner.y, 1e-5);
        assertEquals(1.0F, corner.z, 1e-5);
        // Half way through the fuse: sin(0.25 PI) swell, centred on the block, still standing on it.
        float p = 140 / 280F;
        float swell = 1 + (float) Math.sin(p * p * Math.PI) * 0.5F;
        assertEquals(swell, NuclearBombPose.swell(p), 1e-6);
        Vector3f bottom = NuclearBombPose.matrix(140).transformPosition(new Vector3f(0.5F, 0, 0.5F));
        assertEquals(0.5F, bottom.x, 1e-5);
        assertEquals(0.0F, bottom.y, 1e-5);
        assertEquals(0.5F, bottom.z, 1e-5);
        Vector3f top = NuclearBombPose.matrix(140).transformPosition(new Vector3f(0.5F, 1, 0.5F));
        assertEquals(swell - p * 0.3F, top.y, 1e-5);
        // At the end the swell is back to 1 and the bomb is 0.7 tall.
        assertEquals(0.7F, NuclearBombPose.matrix(280).transformPosition(new Vector3f(0.5F, 1, 0.5F)).y, 1e-5);
    }
}
