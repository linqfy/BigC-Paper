package dev.linqfy.bigCasares.modules.resourcepack;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class ResourcePackStatusListener implements Listener {
    private final BigCasares plugin;
    private final ResourcePackService service;
    private final ResourcePackStatusHandler statusHandler;
    private final BukkitRuntimeRegistrations registrations;

    public ResourcePackStatusListener(
        BigCasares plugin,
        ResourcePackService service,
        BukkitRuntimeRegistrations registrations
    ) {
        this.plugin = plugin;
        this.service = service;
        this.statusHandler = new ResourcePackStatusHandler(service);
        this.registrations = registrations;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        java.util.UUID playerId = player.getUniqueId();

        registrations.scheduleDelayed("join-pack-request", () -> {
            if (!player.isOnline()) {
                return;
            }
            // Joining is always an automatic delivery point. Clear stale
            // manual-mode tags left by an earlier command so the current
            // pack and its textures are requested again.
            for (String tag : java.util.Set.copyOf(player.getScoreboardTags())) {
                if (ManualResourcePackTracker.isManagedTag(tag)) {
                    player.removeScoreboardTag(tag);
                }
            }
            service.forceRequestFor(playerId);

            if (service.isRequired()) {
                registrations.scheduleDelayed("required-pack-timeout", () -> {
                    if (player.isOnline() && service.owesRequiredPack(playerId)) {
                        player.kick(Component.text("Necesitas cargar el resource pack de BigCasares para jugar."));
                    }
                }, 400L);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (plugin.getResourcePackModule().isManualPlayer(event.getPlayer())) {
            return;
        }
        if (!service.isBigCasaresPack(event.getID())) {
            return;
        }
        statusHandler.handle(
            event.getID(),
            event.getPlayer().getUniqueId(),
            event.getStatus().name(),
            () -> event.getPlayer().kick(Component.text("Necesitas cargar el resource pack de BigCasares para jugar."))
        );
        if (ResourcePackStatusHandler.map(event.getStatus().name()) == ResourcePackPlayerState.FAILED) {
            Player player = event.getPlayer();
            registrations.scheduleDelayed("resource-pack-download-retry", () -> {
                if (player.isOnline()) service.retryFailedFor(player.getUniqueId());
            }, 100L);
        }
    }
}
