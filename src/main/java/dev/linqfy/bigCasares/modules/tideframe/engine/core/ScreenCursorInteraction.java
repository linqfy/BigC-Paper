package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.List;
import java.util.Optional;

/** Screen-relative mouse input. Not thread-safe: callers serialise access (UiSession holds a lock). */
public final class ScreenCursorInteraction {
    /** Hover and click share the actual hit; prediction cannot highlight another target. */
    public record Frame(PlaneProjection.Point cursor, Optional<String> hover, Optional<String> selection) { }

    private List<HitTester.Rect> targets;
    private final double snapRadius;
    private final CursorPredictor predictor;
    private double previousYaw, previousPitch;
    /** Degrees of yaw for half the canvas width (128 GUI pixels). */
    public static final double YAW_PER_HALF_WIDTH = 35.0;
    /** Same degrees-per-pixel vertically: half height is 72 GUI pixels, so 35 * 72 / 128. */
    public static final double PITCH_PER_HALF_HEIGHT = YAW_PER_HALF_WIDTH * 72.0 / 128.0;
    private PlaneProjection.Point actual = new PlaneProjection.Point(0, 0);
    private boolean refreshed;
    private volatile double sensitivity = 1;
    private long latestRefreshTime;

    public ScreenCursorInteraction(double initialYaw, double initialPitch, List<HitTester.Rect> targets,
                                   int predictionMs, double snapRadius) {
        requireAngles(initialYaw, initialPitch);
        if (!Double.isFinite(snapRadius) || snapRadius < 0)
            throw new IllegalArgumentException("Snap radius must be finite and not negative");
        this.targets = List.copyOf(targets);
        predictor = new CursorPredictor(predictionMs);
        this.snapRadius = snapRadius;
        previousYaw = normalizeYaw(initialYaw);
        previousPitch = initialPitch;
    }

    public Frame refresh(double yaw, double pitch, long nanoTime, int pingMs) {
        requireAngles(yaw, pitch);
        // Rejected packets must not consume an angle delta or roll prediction time backwards.
        if (refreshed && nanoTime - latestRefreshTime <= 0)
            return frameAt(latestRefreshTime, pingMs);

        double currentYaw = normalizeYaw(yaw);
        double deltaYaw = normalizeYaw(currentYaw - previousYaw);
        // Divide before subtraction to avoid overflow for otherwise finite input angles.
        double deltaPitch = (pitch / PITCH_PER_HALF_HEIGHT - previousPitch / PITCH_PER_HALF_HEIGHT) * sensitivity;
        actual = new PlaneProjection.Point(clamp(actual.x() + deltaYaw / YAW_PER_HALF_WIDTH * sensitivity),
                clamp(actual.y() - deltaPitch));
        previousYaw = currentYaw;
        previousPitch = pitch;
        latestRefreshTime = nanoTime;
        refreshed = true;

        // A stationary pose is evidence of a stop, not permission to keep drifting.
        predictor.sample(actual, nanoTime);
        return frameAt(nanoTime, pingMs);
    }

    public static final double MIN_SENSITIVITY = .25, MAX_SENSITIVITY = 4;
    /** Cursor speed multiplier (1 = 35 degrees of turning per half canvas width). */
    public void setSensitivity(double value) {
        if (!Double.isFinite(value) || value < MIN_SENSITIVITY || value > MAX_SENSITIVITY)
            throw new IllegalArgumentException("Sensitivity must be between " + MIN_SENSITIVITY + " and " + MAX_SENSITIVITY);
        sensitivity = value;
    }

    /** Adopt a new reference orientation without moving the cursor (e.g. a stale pre-reset pose). */
    public void rebase(double yaw, double pitch) {
        requireAngles(yaw, pitch);
        previousYaw = normalizeYaw(yaw);
        previousPitch = pitch;
    }
    /** The most recent frame without sampling a new pose. */
    public Frame current(long nanoTime, int pingMs) { return frameAt(refreshed ? Math.max(nanoTime, latestRefreshTime) : nanoTime, pingMs); }

    /** Keyboard navigation: puts the cursor on the centre of the next target in direction (dx, dy); empty when there is none. */
    public Optional<Frame> focus(int dx, int dy, long nanoTime, int pingMs) {
        var next = KeyboardFocus.next(targets, actual, dx, dy);
        if (next.isEmpty()) return Optional.empty();
        actual = new PlaneProjection.Point(clamp(next.get().x()), clamp(next.get().y()));
        // The mouse keeps moving from here: later rotations add their own deltas to this point.
        predictor.jump(actual, nanoTime);
        if (refreshed) latestRefreshTime = Math.max(latestRefreshTime, nanoTime);
        return Optional.of(frameAt(nanoTime, pingMs));
    }

    /** Replace animated-frame hit boxes without resetting mouse position or orientation. */
    public void replaceTargets(List<HitTester.Rect> targets) {
        this.targets = List.copyOf(targets);
    }

    private Frame frameAt(long nanoTime, int pingMs) {
        var predicted = predictor.predict(nanoTime, pingMs);
        var cursor = HitTester.snapped(targets, predicted, snapRadius);
        var selection = HitTester.hit(targets, actual, snapRadius);
        if (!HitTester.hit(targets, cursor, 0).equals(selection))
            cursor = HitTester.snapped(targets, actual, snapRadius);
        return new Frame(cursor, selection, selection);
    }

    private static void requireAngles(double yaw, double pitch) {
        if (!Double.isFinite(yaw) || !Double.isFinite(pitch))
            throw new IllegalArgumentException("Camera angles must be finite");
    }

    private static double normalizeYaw(double value) {
        double normalized = value % 360.0;
        if (normalized >= 180) normalized -= 360;
        if (normalized < -180) normalized += 360;
        return normalized;
    }

    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
}
