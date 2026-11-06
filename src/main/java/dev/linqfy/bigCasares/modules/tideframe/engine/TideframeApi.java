package dev.linqfy.bigCasares.modules.tideframe.engine;

import dev.linqfy.bigCasares.modules.tideframe.engine.core.demo.RouletteSpin;
import java.math.BigDecimal;
import org.bukkit.entity.Player;

/** The engine's operations, implemented by {@link TideframeEngine} and used by the tideframe-ui module.
 * Calls schedule player-owned updates. Values are supplied by your code, not an economy query. */
public interface TideframeApi {
    void showSequence(Player player);
    void showRoulette(Player player,RouletteSpin.Color chosenColor);
    void showOverlay(Player player,String screenId);
    /** Adds a HUD page over the ones the player sees (a menu stays open). Fires TideframeOpenEvent; no-op when already shown. */
    void addOverlay(Player player,String screenId);
    /** Takes a HUD page away (the others stay). */
    void removeOverlay(Player player,String screenId);
    /**
     * Shows a HUD page as a toast for {@code seconds} (1-60), after the toasts already waiting; {@code values} become its
     * variables (its texts can say {titulo}). It slides in and out with the page's open and close animation.
     */
    void showToast(Player player,String screenId,double seconds,java.util.Map<String,String> values);
    /** Ids of the HUD pages the player sees now: the main one first, then the ones over it. */
    java.util.List<String> overlays(Player player);
    void setBalance(Player player,BigDecimal amount);
    void setBoss(Player player,String title,double health,double maximum);
    void closeViews(Player player);
    /**
     * Boss key prompts over the world: the player must press the key shown (W, A, S, D, SPACE or CLICK) before
     * the white timer bar empties. With a {@code boss} entity, hits damage it and misses hurt the player; the bar
     * follows its real health. {@code boss} and {@code title} may be null (practice boss / entity name).
     * Listen for {@link TideframeChallengeEndEvent} to reward the player.
     */
    void startBossChallenge(Player player,org.bukkit.entity.LivingEntity boss,String title);
    void stopBossChallenge(Player player);

    // ----- Screens (schema 9 logic). Calls are scheduled onto the player's thread unless they return a value. -----
    /** Opens a screen (menu or overlay) for the player. False when no screen has that id. Fires TideframeOpenEvent. */
    boolean openScreen(Player player,String screenId);
    /** Closes the player's menu (TideframeCloseEvent reason ACTION). */
    void closeScreen(Player player);
    /** Id of the menu the player has open. */
    java.util.Optional<String> currentScreen(Player player);
    default boolean isOpen(Player player) { return currentScreen(player).isPresent(); }
    /** Ids of every installed screen. */
    java.util.Set<String> screenIds();
    /**
     * Runs actions in the player's open screen exactly as if the page defined them, written as page maps:
     * {@code Map.of("type","message","value","<green>Hi")}, {@code Map.of("type","delay","value","1s")}...
     * Throws IllegalArgumentException right away when an action is invalid.
     */
    void runActions(Player player,java.util.List<java.util.Map<String,Object>> actions);
    /** One action of a type that takes a single value ("message", "sound", "redirect", "console"...). */
    void runAction(Player player,String type,Object value);
    /** Runs an element's click actions, as if the player left-clicked it (no click event). Accepts list rows ("servers#2"). */
    void clickElement(Player player,String elementId);
    /** Simulates a key (W, A, S, D, SPACE, Q, F, L, 1-9, SCROLL_UP, SCROLL_DOWN): fires TideframeKeyEvent and runs the keybind. */
    void pressKey(Player player,String key);
    /** A variable of the open screen (local first, then the player's global ones). */
    java.util.Optional<String> getVariable(Player player,String name);
    /** Sets a variable ("*name" forces the global one); live text and lists update at once. */
    void setVariable(Player player,String name,String value);
    /**
     * Data for list elements whose {@code list.source} is {@code source}: one map per row. Each row shows {item}
     * (its "name", else its first value), {index} and {item.field}. Kept while the player is online.
     */
    void setList(Player player,String source,java.util.List<java.util.Map<String,String>> rows);
    void clearList(Player player,String source);
    /** Shows or hides an element that changes at run time (hidden, visible-if, or targeted by actions). */
    void setElementVisible(Player player,String elementId,boolean visible);
    /** Moves such an element sideways by {@code dx} logical pixels (0 puts it back). */
    void setElementOffset(Player player,String elementId,int dx);
    /** Tints such an element (#RRGGBB; null removes the tint). */
    void setElementTint(Player player,String elementId,String color);
    /** Plays an element's keyframes (or restarts its PNG frames) from the start, like the {@code animation} action. */
    void playElementAnimation(Player player,String elementId);

    // ----- Screens built in code -----
    /** Starts building a screen in code: see {@link ScreenBuilder}. */
    default ScreenBuilder buildScreen(String id) { return ScreenBuilder.create(id); }
    /**
     * Installs or replaces a screen: it is saved as plugins/BigCasares/tideframe/engine/screens/&lt;id&gt;.json (so it stays after a
     * restart and opens in the editor) and baked into a new resource pack that every online player is offered;
     * open menus close, as with /tideframe reload. The future completes when the new pack is served (exceptionally
     * when the screen cannot be saved or baked). Open it for a player with {@link #openWhenReady}.
     */
    java.util.concurrent.CompletableFuture<Void> registerScreen(dev.linqfy.bigCasares.modules.tideframe.engine.core.Screen screen);
    /** Deletes plugins/BigCasares/tideframe/engine/screens/&lt;id&gt;.json and rebakes (a built-in demo returns with its defaults); false when there was no file. */
    java.util.concurrent.CompletableFuture<Boolean> unregisterScreen(String screenId);
    /** True once the player has loaded the current Tideframe resource pack (screens can open). */
    boolean isPackLoaded(Player player);
    /**
     * Opens the screen as soon as the player has loaded the current pack (at once when it already is). Completes
     * true when it opened, false when the screen does not exist, the player left or 2 minutes passed first.
     */
    java.util.concurrent.CompletableFuture<Boolean> openWhenReady(Player player,String screenId);
}
