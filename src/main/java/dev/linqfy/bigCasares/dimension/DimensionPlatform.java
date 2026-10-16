package dev.linqfy.bigCasares.dimension;

import dev.linqfy.bigCasares.dimension.environment.EnvironmentCycle;
import dev.linqfy.bigCasares.dimension.travel.PortalShape;
import dev.linqfy.bigCasares.dimension.travel.TravelResult;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Public entry point of the dimension framework. Obtain it from the
 * {@code dimension-framework} module or Bukkit's services manager:
 *
 * <pre>{@code
 * DimensionPlatform platform = Bukkit.getServicesManager().load(DimensionPlatform.class);
 * platform.register(myDefinition);
 * }</pre>
 *
 * <p>Registration validates and compiles every registered definition into one
 * datapack under {@code <level>/datapacks/}. Dimensions whose datapack content is
 * new become {@link DimensionStatus.State#PENDING_RESTART}; already-loaded ones
 * activate immediately.</p>
 */
public interface DimensionPlatform {

    /** Registers and (when possible) activates a definition. Duplicate keys fail. */
    DimensionStatus register(DimensionDefinition definition);

    /**
     * Deactivates runtime hooks and forgets the definition. The world and its
     * files are kept; the datapack is rewritten on the next registration change.
     */
    boolean unregister(DimensionKey key);

    Optional<DimensionDefinition> definition(DimensionKey key);

    Collection<DimensionDefinition> definitions();

    DimensionStatus status(DimensionKey key);

    Optional<World> world(DimensionKey key);

    /** The registered dimension a world belongs to, if any. */
    Optional<DimensionKey> dimensionOf(World world);

    Optional<EnvironmentCycle.Snapshot> cycleSnapshot(DimensionKey key, String cycleId);

    /** Sends a player into the dimension at the mapped position of their current location. */
    CompletableFuture<TravelResult> enter(Player player, DimensionKey key);

    /** Teleports to an exact location (safe-spot search around it). */
    CompletableFuture<TravelResult> teleport(Player player, Location target);

    /** Returns a player to their stored return point (or the main world spawn). */
    CompletableFuture<TravelResult> leave(Player player);

    /**
     * Detects a complete frame of a portal type around one of its frame blocks,
     * without lighting it.
     *
     * @param portalId id of a {@link dev.linqfy.bigCasares.dimension.travel.PortalDefinition} of {@code key}
     */
    Optional<PortalShape> findPortalFrame(DimensionKey key, String portalId, Block frameBlock);

    /**
     * Lights the frame around {@code frameBlock} as a portal of the given type without
     * an activation item, for scripted activations (rituals, quests, commands). Fires
     * {@link dev.linqfy.bigCasares.dimension.event.DimensionPortalActivateEvent}. Returns
     * the lit (or already lit) shape, or empty when the dimension is not active, the
     * frame is incomplete or the event was cancelled.
     *
     * @param cause the player responsible, or {@code null}
     */
    Optional<PortalShape> lightPortal(DimensionKey key, String portalId, Block frameBlock, @Nullable Player cause);

    /** Removes the lit portal whose frame or interior contains the block; true when one was removed. */
    boolean extinguishPortal(Block block);
}
