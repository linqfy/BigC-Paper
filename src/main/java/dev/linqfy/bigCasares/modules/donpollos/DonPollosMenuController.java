package dev.linqfy.bigCasares.modules.donpollos;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

final class DonPollosMenuController implements Listener {

    private static final int PVP_SLOT = 13;

    private static final int POLLERIA_SIZE = 45;
    private static final int POLLERIA_HEADER = 4;
    private static final int POLLERIA_WALLET = 40;
    private static final int POLLERIA_CLOSE = 44;

    private final BiFunction<Player, DonPollosTrade, DonPollosResult> tradeHandler;
    private final BiFunction<Player, DonPollosOffer, DonPollosResult> offerHandler;
    private final ToIntFunction<Player> breadCounter;
    private final DonPollosItems items;
    private final java.util.function.BiConsumer<Player, java.util.UUID> pvpHandler;
    private final Set<Player> viewers = new HashSet<>();
    /** Se llama al cerrar el menu de un Don Pollo (para la frase de despedida). */
    private java.util.function.BiConsumer<Player, DonPolloVariant> closed = (player, variant) -> { };

    DonPollosMenuController(
        BiFunction<Player, DonPollosTrade, DonPollosResult> tradeHandler,
        BiFunction<Player, DonPollosOffer, DonPollosResult> offerHandler,
        ToIntFunction<Player> breadCounter,
        DonPollosItems items,
        java.util.function.BiConsumer<Player, java.util.UUID> pvpHandler
    ) {
        this.tradeHandler = tradeHandler;
        this.offerHandler = offerHandler;
        this.breadCounter = breadCounter;
        this.items = items;
        this.pvpHandler = pvpHandler;
    }

    void openPolleria(Player player, DonPollosVariantSettings settings) {
        List<DonPollosOffer> offers = settings.offers();
        int start = (9 - (2 * offers.size() - 1)) / 2;
        int[] productSlots = new int[offers.size()];
        for (int index = 0; index < offers.size(); index++) {
            productSlots[index] = 18 + start + index * 2;
        }
        PolleriaMenuHolder holder = new PolleriaMenuHolder(settings, offers, productSlots);
        Inventory inventory = Bukkit.createInventory(holder, POLLERIA_SIZE, color("§4§lPOLLERIA §6§lAURA 67"));

        ItemStack red = item(Material.RED_STAINED_GLASS_PANE, " ", List.of());
        ItemStack white = item(Material.WHITE_STAINED_GLASS_PANE, " ", List.of());
        ItemStack inside = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack glow = item(Material.YELLOW_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < POLLERIA_SIZE; slot++) {
            boolean stripes = slot < 9 || slot >= 36;
            inventory.setItem(slot, stripes ? (slot % 2 == 0 ? red : white) : inside);
        }
        inventory.setItem(POLLERIA_HEADER, items.bucketIcon("§6§l✦ Polleria Don Pollo Aura 67 ✦", List.of(
            "§7El pollo frito con mas aura del barrio.",
            "§8Se paga con panes.")));
        for (int index = 0; index < offers.size(); index++) {
            DonPollosOffer offer = offers.get(index);
            int slot = productSlots[index];
            inventory.setItem(slot - 9, glow);
            inventory.setItem(slot, items.menuIcon(offer.product(), offer.amount(),
                offer.product().displayName() + (offer.amount() > 1 ? " §7x" + offer.amount() : ""),
                List.of("", "§ePrecio: §f" + offer.price() + " panes", "§a▶ Click para comprar")));
            inventory.setItem(slot + 9, priceTag(offer));
        }
        inventory.setItem(POLLERIA_WALLET, wallet(player));
        inventory.setItem(POLLERIA_CLOSE, item(Material.BARRIER, "§cCerrar", List.of()));
        open(player, inventory);
    }

    void openTrades(Player player, DonPollosVariantSettings settings) {
        List<DonPollosTrade> trades = settings.trades();
        int size = Math.min(54, ((trades.size() + 9) / 9) * 9);
        TradeMenuHolder holder = new TradeMenuHolder(trades, size - 1);
        Inventory inventory = Bukkit.createInventory(holder, size, color(settings.menuTitle()));
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler);
        }
        for (int index = 0; index < trades.size(); index++) {
            inventory.setItem(index, tradeItem(trades.get(index)));
        }
        inventory.setItem(holder.closeSlot(), item(Material.BARRIER, "§cCerrar", List.of()));
        open(player, inventory);
    }

    void openSalsero(Player player, DonPollosVariantSettings settings, java.util.UUID salseroId) {
        Inventory inventory = Bukkit.createInventory(new SalseroMenuHolder(salseroId), 27, color(settings.menuTitle()));
        ItemStack filler = item(Material.MAGENTA_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(PVP_SLOT, item(Material.IRON_SWORD, "§c§lPVP", List.of(
            "§d¡Batalla de baile!",
            "§7El Salsero baila y te pide pasos:",
            "§7saltar, agacharte, izquierda, derecha,",
            "§7adelante o atras. Hacelos a tiempo.",
            "",
            "§aSi le sacas toda la vida: §f¡te tira premio!",
            "§cSi perdes todas tus vidas: §fmoris.",
            "",
            "§eClick para pelear")));
        open(player, inventory);
    }

    void closeAll() {
        for (Player viewer : new ArrayList<>(viewers)) {
            viewer.closeInventory();
        }
        viewers.clear();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DonPollosMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
            || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (holder instanceof TradeMenuHolder trades) {
            if (slot == trades.closeSlot()) {
                player.closeInventory();
            } else if (slot < trades.trades().size()) {
                DonPollosResult result = tradeHandler.apply(player, trades.trades().get(slot));
                player.sendMessage((result.success() ? "§a" : "§c") + result.message());
                player.playSound(player.getLocation(),
                    result.success() ? Sound.ENTITY_CHICKEN_EGG : Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }
        if (holder instanceof PolleriaMenuHolder polleria) {
            handlePolleriaClick(player, event.getInventory(), polleria, slot);
            return;
        }
        if (holder instanceof SalseroMenuHolder salsero && slot == PVP_SLOT) {
            player.closeInventory();
            pvpHandler.accept(player, salsero.salseroId());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof DonPollosMenuHolder) {
            event.setCancelled(true);
        }
    }

    void onMenuClosed(java.util.function.BiConsumer<Player, DonPolloVariant> listener) {
        this.closed = listener;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            viewers.remove(player);
            DonPolloVariant variant = switch (event.getInventory().getHolder()) {
                case TradeMenuHolder trades -> DonPolloVariant.GORDITO;
                case SalseroMenuHolder salsero -> DonPolloVariant.SALSERO;
                case PolleriaMenuHolder polleria -> DonPolloVariant.AURA_67;
                case null, default -> null;
            };
            if (variant != null) {
                closed.accept(player, variant);
            }
        }
    }

    private void handlePolleriaClick(Player player, Inventory inventory, PolleriaMenuHolder holder, int slot) {
        if (slot == POLLERIA_CLOSE) {
            player.closeInventory();
            return;
        }
        for (int index = 0; index < holder.productSlots().length; index++) {
            int productSlot = holder.productSlots()[index];
            if (slot == productSlot || slot == productSlot + 9) {
                DonPollosResult result = offerHandler.apply(player, holder.offers().get(index));
                player.sendMessage((result.success() ? "§a" : "§c") + result.message());
                player.playSound(player.getLocation(),
                    result.success() ? Sound.ENTITY_PLAYER_BURP : Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                inventory.setItem(POLLERIA_WALLET, wallet(player));
                return;
            }
        }
    }

    private ItemStack priceTag(DonPollosOffer offer) {
        ItemStack tag = item(Material.BREAD, "§ePrecio: §f" + offer.price() + " panes",
            List.of("§a▶ Click para comprar"));
        tag.setAmount(offer.price());
        return tag;
    }

    private ItemStack wallet(Player player) {
        int bread = breadCounter.applyAsInt(player);
        ItemStack wallet = item(Material.BREAD, "§eTus panes: §f" + bread,
            List.of(bread == 0 ? "§cNo tenes panes." : "§7Usalos para comprar."));
        wallet.setAmount(Math.max(1, Math.min(64, bread)));
        return wallet;
    }

    private void open(Player player, Inventory inventory) {
        viewers.add(player);
        player.openInventory(inventory);
    }

    private static ItemStack tradeItem(DonPollosTrade trade) {
        DonPollosItem result = trade.result();
        ItemStack stack = item(Material.valueOf(result.material()),
            "§a" + DonPollosService.describe(result),
            List.of("§7Cuesta: §e" + DonPollosService.describe(trade.cost()), "§eClick para intercambiar"));
        stack.setAmount(Math.min(result.amount(), stack.getMaxStackSize()));
        return stack;
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(lore.stream().map(DonPollosMenuController::color).toList());
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private sealed interface DonPollosMenuHolder extends InventoryHolder
        permits TradeMenuHolder, SalseroMenuHolder, PolleriaMenuHolder {
        @Override
        default Inventory getInventory() {
            return null;
        }
    }

    private record TradeMenuHolder(List<DonPollosTrade> trades, int closeSlot) implements DonPollosMenuHolder {
    }

    private record SalseroMenuHolder(java.util.UUID salseroId) implements DonPollosMenuHolder {
    }

    private record PolleriaMenuHolder(DonPollosVariantSettings settings, List<DonPollosOffer> offers,
                                      int[] productSlots) implements DonPollosMenuHolder {
    }
}
