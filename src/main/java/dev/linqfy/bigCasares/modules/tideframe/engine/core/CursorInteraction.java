package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Player-independent input model; access only from a session's owning thread. */
public final class CursorInteraction {
    /** Hover may be predicted. Selection always comes from the latest actual ray. */
    public record Frame(PlaneProjection.Point cursor, Optional<String> hover, Optional<String> selection, boolean inside) { }
    private final PlaneProjection.Plane plane;
    private final List<HitTester.Rect> targets;
    private final double snapRadius;
    private final CursorPredictor predictor;
    private PlaneProjection.Point lastProjected;
    private PlaneProjection.Point cursor = new PlaneProjection.Point(0, 0);

    public CursorInteraction(PlaneProjection.Plane plane, List<HitTester.Rect> targets, int predictionMs, double snapRadius) {
        this.plane = Objects.requireNonNull(plane);
        this.targets = List.copyOf(targets);
        if (!Double.isFinite(snapRadius) || snapRadius < 0)
            throw new IllegalArgumentException("Snap radius must be finite and not negative");
        this.snapRadius = snapRadius;
        predictor = new CursorPredictor(predictionMs);
    }
    public Frame refresh(Vec3 eye, double yawDegrees, double pitchDegrees, long nanoTime, int pingMs) {
        var projected = PlaneProjection.project(plane, eye, yawDegrees, pitchDegrees);
        if (projected.isEmpty()) return new Frame(cursor, Optional.empty(), Optional.empty(), false);
        var actual = projected.get();
        // Eye translation and sneaking change the intersection even at fixed angles.
        if (!actual.equals(lastProjected)) {
            predictor.sample(actual, nanoTime);
            lastProjected = actual;
        }
        var predicted = predictor.predict(nanoTime, pingMs);
        cursor = HitTester.snapped(targets, predicted, snapRadius);
        var hover = HitTester.hit(targets, cursor, 0);
        var selection = HitTester.hit(targets, actual, snapRadius);
        return new Frame(cursor, hover, selection, true);
    }
}
