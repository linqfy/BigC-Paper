package dev.linqfy.bigCasares.modules.geyser;

import dev.linqfy.bigCasares.modules.caichet.CaiChetBedrockBlocks;

import java.util.List;
import java.util.Map;

/** Geyser custom blocks of cai chet: the hotel's retextured copper, one Bedrock cube per texture. */
final class GeyserCaiChetBlocks {

    private GeyserCaiChetBlocks() {
    }

    static List<GeyserCustomBlockDefinition> definitions(List<CaiChetBedrockBlocks.Block> blocks) {
        return blocks.stream().map(GeyserCaiChetBlocks::definition).toList();
    }

    private static GeyserCustomBlockDefinition definition(CaiChetBedrockBlocks.Block block) {
        String key = block.terrainKey();
        return new GeyserCustomBlockDefinition(
            block.javaStates().getFirst(), block.name(), block.displayName(),
            Map.of("up", key, "down", key, "north", key, "south", key, "east", key, "west", key),
            "opaque", List.of(0, 0, 0),
            block.javaStates().subList(1, block.javaStates().size()), block.lightEmission());
    }
}
