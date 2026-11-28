package dev.linqfy.bigCasares.modules.toffy;

/**
 * Where a player was before a tournament teleport, stored by world name so it survives restarts.
 */
record ToffyStoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
    ToffyStoredLocation {
        world = world == null ? "" : world;
    }
}
