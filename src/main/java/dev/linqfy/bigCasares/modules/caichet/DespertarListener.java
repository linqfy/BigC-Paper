package dev.linqfy.bigCasares.modules.caichet;

import net.kyori.adventure.text.Component;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El tótem Despertar lleva al hotel: al usarlo (clic derecho) o cuando te salva de morir, como un tótem normal.
 * Se recuerda de donde viniste para poder volver con /caichet tp.
 */
public final class DespertarListener implements Listener {

    static final NamespacedKey HOTEL_RETURN = new NamespacedKey("caichet", "hotel_return");
    /** Un segundo para que se vea la animacion del tótem antes de cambiar de mundo. */
    private static final long DELAY_TICKS = 20L;

    /** Quien ya uso un Despertar y esta esperando el viaje (con un token por uso). */
    private final Map<UUID, Object> pending = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!DreamItems.isTotem(item) || event.getHand() == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (HotelWorld.isHotel(player.getWorld())) {
            player.sendActionBar(Component.text("Ya estás despierto."));
            return;
        }
        // un solo viaje a la vez: mantener apretado el clic no gasta mas tótems
        Object token = new Object();
        if (pending.putIfAbsent(player.getUniqueId(), token) != null) {
            return;
        }
        // la animacion usa el tótem que tenes en la mano: se gasta recien al viajar
        player.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        EquipmentSlot hand = event.getHand();
        CaiChetRuntime.tasks().later(() -> {
            if (!pending.remove(player.getUniqueId(), token) || !player.isOnline()
                    || HotelWorld.isHotel(player.getWorld())) {
                return;
            }
            // sin tótem (lo tiro o lo guardo en ese segundo) no hay viaje
            if (player.getGameMode() == GameMode.CREATIVE || consumeOne(player, hand)) {
                wake(player);
            }
        }, DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    /** Gasta un Despertar: primero el de esa mano, si no cualquiera del inventario. */
    private static boolean consumeOne(Player player, EquipmentSlot hand) {
        ItemStack inHand = player.getInventory().getItem(hand);
        if (DreamItems.isTotem(inHand)) {
            inHand.setAmount(inHand.getAmount() - 1);
            player.getInventory().setItem(hand, inHand.getAmount() > 0 ? inHand : null);
            return true;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (DreamItems.isTotem(contents[i])) {
                contents[i].setAmount(contents[i].getAmount() - 1);
                player.getInventory().setItem(i, contents[i].getAmount() > 0 ? contents[i] : null);
                return true;
            }
        }
        return false;
    }

    /** Si el Despertar te salva de morir (como cualquier tótem), despues te lleva al hotel. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getHand() == null) {
            return;
        }
        if (!DreamItems.isTotem(player.getInventory().getItem(event.getHand()))) {
            return;
        }
        CaiChetRuntime.tasks().later(() -> wake(player), DELAY_TICKS);
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
