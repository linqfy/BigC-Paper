package dev.linqfy.bigCasares.platform;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientEntityPresentationRegistry {
    private static final ConcurrentHashMap<UUID, Presentation> PRESENTATIONS = new ConcurrentHashMap<>();

    private ClientEntityPresentationRegistry() {
    }

    public static void register(UUID entityId, String javaEntityType, String markerKey) {
        if (entityId == null || javaEntityType == null || javaEntityType.isBlank()
            || markerKey == null || markerKey.isBlank()) {
            throw new IllegalArgumentException("Entity presentation fields must not be blank");
        }
        String normalizedType = javaEntityType.strip().toUpperCase(Locale.ROOT);
        String normalizedMarker = markerKey.strip();
        PRESENTATIONS.compute(entityId, (ignored, current) -> {
            LinkedHashSet<String> markers = new LinkedHashSet<>();
            if (current != null && current.javaEntityType().equals(normalizedType)) {
                markers.addAll(current.markerKeys());
            }
            markers.add(normalizedMarker);
            return new Presentation(normalizedType, Set.copyOf(markers));
        });
    }

    public static Optional<Presentation> find(UUID entityId) {
        return Optional.ofNullable(PRESENTATIONS.get(entityId));
    }

    public static void unregister(UUID entityId) {
        if (entityId != null) {
            PRESENTATIONS.remove(entityId);
        }
    }

    /** Drops one marker and keeps the others, e.g. when a body swaps one model for another. */
    public static void unregister(UUID entityId, String markerKey) {
        if (entityId == null || markerKey == null) {
            return;
        }
        PRESENTATIONS.computeIfPresent(entityId, (ignored, current) -> {
            LinkedHashSet<String> markers = new LinkedHashSet<>(current.markerKeys());
            markers.remove(markerKey.strip());
            return markers.isEmpty() ? null : new Presentation(current.javaEntityType(), Set.copyOf(markers));
        });
    }

    public record Presentation(String javaEntityType, Set<String> markerKeys) {
        public Presentation {
            markerKeys = Set.copyOf(markerKeys);
        }
    }
}
