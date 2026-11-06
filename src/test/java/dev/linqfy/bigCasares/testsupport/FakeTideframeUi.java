package dev.linqfy.bigCasares.testsupport;

import dev.linqfy.bigCasares.modules.tideframe.TideframeClick;
import dev.linqfy.bigCasares.modules.tideframe.TideframeScreenHandler;
import dev.linqfy.bigCasares.modules.tideframe.TideframeUi;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A Tideframe that records what features send it: the lists and texts per player, the screens opened and closed, and
 * who handles each screen's clicks. {@link #click} plays a click as Tideframe's event would.
 */
public final class FakeTideframeUi implements TideframeUi {
    public boolean showable = true;
    /** What the next opening answers (null: it is still pending; {@link #finishOpen} answers later). */
    public Boolean opens = true;
    public final Map<UUID, Map<String, List<Map<String, String>>>> lists = new HashMap<>();
    public final Map<UUID, Map<String, String>> variables = new HashMap<>();
    public final List<String> opened = new ArrayList<>();
    public final List<String> closed = new ArrayList<>();
    public final Map<String, TideframeScreenHandler> handlers = new HashMap<>();
    private final Map<UUID, String> current = new HashMap<>();
    private final Map<UUID, Long> navigation = new HashMap<>();
    private Consumer<Boolean> pending;

    @Override
    public boolean canShow(Player player, String screenId) {
        return showable;
    }

    @Override
    public void open(Player player, String screenId, Runnable prepare, Consumer<Boolean> callback) {
        opened.add(screenId);
        navigation.merge(player.getUniqueId(), 1L, Long::sum);
        if (opens == null) {
            pending = ok -> {
                if (ok) {
                    prepare.run();
                    current.put(player.getUniqueId(), screenId);
                }
                callback.accept(ok);
            };
            return;
        }
        if (opens) {
            prepare.run();
            current.put(player.getUniqueId(), screenId);
        }
        callback.accept(opens);
    }

    @Override
    public long navigationVersion(Player player) {
        return navigation.getOrDefault(player.getUniqueId(), 0L);
    }

    public void finishOpen(boolean ok) {
        Consumer<Boolean> callback = pending;
        pending = null;
        callback.accept(ok);
    }

    @Override
    public void setVariable(Player player, String name, String value) {
        variables.computeIfAbsent(player.getUniqueId(), id -> new LinkedHashMap<>()).put(name, value);
    }

    @Override
    public void setList(Player player, String source, List<Map<String, String>> rows) {
        lists.computeIfAbsent(player.getUniqueId(), id -> new LinkedHashMap<>()).put(source, List.copyOf(rows));
    }

    @Override
    public boolean showing(Player player, String screenId) {
        return screenId.equals(current.get(player.getUniqueId()));
    }

    @Override
    public boolean menuOpen(Player player) {
        return current.containsKey(player.getUniqueId());
    }

    /** Another Tideframe menu is now up for the player (another plugin's, or one a "back" reopened). */
    public void showOther(Player player, String screenId) {
        current.put(player.getUniqueId(), screenId);
    }

    @Override
    public void close(Player player, String screenId) {
        if (showing(player, screenId)) {
            current.remove(player.getUniqueId());
            closed.add(screenId);
            TideframeScreenHandler handler = handlers.get(screenId);
            if (handler != null) handler.closed(player);
        }
    }

    @Override
    public AutoCloseable handle(String screenId, TideframeScreenHandler handler) {
        handlers.put(screenId, handler);
        return () -> handlers.remove(screenId, handler);
    }

    public List<Map<String, String>> list(Player player, String source) {
        return lists.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(source, List.of());
    }

    public String variable(Player player, String name) {
        return variables.getOrDefault(player.getUniqueId(), Map.of()).get(name);
    }

    /** Plays a click as Tideframe's event: true when the feature cancels it (Tideframe would run no action of it). */
    public boolean click(Player player, String screenId, String elementId, int row, boolean right) {
        return click(player, screenId, elementId, row, right, false);
    }

    /** The same, with the player sneaking ({@code shift}) or not. */
    public boolean click(Player player, String screenId, String elementId, int row, boolean right, boolean shift) {
        TideframeScreenHandler handler = handlers.get(screenId);
        return handler != null && handler.click(player, new TideframeClick(player.getUniqueId(), screenId, elementId, row, right, shift));
    }

    /** The player closed the menu some other way (the X, Escape): Tideframe's close event for the whole UI. */
    public void closedByPlayer(Player player, String screenId) {
        current.remove(player.getUniqueId());
        TideframeScreenHandler handler = handlers.get(screenId);
        if (handler != null) handler.closed(player);
    }
}
