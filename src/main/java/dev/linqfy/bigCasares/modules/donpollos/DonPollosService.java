package dev.linqfy.bigCasares.modules.donpollos;

import java.util.Locale;

public final class DonPollosService {

    static final String CURRENCY = "BREAD";

    public DonPollosResult trade(DonPollosInventoryGateway inventory, DonPollosTrade trade) {
        DonPollosItem cost = trade.cost();
        if (inventory.count(cost.material()) < cost.amount()) {
            return DonPollosResult.failure("Te falta: " + describe(cost));
        }
        if (!inventory.remove(cost.material(), cost.amount())) {
            return DonPollosResult.failure("No se pudo cobrar el intercambio.");
        }
        inventory.give(trade.result().material(), trade.result().amount());
        return DonPollosResult.success("Intercambiaste " + describe(cost) + " por " + describe(trade.result()) + ".");
    }

    public DonPollosResult buy(DonPollosInventoryGateway inventory, DonPollosOffer offer) {
        if (inventory.count(CURRENCY) < offer.price()) {
            return DonPollosResult.failure("Te faltan panes: cuesta " + offer.price() + " y tenes "
                + inventory.count(CURRENCY) + ".");
        }
        if (!inventory.remove(CURRENCY, offer.price())) {
            return DonPollosResult.failure("No se pudo cobrar la compra.");
        }
        inventory.giveProduct(offer.product(), offer.amount());
        return DonPollosResult.success("Compraste " + offer.amount() + "x "
            + offer.product().displayName().replaceAll("§.", "") + " por " + offer.price() + " panes.");
    }

    static String describe(DonPollosItem item) {
        return item.amount() + "x " + item.material().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
