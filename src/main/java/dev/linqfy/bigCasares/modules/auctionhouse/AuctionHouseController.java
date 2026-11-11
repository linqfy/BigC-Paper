package dev.linqfy.bigCasares.modules.auctionhouse;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Player-facing actions of the auction house shared by the command and the menus. */
final class AuctionHouseController {

    private final Server server;
    private final AuctionHouseService<ItemStack> service;
    private final AuctionMenus menus;

    AuctionHouseController(Server server, AuctionHouseService<ItemStack> service, AuctionMenus menus) {
        this.server = server;
        this.service = service;
        this.menus = menus;
    }

    AuctionHouseService<ItemStack> service() {
        return service;
    }

    AuctionMenus menus() {
        return menus;
    }

    void sell(Player player, long price) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || hand.getAmount() <= 0) {
            player.sendMessage("§cTenés que tener en la mano el item que querés vender.");
            return;
        }
        ItemStack item = hand.clone();
        switch (service.list(player.getUniqueId(), player.getName(), item, price)) {
            case LISTED -> {
                player.getInventory().setItemInMainHand(null);
                long tax = service.settings().taxOf(price);
                player.sendMessage("§aPublicaste §f" + item.getAmount() + "x " + describe(item)
                    + " §apor §f" + AuctionFormat.money(price) + "§a."
                    + (tax > 0 ? " §7(Impuesto al vender: " + AuctionFormat.money(tax) + ")" : ""));
            }
            case PRICE_TOO_LOW -> player.sendMessage("§cEl precio mínimo es "
                + AuctionFormat.money(service.settings().minPrice()) + ".");
            case PRICE_TOO_HIGH -> player.sendMessage("§cEl precio máximo es "
                + AuctionFormat.money(service.settings().maxPrice()) + ".");
            case TOO_MANY_LISTINGS -> player.sendMessage("§cYa tenés "
                + service.settings().maxListingsPerPlayer() + " subastas publicadas. Retirá alguna con /ah mis.");
        }
    }

    void buy(Player player, long listingId) {
        AuctionHouseService.BuyResult<ItemStack> result = service.buy(player.getUniqueId(), listingId);
        AuctionListing<ItemStack> listing = result.listing();
        switch (result.status()) {
            case BOUGHT -> {
                give(player, List.of(listing.item()));
                player.sendMessage("§aCompraste §f" + listing.item().getAmount() + "x " + describe(listing.item())
                    + " §apor §f" + AuctionFormat.money(listing.price()) + "§a.");
                Player seller = server.getPlayer(listing.seller());
                if (seller != null) {
                    long payout = listing.price() - service.settings().taxOf(listing.price());
                    seller.sendMessage("§a" + player.getName() + " compró tu " + describe(listing.item())
                        + ". Recibiste §f" + AuctionFormat.money(payout) + "§a.");
                }
            }
            case NOT_FOUND -> player.sendMessage("§cEsa subasta ya no está disponible.");
            case OWN_LISTING -> player.sendMessage("§cNo podés comprar tu propia subasta.");
            case INSUFFICIENT_FUNDS -> player.sendMessage("§cNo tenés suficiente dinero. Cuesta "
                + AuctionFormat.money(listing.price()) + ".");
            case PAYMENT_FAILED -> player.sendMessage("§cNo se pudo completar el pago. Intentá de nuevo.");
        }
    }

    void cancel(Player player, long listingId) {
        Optional<ItemStack> item = service.cancel(player.getUniqueId(), listingId);
        if (item.isEmpty()) {
            player.sendMessage("§cEsa subasta ya no está disponible.");
            return;
        }
        give(player, List.of(item.get()));
        player.sendMessage("§eRetiraste " + describe(item.get()) + " de la casa de subastas.");
    }

    void claim(Player player) {
        List<ItemStack> items = service.takeClaims(player.getUniqueId());
        if (items.isEmpty()) {
            player.sendMessage("§7No tenés items pendientes para reclamar.");
            return;
        }
        int kept = give(player, items);
        int delivered = items.size() - kept;
        player.sendMessage(kept == 0
            ? "§aReclamaste " + delivered + " item(s)."
            : "§eReclamaste " + delivered + " item(s); " + kept + " no entraron y siguen pendientes.");
    }

    /** Adds the items to the inventory; what does not fit goes back to the claim box. Returns how many stacks were kept. */
    int give(Player player, List<ItemStack> items) {
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack item : items) {
            leftovers.addAll(player.getInventory().addItem(item.clone()).values());
        }
        if (!leftovers.isEmpty()) {
            service.addClaims(player.getUniqueId(), leftovers);
            player.sendMessage("§eTu inventario está lleno: usá §f/ah reclamar §epara recibir el resto.");
        }
        return leftovers.size();
    }

    static String describe(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().getKey().getKey().replace('_', ' ');
    }
}
