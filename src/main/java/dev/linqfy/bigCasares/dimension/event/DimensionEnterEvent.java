package dev.linqfy.bigCasares.dimension.event;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player is now inside a custom dimension, by any means (framework travel,
 * vanilla teleport, respawn, or joining the server while inside it).
 */
public final class DimensionEnterEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DimensionKey dimension;
    private final boolean firstVisit;

    public DimensionEnterEvent(Player player, DimensionKey dimension, boolean firstVisit) {
        super(player);
        this.dimension = dimension;
        this.firstVisit = firstVisit;
    }

    public DimensionKey dimension() {
        return dimension;
    }

    /** True the first time this player has ever entered the dimension. */
    public boolean isFirstVisit() {
        return firstVisit;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
