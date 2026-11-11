package dev.linqfy.bigCasares.modules.auth;

import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Locale;
import java.util.Set;

/** Connection hooks and every restriction applied to players who have not logged in yet. */
final class AuthListener implements Listener {

    private static final Set<String> PRE_LOGIN_COMMANDS = Set.of("login", "l", "log", "register", "reg");

    private final AuthModule auth;

    AuthListener(AuthModule auth) {
        this.auth = auth;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        String rejection = auth.preLoginRejection(event.getName(), event.getAddress());
        if (rejection != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(rejection));
        }
    }

    @EventHandler
    void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        auth.configure(event.getConnection());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        var profile = event.getConnection().getProfile();
        if (profile.getId() != null) {
            event.setSpawnLocation(auth.spawnLocation(profile.getId(), event.getSpawnLocation()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onJoin(PlayerJoinEvent event) {
        auth.join(event.getPlayer(), event.joinMessage(), event::joinMessage);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onQuit(PlayerQuitEvent event) {
        auth.quit(event.getPlayer());
    }

    @EventHandler
    void onRegisterChannel(PlayerRegisterChannelEvent event) {
        if (AuthModule.CHANNEL.equals(event.getChannel()) && auth.isPending(event.getPlayer())) {
            PlayerAuthState state = auth.state(event.getPlayer());
            AuthPluginChannel.sendChallenge(auth, event.getPlayer(), state);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onChat(AsyncChatEvent event) {
        if (auth.isPending(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(AuthModule.PREFIX + "§cPrimero iniciá sesión.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onCommand(PlayerCommandPreprocessEvent event) {
        if (!auth.isPending(event.getPlayer())) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int namespace = label.indexOf(':');
        if (namespace >= 0) {
            label = label.substring(namespace + 1);
        }
        if (!PRE_LOGIN_COMMANDS.contains(label)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(AuthModule.PREFIX + "§cPrimero iniciá sesión con §f/login§c.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onCommandList(PlayerCommandSendEvent event) {
        if (auth.isPending(event.getPlayer())) {
            event.getCommands().removeIf(command -> {
                String label = command.substring(command.indexOf(':') + 1).toLowerCase(Locale.ROOT);
                return !PRE_LOGIN_COMMANDS.contains(label);
            });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!auth.isPending(player) || !event.hasChangedPosition()) {
            return;
        }
        Location to = event.getTo();
        Location spawn = auth.limboSpawn();
        if (to.getWorld() != spawn.getWorld() || to.distanceSquared(spawn) > 2.25) {
            spawn.setYaw(to.getYaw());
            spawn.setPitch(to.getPitch());
            event.setTo(spawn);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onTeleport(PlayerTeleportEvent event) {
        if (!auth.allowsTeleport(event.getPlayer()) && event.getTo().getWorld() != auth.limbo()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onPortal(PlayerPortalEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void onRespawn(PlayerRespawnEvent event) {
        if (auth.isPending(event.getPlayer())) {
            event.setRespawnLocation(auth.limboSpawn());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onDeath(PlayerDeathEvent event) {
        if (auth.isPending(event.getPlayer())) {
            event.getDrops().clear();
            event.setKeepInventory(true);
        }
        event.getDrops().removeIf(auth::isQrMap);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onInteract(PlayerInteractEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onInteractEntity(PlayerInteractEntityEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onBreak(BlockBreakEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onPlace(BlockPlaceEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onDrop(PlayerDropItemEvent event) {
        if (auth.isQrMap(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
            return;
        }
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfPending(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onSwap(PlayerSwapHandItemsEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onConsume(PlayerItemConsumeEvent event) {
        cancelIfPending(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfPending(player, event);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player damager) {
            cancelIfPending(damager, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onTarget(EntityTargetEvent event) {
        Entity target = event.getTarget();
        if (target instanceof Player player) {
            cancelIfPending(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            cancelIfPending(player, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && auth.isPending(player)
            && !(event.getInventory().getHolder() instanceof PatternMenu)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof PatternMenu menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                menu.click(event.getSlot());
            }
            return;
        }
        if (event.getWhoClicked() instanceof Player player) {
            if (auth.isPending(player)) {
                event.setCancelled(true);
            } else if (auth.isQrMap(event.getCurrentItem())
                && event.getView().getTopInventory() != player.getInventory()
                && event.getView().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
                // The setup map never leaves the player's own inventory.
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PatternMenu
            || event.getWhoClicked() instanceof Player player && auth.isPending(player)) {
            event.setCancelled(true);
        }
    }

    private void cancelIfPending(Player player, Cancellable event) {
        if (auth.isPending(player)) {
            event.setCancelled(true);
        }
    }
}
