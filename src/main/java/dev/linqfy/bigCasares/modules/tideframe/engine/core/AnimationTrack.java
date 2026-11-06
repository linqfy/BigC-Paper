package dev.linqfy.bigCasares.modules.tideframe.engine.core;

/** Independent smoothstep entrance tracks, evaluated from normalized elapsed time. */
public final class AnimationTrack {
    private AnimationTrack() { }

    public enum Kind { FADE, SLIDE, SCALE }
    public record Frame(double opacity, double offsetY, double scale) { }
    public static Frame at(Kind kind, double progress) {
        if (!Double.isFinite(progress)) throw new IllegalArgumentException("Animation progress must be finite");
        double t = Math.max(0, Math.min(1, progress));
        double smooth = t * t * (3 - 2 * t);
        return switch (kind) {
            case FADE -> new Frame(smooth, 0, 1);
            case SLIDE -> new Frame(1, .25 * (1 - smooth), 1);
            case SCALE -> new Frame(1, 0, .05 + .95 * smooth);
        };
    }
}
