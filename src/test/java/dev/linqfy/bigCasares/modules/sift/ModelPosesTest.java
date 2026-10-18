package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;
import org.bukkit.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelPosesTest {

    private static final class Recorder implements JavaModelGateway {
        final List<String> calls = new ArrayList<>();

        @Override public JavaModelHandle attach(Entity anchor, String modelKey) { throw new UnsupportedOperationException(); }
        @Override public boolean animate(JavaModelHandle handle, String animationKey) { calls.add("play " + animationKey); return true; }
        @Override public boolean animateOnce(JavaModelHandle handle, String animationKey, Runnable onEnd) { calls.add("once " + animationKey); return true; }
        @Override public boolean stopAnimation(JavaModelHandle handle, String animationKey) { calls.add("stop " + animationKey); return true; }
        @Override public boolean scale(JavaModelHandle handle, float factor) { return true; }
        @Override public void close(JavaModelHandle handle) { }
    }

    @Test
    void aPoseChangeStopsThePreviousAnimationAndStartsTheNext() {
        Recorder models = new Recorder();
        ModelPoses poses = new ModelPoses(models, new JavaModelHandle(UUID.randomUUID(), "m"));
        poses.show("search_walk");
        poses.show("search_walk");
        poses.show("pickup");
        poses.show(null);
        poses.show(null);
        poses.show("bow");
        assertEquals(List.of("play search_walk", "stop search_walk", "play pickup", "stop pickup", "play bow"), models.calls);
        assertEquals("bow", poses.current());
    }

    @Test
    void theFirstPoseHasNothingToStop() {
        Recorder models = new Recorder();
        ModelPoses poses = new ModelPoses(models, new JavaModelHandle(UUID.randomUUID(), "m"));
        assertNull(poses.current());
        poses.show(null);
        assertEquals(List.of(), models.calls);
    }
}
