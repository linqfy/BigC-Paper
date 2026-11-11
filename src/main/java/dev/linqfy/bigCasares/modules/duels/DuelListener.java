package dev.linqfy.bigCasares.modules.duels;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;
import java.util.Set;

final class DuelListener implements Listener {

    private static final Set<String> ALLOWED_COMMANDS = Set.of("duel", "duelo", "duelos", "bigcasares:duel");
    static final String BYPASS_PERMISSION = "bigcasares.duels.bypass";

    private final DuelService service;

    DuelListener(DuelService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof DuelDraftMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player && event.getClickedInventory() == event.getView().getTopInventory()) {
            service.clickDraft(player, menu, event.getSlot());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof DuelDraftMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DuelSession session = service.sessionOf(player.getUniqueId());
        if (session == null || !session.inArena()) {
            return;
        }
        if (session.phase() != DuelSession.Phase.FIGHT) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player attacker
            && !attacker.getUniqueId().equals(session.opponent(player.getUniqueId()))) {
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() < player.getHealth() || holdsTotem(player, event.getCause())) {
            return;
        }
        // Never let the duel kill: the loss is decided here, without drops or a respawn.
        event.setCancelled(true);
        service.lose(player.getUniqueId(), player.getName() + " cayó");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttackFromDuelist(EntityDamageByEntityEvent event) {
        // Duelists cannot hurt anyone but their opponent (and outsiders cannot hurt duelists, above).
        if (event.getDamager() instanceof Player attacker && service.inArena(attacker)
            && event.getEntity() instanceof Player victim
            && !victim.getUniqueId().equals(service.sessionOf(attacker.getUniqueId()).opponent(attacker.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!service.inArena(player) && !service.isDuelWorld(player.getWorld())) {
            return;
        }
        // A death that slipped past the damage check (e.g. /kill): kit items stay out of the world.
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        if (service.inArena(player)) {
            service.lose(player.getUniqueId(), player.getName() + " murió");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        service.scheduleRestoreIfSaved(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.scheduleRestoreIfSaved(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (service.sessionOf(player.getUniqueId()) != null) {
            service.lose(player.getUniqueId(), player.getName() + " se desconectó");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        DuelSession session = service.sessionOf(event.getPlayer().getUniqueId());
        if (session == null || session.phase() != DuelSession.Phase.COUNTDOWN) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || to.getY() > from.getY()) {
            Location frozen = from.clone();
            frozen.setYaw(to.getYaw());
            frozen.setPitch(to.getPitch());
            event.setTo(frozen);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!service.inArena(player) || player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        String message = event.getMessage();
        int space = message.indexOf(' ');
        String root = (space < 0 ? message.substring(1) : message.substring(1, space)).toLowerCase(Locale.ROOT);
        if (!ALLOWED_COMMANDS.contains(root)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("No podés usar comandos durante un duelo.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (service.inArena(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!service.isDuelWorld(player.getWorld()) || player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        DuelSession session = service.sessionOf(player.getUniqueId());
        if (session == null || session.phase() != DuelSession.Phase.FIGHT) {
            event.setCancelled(true);
            return;
        }
        Block block = event.getBlockPlaced();
        session.placed(block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!service.isDuelWorld(player.getWorld()) || player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        DuelSession session = service.sessionOf(player.getUniqueId());
        Block block = event.getBlock();
        if (session == null || session.phase() != DuelSession.Phase.FIGHT
            || !session.wasPlaced(block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
            return;
        }
        session.broken(block.getX(), block.getY(), block.getZ());
        event.setDropItems(false);
    }

    private static boolean holdsTotem(Player player, EntityDamageEvent.DamageCause cause) {
        if (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.KILL) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        return inventory.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
            || inventory.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }
}
