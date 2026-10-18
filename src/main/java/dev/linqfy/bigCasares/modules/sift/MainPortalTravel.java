package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.event.DimensionTravelEvent;
import dev.linqfy.bigCasares.modules.sift.feature.SiftMainPortalSite.Anchor;
import org.bukkit.Location;

import java.util.Optional;

/** The decisions of the main portal's travel listener, free of server state so that they can be tested. */
final class MainPortalTravel {

    /** What walking into the main portal does. */
    enum Exit {
        /** The framework returns the player to their stored return point. */
        FRAMEWORK,
        /** The portal the player entered through is gone: an exit rift at their respawn point, with a notice. */
        ENTRANCE_CLOSED,
        /** The player came in through a rift, so there is no entrance: an exit rift at their respawn point. */
        FROM_RIFT
    }

    /** Whether a position holds a lit portal's interior (a light block). */
    @FunctionalInterface
    interface Probe {
        boolean portalInterior(int x, int y, int z);
    }

    private MainPortalTravel() {
    }

    /**
     * The destination of players entering the Sift through the Ancient City portal: the main portal's
     * anchor, facing the way they faced (the mod's {@code teleportIntoSift}). Empty when the transfer is
     * anything else (the crafted portal, a command, a rift), which keep the framework's destination.
     */
    static Optional<Location> redirect(DimensionTravelEvent event, Anchor anchor) {
        if (!event.dimension().equals(SiftDimension.KEY) || !event.isEntering()
            || event.cause() != DimensionTravelEvent.Cause.PORTAL
            || !event.portal().equals(Optional.of(SiftDimension.ANCIENT_CITY_PORTAL))) {
            return Optional.empty();
        }
        Location from = event.from();
        return Optional.of(new Location(event.to().getWorld(), anchor.x() + 0.5, anchor.y(), anchor.z() + 0.5,
            from.getYaw(), from.getPitch()));
    }

    /** Whether the event is a player walking into the main portal from inside the Sift. */
    static boolean leavesThroughMainPortal(DimensionTravelEvent event) {
        return event.dimension().equals(SiftDimension.KEY) && !event.isEntering()
            && event.cause() == DimensionTravelEvent.Cause.PORTAL
            && event.portal().equals(Optional.of(SiftDimension.MAIN_PORTAL));
    }

    /**
     * Where a player leaving through the main portal goes. The mod checks that the portal block it stored
     * still is a Sift portal; here the stored return point is where the player stood next to the lit portal,
     * so the portal still stands when a light block of its interior is within two blocks.
     */
    static Exit exit(boolean arrivedByRift, Location returnPoint, Probe probe) {
        if (arrivedByRift) {
            return Exit.FROM_RIFT;
        }
        return entranceStands(returnPoint, probe) ? Exit.FRAMEWORK : Exit.ENTRANCE_CLOSED;
    }

    static boolean entranceStands(Location returnPoint, Probe probe) {
        int x = returnPoint.getBlockX();
        int y = returnPoint.getBlockY();
        int z = returnPoint.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (probe.portalInterior(x + dx, y + dy, z + dz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
