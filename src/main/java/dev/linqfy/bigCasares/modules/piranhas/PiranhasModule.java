package dev.linqfy.bigCasares.modules.piranhas;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;

/** Schools of piranhas in jungle and swamp waters that hunt swimmers. */
public final class PiranhasModule implements PluginModule {

    public static final String MODULE_ID = "piranhas";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;

    public PiranhasModule(BigCasares plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        PiranhaSettings settings = PiranhaSettings.load(plugin.getConfig());
        PiranhaRuntime runtime = new PiranhaRuntime(settings, new NamespacedKey(plugin, "piranha"));
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.ownCleanup("piranhas-runtime", runtime::shutdown);
        registrations.registerListener("piranhas-listener", new PiranhaListener(runtime));
        PluginCommand command = plugin.getCommand("piranha");
        if (command != null) {
            PiranhaCommand handler = new PiranhaCommand(runtime);
            registrations.bindCommand("piranhas-command", command, handler, handler);
        }
        // Pick up piranhas already loaded (after a plugin reload).
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                runtime.track(entity);
            }
        }
        registrations.scheduleRepeating("piranhas-hunt", runtime::huntTick,
            PiranhaRuntime.HUNT_PERIOD_TICKS, PiranhaRuntime.HUNT_PERIOD_TICKS);
        registrations.scheduleRepeating("piranhas-spawn",
            () -> runtime.spawnTick(Bukkit.getOnlinePlayers()), settings.spawnIntervalTicks(),
            settings.spawnIntervalTicks());
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }
}
