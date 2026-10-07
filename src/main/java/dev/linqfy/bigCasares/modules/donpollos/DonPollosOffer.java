package dev.linqfy.bigCasares.modules.donpollos;

import java.util.Objects;

public record DonPollosOffer(DonPollosProduct product, int amount, int price) {

    public DonPollosOffer {
        Objects.requireNonNull(product, "product");
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("amount must be between 1 and 64: " + amount);
        }
        if (price < 1 || price > 64) {
            throw new IllegalArgumentException("price must be between 1 and 64: " + price);
        }
    }
}
