package dev.linqfy.bigCasares.module.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RuntimeRegistrationScope {

    private final RuntimeGeneration generation;
    private final String ownerId;
    /** En orden de registro; por id para que {@link #forget} sea O(1) aunque haya muchas tareas vivas. */
    private final Map<String, Registration> registrations = new LinkedHashMap<>();
    private boolean closed;

    public RuntimeRegistrationScope() {
        this(new RuntimeGeneration(0), "runtime");
    }

    public RuntimeRegistrationScope(RuntimeGeneration generation, String ownerId) {
        this.generation = Objects.requireNonNull(generation, "generation");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId").trim();
        if (this.ownerId.isEmpty()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
    }

    public synchronized void register(String resourceId, RuntimeCleanupAction action) {
        String normalizedId = Objects.requireNonNull(resourceId, "resourceId").trim();
        Objects.requireNonNull(action, "action");
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (closed) {
            throw new IllegalStateException("Runtime registration scope is already closed");
        }
        if (registrations.containsKey(normalizedId)) {
            throw new IllegalArgumentException("Duplicate runtime resource id: " + normalizedId);
        }
        registrations.put(normalizedId, new Registration(normalizedId, action));
    }

    public synchronized boolean forget(String resourceId) {
        String normalizedId = Objects.requireNonNull(resourceId, "resourceId").trim();
        if (normalizedId.isEmpty()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (closed) {
            return false;
        }
        return registrations.remove(normalizedId) != null;
    }

    public RuntimeCleanupReport close() {
        List<Registration> snapshot;
        synchronized (this) {
            if (closed) {
                return new RuntimeCleanupReport(List.of(), true);
            }
            closed = true;
            generation.retire();
            snapshot = List.copyOf(registrations.values());
            registrations.clear();
        }

        List<RuntimeCleanupOutcome> outcomes = new ArrayList<>(snapshot.size());
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            Registration registration = snapshot.get(index);
            try {
                registration.action().clean();
                outcomes.add(new RuntimeCleanupOutcome(registration.resourceId(), null));
            } catch (Throwable failure) {
                outcomes.add(new RuntimeCleanupOutcome(registration.resourceId(), failure));
            }
        }
        return new RuntimeCleanupReport(outcomes, false);
    }

    /** Recursos registrados y todavia no liberados ni olvidados. */
    public synchronized int size() {
        return registrations.size();
    }

    public RuntimeGeneration generation() {
        return generation;
    }

    public String ownerId() {
        return ownerId;
    }

    public Runnable guard(Runnable callback) {
        return generation.guard(callback);
    }

    private record Registration(String resourceId, RuntimeCleanupAction action) {
    }
}
