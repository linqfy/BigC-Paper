package dev.linqfy.bigCasares.modules.tideframe;

import java.util.Objects;
import java.util.UUID;

/**
 * A click on a BigCasares Tideframe screen, without Tideframe types. {@code row} is the list row clicked (0 = first row
 * shown) or -1 for a plain element; BigCasares lists never hold more rows than they show, so it is also the data index.
 */
public record TideframeClick(UUID playerId, String screenId, String elementId, int row, boolean rightClick, boolean shiftClick) {
    public TideframeClick {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(screenId, "screenId");
        Objects.requireNonNull(elementId, "elementId");
    }
}
