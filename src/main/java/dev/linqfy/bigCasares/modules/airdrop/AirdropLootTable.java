package dev.linqfy.bigCasares.modules.airdrop;

import java.util.List;

public final class AirdropLootTable {

    private AirdropLootTable() {}

    public static List<AirdropLootEntry> forType(AirdropType type) {
        return switch (type) {
            case HE -> List.of(
                    AirdropLootEntry.of("TNT", 8),
                    AirdropLootEntry.of("GUNPOWDER", 32),
                    AirdropLootEntry.of("FLINT_AND_STEEL", 1),
                    AirdropLootEntry.of("FIRE_CHARGE", 16)
            );
            case LUXURY -> List.of(
                    AirdropLootEntry.of("DIAMOND", 16),
                    AirdropLootEntry.of("NETHERITE_INGOT", 4),
                    AirdropLootEntry.of("GOLD_INGOT", 32),
                    AirdropLootEntry.of("EMERALD", 24)
            );
            case ENCHANT -> List.of(
                    AirdropLootEntry.enchanted("ENCHANTED_BOOK", 1),
                    AirdropLootEntry.enchanted("ENCHANTED_BOOK", 1),
                    AirdropLootEntry.enchanted("ENCHANTED_BOOK", 1),
                    AirdropLootEntry.of("LAPIS_LAZULI", 64),
                    AirdropLootEntry.of("EXPERIENCE_BOTTLE", 32)
            );
        };
    }
}
