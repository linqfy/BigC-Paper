package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the player's owning thread when they confirm text typed into a Tideframe input dialog, after the
 * filter accepted it and before it is saved to the variable. Cancel it to reject the text (on-cancel actions run).
 */
public final class TideframeInputEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String screenId;
    private final String variable;
    private final String text;

    public TideframeInputEvent(Player player, String screenId, String variable, String text) {
        super(player);
        this.screenId = Objects.requireNonNull(screenId);
        this.variable = Objects.requireNonNull(variable);
        this.text = Objects.requireNonNull(text);
    }
    public String getScreenId() { return screenId; }
    public String getVariable() { return variable; }
    public String getText() { return text; }
    private boolean cancelled;
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
