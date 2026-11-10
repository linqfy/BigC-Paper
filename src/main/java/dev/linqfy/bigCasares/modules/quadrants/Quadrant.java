package dev.linqfy.bigCasares.modules.quadrants;

/** Los cuatro cuadrantes alrededor del centro. En Minecraft el norte es Z negativo y el este X positivo. */
public enum Quadrant {
    NORTH_EAST,
    NORTH_WEST,
    SOUTH_EAST,
    SOUTH_WEST;

    public static Quadrant at(double x, double z, double centerX, double centerZ) {
        boolean east = x >= centerX;
        boolean north = z < centerZ;
        if (north) {
            return east ? NORTH_EAST : NORTH_WEST;
        }
        return east ? SOUTH_EAST : SOUTH_WEST;
    }

    public Quadrant opposite() {
        return switch (this) {
            case NORTH_EAST -> SOUTH_WEST;
            case SOUTH_WEST -> NORTH_EAST;
            case NORTH_WEST -> SOUTH_EAST;
            case SOUTH_EAST -> NORTH_WEST;
        };
    }
}
