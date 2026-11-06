package dev.linqfy.bigCasares.modules.legendary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Main-thread movement leases. Ending one storm cannot release another storm's targets. */
final class LegendaryParalysis {
    record Point(UUID world, double x, double y, double z) {
        Point {
            Objects.requireNonNull(world, "world");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Posición de parálisis inválida.");
            }
        }
    }

    private static final class Lease {
        final Point position;
        final Set<UUID> fields = new HashSet<>();

        Lease(Point position) { this.position = position; }
    }

    private final Map<UUID, Set<UUID>> fields = new HashMap<>();
    private final Map<UUID, Lease> targets = new HashMap<>();

    void sync(UUID field, Map<UUID, Point> occupants) {
        var snapshot = Map.copyOf(occupants);
        for (UUID previous : Set.copyOf(fields.getOrDefault(field, Set.of()))) {
            if (!snapshot.containsKey(previous)) remove(field, previous);
        }
        snapshot.forEach((target, position) -> acquire(field, target, position));
    }

    void acquire(UUID field, UUID target, Point position) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(position, "position");
        fields.computeIfAbsent(field, ignored -> new HashSet<>()).add(target);
        targets.computeIfAbsent(target, ignored -> new Lease(position)).fields.add(field);
    }

    private void remove(UUID field, UUID target) {
        Set<UUID> members = fields.get(field);
        if (members != null) {
            members.remove(target);
            if (members.isEmpty()) fields.remove(field);
        }
        Lease lease = targets.get(target);
        if (lease != null) {
            lease.fields.remove(field);
            if (lease.fields.isEmpty()) targets.remove(target);
        }
    }

    void release(UUID field) {
        for (UUID target : Set.copyOf(fields.getOrDefault(field, Set.of()))) remove(field, target);
    }

    void forget(UUID target) {
        Lease lease = targets.get(target);
        if (lease != null) for (UUID field : Set.copyOf(lease.fields)) remove(field, target);
    }

    Optional<Point> anchor(UUID target) {
        Lease lease = targets.get(target);
        return lease == null ? Optional.empty() : Optional.of(lease.position);
    }

    Set<UUID> targets() { return Set.copyOf(targets.keySet()); }

    void clear() { fields.clear(); targets.clear(); }

    static boolean inside(Point center, Point target, double radius) {
        double x = center.x() - target.x(), z = center.z() - target.z();
        return Double.isFinite(radius) && radius >= 0 && center.world().equals(target.world())
            && Math.abs(center.y() - target.y()) <= 4 && x * x + z * z <= radius * radius;
    }
}
