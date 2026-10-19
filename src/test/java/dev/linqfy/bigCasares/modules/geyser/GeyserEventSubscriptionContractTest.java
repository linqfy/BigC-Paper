package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.testsupport.CompiledClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Floodgate ships its own unrelocated copy of {@code org.geysermc.event}. A plugin that resolves {@code @Subscribe}
 * from that copy gets every annotated method ignored by Geyser, so the bridge has to subscribe explicitly.
 * Geyser is not on the test classpath, so this reads the compiled bridge instead of running it.
 */
class GeyserEventSubscriptionContractTest {

    private static final Set<String> EVENT_BUSES = Set.of(
        "org/geysermc/geyser/api/event/EventBus", "org/geysermc/event/bus/OwnedEventBus");

    @Test
    void bridgeSubscribesToEveryGeyserEventExplicitly() throws IOException {
        CompiledClass bridge = CompiledClass.of(getClass(), "dev.linqfy.bigCasares.modules.geyser.GeyserRuntimeBridge");

        assertTrue(bridge.callsAnyOwner("subscribe", EVENT_BUSES), "the bridge has to call eventBus().subscribe(...)");
        assertFalse(bridge.callsAnyOwner("register", EVENT_BUSES),
            "eventBus().register(...) relies on @Subscribe, which Geyser ignores when Floodgate is installed");
        assertFalse(bridge.mentionsType("org.geysermc.event.subscribe.Subscribe"), "no method may be annotated");
        assertTrue(bridge.callsAnyOwner("unregisterAll", EVENT_BUSES), "closing has to drop every subscription");
        // Geyser overrides every mushroom-block state itself, from a handler subscribed after this plugin's.
        assertTrue(bridge.referencedClasses().contains("org/geysermc/event/PostOrder"),
            "content blocks have to subscribe with an order so they come after Geyser's own mushroom blocks");
        for (String event : new String[]{
            "org/geysermc/geyser/api/event/lifecycle/GeyserDefineResourcePacksEvent",
            "org/geysermc/geyser/api/event/lifecycle/GeyserDefineEntitiesEvent",
            "org/geysermc/geyser/api/event/lifecycle/GeyserDefineCustomItemsEvent",
            "org/geysermc/geyser/api/event/lifecycle/GeyserDefineCustomBlocksEvent",
            "org/geysermc/geyser/api/event/java/ServerSpawnEntityEvent"}) {
            assertTrue(bridge.referencedClasses().contains(event), event + " has to be subscribed");
        }
    }

    @Test
    void readerSeesTheRegressionThisTestGuardsAgainst() throws IOException {
        CompiledClass self = CompiledClass.of(getClass(), Annotated.class.getName());
        assertTrue(self.mentionsType("java.lang.Deprecated"));
        assertFalse(self.mentionsType("org.geysermc.event.subscribe.Subscribe"));
    }

    @SuppressWarnings("unused")
    private static final class Annotated {
        @Deprecated
        void handler() {
        }
    }
}
