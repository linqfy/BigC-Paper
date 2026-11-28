package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Buying and selling for every shop UI (chest menus, Bedrock forms and the full-screen panel). Each call settles one
 * trade synchronously on the main thread: the catalog entry is validated, the economy result is inspected, and the
 * inventory change happens exactly once or is rolled back.
 */
public final class ShopService {

    private static final Logger LOG = Logger.getLogger(ShopService.class.getName());
    // Material.isAir() needs the server registry; these are the only air materials.
    private static final Set<Material> AIR = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);

    private final ShopItemResolver resolver;
    private final ShopEconomyGateway economy;
    private final ToIntFunction<Player> postGrantEnforcer;

    public ShopService(ShopItemResolver resolver, ShopEconomyGateway economy) {
        this(resolver, economy, player -> 0);
    }

    public ShopService(ShopItemResolver resolver, ShopEconomyGateway economy, ToIntFunction<Player> postGrantEnforcer) {
        this.resolver = resolver;
        this.economy = economy;
        this.postGrantEnforcer = postGrantEnforcer;
    }

    /** Positive, finite prices only: a negative price would turn a withdrawal into a deposit. */
    static boolean tradable(double price) {
        return Double.isFinite(price) && price > 0;
    }

    static boolean valid(ShopEntry entry, boolean sell) {
        return entry != null && entry.amount() > 0 && tradable(sell ? entry.sellPrice() : entry.buyPrice());
    }

    public ShopTransactionResult buy(ShopEntry entry) {
        if (!valid(entry, false)) {
            return ShopTransactionResult.failure("Esta operación no está disponible.");
        }
        if (!economy.has(entry.buyPrice())) {
            return ShopTransactionResult.failure("Saldo insuficiente.");
        }
        if (!economy.withdraw(entry.buyPrice())) {
            return ShopTransactionResult.failure("No se pudo cobrar la compra.");
        }
        return ShopTransactionResult.success("Compra realizada.", resolver.createStack(entry));
    }

    public ShopTransactionResult sell(List<ItemStack> contents, ShopEntry entry) {
        if (!valid(entry, true)) {
            return ShopTransactionResult.failure("Esta operación no está disponible.");
        }
        int total = contents.stream()
            .filter(stack -> resolver.matches(stack, entry))
            .mapToInt(ItemStack::getAmount)
            .sum();

        if (total < entry.amount()) {
            return ShopTransactionResult.failure("No tenes suficientes items para vender.");
        }
        if (!economy.deposit(entry.sellPrice())) {
            return ShopTransactionResult.failure("No se pudo pagar la venta.");
        }
        return ShopTransactionResult.success("Venta realizada.", null);
    }

    /** The full-screen panel's entry point: one trade for the offer the server re-read from the catalog. */
    ShopTransactionResult trade(Player player, ShopEntry entry, boolean sell) {
        if (player == null) {
            return ShopTransactionResult.failure("Esta operación no está disponible.");
        }
        return sell ? sell(player, entry) : buy(player, entry);
    }

    public ShopTransactionResult buy(Player player, ShopEntry entry) {
        if (player == null) {
            return ShopTransactionResult.success("Compra realizada.", resolver.createStack(entry), postGrantEnforcer.applyAsInt(null));
        }
        if (!valid(entry, false)) {
            return ShopTransactionResult.failure("Esta operación no está disponible.");
        }
        ItemStack purchased;
        try {
            purchased = resolver.createStack(entry);
        } catch (RuntimeException unavailable) {
            return ShopTransactionResult.failure("El producto no está disponible.");
        }
        if (purchased == null || AIR.contains(purchased.getType()) || purchased.getAmount() != entry.amount()) {
            return ShopTransactionResult.failure("El producto no está disponible.");
        }
        if (!economy.has(player, entry.buyPrice())) {
            return ShopTransactionResult.failure("Saldo insuficiente.");
        }
        boolean charged;
        try {
            charged = economy.withdraw(player, entry.buyPrice());
        } catch (RuntimeException failure) {
            // The provider may or may not have applied it; nothing is delivered and nothing is retried.
            LOG.log(Level.WARNING, "Shop withdrawal for " + player.getUniqueId() + " failed with an error; "
                + "the provider did not confirm whether " + entry.buyPrice() + " was charged.", failure);
            return ShopTransactionResult.failure("No se pudo cobrar la compra.");
        }
        if (!charged) {
            return ShopTransactionResult.failure("No se pudo cobrar la compra.");
        }

        // A refund is only safe while nothing of the purchase stays with the player: the inventory is put back
        // exactly as it was, and once any stack reached the world the sale stands (it cannot be taken back).
        ItemStack[] before = copy(player.getInventory().getStorageContents());
        int dropped = 0;
        try {
            // A full inventory follows the existing policy: what does not fit drops at the player's feet.
            var leftovers = player.getInventory().addItem(purchased.clone());
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped++;
            }
        } catch (RuntimeException deliveryFailed) {
            if (dropped > 0) {
                LOG.log(Level.SEVERE, "Shop delivery for " + player.getUniqueId() + " failed after part of " + entry.id()
                    + " was dropped; the purchase stands without refund.", deliveryFailed);
                return ShopTransactionResult.failure("La compra se entregó en parte; avisale a un administrador.");
            }
            try {
                player.getInventory().setStorageContents(before);
            } catch (RuntimeException restoreFailed) {
                LOG.log(Level.SEVERE, "Shop delivery for " + player.getUniqueId() + " failed and the inventory could not"
                    + " be restored; " + entry.id() + " is not refunded.", restoreFailed);
                return ShopTransactionResult.failure("No se pudo entregar la compra; avisale a un administrador.");
            }
            if (refund(player, entry)) {
                return ShopTransactionResult.failure("No se pudo entregar la compra; se devolvió el dinero.");
            }
            return ShopTransactionResult.failure("No se pudo entregar la compra ni devolver el dinero; avisale a un administrador.");
        }
        int overflowRemoved = postGrantEnforcer.applyAsInt(player);
        return ShopTransactionResult.success("Compraste " + entry.id() + " por " + economy.format(entry.buyPrice()) + ".", purchased, overflowRemoved);
    }

    /** True only when the provider confirmed the refund; a refused or ambiguous one is logged and never retried. */
    private boolean refund(Player player, ShopEntry entry) {
        try {
            if (economy.deposit(player, entry.buyPrice())) {
                return true;
            }
            LOG.log(Level.SEVERE, "Shop refund refused for " + player.getUniqueId() + ": " + entry.buyPrice()
                + " for " + entry.id() + " was charged without delivery.");
        } catch (RuntimeException failure) {
            LOG.log(Level.SEVERE, "Shop refund for " + player.getUniqueId() + " failed with an error; the provider did not"
                + " confirm whether " + entry.buyPrice() + " for " + entry.id() + " was given back.", failure);
        }
        return false;
    }

    public ShopTransactionResult sell(Player player, ShopEntry entry) {
        if (!valid(entry, true)) {
            return ShopTransactionResult.failure("Esta operación no está disponible.");
        }
        if (!resolver.available(entry)) {
            return ShopTransactionResult.failure("El producto no está disponible.");
        }
        ItemStack[] original = copy(player.getInventory().getStorageContents());
        ItemStack[] contents = copy(original);
        int total = 0;
        for (ItemStack stack : contents) {
            if (resolver.matches(stack, entry)) {
                total += stack.getAmount();
            }
        }

        if (total < entry.amount()) {
            return ShopTransactionResult.failure("No tenes suficientes items para vender.");
        }

        // Take the items first, so a paid sale never leaves them in the inventory, and put back the exact
        // stacks only when the economy answers that it did not pay.
        removeMatching(contents, entry);
        player.getInventory().setStorageContents(contents);
        boolean paid;
        try {
            paid = economy.deposit(player, entry.sellPrice());
        } catch (RuntimeException failure) {
            // The provider may have paid before failing: giving the items back could leave the player with both,
            // so the sale stands and is logged for an administrator to check the balance.
            LOG.log(Level.SEVERE, "Shop sale payment for " + player.getUniqueId() + " failed with an error; the provider"
                + " did not confirm whether " + entry.sellPrice() + " for " + entry.amount() + " " + entry.id()
                + " was paid. The items stay removed.", failure);
            return ShopTransactionResult.failure("No se confirmó el pago de la venta; avisale a un administrador.");
        }
        if (!paid) {
            player.getInventory().setStorageContents(original);
            return ShopTransactionResult.failure("No se pudo pagar la venta.");
        }
        return ShopTransactionResult.success("Vendiste " + entry.id() + " por " + economy.format(entry.sellPrice()) + ".", null);
    }

    private static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private void removeMatching(ItemStack[] contents, ShopEntry entry) {
        int remaining = entry.amount();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (!resolver.matches(stack, entry)) {
                continue;
            }

            int taken = Math.min(stack.getAmount(), remaining);
            int nextAmount = stack.getAmount() - taken;
            remaining -= taken;

            if (nextAmount <= 0) {
                contents[i] = null;
            } else {
                stack.setAmount(nextAmount);
                contents[i] = stack;
            }
        }
    }
}
