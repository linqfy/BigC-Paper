package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * Fired on the player's owning thread after a Tideframe screen closed or was replaced, with the reason.
 * Save per-player state here (variables, lists): they are cleared when the whole UI closes.
 */
public final class TideframeCloseEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String screenId;
    private final Reason reason;

    public TideframeCloseEvent(Player player, String screenId, Reason reason) {
        super(player);
        this.screenId = Objects.requireNonNull(screenId);
        this.reason = Objects.requireNonNull(reason);
    }
    public String getScreenId() { return screenId; }
    public Reason getReason() { return reason; }
    /** Why the screen went away. */
    public enum Reason {
        /** A close action, the X button or the API. */ ACTION,
        /** Shift (sneak), when the page closes on sneak. */ SNEAK,
        /** F or Shift+F, when the page closes on them. */ KEY,
        /** The player took damage and the page closes on damage. */ DAMAGE,
        /** The player died. */ DEATH,
        /** The player left the server. */ QUIT,
        /** Another screen replaced this one (redirect, back, command). */ REPLACED,
        /** /tideframe close, a reload or the plugin stopping. */ COMMAND,
        /** The player can no longer use it (permission, world change, vehicle, pack declined). */ INVALID
    }
    /** True when no Tideframe screen stays open (not replaced by another). */
    public boolean isWholeSession() { return reason != Reason.REPLACED; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
