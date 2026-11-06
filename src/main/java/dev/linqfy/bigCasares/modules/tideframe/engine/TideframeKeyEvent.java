package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the player's owning thread when they press a key while a Tideframe menu is open: W, A, S, D, SPACE,
 * Q, F, L, 1-9, SCROLL_UP, SCROLL_DOWN, each with a SHIFT_ prefix while sneaking. Cancel it to stop the page's
 * keybind (and close-on-F) for this press.
 */
public final class TideframeKeyEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String screenId;
    private final String key;
    private final String hoveredId;

    public TideframeKeyEvent(Player player, String screenId, String key, String hoveredId) {
        super(player);
        this.screenId = Objects.requireNonNull(screenId);
        this.key = Objects.requireNonNull(key);
        this.hoveredId = hoveredId;
    }
    public String getScreenId() { return screenId; }
    public String getKey() { return key; }
    public String getHoveredId() { return hoveredId; }
    /** The key without the SHIFT_ prefix. */
    public String getBaseKey() { return key.startsWith("SHIFT_") ? key.substring(6) : key; }
    public boolean isShift() { return key.startsWith("SHIFT_"); }
    /** True when the cursor was over an element (or list row) when the key was pressed. */
    public boolean hasHovered() { return hoveredId != null; }
    private boolean cancelled;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
