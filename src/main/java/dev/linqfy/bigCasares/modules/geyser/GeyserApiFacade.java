package dev.linqfy.bigCasares.modules.geyser;

import java.nio.file.Path;
import java.util.UUID;

public interface GeyserApiFacade extends AutoCloseable {
    boolean isBedrockPlayer(UUID playerId);

    GeyserCapabilities capabilities();

    default boolean sendForm(UUID playerId, BedrockShopForm form) {
        return false;
    }

    default boolean registerResourcePack(Path pack) {
        return false;
    }

    default boolean registerCustomItem(GeyserCustomItemDefinition definition) {
        return false;
    }

    default boolean registerCustomEntity(GeyserCustomEntityDefinition definition) {
        return false;
    }

    default boolean registerCustomBlock(GeyserCustomBlockDefinition definition) {
        return false;
    }

    @Override
    default void close() {
    }
}
