package dev.linqfy.bigCasares.modules.netheritequest;

/** Horizontal distance of one movement, ignoring jumps too long to be steps (teleports, portals). */
final class WalkDistance {

    private WalkDistance() {
    }

    static double horizontal(double fromX, double fromZ, double toX, double toZ, double maxStep) {
        double distance = Math.hypot(toX - fromX, toZ - fromZ);
        if (!Double.isFinite(distance) || distance <= 0.0 || distance > maxStep) {
            return 0.0;
        }
        return distance;
    }
}
