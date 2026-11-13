package dev.linqfy.bigCasares.modules.legendary;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegendaryFlightTest {
    @Test
    void heavenHoverDoesNotChangeThePersistentGravityFlagEvenIfTheServerStopsMidCast() {
        for (boolean previousGravity : new boolean[]{true, false}) {
            boolean[] gravity = {previousGravity};
            Vector[] velocity = {new Vector(0, -.3, 0)};
            UUID worldId = UUID.randomUUID(), playerId = UUID.randomUUID();
            World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getUID")) return worldId;
                        throw new AssertionError(method.getName());
                    });
            Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> playerId;
                        case "getWorld" -> world;
                        case "getLocation" -> new Location(world, 0, 64, 0);
                        case "isOnGround" -> false;
                        case "hasGravity" -> gravity[0];
                        case "setGravity" -> { gravity[0] = (boolean) args[0]; yield null; }
                        case "getVelocity" -> velocity[0].clone();
                        case "setVelocity" -> { velocity[0] = ((Vector) args[0]).clone(); yield null; }
                        case "setFallDistance" -> null;
                        default -> throw new AssertionError(method.getName());
                    });
            var skill = LegendaryElementalSkill.HEAVENS_FALL;
            var flight = new LegendaryWindAbilities.Flight(player, skill,
                    new LegendaryElementalSkill.Settings(skill, Map.of()));
            flight.age = 3;
            flight.tick(player, null);
            assertEquals(0, velocity[0].getY(), "hover must stop downward motion");
            assertEquals(previousGravity, gravity[0], "an autosave during hover must keep the original gravity");
            flight.close(player);
            assertEquals(previousGravity, gravity[0]);
        }
    }
}
