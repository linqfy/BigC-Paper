package dev.linqfy.bigCasares.dimension.event;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.travel.PortalShape;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A portal frame is about to be lit, by a player with the activation item or by
 * code ({@link dev.linqfy.bigCasares.dimension.DimensionPlatform#lightPortal}, e.g.
 * a ritual). Cancel to keep it inactive.
 */
public final class DimensionPortalActivateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final DimensionKey dimension;
    private final String portalDefinition;
    private final World world;
    private final PortalShape shape;
    private boolean cancelled;

    public DimensionPortalActivateEvent(
        Player player, DimensionKey dimension, String portalDefinition, World world, PortalShape shape
    ) {
        this.player = player;
        this.dimension = dimension;
        this.portalDefinition = portalDefinition;
        this.world = world;
        this.shape = shape;
    }

    /** The player who lit the frame, or {@code null} for scripted activations. */
    public @Nullable Player getPlayer() {
        return player;
    }

    public DimensionKey dimension() {
        return dimension;
    }

    public String portalDefinition() {
        return portalDefinition;
    }

    public World world() {
        return world;
    }

    public PortalShape shape() {
        return shape;
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
