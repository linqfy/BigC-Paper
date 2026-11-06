package dev.linqfy.bigCasares.modules.tideframe.engine.core;

import java.util.List;
import java.util.Optional;

/**
 * Keyboard navigation (behavior.keyboard-navigation): which button W, A, S or D moves the focus to. Boxes are compared
 * in logical pixels (256 x 144 canvas, y up): a candidate's centre must lie beyond the current box in the pressed
 * direction, and the gap to it that way must be at least the gap across (so a wide bar below is reached from any
 * column, and a neighbour one pixel higher is not "up"). The smallest gap wins, the sideways gap counting double; ties
 * go to the button nearest the cursor. The button under the cursor is the starting box; with none, the cursor is, and
 * a first press that finds nothing that way enters the nearest button.
 */
public final class KeyboardFocus {
    private KeyboardFocus() { }
    private static final double HALF_WIDTH = 128, HALF_HEIGHT = 72;

    /** {@code dx}, {@code dy}: one of (0, 1) up, (0, -1) down, (-1, 0) left, (1, 0) right. */
    public static Optional<HitTester.Rect> next(List<HitTester.Rect> targets, PlaneProjection.Point cursor, int dx, int dy) {
        if (Math.abs(dx) + Math.abs(dy) != 1) throw new IllegalArgumentException("Direction must be one of the four axes");
        var current = HitTester.hit(targets, cursor, 0).flatMap(id -> targets.stream().filter(rect -> rect.id().equals(id)).reduce((first, last) -> last));
        double cursorX = cursor.x() * HALF_WIDTH, cursorY = cursor.y() * HALF_HEIGHT;
        double[] from = current.map(KeyboardFocus::box).orElse(new double[]{cursorX, cursorY, cursorX, cursorY});
        HitTester.Rect best = null, nearest = null;
        double bestScore = Double.POSITIVE_INFINITY, bestDistance = Double.POSITIVE_INFINITY, nearestDistance = Double.POSITIVE_INFINITY;
        for (var rect : targets) {
            if (current.isPresent() && rect.id().equals(current.get().id())) continue;
            double[] to = box(rect);
            double distance = Math.hypot(rect.x() * HALF_WIDTH - cursorX, rect.y() * HALF_HEIGHT - cursorY);
            if (distance < nearestDistance) { nearest = rect; nearestDistance = distance; }
            double beyond = ((to[0] + to[2]) - (from[0] + from[2])) / 2 * dx + ((to[1] + to[3]) - (from[1] + from[3])) / 2 * dy;
            double along = Math.max(0, dx > 0 ? to[0] - from[2] : dx < 0 ? from[0] - to[2] : dy > 0 ? to[1] - from[3] : from[1] - to[3]);
            double across = dx != 0 ? Math.max(0, Math.max(to[1] - from[3], from[1] - to[3])) : Math.max(0, Math.max(to[0] - from[2], from[0] - to[2]));
            if (beyond <= 0 || along < across) continue;
            double score = along + 2 * across;
            if (score < bestScore - 1e-9 || Math.abs(score - bestScore) <= 1e-9 && distance < bestDistance) { best = rect; bestScore = score; bestDistance = distance; }
        }
        if (best == null && current.isEmpty()) best = nearest;
        return Optional.ofNullable(best);
    }
    /** Left, bottom, right, top in logical pixels. */
    private static double[] box(HitTester.Rect rect) {
        return new double[]{(rect.x() - rect.width() / 2) * HALF_WIDTH, (rect.y() - rect.height() / 2) * HALF_HEIGHT,
                (rect.x() + rect.width() / 2) * HALF_WIDTH, (rect.y() + rect.height() / 2) * HALF_HEIGHT};
    }
}
