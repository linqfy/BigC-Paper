package dev.linqfy.bigCasares.modules.staffevents;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the code-built arenas, kept free of Bukkit so it can be tested. Each event
 * type has its own centre in the event world, far enough apart that arenas never touch.
 */
public record ArenaLayout(StaffEventType type, int centerX, int floorY, int centerZ, int radius,
                          int layers, int layerGap, int hillRadius) {

    private static final int SPACING = 512;

    public ArenaLayout {
        if (!type.arena()) {
            throw new IllegalArgumentException(type + " has no arena");
        }
        if (radius <= 0 || layers <= 0 || layerGap <= 0 || hillRadius < 0) {
            throw new IllegalArgumentException("Invalid arena dimensions");
        }
    }

    public static ArenaLayout of(StaffEventType type, StaffEventSettings settings) {
        int centerX = type.ordinal() * SPACING;
        int y = settings.arenaY();
        return switch (type) {
            case KOTH -> new ArenaLayout(type, centerX, y, 0, settings.koth().platformRadius(), 1, 1,
                settings.koth().hillRadius());
            case TNT_TAG -> new ArenaLayout(type, centerX, y, 0, settings.tntTag().platformRadius(), 1, 1, 0);
            case SPLEEF -> new ArenaLayout(type, centerX, y, 0, settings.spleef().radius(),
                settings.spleef().layers(), settings.spleef().layerGap(), 0);
            case LIFESTEAL -> throw new IllegalArgumentException("Lifesteal has no arena");
        };
    }

    /** Whether a column offset from the centre belongs to a disc of the given radius. */
    public static boolean inDisc(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius + radius;
    }

    /** Y of the block players stand on in each layer, top layer first. */
    public List<Integer> layerFloors() {
        List<Integer> floors = new ArrayList<>(layers);
        for (int layer = 0; layer < layers; layer++) {
            floors.add(floorY - 1 - layer * layerGap);
        }
        return floors;
    }

    public int lowestFloor() {
        return floorY - 1 - (layers - 1) * layerGap;
    }

    /** Below this Y a player has left the arena (fell into the void). */
    public double fallOutY() {
        return lowestFloor() - 4.0;
    }

    /** Height of the KOTH hill top players stand on. */
    public int hillTopY() {
        return floorY + 1;
    }

    /** Whether a position counts as standing on the KOTH hill. */
    public boolean onHill(double x, double y, double z) {
        double dx = x - (centerX + 0.5);
        double dz = z - (centerZ + 0.5);
        return dx * dx + dz * dz <= (hillRadius + 0.5) * (hillRadius + 0.5)
            && y >= hillTopY() - 0.1 && y <= hillTopY() + 4.0;
    }

    /** Lowest and highest Y cleared before building. */
    public int clearMinY() {
        return lowestFloor() - 2;
    }

    public int clearMaxY() {
        return floorY + 8;
    }

    public int clearRadius() {
        return radius + 4;
    }
}
