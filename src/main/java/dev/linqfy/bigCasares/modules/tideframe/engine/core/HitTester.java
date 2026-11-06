package dev.linqfy.bigCasares.modules.tideframe.engine.core;
import java.util.List;
import java.util.Optional;

/** Rectangle hit testing in normalized plane coordinates. Last element is on top. */
public final class HitTester {
    private HitTester() { }

    public record Rect(String id, double x, double y, double width, double height) {
        public Rect {
            if (id == null || id.isBlank() || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0
                    || !Double.isFinite(x - width / 2) || !Double.isFinite(x + width / 2)
                    || !Double.isFinite(y - height / 2) || !Double.isFinite(y + height / 2))
                throw new IllegalArgumentException("Rectangle must have a name and finite positive bounds");
        }
    }
    public static Optional<String> hit(List<Rect> rectangles, PlaneProjection.Point point, double snapRadius) {
        return target(rectangles, point, snapRadius).map(Rect::id);
    }
    /** Keep inside points unchanged; otherwise snap to the nearest edge/corner, never the center. */
    public static PlaneProjection.Point snapped(List<Rect> rectangles, PlaneProjection.Point point, double radius) {
        return target(rectangles, point, radius).map(rect -> closest(rect, point)).orElse(point);
    }
    private static Optional<Rect> target(List<Rect> rectangles, PlaneProjection.Point point, double radius) {
        if (!Double.isFinite(radius) || radius < 0)
            throw new IllegalArgumentException("Snap radius must be finite and not negative");
        // Inside takes precedence over an outside snap; reverse traversal defines overlap priority.
        for (int i = rectangles.size() - 1; i >= 0; i--) {
            Rect rect = rectangles.get(i);
            if (point.equals(closest(rect, point))) return Optional.of(rect);
        }
        Rect best = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = rectangles.size() - 1; i >= 0; i--) {
            Rect rect = rectangles.get(i);
            var nearestPoint = closest(rect, point);
            double distance = Math.hypot(point.x() - nearestPoint.x(), point.y() - nearestPoint.y());
            if (distance <= radius + 1e-12 && distance < nearest) {
                best = rect;
                nearest = distance;
            }
        }
        return Optional.ofNullable(best);
    }
    private static PlaneProjection.Point closest(Rect rect, PlaneProjection.Point point) {
        return new PlaneProjection.Point(Math.max(rect.x - rect.width / 2, Math.min(rect.x + rect.width / 2, point.x())),
                Math.max(rect.y - rect.height / 2, Math.min(rect.y + rect.height / 2, point.y())));
    }
}
