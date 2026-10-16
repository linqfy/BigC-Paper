package dev.linqfy.bigCasares.modules.model;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelScaler;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class BetterModelJavaModelGateway implements JavaModelGateway {
    private final TrackerPort trackerPort;
    private final Map<JavaModelHandle, TrackerSession> sessions = new HashMap<>();

    BetterModelJavaModelGateway() {
        this(new BetterModelTrackerPort());
    }

    BetterModelJavaModelGateway(TrackerPort trackerPort) {
        this.trackerPort = Objects.requireNonNull(trackerPort, "trackerPort");
    }

    @Override
    public JavaModelHandle attach(Entity anchor, String modelKey) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(modelKey, "modelKey");

        var handle = new JavaModelHandle(anchor.getUniqueId(), modelKey);
        sessions.computeIfAbsent(handle, ignored -> trackerPort.attach(anchor, modelKey));
        return handle;
    }

    @Override
    public boolean animate(JavaModelHandle handle, String animationKey) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(animationKey, "animationKey");

        var session = sessions.get(handle);
        return session != null && session.animate(animationKey);
    }

    @Override
    public boolean animateOnce(JavaModelHandle handle, String animationKey, Runnable onEnd) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(animationKey, "animationKey");
        Objects.requireNonNull(onEnd, "onEnd");

        var session = sessions.get(handle);
        return session != null && session.animateOnce(animationKey, onEnd);
    }

    @Override
    public boolean stopAnimation(JavaModelHandle handle, String animationKey) {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(animationKey, "animationKey");

        var session = sessions.get(handle);
        return session != null && session.stopAnimation(animationKey);
    }

    @Override
    public boolean scale(JavaModelHandle handle, float factor) {
        Objects.requireNonNull(handle, "handle");

        var session = sessions.get(handle);
        return session != null && session.scale(factor);
    }

    @Override
    public void close(JavaModelHandle handle) {
        Objects.requireNonNull(handle, "handle");

        var session = sessions.remove(handle);
        if (session != null) {
            session.close();
        }
    }

}

final class BetterModelTrackerPort implements TrackerPort {
    private final OwnedTrackerCreator trackerCreator;

    BetterModelTrackerPort() {
        this(BetterModelTrackerPort::createOwnedTracker);
    }

    BetterModelTrackerPort(OwnedTrackerCreator trackerCreator) {
        this.trackerCreator = Objects.requireNonNull(trackerCreator, "trackerCreator");
    }

    @Override
    public TrackerSession attach(Entity anchor, String modelKey) {
        return trackerCreator.create(anchor, modelKey);
    }

    private static TrackerSession createOwnedTracker(Entity anchor, String modelKey) {
        EntityTracker tracker = BetterModel.model(modelKey)
                .map(renderer -> renderer.getOrCreate(BukkitAdapter.adapt(anchor)))
                .orElseThrow(() -> new IllegalStateException("BetterModel model not loaded: " + modelKey));
        return new BetterModelTrackerSession(tracker);
    }

    private record BetterModelTrackerSession(EntityTracker tracker) implements TrackerSession {
        @Override
        public boolean animate(String animationKey) {
            return tracker.animate(animationKey);
        }

        @Override
        public boolean animateOnce(String animationKey, Runnable onEnd) {
            return tracker.animate(animationKey, AnimationModifier.DEFAULT_WITH_PLAY_ONCE, onEnd);
        }

        @Override
        public boolean stopAnimation(String animationKey) {
            return tracker.stopAnimation(animationKey);
        }

        @Override
        public boolean scale(float factor) {
            tracker.scaler(ModelScaler.value(factor));
            return true;
        }

        @Override
        public void close() {
            tracker.close();
        }
    }
}

@FunctionalInterface
interface OwnedTrackerCreator {
    TrackerSession create(Entity anchor, String modelKey);
}

interface TrackerPort {
    TrackerSession attach(Entity anchor, String modelKey);
}

interface TrackerSession {
    boolean animate(String animationKey);

    boolean animateOnce(String animationKey, Runnable onEnd);

    default boolean stopAnimation(String animationKey) {
        return false;
    }

    boolean scale(float factor);

    void close();
}
