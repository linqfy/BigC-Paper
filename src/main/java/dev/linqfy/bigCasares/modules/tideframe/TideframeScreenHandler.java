package dev.linqfy.bigCasares.modules.tideframe;

import org.bukkit.entity.Player;

/** What a BigCasares feature does with clicks on its own Tideframe screen, and when the player's menus close. Main thread. */
public interface TideframeScreenHandler {
    /**
     * True to cancel the click in Tideframe, so the button's own Tideframe action does not run. BigCasares' buttons carry
     * none except "close"; false leaves the click to Tideframe.
     */
    boolean click(Player player, TideframeClick click);

    /**
     * The player's whole Tideframe UI closed, whatever screen was last, and Tideframe forgot the player's variables and
     * lists: forget what the player was shown. A screen only replaced by another (even by itself, opened again) keeps
     * them, so it is not reported.
     */
    default void closed(Player player) {
    }
}
