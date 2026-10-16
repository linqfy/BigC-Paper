package dev.linqfy.bigCasares.modules.model;

import org.bukkit.entity.Entity;

public interface JavaModelGateway {
    JavaModelHandle attach(Entity anchor, String modelKey);

    boolean animate(JavaModelHandle handle, String animationKey);

    /**
     * Plays a one-shot animation and runs {@code onEnd} when it finishes, so
     * the caller can return the model to its looping idle state. Returns
     * {@code false} when there is no live tracker for the handle.
     */
    boolean animateOnce(JavaModelHandle handle, String animationKey, Runnable onEnd);

    /**
     * Stops a playing animation (for example a held pose). Returns {@code false}
     * when there is no live tracker or the gateway cannot stop animations.
     */
    default boolean stopAnimation(JavaModelHandle handle, String animationKey) {
        return false;
    }

    /** Scales the live tracker by a constant factor. Returns {@code false} when there is no live tracker. */
    boolean scale(JavaModelHandle handle, float factor);

    void close(JavaModelHandle handle);
}
