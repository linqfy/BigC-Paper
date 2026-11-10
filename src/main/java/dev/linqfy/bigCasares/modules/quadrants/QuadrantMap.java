package dev.linqfy.bigCasares.modules.quadrants;

import java.util.Objects;
import java.util.Optional;

/** Resuelve la zona de una posición. Los mundos que no están configurados no tienen zona. */
public final class QuadrantMap {

    private final QuadrantSettings settings;

    public QuadrantMap(QuadrantSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public Optional<QuadrantZone> zoneAt(String worldName, double x, double z) {
        if (worldName == null || !settings.worlds().contains(worldName)) {
            return Optional.empty();
        }
        return Optional.of(zoneOf(Quadrant.at(x, z, settings.centerX(), settings.centerZ())));
    }

    public QuadrantZone zoneOf(Quadrant quadrant) {
        Quadrant anarchy = settings.anarchyQuadrant();
        return quadrant == anarchy || quadrant == anarchy.opposite() ? QuadrantZone.ANARCHY : QuadrantZone.CHILL;
    }
}
