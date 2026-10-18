package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.model.JavaModelGateway;
import dev.linqfy.bigCasares.modules.model.JavaModelHandle;

/**
 * Keeps one pose animation playing on a creature's model. GeckoLib drives the mod's creatures with a controller
 * that picks one animation from the creature's state every tick; BetterModel plays animations until they are
 * stopped, so the caller names the pose it wants and this class stops the previous one and starts the new one,
 * and only when the pose changes. The converted models rename the mod's idle and walk animations so that
 * BetterModel's own idle/walk handling never competes with these poses.
 */
final class ModelPoses {

    private final JavaModelGateway models;
    private final JavaModelHandle handle;
    private String current;

    ModelPoses(JavaModelGateway models, JavaModelHandle handle) {
        this.models = models;
        this.handle = handle;
    }

    /** Plays the animation, replacing the previous pose; null shows the plain model. */
    void show(String animation) {
        if (animation == null ? current == null : animation.equals(current)) {
            return;
        }
        if (current != null) {
            models.stopAnimation(handle, current);
        }
        current = animation;
        if (animation != null) {
            models.animate(handle, animation);
        }
    }

    String current() {
        return current;
    }
}
