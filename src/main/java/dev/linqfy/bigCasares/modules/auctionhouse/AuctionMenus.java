package dev.linqfy.bigCasares.modules.auctionhouse;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Builds the chest menus of the auction house. */
final class AuctionMenus {

    static final int PAGE_SIZE = 45;
    static final int SLOT_PREVIOUS = 45;
    static final int SLOT_MINE = 48;
    static final int SLOT_INFO = 49;
    static final int SLOT_CLAIMS = 50;
    static final int SLOT_NEXT = 53;
    static final int SLOT_CONFIRM = 11;
    static final int SLOT_PREVIEW = 13;
    static final int SLOT_CANCEL = 15;

    private final AuctionHouseService<ItemStack> service;
    private final AuctionBank bank;
    private final Clock clock;

    AuctionMenus(AuctionHouseService<ItemStack> service, AuctionBank bank, Clock clock) {
        this.service = service;
        this.bank = bank;
        this.clock = clock;
    }

    void openBrowse(Player player, int requestedPage) {
        List<AuctionListing<ItemStack>> listings = service.active();
        int pages = Math.max(1, (listings.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        AuctionMenuHolder holder = new AuctionMenuHolder(AuctionMenuHolder.View.BROWSE, page);
        Inventory inventory = Bukkit.createInventory(holder, 54, "Subastas (" + (page + 1) + "/" + pages + ")");
        holder.inventory(inventory);
        fillListings(holder, inventory, listings, page, player,
            "§eClick para comprar", "§8Es tu subasta (/ah mis para retirarla)");
        fillFooter(inventory);
        if (page > 0) {
            inventory.setItem(SLOT_PREVIOUS, button(Material.ARROW, "§fPágina anterior"));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, button(Material.ARROW, "§fPágina siguiente"));
        }
        inventory.setItem(SLOT_MINE, button(Material.CHEST, "§bMis subastas",
            "§7Publicadas: §f" + service.listingsOf(player.getUniqueId()).size()
                + "§7/§f" + service.settings().maxListingsPerPlayer()));
        inventory.setItem(SLOT_INFO, button(Material.GOLD_INGOT, "§6Casa de subastas",
            "§7Saldo: §a" + AuctionFormat.money(bank.balance(player.getUniqueId())),
            "§7Vender: §f/ah vender <precio>",
            "§7Impuesto de venta: §f" + service.settings().taxPercent() + "%"));
        inventory.setItem(SLOT_CLAIMS, claimsButton(player));
        player.openInventory(inventory);
    }

    void openMine(Player player) {
        AuctionMenuHolder holder = new AuctionMenuHolder(AuctionMenuHolder.View.MINE, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, "Mis subastas");
        holder.inventory(inventory);
        fillListings(holder, inventory, service.listingsOf(player.getUniqueId()), 0, player,
            "§cClick para retirar", "§cClick para retirar");
        fillFooter(inventory);
        inventory.setItem(SLOT_PREVIOUS, button(Material.ARROW, "§fVolver"));
        inventory.setItem(SLOT_CLAIMS, claimsButton(player));
        player.openInventory(inventory);
    }

    void openConfirm(Player player, AuctionListing<ItemStack> listing) {
        AuctionMenuHolder holder = new AuctionMenuHolder(AuctionMenuHolder.View.CONFIRM, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, "Confirmar compra");
        holder.inventory(inventory);
        holder.bind(SLOT_CONFIRM, listing.id());
        inventory.setItem(SLOT_CONFIRM, button(Material.LIME_STAINED_GLASS_PANE, "§aComprar",
            "§7Precio: §a" + AuctionFormat.money(listing.price())));
        inventory.setItem(SLOT_PREVIEW, decorate(listing, player, null, null));
        inventory.setItem(SLOT_CANCEL, button(Material.RED_STAINED_GLASS_PANE, "§cCancelar"));
        player.openInventory(inventory);
    }

    private void fillListings(
        AuctionMenuHolder holder,
        Inventory inventory,
        List<AuctionListing<ItemStack>> listings,
        int page,
        Player viewer,
        String action,
        String ownAction
    ) {
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < listings.size(); slot++) {
            AuctionListing<ItemStack> listing = listings.get(start + slot);
            inventory.setItem(slot, decorate(listing, viewer, action, ownAction));
            holder.bind(slot, listing.id());
        }
    }

    private ItemStack decorate(AuctionListing<ItemStack> listing, Player viewer, String action, String ownAction) {
        ItemStack display = listing.item().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) {
            return display;
        }
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.add("");
        lore.add("§7Precio: §a" + AuctionFormat.money(listing.price()));
        lore.add("§7Vendedor: §f" + listing.sellerName());
        lore.add("§7Expira en: §f" + AuctionFormat.remaining(Duration.between(clock.instant(), listing.expiresAt())));
        if (action != null) {
            lore.add(listing.seller().equals(viewer.getUniqueId()) ? ownAction : action);
        }
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack claimsButton(Player player) {
        int waiting = service.claims(player.getUniqueId()).size();
        return button(Material.ENDER_CHEST, "§dReclamar items",
            waiting == 0 ? "§7No tenés items pendientes." : "§7Pendientes: §f" + waiting,
            "§7Items vencidos o retirados que no entraron en tu inventario.");
    }

    private static void fillFooter(Inventory inventory) {
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = PAGE_SIZE; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
