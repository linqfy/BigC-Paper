package dev.linqfy.bigCasares.dimension.travel;

import java.time.Duration;
import java.util.Objects;

/** Outcome of a travel request. */
public record TravelResult(Status status, String message, Duration retryAfter) {

    public enum Status {
        TELEPORTED, DIMENSION_UNAVAILABLE, NO_PERMISSION, ON_COOLDOWN, NO_SAFE_LOCATION, CANCELLED, FAILED
    }

    public TravelResult {
        Objects.requireNonNull(status, "status");
        message = message == null ? "" : message;
        retryAfter = retryAfter == null ? Duration.ZERO : retryAfter;
    }

    public static TravelResult of(Status status, String message) {
        return new TravelResult(status, message, Duration.ZERO);
    }

    public boolean succeeded() {
        return status == Status.TELEPORTED;
    }
}
