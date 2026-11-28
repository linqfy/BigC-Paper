package dev.linqfy.bigCasares.modules.caichet;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.List;

/**
 * El filtro del Sueño (VHS nostalgico: bruma rosada, viñeta, grano, "▶ PLAY"): es un "overlay" de camara, como el
 * de la calabaza. Al entrar al Sueño se pone un item invisible en la cabeza (y se guarda el casco); al salir se
 * devuelve el casco. No se puede sacar ni tirar mientras tanto.
 */
public final class DreamFilter implements Listener {

    private static final NamespacedKey FILTER_KEY = new NamespacedKey("caichet", "dream_filter");
    private static final NamespacedKey SAVED_HELMET = new NamespacedKey("caichet", "dream_saved_helmet");
    /** Marca que el plugin le puso la hora fija del Sueño (para devolverla solo si fue el plugin). */
    private static final NamespacedKey BEDROCK_TIME = new NamespacedKey("caichet", "bedrock_dream_time");
    private static final byte[] NO_HELMET = new byte[0];
    /** Lo que piensa quien entra con casco (el casco queda guardado hasta que sale). */
    private static final List<String> HELMET_LINES = List.of("No siento nada en la cabeza", "¿Mi casco?",
        "Capaz lo tenga cuando salga de este maldito lugar");
    private static final long HELMET_FIRST_LINE_TICKS = 40;
    private static final long HELMET_LINE_TICKS = 50;

    static ItemStack filter() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("El Sueño").decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(FILTER_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.setItemModel(new NamespacedKey("caichet", "dream_filter"));
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HEAD)
                .cameraOverlay(Key.key("caichet", "misc/dream_filter"))
                .swappable(false)
                .dispensable(false)
                .damageOnHurt(false)
                .build());
        return item;
    }

    static boolean isFilter(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(FILTER_KEY, PersistentDataType.BYTE);
    }

    static void equip(Player player) {
        if (Bedrock.is(player)) {
            return; // Bedrock no soporta el overlay de camara
        }
        if (isFilter(player.getInventory().getHelmet())) {
            return;
        }
        ItemStack helmet = player.getInventory().getHelmet();
        boolean wearing = helmet != null && !helmet.getType().isAir();
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(SAVED_HELMET, PersistentDataType.BYTE_ARRAY)) {
            // ya hay un casco guardado (murio y reaparecio dentro del Sueño): ese no se pisa. Si ademas trae otro
            // puesto, va al inventario
            if (wearing) {
                player.getInventory().setHelmet(null);
                giveBack(player, helmet);
            }
        } else {
            pdc.set(SAVED_HELMET, PersistentDataType.BYTE_ARRAY, wearing ? helmet.serializeAsBytes() : NO_HELMET);
            if (wearing) {
                missHelmet(player);
            }
        }
        player.getInventory().setHelmet(filter());
    }

    /** Entro con casco y ya no lo siente: lo dice en tres frases, una atras de la otra. */
    private static void missHelmet(Player player) {
        Title.Times times = Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(1900), Duration.ofMillis(300));
        for (int i = 0; i < HELMET_LINES.size(); i++) {
            Component line = Component.text(HELMET_LINES.get(i), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true);
            CaiChetRuntime.tasks().later(() -> {
                // solo si sigue adentro y con el filtro puesto (si ya salio, el casco volvio)
                if (player.isOnline() && DreamWorld.isDream(player.getWorld())
                        && isFilter(player.getInventory().getHelmet())) {
                    player.showTitle(Title.title(Component.empty(), line, times));
                }
            }, HELMET_FIRST_LINE_TICKS + i * HELMET_LINE_TICKS);
        }
    }

    static void unequip(Player player) {
        resetBedrockTime(player); // al apagar el plugin o salir del Sueño
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (!isFilter(player.getInventory().getHelmet()) && !pdc.has(SAVED_HELMET, PersistentDataType.BYTE_ARRAY)) {
            return;
        }
        byte[] saved = pdc.get(SAVED_HELMET, PersistentDataType.BYTE_ARRAY);
        ItemStack helmet = null;
        if (saved != null && saved.length > 0) {
            try {
                helmet = ItemStack.deserializeBytes(saved);
            } catch (RuntimeException ignored) {
                // casco que ya no se puede leer: queda la cabeza vacia
            }
        }
        // si de alguna forma se puso otro casco encima del filtro, ese no se pierde: va al inventario
        ItemStack worn = player.getInventory().getHelmet();
        if (helmet != null && worn != null && !worn.getType().isAir() && !isFilter(worn)) {
            player.getInventory().setHelmet(null);
            giveBack(player, worn);
        }
        if (isFilter(player.getInventory().getHelmet()) || helmet != null) {
            player.getInventory().setHelmet(helmet);
        }
        removeStray(player);
        pdc.remove(SAVED_HELMET);
    }

    /** Saca cualquier filtro que haya quedado fuera de la cabeza (en la mano, el inventario o el cursor). */
    private static void removeStray(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (isFilter(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
        if (isFilter(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
        if (isFilter(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private static void giveBack(Player player, ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }

    /**
     * Con el filtro puesto no se puede poner otro casco con clic derecho (el juego los intercambiaria y el filtro
     * quedaria en la mano).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquip(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || isFilter(item) || !isFilter(event.getPlayer().getInventory().getHelmet())) {
            return;
        }
        Equippable equippable = item.getData(DataComponentTypes.EQUIPPABLE);
        if (equippable != null && equippable.slot() == EquipmentSlot.HEAD) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** Hora fija para Bedrock en el Sueño: un atardecer rosado, ya que el cielo del bioma no le llega. */
    private static final long BEDROCK_DREAM_TIME = 12700;

    private static void resetBedrockTime(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(BEDROCK_TIME, PersistentDataType.BYTE)) {
            player.resetPlayerTime();
            pdc.remove(BEDROCK_TIME);
        }
    }

    private static void sync(Player player) {
        if (DreamWorld.isDream(player.getWorld())) {
            if (Bedrock.is(player)) {
                player.setPlayerTime(BEDROCK_DREAM_TIME, false);
                player.getPersistentDataContainer().set(BEDROCK_TIME, PersistentDataType.BYTE, (byte) 1);
            }
        } else {
            resetBedrockTime(player);
        }
        if (DreamWorld.isDream(player.getWorld())) {
            equip(player);
        } else {
            unequip(player);
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        sync(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sync(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        CaiChetRuntime.tasks().next(() -> sync(player));
    }

    /**
     * Morir en el Sueño: antes de que nadie mas mire el inventario (la tumba y el totem de conservacion de BigCasares
     * lo leen directo, y vanilla lo vacia despues del evento), el filtro sale y el casco guardado vuelve a la cabeza. Asi
     * el casco corre la misma suerte que el resto del equipo: cae, va a la tumba o se conserva, pero nunca se pierde ni
     * se duplica, y el filtro nunca sale del Sueño como item.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        event.getDrops().removeIf(DreamFilter::isFilter);
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        byte[] saved = pdc.get(SAVED_HELMET, PersistentDataType.BYTE_ARRAY);
        pdc.remove(SAVED_HELMET);
        if (isFilter(player.getInventory().getHelmet())) {
            player.getInventory().setHelmet(null);
        }
        removeStray(player);
        if (saved == null || saved.length == 0) {
            return;
        }
        ItemStack helmet;
        try {
            helmet = ItemStack.deserializeBytes(saved);
        } catch (RuntimeException ignored) {
            return; // casco que ya no se puede leer
        }
        // vuelve siempre a la cabeza: si el totem o la tumba conservan el inventario, el casco va con el resto
        player.getInventory().setHelmet(helmet);
        // como en vanilla, la maldicion de desaparicion no deja caer el casco (el inventario se vacia despues)
        if (!event.getKeepInventory() && !helmet.containsEnchantment(Enchantment.VANISHING_CURSE)) {
            event.getDrops().add(helmet.clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (isFilter(event.getCurrentItem()) || isFilter(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isFilter(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
