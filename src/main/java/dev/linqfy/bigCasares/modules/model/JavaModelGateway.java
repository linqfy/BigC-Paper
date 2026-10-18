package dev.linqfy.bigCasares.modules.model;

import org.bukkit.entity.Entity;

import java.util.Collection;

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

    /**
     * Draws the named bones at a fixed light level whatever the light around the model (a glow layer). Returns
     * {@code false} when there is no live tracker or the gateway cannot set a brightness.
     *
     * @param block block light, 0 to 15
     * @param sky   sky light, 0 to 15
     * @param bones bone names as the model's groups are called
     */
    default boolean brighten(JavaModelHandle handle, int block, int sky, Collection<String> bones) {
        return false;
    }

    void close(JavaModelHandle handle);
}
