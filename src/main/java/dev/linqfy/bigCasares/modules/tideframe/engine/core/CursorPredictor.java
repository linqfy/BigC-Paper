package dev.linqfy.bigCasares.modules.tideframe.engine.core;

/** Small, damped cursor prediction. Call only from a single session owner. */
public final class CursorPredictor {
    private static final double MAX_LEAD_MS = 16;
    private static final double MAX_DISPLACEMENT = .05;
    private static final double DECAY_START_MS = 50;
    private static final double STALE_MS = 150;
    private final int horizonMs;
    private PlaneProjection.Point latest = new PlaneProjection.Point(0, 0);
    private boolean sampled;
    private long latestTime;
    private double velocityX, velocityY;

    public CursorPredictor(int horizonMs) {
        if (horizonMs < 0) throw new IllegalArgumentException("Prediction horizon must not be negative");
        this.horizonMs = Math.min(75, horizonMs);
    }
    public void sample(PlaneProjection.Point point, long nanoTime) {
        if (sampled && nanoTime - latestTime <= 0) return;
        var bounded = new PlaneProjection.Point(clamp(point.x()), clamp(point.y()));
        if (sampled) {
            double intervalMs = (nanoTime - latestTime) / 1_000_000.0;
            // A click can sample the same pose just after a tick. Such a short
            // interval is not reliable velocity evidence; gaps also start fresh.
            if (intervalMs < 4 || intervalMs > STALE_MS) {
                velocityX = velocityY = 0;
            } else {
                double weight = 1 - Math.exp(-intervalMs / 30);
                velocityX = smoothVelocity(velocityX, (bounded.x() - latest.x()) / intervalMs, weight);
                velocityY = smoothVelocity(velocityY, (bounded.y() - latest.y()) / intervalMs, weight);
            }
        }
        latest = bounded;
        latestTime = nanoTime;
        sampled = true;
    }
    /** The cursor was moved on purpose (keyboard navigation): start again from there, with no momentum. */
    public void jump(PlaneProjection.Point point, long nanoTime) {
        latest = new PlaneProjection.Point(clamp(point.x()), clamp(point.y()));
        latestTime = sampled ? Math.max(nanoTime, latestTime) : nanoTime;
        sampled = true;
        velocityX = velocityY = 0;
    }
    public PlaneProjection.Point predict(long nanoTime, int pingMs) {
        if (!sampled) return latest;
        double ageMs = (nanoTime - latestTime) / 1_000_000.0;
        if (ageMs < 0 || ageMs >= STALE_MS || pingMs <= 0 || horizonMs == 0) return latest;
        // The caller passes the real clock. The short lead is explicit here,
        // rather than advancing the clock by both configuration and latency.
        double leadMs = Math.min(MAX_LEAD_MS, pingMs / 2.0);
        double advanceMs = Math.min(horizonMs, ageMs + leadMs);
        double decay = 1;
        if (ageMs > DECAY_START_MS) {
            double progress = (ageMs - DECAY_START_MS) / (STALE_MS - DECAY_START_MS);
            decay = 1 - progress * progress * (3 - 2 * progress);
        }
        double dx = velocityX * advanceMs, dy = velocityY * advanceMs;
        double length = Math.hypot(dx, dy);
        double scale = decay * (length > MAX_DISPLACEMENT ? MAX_DISPLACEMENT / length : 1);
        return new PlaneProjection.Point(clamp(latest.x() + dx * scale),
                clamp(latest.y() + dy * scale));
    }
    private static double smoothVelocity(double previous, double current, double weight) {
        // Stops and reversals take effect immediately, without old momentum.
        // Equivalent degree/normalized-coordinate calculations can differ by
        // a final floating-point bit; such noise must also count as a stop.
        if (Math.abs(current) < 1e-10) return 0;
        if (previous * current <= 0) return current;
        return previous + weight * (current - previous);
    }
    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
}
