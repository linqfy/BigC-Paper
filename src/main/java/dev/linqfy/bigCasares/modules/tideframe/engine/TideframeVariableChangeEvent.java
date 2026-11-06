package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the player's owning thread after a variable of an open Tideframe screen changed value: an action, a text
 * input, the API or an on-change list set it. {@code getOldValue()} is null when the variable did not exist before,
 * {@code getNewValue()} is null when it is gone. A global variable shared by the menu and a HUD fires once.
 */
public final class TideframeVariableChangeEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String screenId, name, oldValue, newValue;

    public TideframeVariableChangeEvent(Player player, String screenId, String name, String oldValue, String newValue) {
        super(player);
        this.screenId = Objects.requireNonNull(screenId);
        this.name = Objects.requireNonNull(name);
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    /** The screen whose actions (or API call) changed the variable. */
    public String getScreenId() { return screenId; }
    /** The variable's name, without the * or $ global marker. */
    public String getName() { return name; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
