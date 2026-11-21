package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicInteractionsTest {

    @Test
    void anAirClickIsNotADenialButAnotherPluginsDenialIs() {
        PlayerInteractEvent air = new PlayerInteractEvent(player(), Action.RIGHT_CLICK_AIR, null, null, null);
        // Bukkit marca cancelado el click al aire (no hay bloque), pero el item se puede usar
        assertTrue(air.isCancelled());
        assertFalse(MythicInteractions.deniedByOthers(air));

        air.setUseItemInHand(Event.Result.DENY);
        assertTrue(MythicInteractions.deniedByOthers(air));

        PlayerInteractEvent cancelled = new PlayerInteractEvent(player(), Action.RIGHT_CLICK_AIR, null, null, null);
        cancelled.setCancelled(true);
        assertTrue(MythicInteractions.deniedByOthers(cancelled));
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, arguments) -> null);
    }
}
