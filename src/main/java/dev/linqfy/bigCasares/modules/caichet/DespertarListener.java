package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;

/**
 * La bebida Despertar lleva al hotel al terminar de beberla (la comida y el gasto los hace el juego, como con cualquier
 * consumible; lo que pasa despues esta en {@link HotelLoot}, junto a las otras bebidas). En el hotel no se puede beber.
 * Se recuerda de donde viniste para poder volver con /caichet tp.
 */
public final class DespertarListener implements Listener {

    static final NamespacedKey HOTEL_RETURN = new NamespacedKey("caichet", "hotel_return");

    /** Ya estas en el hotel: no se gasta. Va antes que {@link HotelLoot#onConsume}, que ignora lo cancelado. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (DreamItems.isWakeDrink(event.getItem()) && HotelWorld.isHotel(event.getPlayer().getWorld())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("Ya estás despierto."));
        }
    }

    static void wake(Player player) {
        World hotel = HotelWorld.get();
        if (!player.isOnline() || hotel == null || HotelWorld.isHotel(player.getWorld())) {
            return;
        }
        PortalListener.saveReturn(player, HOTEL_RETURN);
        player.teleportAsync(HotelWorld.spawn(hotel)); // carga el chunk sin frenar el servidor
    }

    static void leave(Player player) {
        player.teleportAsync(PortalListener.safe(PortalListener.loadReturn(player, HOTEL_RETURN)));
    }
}
