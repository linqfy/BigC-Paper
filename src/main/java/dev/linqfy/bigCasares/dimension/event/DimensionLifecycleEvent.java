package dev.linqfy.bigCasares.dimension.event;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.DimensionStatus;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** A registered dimension changed lifecycle state (registered, pending restart, active, deactivated...). */
public final class DimensionLifecycleEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DimensionKey dimension;
    private final DimensionStatus status;

    public DimensionLifecycleEvent(DimensionKey dimension, DimensionStatus status) {
        this.dimension = dimension;
        this.status = status;
    }

    public DimensionKey dimension() {
        return dimension;
    }

    public DimensionStatus status() {
        return status;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
