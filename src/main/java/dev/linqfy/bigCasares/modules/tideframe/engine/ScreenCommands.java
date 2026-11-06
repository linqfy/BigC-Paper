package dev.linqfy.bigCasares.modules.tideframe.engine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Commands that open screens (screen logic {@code command: shop} makes /shop). Registered in the server's command
 * map under the plugin's prefix too (/tideframe:shop), replaced on every reload, and announced to online players.
 * Global thread only.
 */
final class ScreenCommands {
    private final Plugin plugin;
    private final SchedulerBridge scheduler;
    private final BiConsumer<Player, String> open;
    private final List<Command> registered = new ArrayList<>();

    ScreenCommands(Plugin plugin, SchedulerBridge scheduler, BiConsumer<Player, String> open) { this.plugin = plugin; this.scheduler = scheduler; this.open = open; }

    /** {@code commands}: command name → screen id. Returns the names that could not be registered (taken by another plugin). */
    List<String> replace(Map<String, String> commands) {
        var map = commandMap();
        if (map == null) return List.copyOf(commands.keySet());
        unregister(map);
        var refused = new ArrayList<String>();
        commands.forEach((name, screen) -> {
            var command = new Command(name, "Opens the Tideframe screen " + screen, "/" + name, List.of()) {
                @Override public boolean execute(CommandSender sender, String label, String[] args) {
                    if (!(sender instanceof Player player)) { sender.sendMessage("Only players can open screens."); return true; }
                    open.accept(player, screen);
                    return true;
                }
            };
            command.setPermission("tideframe.use");
            String prefix = plugin.getName().toLowerCase(java.util.Locale.ROOT);
            // register returns false when the plain label was taken and only "prefix:name" was registered.
            if (!map.register(name, prefix, command)) refused.add(name);
            registered.add(command);
        });
        sync();
        return refused;
    }
    void clear() { var map = commandMap(); if (map != null) unregister(map); sync(); }
    private void unregister(CommandMap map) {
        if (registered.isEmpty()) return;
        var known = knownCommands(map);
        for (var command : registered) {
            command.unregister(map);
            if (known != null) known.values().removeIf(value -> value == command);
        }
        registered.clear();
    }
    private static CommandMap commandMap() {
        try { return (CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer()); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Command> knownCommands(CommandMap map) {
        try { return (Map<String, Command>) map.getClass().getMethod("getKnownCommands").invoke(map); }
        catch (ReflectiveOperationException | RuntimeException unavailable) { return null; }
    }
    /** Brigadier trees on the server and on every client must learn about added/removed commands. */
    private void sync() {
        try {
            Method syncCommands = Bukkit.getServer().getClass().getMethod("syncCommands");
            syncCommands.invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException | RuntimeException unavailable) { /* clients see them after rejoining */ }
        for (var player : Bukkit.getOnlinePlayers()) scheduler.entity(player, () -> {
            try { player.updateCommands(); } catch (RuntimeException ignored) { }
        });
    }
}
