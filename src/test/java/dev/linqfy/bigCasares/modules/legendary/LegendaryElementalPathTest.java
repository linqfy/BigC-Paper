package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;

class LegendaryElementalPathTest {
    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, (proxy, method, args) -> {
                if (method.getName().equals("equals")) return proxy == args[0];
                throw new AssertionError("An invalid path must not query the world: " + method.getName());
            });

    @Test
    void longTeleportsAreRejectedBeforeAnyChunkOrProtectionQueries() {
        var context = new LegendaryElementalContext(null, null);
        assertFalse(context.pathAllowed(null, new Location(world, 0, 64, 0),
                new Location(world, 1_000_000, 64, 0), LegendaryProtectionEvent.Action.MOVE));
        assertFalse(context.clear(new Location(world, 0, 64, 0), new Location(world, 1_000_000, 64, 0)));
    }

    @Test
    void nonFiniteEndpointsCannotReachRayTracingOrRegionQueries() {
        var context = new LegendaryElementalContext(null, null);
        for (double coordinate : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(context.pathAllowed(null, new Location(world, 0, 64, 0),
                    new Location(world, coordinate, 64, 0), LegendaryProtectionEvent.Action.MOVE));
            assertFalse(context.clear(new Location(world, 0, 64, 0), new Location(world, coordinate, 64, 0)));
        }
    }
}
