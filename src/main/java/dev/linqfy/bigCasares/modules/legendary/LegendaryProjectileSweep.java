package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class LegendaryProjectileSweep {
    @FunctionalInterface
    public interface Trace { RayTraceResult trace(Vector direction, double distance); }
    public record Step(Location destination, boolean impact, Entity hitEntity) {}
    private LegendaryProjectileSweep() {}

    /** Sweep the entire step, rather than checking only its endpoint and tunnelling through walls. */
    public static Step advance(Location start, Vector velocity, Trace trace) {
        double distance = velocity.length();
        if (!Double.isFinite(distance) || distance <= 0) throw new IllegalArgumentException("Velocidad de proyectil inválida.");
        RayTraceResult hit = trace.trace(velocity.clone().normalize(), distance);
        return hit == null ? new Step(start.clone().add(velocity), false, null)
            : new Step(hit.getHitPosition().toLocation(start.getWorld()), true, hit.getHitEntity());
    }
}
