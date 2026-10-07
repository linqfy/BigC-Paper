package dev.linqfy.bigCasares.modules.donpollos;

import java.util.Locale;

public record DonPollosItem(String material, int amount) {

    public DonPollosItem {
        if (material == null || material.isBlank()) {
            throw new IllegalArgumentException("material cannot be blank");
        }
        material = material.strip().toUpperCase(Locale.ROOT);
        if (amount < 1 || amount > 640) {
            throw new IllegalArgumentException("amount must be between 1 and 640: " + amount);
        }
    }
}
