package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.model.ClientPresentedModelGateway;
import dev.linqfy.bigCasares.platform.ClientPlatform;
import dev.linqfy.bigCasares.platform.ClientPlatformGateway;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Bedrock-only bodies for models that hang on invisible marker stands (the rifts and the ritual's Singer). Such a
 * model is BetterModel display entities around an invisible stand on Java; Geyser shows neither, and a Bedrock
 * client would hide the stand even when Geyser gives it the model. So a second, visible marker stand stands at
 * the anchor: hidden from everyone by default and shown to Bedrock players only, it is what Geyser turns into
 * the Bedrock entity drawing the model. Nothing is spawned while Geyser is not installed.
 *
 * <p>Re-sending a body to Bedrock players is hiding it from them and showing it again: the server sends a
 * removal and a fresh spawn, which Geyser translates with the model now recorded.</p>
 */
final class SiftBedrockStandIns implements ClientPresentedModelGateway.OtherClients, Listener {

    private final JavaPlugin plugin;
    private final ClientPlatformGateway platforms;
    private final Set<Entity> open = new LinkedHashSet<>();

    SiftBedrockStandIns(JavaPlugin plugin, ClientPlatformGateway platforms) {
        this.plugin = plugin;
        this.platforms = platforms;
    }

    @Override
    public Optional<Entity> openStandIn(Entity anchor) {
        if (plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) {
            return Optional.empty();
        }
        Location at = anchor.getLocation();
        ArmorStand standIn = at.getWorld().spawn(at, ArmorStand.class, stand -> {
            stand.setVisibleByDefault(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
            stand.setRotation(at.getYaw(), 0.0F);
        });
        open.add(standIn);
        return Optional.of(standIn);
    }

    @Override
    public void closeStandIn(Entity standIn) {
        open.remove(standIn);
        standIn.remove();
    }

    @Override
    public void respawn(Entity body) {
        for (Player player : body.getWorld().getPlayers()) {
            if (isBedrock(player)) {
                if (player.canSee(body)) {
                    player.hideEntity(plugin, body);
                }
                player.showEntity(plugin, body);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        open.removeIf(standIn -> !standIn.isValid());
        open.forEach(standIn -> showIfBedrock(event.getPlayer(), standIn));
    }

    private void showIfBedrock(Player player, Entity standIn) {
        if (isBedrock(player)) {
            player.showEntity(plugin, standIn);
        }
    }

    private boolean isBedrock(Player player) {
        return platforms.resolvePlatform(player.getUniqueId()) == ClientPlatform.BEDROCK;
    }
}
