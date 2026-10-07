package dev.linqfy.bigCasares.modules.donpollos;

import java.util.OptionalDouble;

public final class DonPollosSalseroPolicy {

    private final double followRadiusSquared;
    private final double stopDistanceSquared;

    public DonPollosSalseroPolicy(double followRadius, double stopDistance) {
        if (followRadius <= 0.0 || stopDistance <= 0.0 || stopDistance >= followRadius) {
            throw new IllegalArgumentException("Salsero needs 0 < stop-distance < follow-radius");
        }
        this.followRadiusSquared = followRadius * followRadius;
        this.stopDistanceSquared = stopDistance * stopDistance;
    }

    public double followRadiusSquared() {
        return followRadiusSquared;
    }

    public DonPollosSalseroAction decide(OptionalDouble nearestPlayerDistanceSquared) {
        if (nearestPlayerDistanceSquared.isEmpty()) {
            return DonPollosSalseroAction.IDLE;
        }
        double distance = nearestPlayerDistanceSquared.getAsDouble();
        if (distance > followRadiusSquared) {
            return DonPollosSalseroAction.IDLE;
        }
        return distance <= stopDistanceSquared ? DonPollosSalseroAction.STOP : DonPollosSalseroAction.FOLLOW;
    }
}
