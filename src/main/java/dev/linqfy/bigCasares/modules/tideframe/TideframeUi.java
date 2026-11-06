package dev.linqfy.bigCasares.modules.tideframe;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tideframe screens for BigCasares features, presentation only: the features keep their services, permissions and
 * prices. {@link #NONE} (Tideframe missing, its service gone, or the module off) shows nothing, so every caller keeps
 * its inventory, form or chat menu as the fallback. Screens, lists and variables are all named with {@link #PREFIX}.
 */
public interface TideframeUi {
    String PREFIX = "bigc_";

    /**
     * True when {@code player} can be shown {@code screenId} now: Tideframe has the screen, the player is on Java with
     * Tideframe's own pack loaded, and nothing they are doing (gliding, a vehicle, mid-air) should be frozen by a menu.
     */
    boolean canShow(Player player, String screenId);

    /**
     * Opens the screen once Tideframe's pack is ready. {@code opened} runs on the main thread with true when it opened,
     * or false (pack refused, player gone, timeout, BigCasares reloaded) so the caller can fall back. An opening
     * overtaken by a newer one for the same player answers true: the newer one decides what the player sees.
     */
    default void open(Player player, String screenId, Consumer<Boolean> opened) {
        open(player, screenId, () -> { }, opened);
    }

    /** Prepares the view during the actual open event, after the previous screen's close has finished. */
    void open(Player player, String screenId, Runnable prepare, Consumer<Boolean> opened);

    /** The player's latest navigation request, retained after it completes; deferred actions use it to detect replacements. */
    long navigationVersion(Player player);

    /** Records classic-menu navigation too, waiting for any existing camera-locked screen to close. */
    default void openClassic(Player player, Runnable open) {
        open.run();
    }

    /** A text the screen shows ({name}); always a player-global Tideframe variable. */
    void setVariable(Player player, String name, String value);

    /** The rows of a list the screen repeats; callers never send more rows than the list shows. */
    void setList(Player player, String source, List<Map<String, String>> rows);

    /** True when {@code player}'s open Tideframe menu is {@code screenId}. */
    boolean showing(Player player, String screenId);

    /** True when {@code player} has any Tideframe menu open, BigCasares' or another plugin's (it holds the camera). */
    boolean menuOpen(Player player);

    /** Closes {@code player}'s menu only when it is {@code screenId} (never another plugin's screen). */
    void close(Player player, String screenId);

    /** Sends clicks on, and the closing of, {@code screenId} to {@code handler} until the result is closed. */
    AutoCloseable handle(String screenId, TideframeScreenHandler handler);

    TideframeUi NONE = new TideframeUi() {
        @Override public boolean canShow(Player player, String screenId) { return false; }
        @Override public void open(Player player, String screenId, Runnable prepare, Consumer<Boolean> opened) { opened.accept(false); }
        @Override public long navigationVersion(Player player) { return 0; }
        @Override public void setVariable(Player player, String name, String value) { }
        @Override public void setList(Player player, String source, List<Map<String, String>> rows) { }
        @Override public boolean showing(Player player, String screenId) { return false; }
        @Override public boolean menuOpen(Player player) { return false; }
        @Override public void close(Player player, String screenId) { }
        @Override public AutoCloseable handle(String screenId, TideframeScreenHandler handler) { return () -> { }; }
    };
}
