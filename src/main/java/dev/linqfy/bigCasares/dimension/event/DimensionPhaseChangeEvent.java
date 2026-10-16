package dev.linqfy.bigCasares.dimension.event;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * An environment cycle of an active dimension entered a new phase. Fired on the
 * server thread when the world clock crosses a phase boundary (including
 * {@code /time} jumps) and once on activation with a null {@link #from()}.
 */
public final class DimensionPhaseChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DimensionKey dimension;
    private final World world;
    private final String cycleId;
    private final EnvironmentCycle.Snapshot from;
    private final EnvironmentCycle.Snapshot to;

    public DimensionPhaseChangeEvent(
        DimensionKey dimension, World world, String cycleId, EnvironmentCycle.Snapshot from, EnvironmentCycle.Snapshot to
    ) {
        this.dimension = dimension;
        this.world = world;
        this.cycleId = cycleId;
        this.from = from;
        this.to = to;
    }

    public DimensionKey dimension() {
        return dimension;
    }

    public World world() {
        return world;
    }

    public String cycleId() {
        return cycleId;
    }

    /** Previous phase, or null on activation. */
    public EnvironmentCycle.Snapshot from() {
        return from;
    }

    public EnvironmentCycle.Snapshot to() {
        return to;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
