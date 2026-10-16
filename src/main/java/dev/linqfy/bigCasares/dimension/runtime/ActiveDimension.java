package dev.linqfy.bigCasares.dimension.runtime;

import dev.linqfy.bigCasares.dimension.DimensionDefinition;
import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.logging.Logger;

/** Runtime handle for a dimension whose world is loaded. */
public interface ActiveDimension {

    DimensionDefinition definition();

    World world();

    /** Scoped registrations; everything registered here is released on deactivation. */
    BukkitRuntimeRegistrations registrations();

    /** Current snapshot of one of the definition's environment cycles. */
    Optional<EnvironmentCycle.Snapshot> snapshot(String cycleId);

    /** Players currently inside the dimension. */
    Collection<Player> players();

    /** Persistent per-dimension string values (survive restarts). */
    DimensionValues values();

    Logger logger();

    /** Persistent key/value storage owned by one dimension. */
    interface DimensionValues {
        Optional<String> get(String key);

        void put(String key, String value);

        void remove(String key);
    }
}
