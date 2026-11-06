package dev.linqfy.bigCasares.modules.tideframe;

import org.bukkit.entity.Player;

/** Tideframe's open, click and close events as BigCasares receives them (see {@link TideframeEventRelay}). */
public interface TideframeEventSink {
    /** False when Tideframe must not open {@code screenId} for {@code player}: the opening is then cancelled. */
    boolean allowOpen(Player player, String screenId);

    /** True to cancel the click in Tideframe: the button's own Tideframe action then does not run. */
    boolean click(Player player, TideframeClick click);

    /** The player's Tideframe menu {@code screenId} closed; {@code wholeSession}: not replaced by another menu. */
    void closed(Player player, String screenId, boolean wholeSession);
}
