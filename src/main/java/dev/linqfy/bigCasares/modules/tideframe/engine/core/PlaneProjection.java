package dev.linqfy.bigCasares.modules.tideframe.engine.core;
import java.util.Optional;
import java.util.Objects;

/** Ray/plane projection. Coordinates are centered, with right/up positive. */
public final class PlaneProjection {
    public static final double HALF_WIDTH = 1.2;
    public static final double HALF_HEIGHT = .7;
    private static final double EPSILON = 1e-10;

    private PlaneProjection() { }

    public record Point(double x, double y) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y))
                throw new IllegalArgumentException("Point coordinates must be finite");
        }
    }
    public record Plane(Vec3 eye, Vec3 forward, Vec3 right, Vec3 up, double distance) {
        public Plane {
            Objects.requireNonNull(eye); Objects.requireNonNull(forward);
            Objects.requireNonNull(right); Objects.requireNonNull(up);
            if (!Double.isFinite(distance) || distance <= 0)
                throw new IllegalArgumentException("Plane distance must be positive and finite");
        }
    }
    public static Plane create(Vec3 eye, double yawDegrees, double pitchDegrees, double distance) {
        Vec3 forward = direction(yawDegrees, pitchDegrees);
        double yaw = Math.toRadians(yawDegrees);
        // Defined directly from yaw, including when pitch is vertical.
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(forward).normalized();
        return new Plane(eye, forward, right, up, distance);
    }
    public static Optional<Point> project(Plane plane, Vec3 eye, double yawDegrees, double pitchDegrees) {
        Vec3 ray = direction(yawDegrees, pitchDegrees);
        double denominator = ray.dot(plane.forward);
        if (denominator <= EPSILON) return Optional.empty();
        Vec3 center = plane.eye.add(plane.forward.multiply(plane.distance));
        double distance = center.add(eye.multiply(-1)).dot(plane.forward) / denominator;
        if (!Double.isFinite(distance) || distance <= 0) return Optional.empty();
        Vec3 offset = eye.add(ray.multiply(distance)).add(center.multiply(-1));
        double x = offset.dot(plane.right) / HALF_WIDTH;
        double y = offset.dot(plane.up) / HALF_HEIGHT;
        if (Math.abs(x) > 1 + EPSILON || Math.abs(y) > 1 + EPSILON) return Optional.empty();
        return Optional.of(new Point(clamp(x), clamp(y)));
    }
    public static Vec3 world(Plane plane, Point point) {
        return plane.eye.add(plane.forward.multiply(plane.distance))
                .add(plane.right.multiply(point.x * HALF_WIDTH))
                .add(plane.up.multiply(point.y * HALF_HEIGHT));
    }
    private static Vec3 direction(double yawDegrees, double pitchDegrees) {
        if (!Double.isFinite(yawDegrees) || !Double.isFinite(pitchDegrees))
            throw new IllegalArgumentException("Camera angles must be finite");
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }
    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
}
