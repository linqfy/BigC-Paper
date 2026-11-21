package dev.linqfy.bigCasares.modules.trimabilities;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;

/**
 * "Ramas pre-netherite": a full set of diamond armor sharing one trim pattern grants a mild passive
 * ability (a potion effect, damage reduction, an attribute bonus or an immunity) chosen by the pattern.
 */
public final class TrimAbilitiesModule implements PluginModule {

    public static final String MODULE_ID = "trim-abilities";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;

    public TrimAbilitiesModule(BigCasares plugin) {
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
        TrimAbilitySettings settings = TrimAbilitySettings.load(plugin.getConfig());
        TrimAbilityRuntime runtime = new TrimAbilityRuntime(
            new TrimSetRules(settings.tiers()), new NamespacedKey(plugin, "trim_ability"));
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        registrations.ownCleanup("trim-abilities-runtime", () -> runtime.shutdown(Bukkit.getOnlinePlayers()));
        registrations.registerListener("trim-abilities-listener", new TrimAbilityListener(runtime));
        registrations.scheduleRepeating("trim-abilities-check",
            () -> runtime.check(Bukkit.getOnlinePlayers()), 1L, settings.checkIntervalTicks());
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
