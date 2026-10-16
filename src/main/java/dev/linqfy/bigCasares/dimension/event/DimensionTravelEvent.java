package dev.linqfy.bigCasares.dimension.event;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Fired before a framework-managed transfer. Listeners may cancel it or change
 * the destination; the framework still runs its safe-arrival search around the
 * final destination.
 */
public final class DimensionTravelEvent extends PlayerEvent implements Cancellable {

    /** Why the transfer happens. */
    public enum Cause {
        PORTAL, COMMAND, RETURN, API, EVACUATION, RIFT
    }

    private static final HandlerList HANDLERS = new HandlerList();

    private final DimensionKey dimension;
    private final boolean entering;
    private final Cause cause;
    private final Location from;
    private Location to;
    private boolean cancelled;

    public DimensionTravelEvent(
        Player player, DimensionKey dimension, boolean entering, Cause cause, Location from, Location to
    ) {
        super(player);
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.entering = entering;
        this.cause = Objects.requireNonNull(cause, "cause");
        this.from = from.clone();
        this.to = to.clone();
    }

    /** The custom dimension being entered or left. */
    public DimensionKey dimension() {
        return dimension;
    }

    /** True when travelling into {@link #dimension()}, false when leaving it. */
    public boolean isEntering() {
        return entering;
    }

    public Cause cause() {
        return cause;
    }

    public Location from() {
        return from.clone();
    }

    public Location to() {
        return to.clone();
    }

    public void setTo(Location to) {
        this.to = Objects.requireNonNull(to, "to").clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
