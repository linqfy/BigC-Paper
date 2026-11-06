package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.platform.ClientPlatform;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The real integration service with Tideframe's deferred open and on-close command ordering. */
public final class TideframeTestConnection {
    private final Map<Long, List<Runnable>> tasks = new HashMap<>();
    private final Map<UUID, String> current = new HashMap<>();
    private final Map<UUID, Map<String, List<Map<String, String>>>> lists = new HashMap<>();
    private final Map<UUID, Map<String, String>> variables = new HashMap<>();
    private long tick;
    public boolean ownPack = true;
    private final TideframeUiService service = new TideframeUiService(new Bridge(), id -> ClientPlatform.JAVA,
        () -> ownPack, task -> after(1, task), failure -> { throw failure; });

    public TideframeUi ui() {
        return service;
    }

    public void after(long ticks, Runnable task) {
        tasks.computeIfAbsent(tick + Math.max(1, ticks), ignored -> new ArrayList<>()).add(task);
    }

    public void tick() {
        List<Runnable> due = tasks.remove(++tick);
        if (due != null) due.forEach(Runnable::run);
    }

    public boolean click(Player player, String screen, String element, int row) {
        return service.click(player, new TideframeClick(player.getUniqueId(), screen, element, row, false, false));
    }

    public void closeWithCommand(Player player, String screen, Runnable command) {
        command.run();
        current.remove(player.getUniqueId());
        service.closed(player, screen, true);
        // Tideframe forgets globals/lists after emitting its whole-session close event.
        lists.remove(player.getUniqueId());
        variables.remove(player.getUniqueId());
    }

    public List<Map<String, String>> list(Player player, String source) {
        return lists.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(source, List.of());
    }

    private final class Bridge implements TideframeBridge {
        @Override public Set<String> screenIds() { return Set.of("bigc_tienda", "bigc_misiones"); }
        @Override public boolean isPackLoaded(Player player) { return true; }
        @Override public Optional<String> currentScreen(Player player) { return Optional.ofNullable(current.get(player.getUniqueId())); }
        @Override public void closeScreen(Player player) {
            after(1, () -> closeWithCommand(player, current.get(player.getUniqueId()), () -> { }));
        }
        @Override public CompletableFuture<Boolean> openWhenReady(Player player, String screenId) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            after(1, () -> {
                boolean allowed = service.allowOpen(player, screenId);
                if (allowed) current.put(player.getUniqueId(), screenId);
                result.complete(screenId.equals(current.get(player.getUniqueId())));
            });
            return result;
        }
        @Override public void setVariable(Player player, String name, String value) {
            after(1, () -> variables.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(name, value));
        }
        @Override public void setList(Player player, String source, List<Map<String, String>> rows) {
            List<Map<String, String>> copy = List.copyOf(rows);
            after(1, () -> lists.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(source, copy));
        }
    }
}
