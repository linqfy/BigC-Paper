package dev.linqfy.bigCasares.modules.villagermood;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;

import java.util.concurrent.ThreadLocalRandom;

public final class VillagerMoodModule implements PluginModule {

    public static final String MODULE_ID = "villager-mood";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;
    private VillagerMoodRuntime runtime;

    public VillagerMoodModule(BigCasares plugin) {
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
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        VillagerMoodSettings settings = VillagerMoodSettings.load(plugin.getConfig());
        VillagerMoodRuntime created = new VillagerMoodRuntime(
            settings,
            new NamespacedKey(plugin, "villager_confined_ticks"),
            new NamespacedKey(plugin, "villager_annoyance_ticks"),
            new NamespacedKey(plugin, "villager_riot_golem"),
            () -> ThreadLocalRandom.current().nextDouble(),
            Bukkit::getCurrentTick,
            callback -> registrations.scheduleImmediate("villager-mood-close-trade", callback)
        );
        scope.register("villager-mood-runtime", created::shutdown);
        Bukkit.getWorlds().forEach(world -> world.getEntities().forEach(created::removeMafiaOrphan));
        registrations.registerListener("villager-mood-listener", new VillagerMoodListener(created));
        registrations.scheduleRepeating("villager-mood-check",
            () -> created.check(Bukkit.getWorlds(), Bukkit.getOnlinePlayers()),
            settings.checkIntervalTicks(), settings.checkIntervalTicks());
        registrations.scheduleRepeating("villager-mood-actions", created::tickActions,
            VillagerMoodRuntime.ACTION_INTERVAL_TICKS, VillagerMoodRuntime.ACTION_INTERVAL_TICKS);
        runtime = created;
    }

    @Override
    public void onDisable() {
        VillagerMoodRuntime current = runtime;
        runtime = null;
        if (current != null) {
            current.shutdown();
        }
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
    }
}
