package dev.linqfy.bigCasares.dimension.travel;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * How players enter and leave a dimension.
 *
 * @param mapping              source-world to dimension coordinate mapping (and inverse for returns)
 * @param entryPermission      permission required to enter (null = none)
 * @param cooldown             minimum time between two transfers of the same player
 * @param rememberReturnPoint  store where the player came from and return there
 * @param arrivalSearchRadius  radius (blocks) searched for a safe arrival spot
 * @param portals              portal types leading into the dimension
 */
public record TravelRules(
    CoordinateMapping mapping,
    String entryPermission,
    Duration cooldown,
    boolean rememberReturnPoint,
    int arrivalSearchRadius,
    List<PortalDefinition> portals
) {

    public static final TravelRules DEFAULT = new TravelRules(
        CoordinateMapping.IDENTITY, null, Duration.ofSeconds(5), true, 48, List.of());

    public TravelRules {
        Objects.requireNonNull(mapping, "mapping");
        Objects.requireNonNull(cooldown, "cooldown");
        if (cooldown.isNegative()) {
            throw new IllegalArgumentException("Cooldown must not be negative");
        }
        if (arrivalSearchRadius < 0 || arrivalSearchRadius > 512) {
            throw new IllegalArgumentException("arrivalSearchRadius must be within [0, 512]");
        }
        portals = List.copyOf(portals);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (PortalDefinition portal : portals) {
            if (!ids.add(portal.id())) {
                throw new IllegalArgumentException("Duplicate portal id: " + portal.id());
            }
        }
    }

    public TravelRules withPortals(List<PortalDefinition> newPortals) {
        return new TravelRules(mapping, entryPermission, cooldown, rememberReturnPoint, arrivalSearchRadius, newPortals);
    }
}
