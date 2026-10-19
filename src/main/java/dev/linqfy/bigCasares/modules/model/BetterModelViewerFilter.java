package dev.linqfy.bigCasares.modules.model;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.event.ModelEventListener;
import kr.toxicity.model.api.event.ModelSpawnAtPlayerEvent;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Keeps BetterModel from showing some entity models to some players. A player BetterModel shows a model to also
 * gets the model's body as invisible (BetterModel rewrites the body's entity data per player), which is what a
 * Bedrock player must not get when Geyser draws that body as a Bedrock entity with the model: Bedrock does not
 * draw invisible entities. Geyser ignores the model's display entities, so skipping them costs those players
 * nothing.
 */
public final class BetterModelViewerFilter {

    /** Whether the model on an entity stays hidden from a player. Called on BetterModel's threads. */
    @FunctionalInterface
    public interface Hidden {
        boolean test(UUID entityId, UUID playerId);
    }

    private BetterModelViewerFilter() {
    }

    /** Subscribes the filter; the returned action unsubscribes it. */
    public static Runnable install(Plugin plugin, Hidden hidden) {
        ModelEventListener listener = BetterModel.eventBus().subscribe(plugin::isEnabled, ModelSpawnAtPlayerEvent.class,
            event -> {
                if (event.getTracker() instanceof EntityTracker tracker
                    && hidden.test(tracker.registry().uuid(), event.getPlayer().uuid())) {
                    event.setCancelled(true);
                }
            });
        return listener::unregister;
    }
}
