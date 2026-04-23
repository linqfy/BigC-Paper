package dev.linqfy.bigCasares.modules.airdrop;

import java.util.Objects;

public record AirdropLootEntry(String materialName, int amount, boolean enchanted) {

    public AirdropLootEntry {
        Objects.requireNonNull(materialName, "materialName");
        if (materialName.isBlank()) {
            throw new IllegalArgumentException("materialName cannot be blank");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    public static AirdropLootEntry of(String materialName, int amount) {
        return new AirdropLootEntry(materialName, amount, false);
    }

    public static AirdropLootEntry enchanted(String materialName, int amount) {
        return new AirdropLootEntry(materialName, amount, true);
    }
}
