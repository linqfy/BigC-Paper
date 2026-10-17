package dev.linqfy.bigCasares.dimension.event;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired before a framework-managed transfer, after the safe-arrival search.
 * Listeners may cancel it or change the destination, which is then used as given;
 * {@link #portal()} names the portal definition a portal transfer went through.
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
    private final String portal;
    private Location to;
    private boolean cancelled;

    public DimensionTravelEvent(
        Player player, DimensionKey dimension, boolean entering, Cause cause, Location from, Location to
    ) {
        this(player, dimension, entering, cause, from, to, null);
    }

    public DimensionTravelEvent(
        Player player, DimensionKey dimension, boolean entering, Cause cause, Location from, Location to,
        @Nullable String portal
    ) {
        super(player);
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.entering = entering;
        this.cause = Objects.requireNonNull(cause, "cause");
        this.from = from.clone();
        this.to = to.clone();
        this.portal = portal;
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

    /** Id of the {@code PortalDefinition} used, for {@link Cause#PORTAL} transfers. */
    public Optional<String> portal() {
        return Optional.ofNullable(portal);
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
