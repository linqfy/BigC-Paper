package dev.linqfy.bigCasares.modules.tideframe.engine.core.logic;

import java.util.List;
import java.util.Set;

/**
 * Keyframe animation of one element (schema 9 {@code keyframes}): position offset, scale, opacity and rotation at
 * given times, eased in between. The pack bakes it as frames (one layer per frame, at most {@link #MAX_FRAMES}), so a
 * client draws every step natively. {@code trigger}: "open" plays once when the screen opens, "loop" repeats,
 * "hover" plays forward while the cursor is on the element and back when it leaves, "action" waits for an
 * {@code animation} action naming the element.
 */
public record Motion(String trigger, String easing, List<Step> steps) {
    public static final Set<String> TRIGGERS = Set.of("open", "loop", "hover", "action");
    public static final Set<String> EASINGS = Set.of("linear", "ease-in", "ease-out", "ease-in-out");
    /** Frames one element may bake (shared with image-sequence frames). */
    public static final int MAX_FRAMES = 64;
    /** Milliseconds between frames when the animation is long enough not to hit {@link #MAX_FRAMES}. */
    public static final int FRAME_MS = 50;

    /** One keyframe; null fields keep the previous keyframe's value (first keyframe: 0, 0, 1, 1, 0). */
    public record Step(int at, Double x, Double y, Double scale, Double opacity, Double rotation) {
        public Step {
            if (at < 0 || at > 10_000) throw new IllegalArgumentException("Keyframe time must be 0 to 10000 ms");
            check(x, -256, 256, "x"); check(y, -144, 144, "y"); check(scale, .1, 4, "scale"); check(opacity, 0, 1, "opacity"); check(rotation, -360, 360, "rotation");
        }
        private static void check(Double value, double min, double max, String name) {
            if (value != null && (!Double.isFinite(value) || value < min || value > max))
                throw new IllegalArgumentException("Keyframe " + name + " must be " + min + " to " + max);
        }
    }
    /** Interpolated look at one moment: offset in logical pixels (y grows downwards), scale, opacity, degrees. */
    public record Pose(double x, double y, double scale, double opacity, double rotation) {
        public static final Pose IDENTITY = new Pose(0, 0, 1, 1, 0);
        public boolean identity() { return equals(IDENTITY); }
    }

    public Motion {
        if (trigger == null || !TRIGGERS.contains(trigger)) throw new IllegalArgumentException("Keyframe trigger must be open, loop, hover or action");
        if (easing == null || !EASINGS.contains(easing)) throw new IllegalArgumentException("Easing must be linear, ease-in, ease-out or ease-in-out");
        steps = List.copyOf(steps);
        if (steps.size() < 2 || steps.size() > 16) throw new IllegalArgumentException("Keyframes need 2 to 16 steps");
        if (steps.getFirst().at() != 0) throw new IllegalArgumentException("The first keyframe must be at 0 ms");
        for (int i = 1; i < steps.size(); i++)
            if (steps.get(i).at() <= steps.get(i - 1).at()) throw new IllegalArgumentException("Keyframe times must increase");
    }
    public int durationMs() { return steps.getLast().at(); }
    /** Baked frames: one every 50 ms (both ends included), fewer for long animations. */
    public int frames() { return Math.min(MAX_FRAMES, durationMs() / FRAME_MS + 1); }
    /** Time between baked frames. */
    public int intervalMs() { int n = frames(); return n <= 1 ? FRAME_MS : Math.max(1, Math.round(durationMs() / (float) (n - 1))); }
    /** Pose of baked frame {@code k}. */
    public Pose frame(int k) {
        int n = frames();
        return at(n <= 1 ? 0 : durationMs() * (double) k / (n - 1));
    }
    /** Pose at {@code ms} after the start (clamped to the animation). */
    public Pose at(double ms) {
        double[] from = {0, 0, 1, 1, 0}, to = from.clone();
        fill(from, steps.getFirst());
        if (ms <= 0) return pose(from);
        for (int i = 1; i < steps.size(); i++) {
            to = from.clone(); fill(to, steps.get(i));
            var previous = steps.get(i - 1); var next = steps.get(i);
            if (ms <= next.at()) {
                double t = ease((ms - previous.at()) / (next.at() - previous.at()));
                double[] out = new double[5];
                for (int j = 0; j < 5; j++) out[j] = from[j] + (to[j] - from[j]) * t;
                return pose(out);
            }
            from = to;
        }
        return pose(from);
    }
    private static void fill(double[] values, Step step) {
        if (step.x() != null) values[0] = step.x();
        if (step.y() != null) values[1] = step.y();
        if (step.scale() != null) values[2] = step.scale();
        if (step.opacity() != null) values[3] = step.opacity();
        if (step.rotation() != null) values[4] = step.rotation();
    }
    private static Pose pose(double[] v) {
        return new Pose(round(v[0]), round(v[1]), round(v[2]), round(v[3]), round(v[4]));
    }
    /** Six decimals: identical frames stay identical (and share their baked layer bytes). */
    private static double round(double value) { return Math.round(value * 1e6) / 1e6; }
    double ease(double t) { return ease(easing, t); }
    /** Progress 0..1 through an easing curve (cubic in, out or in-out). */
    public static double ease(String easing, double t) {
        t = Math.max(0, Math.min(1, t));
        return switch (easing) {
            case "ease-in" -> t * t * t;
            case "ease-out" -> 1 - Math.pow(1 - t, 3);
            case "ease-in-out" -> t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            default -> t;
        };
    }
    /**
     * Frame shown {@code elapsedNanos} after a playback started at frame {@code from}: forwards (once or looping)
     * or backwards to frame 0.
     */
    public static int frameAt(long elapsedNanos, int intervalMs, int count, int from, boolean reverse, boolean loop) {
        if (count <= 1) return 0;
        long steps = elapsedNanos <= 0 ? 0 : elapsedNanos / (intervalMs * 1_000_000L);
        int start = Math.max(0, Math.min(count - 1, from));
        if (reverse) return (int) Math.max(0, start - steps);
        if (loop) return (int) ((start + steps) % count);
        return (int) Math.min(count - 1, start + steps);
    }
}
