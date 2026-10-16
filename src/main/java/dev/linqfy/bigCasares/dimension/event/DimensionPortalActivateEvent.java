package dev.linqfy.bigCasares.dimension.event;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.travel.PortalShape;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** A player is about to light a portal frame; cancel to keep it inactive. */
public final class DimensionPortalActivateEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DimensionKey dimension;
    private final String portalDefinition;
    private final World world;
    private final PortalShape shape;
    private boolean cancelled;

    public DimensionPortalActivateEvent(
        Player player, DimensionKey dimension, String portalDefinition, World world, PortalShape shape
    ) {
        super(player);
        this.dimension = dimension;
        this.portalDefinition = portalDefinition;
        this.world = world;
        this.shape = shape;
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
