package dev.linqfy.bigCasares.modules.forex;

import java.util.Map;
import java.util.UUID;

/** A creator ran off with {@code stolen}; {@code refunds} is the leftover split among the holders. */
public record RugPull(String ticker, String name, UUID creator, long stolen, double fraction,
                      Map<UUID, Long> refunds) {
}
