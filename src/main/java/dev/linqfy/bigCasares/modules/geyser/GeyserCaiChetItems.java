package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.modules.caichet.CaiChetBedrockItems;

import java.util.List;

/** Geyser custom items of cai chet: one Bedrock item per Java item model, on top of the same vanilla item. */
final class GeyserCaiChetItems {

    private GeyserCaiChetItems() {
    }

    static List<GeyserCustomItemDefinition> definitions(List<CaiChetBedrockItems.Item> items) {
        return items.stream()
            .map(item -> new GeyserCustomItemDefinition(
                item.javaBaseItem(), item.itemModel(), item.itemModel(), item.icon(), item.displayName(),
                consumption(item.drinkMillis())))
            .toList();
    }

    private static GeyserItemConsumption consumption(long drinkMillis) {
        return drinkMillis > 0
            ? new GeyserItemConsumption(drinkMillis / 1000f, GeyserItemConsumption.Animation.DRINK)
            : null;
    }
}
