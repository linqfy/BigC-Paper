package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the player's owning thread when the cursor moves onto or off an element (or list row "id#2").
 * {@code getPreviousId()} or {@code getCurrentId()} is null on empty space.
 */
public final class TideframeHoverEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String screenId;
    private final String previousId;
    private final String currentId;

    public TideframeHoverEvent(Player player, String screenId, String previousId, String currentId) {
        super(player);
        this.screenId = Objects.requireNonNull(screenId);
        this.previousId = previousId;
        this.currentId = currentId;
    }
    public String getScreenId() { return screenId; }
    public String getPreviousId() { return previousId; }
    public String getCurrentId() { return currentId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
