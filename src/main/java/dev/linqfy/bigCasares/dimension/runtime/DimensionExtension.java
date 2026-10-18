package dev.linqfy.bigCasares.dimension.runtime;

import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.generator.BlockPopulator;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Optional runtime behaviour of a dimension. Every hook has a no-op default so
 * an extension implements only what it needs.
 *
 * <p>All hooks run on the server thread except {@link #populators}, whose
 * populators run on world-generation threads (see {@link BlockPopulator}).
 * Resources registered through {@link ActiveDimension#registrations()} are
 * released automatically when the dimension deactivates.</p>
 */
public interface DimensionExtension {

    /** The dimension's world is available; register listeners and tasks here. */
    default void onActivate(ActiveDimension dimension) {
    }

    /** Called before deactivation (plugin disable/reload); persist state here. */
    default void onDeactivate(ActiveDimension dimension) {
    }

    /**
     * Java-side decoration run by Paper during the native feature step for every
     * newly generated chunk. Populators must be thread-safe and deterministic:
     * use only the provided random and region.
     */
    default List<BlockPopulator> populators(ActiveDimension dimension) {
        return List.of();
    }

    /** Ids of the landmarks {@code /dimension locate} can find in this dimension (for example {@code main_portal}). */
    default List<String> landmarks() {
        return List.of();
    }

    /**
     * Where the landmark stands, for {@code /dimension locate}; empty when it does not exist (yet). An
     * extension whose landmark is built lazily may start building it here and answer on a later call.
     *
     * @param landmark one of {@link #landmarks()}
     * @param from     where the sender stands, for landmarks that exist in several places (may be in another world)
     */
    default Optional<Location> locate(ActiveDimension dimension, String landmark, Location from) {
        return Optional.empty();
    }

    default void onPlayerEnter(ActiveDimension dimension, Player player) {
    }

    default void onPlayerExit(ActiveDimension dimension, Player player) {
    }

    /** A cycle moved to a new phase (also fired once on activation with {@code from == null}). */
    default void onPhaseChange(
        ActiveDimension dimension, EnvironmentCycle cycle, EnvironmentCycle.Snapshot from, EnvironmentCycle.Snapshot to
    ) {
    }

    /**
     * Coarse periodic hook (every {@link #pulseIntervalTicks()} ticks) invoked only
     * while players are inside the dimension. Keep work proportional to players.
     */
    default void onPulse(ActiveDimension dimension, Collection<Player> players) {
    }

    default long pulseIntervalTicks() {
        return 20L;
    }
}
