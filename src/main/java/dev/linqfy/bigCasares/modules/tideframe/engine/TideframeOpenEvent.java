package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the player's owning thread before a Tideframe screen opens (command, API, page command,
 * redirect, open on join). Cancel it to keep the screen closed, for example to check your own requirements.
 * {@code isOverlay()} is true for passive HUD overlays (the player keeps moving).
 */
public final class TideframeOpenEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String screenId;
    private final String previousScreenId;
    private final boolean overlay;

    public TideframeOpenEvent(Player player, String screenId, String previousScreenId, boolean overlay) {
        super(player);
        this.screenId = Objects.requireNonNull(screenId);
        this.previousScreenId = previousScreenId;
        this.overlay = overlay;
    }
    public String getScreenId() { return screenId; }
    public String getPreviousScreenId() { return previousScreenId; }
    public boolean isOverlay() { return overlay; }
    /** True when another Tideframe screen is open and this one replaces it (in the same session). */
    public boolean isRedirect() { return previousScreenId != null; }
    private boolean cancelled;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
