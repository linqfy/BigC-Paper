package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * config.yml editor.reopen-after-reload: a reload (by command, or by publishing from the editor) closes every menu;
 * each player gets the menu they had open back as soon as they load the new pack. Boss prompts are not reopened:
 * they belong to a running challenge, which the reload ended.
 */
final class ReopenAfterReload {
    private final Map<UUID, String> waiting = new ConcurrentHashMap<>();

    /** Before the reload closes the menus: {@code screen} was open for {@code player} (null: nothing to reopen). */
    void remember(UUID player, String screen) {
        if (screen == null || screen.startsWith("challenge_")) waiting.remove(player);
        else waiting.put(player, screen);
    }
    /** The new pack loaded: the screen to open again, once; null when there is none. */
    String take(UUID player) { return waiting.remove(player); }
    /** Declined pack, quit or a menu opened meanwhile: nothing to reopen. */
    void forget(UUID player) { waiting.remove(player); }
    void clear() { waiting.clear(); }
}
