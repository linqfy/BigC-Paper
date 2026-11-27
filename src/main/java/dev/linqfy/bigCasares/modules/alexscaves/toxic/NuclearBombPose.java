package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import org.joml.Matrix4f;

/**
 * The primed bomb's animation (mod {@code NuclearBombRenderer}), as the transformation of the block display that
 * stands in for the mod's bomb entity. With {@code p} the share of the fuse burnt, the bomb shakes about its vertical
 * axis by {@code cos(age * 3.25) * 1.2 * p * PI} degrees, widens by {@code p * 0.03}, and swells by
 * {@code 1 + sin(p^2 * PI) * 0.5} while it sinks {@code p * 0.3} of that in height, standing on its block.
 * (The mod also tints it green as it burns; a block display cannot be tinted.)
 */
final class NuclearBombPose {
    private NuclearBombPose() {
    }

    static float progress(int fuseTicks) {
        return Math.min(1.0F, fuseTicks / (float) ToxicNuclearBomb.FUSE_TICKS);
    }

    static float swell(float progress) {
        return 1.0F + (float) Math.sin(progress * progress * Math.PI) * 0.5F;
    }

    static float shakeDegrees(int age, float progress) {
        return (float) (Math.cos(age * 3.25D) * 1.2D * progress * Math.PI);
    }

    /** Transformation of a block display placed at the bomb block's corner. */
    static Matrix4f matrix(int fuseTicks) {
        float p = progress(fuseTicks);
        float swell = swell(p);
        float widen = 1.0F + p * 0.03F;
        return new Matrix4f()
            .translate(0.5F, 0.0F, 0.5F)
            .rotateY((float) Math.toRadians(shakeDegrees(fuseTicks, p)))
            .scale(widen, 1.0F, widen)
            .scale(swell, swell - p * 0.3F, swell)
            .translate(-0.5F, 0.0F, -0.5F);
    }
}
