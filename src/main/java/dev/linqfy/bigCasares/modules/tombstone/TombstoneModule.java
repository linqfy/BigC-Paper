package dev.linqfy.bigCasares.modules.tombstone;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.entity.Player;

public final class TombstoneModule implements PluginModule {
    private final BigCasares plugin;
    private TombstoneRuntime runtime;
    private RuntimeRegistrationScope compatibilityScope;
    private Predicate<UUID> deathCaptureEligibility = ignored -> true;

    public TombstoneModule(BigCasares plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "death-tombstone-system"; }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);
        long lifetimeSeconds = Math.max(1L, plugin.getConfig().getLong("death-tombstone-system.lifetime-seconds", 900L));
        TombstoneRuntime created = new TombstoneRuntime(
            plugin,
            new YamlTombstoneStorage(plugin.getDataFolder().toPath().resolve("data/tombstones/tombstones.yml")),
            Clock.systemUTC(), Duration.ofSeconds(lifetimeSeconds), registrations
        );
        registrations.registerListener("tombstone-listener",
            new TombstoneListener(created, registrations, this::shouldCaptureDeath));
        // Registered before load(): a failed load still removes whatever visuals it already spawned.
        scope.register("tombstone-runtime", () -> {
            if (runtime == created) runtime = null;
            created.shutdown();
        });
        created.load();
        this.runtime = created;
    }

    @Override
    public void onDisable() {
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) scope.close();
    }

    public boolean openNearby(Player player, double radius) {
        TombstoneRuntime current = runtime;
        return current != null && current.openNearby(player, radius);
    }

    public void setDeathCaptureEligibility(Predicate<UUID> eligibility) {
        deathCaptureEligibility = eligibility == null ? ignored -> true : eligibility;
    }

    private boolean shouldCaptureDeath(UUID playerId) {
        return deathCaptureEligibility.test(playerId);
    }
}
