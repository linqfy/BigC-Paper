package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the player's owning thread when a carried (draggable) element is dropped on another element.
 * Cancel it to stop the target's on-drop actions; the carried element returns either way.
 */
public final class TideframeDropEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String screenId;
    private final String draggedId;
    private final String targetId;

    public TideframeDropEvent(Player player, String screenId, String draggedId, String targetId) {
        super(player);
        this.screenId = Objects.requireNonNull(screenId);
        this.draggedId = Objects.requireNonNull(draggedId);
        this.targetId = Objects.requireNonNull(targetId);
    }
    public String getScreenId() { return screenId; }
    public String getDraggedId() { return draggedId; }
    public String getTargetId() { return targetId; }
    private boolean cancelled;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
