package dev.linqfy.bigCasares.modules.servercontrol;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import dev.linqfy.bigCasares.communication.EmojiAliasService;
import dev.linqfy.bigCasares.modules.moderation.AbuseSignal;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.potion.PotionEffectType;

import java.time.Instant;

public final class ServerControlListener implements Listener {
    private final ServerControlModule module;
    private final ServerControlMenu menu;
    private final EmojiAliasService emojiAliases;

    public ServerControlListener(ServerControlModule module, ServerControlMenu menu, EmojiAliasService emojiAliases) {
        this.module = module;
        this.menu = menu;
        this.emojiAliases = emojiAliases;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ControlMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            menu.click(player, holder.menu(), event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        if (module.effectiveEndAccess(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cEl acceso al End está desactivado.");
        module.signal(new AbuseSignal(
            Instant.now(), event.getPlayer().getUniqueId(), event.getPlayer().getName(), "blocked-end", 20,
            "Intento de entrar al End"
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        Player player = event.getPlayer();
        if (module.service().state().elytraRocketsEnabled() || module.canBypassRestrictions(player)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§cLos cohetes con elytra están desactivados.");
        module.signal(new AbuseSignal(
            Instant.now(), player.getUniqueId(), player.getName(), "blocked-rocket", 20,
            "Intento de impulsar una elytra"
        ));
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        event.getWorld().setPVP(module.service().effectivePvp(Instant.now()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        module.vanish().handleViewerJoin(event.getPlayer());
        if (module.vanish().isVanished(event.getPlayer())) {
            event.joinMessage(null);
        } else {
            event.joinMessage(Component.translatable("multiplayer.player.joined")
                .arguments(Component.text(event.getPlayer().getName()).color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }
        module.resistance().handlePlayer(event.getPlayer());
        module.refreshDiscordPlayers();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        module.runNextTick(() -> module.resistance().handlePlayer(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResistanceChanged(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)
            || event.getModifiedType() != PotionEffectType.RESISTANCE
            || module.resistance().level() == ResistanceLevel.OFF) {
            return;
        }
        module.runNextTick(() -> module.resistance().ensureMinimum(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (module.vanish().handleDisconnect(event.getPlayer())) {
            event.quitMessage(null);
        } else {
            event.quitMessage(Component.translatable("multiplayer.player.left")
                .arguments(Component.text(event.getPlayer().getName()).color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
        }
        module.resistance().handleDisconnect(event.getPlayer());
        module.refreshDiscordPlayers();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player damager = resolvePlayer(event.getDamager());
        Player damaged = event.getEntity() instanceof Player player ? player : null;
        if ((damager != null && module.vanish().isVanished(damager)) || (damaged != null && module.vanish().isVanished(damaged))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && module.vanish().isVanished(player)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && module.vanish().isVanished(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (module.vanish().isVanished(player)) {
            event.setCancelled(true);
            module.runNextTick(() -> player.sendMessage("§cNo podés hablar por el chat público mientras estás en vanish."));
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        event.message(Component.text(emojiAliases.replace(plain)));
        module.bridgeMinecraftChat(player, plain);
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ControlMenuHolder) {
            event.setCancelled(true);
        }
    }
}
