package dev.linqfy.bigCasares.dimension.event;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** A player left a custom dimension (changed world or quit while inside it). */
public final class DimensionExitEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DimensionKey dimension;
    private final boolean disconnected;

    public DimensionExitEvent(Player player, DimensionKey dimension, boolean disconnected) {
        super(player);
        this.dimension = dimension;
        this.disconnected = disconnected;
    }

    public DimensionKey dimension() {
        return dimension;
    }

    /** True when the exit is the player quitting the server. */
    public boolean isDisconnect() {
        return disconnected;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
