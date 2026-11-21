package dev.linqfy.bigCasares.modules.humillacion;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.tombstone.TombstoneModule;

import java.time.Clock;

/**
 * Humillación: a hit that overkills a player by a large margin strips them instead of killing them.
 * Their whole inventory goes into a tombstone right beside them and they are left at one heart.
 */
public final class HumillacionModule implements PluginModule {

    public static final String MODULE_ID = "humillacion";

    private final BigCasares plugin;
    private final TombstoneModule tombstones;
    private RuntimeRegistrationScope compatibilityScope;

    public HumillacionModule(BigCasares plugin, TombstoneModule tombstones) {
        this.plugin = plugin;
        this.tombstones = tombstones;
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
        HumillacionListener listener = new HumillacionListener(
            HumillacionSettings.load(plugin.getConfig()), tombstones, Clock.systemUTC()
        );
        scope.register("humillacion-runtime", listener::shutdown);
        registrations.registerListener("humillacion-listener", listener);
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
