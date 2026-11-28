package dev.linqfy.bigCasares.modules.caichet;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * En el desierto, el hotel y el Sueño no se puede marcar el punto de reaparicion: ni camas ni anclas. Al intentarlo
 * aparece "No, no estoy cansado" y no pasa nada (la cama tampoco explota). Los comandos de administrador
 * (/spawnpoint) siguen andando.
 */
final class NoSpawnPoints implements Listener {

    static final String MESSAGE = "No, no estoy cansado";

    static boolean isCaiChetWorld(World world) {
        return world != null && (world.equals(PortalListener.dimension()) || HotelWorld.isHotel(world)
            || DreamWorld.isDream(world));
    }

    /** La causa es dormir o un ancla (no un comando ni otro plugin). */
    static boolean isPlayerSpawn(String cause) {
        return "BED".equals(cause) || "RESPAWN_ANCHOR".equals(cause);
    }

    /** Clic derecho en una cama o un ancla: se corta antes de dormir, cargar el ancla o explotar. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || !isCaiChetWorld(block.getWorld())) {
            return;
        }
        Material type = block.getType();
        if (!Tag.BEDS.isTagged(type) && type != Material.RESPAWN_ANCHOR) {
            return;
        }
        if (event.getPlayer().isSneaking() && event.getItem() != null && event.getItem().getType().isBlock()) {
            return; // agachado con un bloque en la mano: esta construyendo al lado, no usando la cama
        }
        // el clic llega una vez por mano: se cancelan las dos (si no, la otra mano usaria la cama), pero el cartel
        // sale una sola vez
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            refuse(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        if (isCaiChetWorld(event.getBed().getWorld())) {
            event.setCancelled(true);
            refuse(event.getPlayer());
        }
    }

    /** Por si llega por otro lado (otro plugin que acueste al jugador): igual no se guarda. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSetSpawn(PlayerSetSpawnEvent event) {
        if (isPlayerSpawn(event.getCause().name()) && event.getLocation() != null
            && isCaiChetWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    private static void refuse(Player player) {
        player.sendActionBar(Component.text(MESSAGE, NamedTextColor.GRAY));
    }
}
