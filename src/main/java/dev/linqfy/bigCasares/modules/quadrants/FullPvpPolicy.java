package dev.linqfy.bigCasares.modules.quadrants;

import org.bukkit.Location;

/** Bukkit service exposed by the enabled quadrant module. */
@FunctionalInterface
public interface FullPvpPolicy {
    boolean isFullPvp(Location location);
}
