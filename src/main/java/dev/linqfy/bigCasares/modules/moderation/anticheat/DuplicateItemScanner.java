package dev.linqfy.bigCasares.modules.moderation.anticheat;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Anti-dupe. A los items valiosos que no se apilan (elytra, totems, equipo de netherite, shulkers...) les pone un id
 * unico invisible; despues, en cada pasada, mira inventarios, cursores, ender chests y contenedores abiertos de todos los
 * jugadores en el mismo tick. Dos lugares con el mismo id a la vez es un dupe.
 *
 * <p>Solo marca items vanilla sin otros datos persistentes: los items de otros modulos (que se comparan por sus propios
 * datos) no se tocan. Los jugadores en creativo no se escanean, y lo que clonan con el clic del medio pierde el id.
 */
public final class DuplicateItemScanner implements Listener {
    public static final long SCAN_PERIOD_TICKS = 40;

    private final Server server;
    private final NamespacedKey key;
    private final AnticheatSettings settings;
    private final BiConsumer<Player, Detection> sink;
    private final DuplicateLedger ledger = new DuplicateLedger();

    public DuplicateItemScanner(
        Server server, NamespacedKey key, AnticheatSettings settings, BiConsumer<Player, Detection> sink
    ) {
        this.server = server;
        this.key = key;
        this.settings = settings;
        this.sink = sink;
    }

    public void scan() {
        if (!settings.checkEnabled("item-duplicate")) {
            return;
        }
        ledger.beginPass();
        Set<Object> visited = new HashSet<>();
        for (Player player : server.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            scanInventory(player.getInventory(), visited, holder(player, "inventario"));
            scanCursor(player);
            scanInventory(player.getEnderChest(), visited, holder(player, "ender chest"));
            Inventory top = player.getOpenInventory().getTopInventory();
            if (top.getType() != InventoryType.CREATIVE && realContainer(top)) {
                scanInventory(top, visited, holder(player, "contenedor " + describe(top)));
            }
        }
        for (DuplicateLedger.Duplicate duplicate : ledger.endPass(System.currentTimeMillis())) {
            String evidence = duplicate.item() + " con el mismo id en " + where(duplicate.first())
                + " y en " + where(duplicate.second());
            Detection detection = new Detection("item-duplicate", evidence);
            report(duplicate.first().playerId(), detection);
            if (!duplicate.second().playerId().equals(duplicate.first().playerId())) {
                report(duplicate.second().playerId(), detection);
            }
        }
    }

    /**
     * Solo contenedores del mundo (cofres, barriles, shulkers, vagonetas, burros, inventarios de jugadores). Los menus de
     * los modulos tienen su propio holder o ninguno: sus iconos no son items reales y no se tocan.
     */
    private static boolean realContainer(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        return holder instanceof Container || holder instanceof DoubleChest || holder instanceof Entity;
    }

    /** El clic del medio en creativo copia el item entero: la copia no puede llevar el mismo id. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        ItemStack cursor = event.getCursor();
        if (cursor.getType().isAir() || !cursor.hasItemMeta()) {
            return;
        }
        ItemMeta meta = cursor.getItemMeta();
        if (meta.getPersistentDataContainer().has(key)) {
            meta.getPersistentDataContainer().remove(key);
            cursor.setItemMeta(meta);
            event.setCursor(cursor);
        }
    }

    public void clear() {
        ledger.clear();
    }

    private void scanInventory(Inventory inventory, Set<Object> visited, DuplicateLedger.Holder holder) {
        if (!visited.add(identity(inventory))) {
            return; // el mismo inventario visto por dos jugadores (o un admin mirando el de otro) se cuenta una vez
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!tracked(item)) {
                continue;
            }
            String id = idOf(item);
            if (id == null) {
                id = assign(item);
                inventory.setItem(slot, item);
            }
            ledger.seen(id, item.getType().name(), holder);
        }
    }

    private void scanCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (!tracked(cursor)) {
            return;
        }
        String id = idOf(cursor);
        if (id == null) {
            id = assign(cursor);
            player.setItemOnCursor(cursor);
        }
        ledger.seen(id, cursor.getType().name(), holder(player, "cursor"));
    }

    private boolean tracked(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() != 1 || item.getMaxStackSize() != 1) {
            return false;
        }
        Material type = item.getType();
        if (!settings.dupeTrackedItems().contains(type)
            && !(settings.trackShulkerBoxes() && Tag.SHULKER_BOXES.isTagged(type))) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return true;
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey existing : data.getKeys()) {
            if (!existing.equals(key)) {
                return false; // item de otro modulo: se identifica por sus propios datos
            }
        }
        return true;
    }

    private String idOf(ItemStack item) {
        if (!item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private String assign(ItemStack item) {
        String id = UUID.randomUUID().toString();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return id;
    }

    /**
     * Identidad estable del inventario: el dueno (jugador o entidad) o la posicion del bloque. Los cofres dobles se
     * envuelven en un objeto nuevo por cada espectador, asi que la identidad del objeto no alcanza.
     */
    private static Object identity(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof HumanEntity human) {
            return inventory.getType() + ":" + human.getUniqueId();
        }
        if (holder instanceof Entity entity) {
            return inventory.getType() + ":" + entity.getUniqueId();
        }
        Location location = inventory.getLocation();
        if (location != null && location.getWorld() != null) {
            return inventory.getType() + ":" + location.getWorld().getName() + ":" + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
        }
        return inventory;
    }

    private static String describe(Inventory inventory) {
        Location location = inventory.getLocation();
        String type = inventory.getType().name().toLowerCase(java.util.Locale.ROOT);
        return location == null ? type
            : type + " en " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private static DuplicateLedger.Holder holder(Player player, String place) {
        return new DuplicateLedger.Holder(player.getUniqueId(), player.getName(), place);
    }

    private static String where(DuplicateLedger.Holder holder) {
        return holder.place() + " de " + holder.playerName();
    }

    private void report(UUID playerId, Detection detection) {
        Player player = server.getPlayer(playerId);
        if (player != null) {
            sink.accept(player, detection);
        }
    }
}
