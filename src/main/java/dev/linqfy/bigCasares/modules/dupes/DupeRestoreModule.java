package dev.linqfy.bigCasares.modules.dupes;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

public final class DupeRestoreModule implements PluginModule, CommandExecutor, TabCompleter {
    public static final String MODULE_ID = "dupe-restore";
    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private RuntimeRegistrationScope activeStrings;
    private boolean stringsEnabled;

    public DupeRestoreModule(BigCasares plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override public String getId() { return MODULE_ID; }

    @Override public void onEnable() {
        compatibilityScope = new RuntimeRegistrationScope();
        onEnable(compatibilityScope);
    }

    @Override public void onEnable(RuntimeRegistrationScope scope) {
        var command = plugin.getCommand("duperestore");
        if (command == null) throw new IllegalStateException("Falta el comando duperestore.");
        scope.register("dupe-string-session", this::closeStrings);
        var registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.bindCommand("dupe-command", command, this, this);
        configureStrings(DupeRestoreSettings.load(plugin.getConfig()));
        plugin.getLogger().info("[DupeRestore] Hilo=" + stringsEnabled + ". " + nativeStatus());
    }

    private void configureStrings(DupeRestoreSettings settings) {
        RuntimeRegistrationScope candidate = new RuntimeRegistrationScope();
        try {
            if (settings.strings()) {
                var registrations = new BukkitRuntimeRegistrations(plugin, candidate);
                StringDupeListener listener = registrations.registerListener("dupe-string-listener",
                        new StringDupeListener(registrations));
                registrations.ownCloseable("dupe-string-state", listener);
            }
        } catch (RuntimeException | Error failure) {
            candidate.close();
            throw failure;
        }
        closeStrings();
        activeStrings = candidate;
        stringsEnabled = settings.strings();
    }

    private void closeStrings() {
        if (activeStrings != null) activeStrings.close();
        activeStrings = null;
        stringsEnabled = false;
    }

    private String nativeStatus() {
        try {
            FileConfiguration snapshot = plugin.getServer().spigot().getPaperConfig();
            PaperOptions options = PaperOptions.read(snapshot::get);
            return "Paper (archivo): TNT/alfombras/raíles=" + options.pistons()
                    + ", ganchos=" + options.hooks() + ". Reiniciá Paper tras editar unsupported-settings.";
        } catch (RuntimeException error) {
            return "Revisá allow-piston-duplication y skip-tripwire-hook-placement-validation en paper-global.yml.";
        }
    }

    @Override public void onDisable() {
        closeStrings();
        if (compatibilityScope != null) compatibilityScope.close();
        compatibilityScope = null;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("duperestore.admin")) return true;
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("status"))) {
            sender.sendMessage("DupeRestore: hilo=" + stringsEnabled);
            sender.sendMessage(nativeStatus());
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            try {
                plugin.reloadConfig();
                configureStrings(DupeRestoreSettings.load(plugin.getConfig()));
                sender.sendMessage("Hilo recargado: " + stringsEnabled);
            } catch (RuntimeException error) {
                plugin.getLogger().log(Level.WARNING, "No se pudo recargar el módulo de hilo.", error);
                sender.sendMessage("No se pudo recargar: " + error.getMessage());
            }
            return true;
        }
        return false;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("duperestore.admin") || args.length != 1) return List.of();
        return List.of("status", "reload").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
