package dev.linqfy.bigCasares.modules.phasearrows;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.NamespacedKey;

/** Spectral arrows phase through thin walls (configurable thickness and number of walls per arrow). */
public final class PhaseArrowsModule implements PluginModule {

    public static final String MODULE_ID = "phase-arrows";

    private final BigCasares plugin;
    private RuntimeRegistrationScope compatibilityScope;

    public PhaseArrowsModule(BigCasares plugin) {
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
        PhaseArrowSettings settings = PhaseArrowSettings.load(plugin.getConfig());
        new BukkitRuntimeRegistrations(plugin, scope).registerListener("phase-arrows-listener",
            new PhaseArrowListener(settings, new NamespacedKey(plugin, "phase_arrow_phases")));
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
