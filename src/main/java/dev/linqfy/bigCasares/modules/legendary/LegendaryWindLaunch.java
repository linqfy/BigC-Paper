package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.util.Vector;

final class LegendaryWindLaunch {
    private LegendaryWindLaunch() { }

    static Vector velocity(Vector current, double boost) {
        return current.clone().setY(Math.max(current.getY(), boost));
    }
}
