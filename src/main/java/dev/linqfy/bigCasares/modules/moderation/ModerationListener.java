package dev.linqfy.bigCasares.modules.moderation;

import dev.linqfy.bigCasares.BigCasares;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ModerationListener implements Listener {
    private static final Set<PotionEffectType> MOVEMENT_EFFECTS = Set.of(
        PotionEffectType.SPEED,
        PotionEffectType.JUMP_BOOST,
        PotionEffectType.LEVITATION,
        PotionEffectType.SLOW_FALLING,
        PotionEffectType.DOLPHINS_GRACE
    );

    private final BigCasares plugin;
    private final ModerationModule module;
    private final ModerationSignalTracker tracker;
    private final MovementTracker movement = new MovementTracker();
    private final Map<UUID, Instant> lastCombat = new HashMap<>();
    private final Map<UUID, Instant> packAccepted = new HashMap<>();
    private final Map<UUID, Boolean> firstSeen = new HashMap<>();
    private final Map<UUID, Integer> lastMovementTick = new HashMap<>();

    public ModerationListener(BigCasares plugin, ModerationModule module, ModerationSignalTracker tracker) {
        this.plugin = plugin;
        this.module = module;
        this.tracker = tracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        tracker.chat(Instant.now(), event.getPlayer().getUniqueId(), event.getPlayer().getName(), message)
            .forEach(module::acceptSignal);
        module.audit().publish(AuditEvent.player(
            AuditSeverity.INFO, "chat", "player-chat", event.getPlayer().getUniqueId(), event.getPlayer().getName(), message
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        tracker.command(Instant.now(), player.getUniqueId(), player.getName(), event.getMessage(), player.isOp())
            .forEach(module::acceptSignal);
        module.audit().publish(AuditEvent.player(
            player.isOp() ? AuditSeverity.WARNING : AuditSeverity.INFO,
            "command", player.isOp() ? "admin-command" : "player-command",
            player.getUniqueId(), player.getName(), event.getMessage()
        ));
        if (player.isOp()) {
            module.markAdminGive(event.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        module.audit().publish(AuditEvent.system(
            AuditSeverity.WARNING, "command", "console-command", event.getCommand()
        ));
        module.markAdminGive(event.getCommand());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = rawIp(player);
        boolean isFirstSeen = !module.observations().hasSeen(player.getUniqueId());
        firstSeen.put(player.getUniqueId(), isFirstSeen);
        module.observations().markSeen(player.getUniqueId());
        tracker.join(Instant.now(), player.getUniqueId(), player.getName(), ip).forEach(module::acceptSignal);
        module.observePlayer(player, "join", Map.of("ip", ip));
        module.audit().publish(new AuditEvent(
            Instant.now(), AuditSeverity.INFO, "lifecycle", "player-join", java.util.Optional.of(player.getUniqueId()),
            player.getName(), player.getName() + " ingresó", Map.of("ip", ip)
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Instant combatAt = lastCombat.remove(player.getUniqueId());
        if (combatAt != null && Duration.between(combatAt, Instant.now()).compareTo(Duration.ofSeconds(15)) <= 0) {
            module.acceptSignal(new AbuseSignal(
                Instant.now(), player.getUniqueId(), player.getName(), "combat-logout", 20,
                "Desconexión dentro de los 15 segundos posteriores a combate"
            ));
        }
        movement.forget(player.getUniqueId());
        lastMovementTick.remove(player.getUniqueId());
        packAccepted.remove(player.getUniqueId());
        firstSeen.remove(player.getUniqueId());
        module.forgetInventoryTracking(player);
        module.audit().publish(AuditEvent.player(
            AuditSeverity.INFO, "lifecycle", "player-quit", player.getUniqueId(), player.getName(),
            player.getName() + " salió"
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        module.audit().publish(AuditEvent.player(
            AuditSeverity.WARNING, "lifecycle", "player-kick", event.getPlayer().getUniqueId(),
            event.getPlayer().getName(), PlainTextComponentSerializer.plainText().serialize(event.reason())
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        module.audit().publish(AuditEvent.player(
            AuditSeverity.INFO, "lifecycle", "player-death", event.getPlayer().getUniqueId(),
            event.getPlayer().getName(), event.deathMessage() == null ? "Muerte sin mensaje"
                : PlainTextComponentSerializer.plainText().serialize(event.deathMessage())
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || !event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        Integer previousTick = lastMovementTick.put(player.getUniqueId(), player.getTicksLived());
        if (previousTick != null && previousTick == player.getTicksLived()) {
            return;
        }
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double upward = event.getTo().getY() - event.getFrom().getY();
        double expected = Math.max(0.215, player.getWalkSpeed() * 1.075) * (player.isSprinting() ? 1.3 : 1.0);
        boolean exempt = player.isInsideVehicle() || player.isGliding() || player.isRiptiding()
            || player.isFlying()
            || player.getAllowFlight() && !module.hasTechnicalFlight(player.getUniqueId())
            || MOVEMENT_EFFECTS.stream().anyMatch(player::hasPotionEffect);
        movement.sample(
            Instant.now(), player.getUniqueId(), player.getName(), horizontal, upward, expected, exempt
        ).ifPresent(module::acceptSignal);
    }

    void exemptMovement(UUID playerId, Instant until) {
        movement.exempt(playerId, until);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        movement.exempt(event.getPlayer().getUniqueId(), Instant.now().plusSeconds(2));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        movement.exempt(event.getPlayer().getUniqueId(), Instant.now().plusSeconds(3));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent event) {
        movement.exempt(event.getPlayer().getUniqueId(), Instant.now().plusSeconds(2));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player target)) {
            return;
        }
        Instant now = Instant.now();
        lastCombat.put(attacker.getUniqueId(), now);
        lastCombat.put(target.getUniqueId(), now);
        double reach = attacker.getEyeLocation().distance(target.getLocation().add(0, 1, 0));
        if (reach > 4.5) {
            module.acceptSignal(new AbuseSignal(
                now, attacker.getUniqueId(), attacker.getName(), "melee-reach", 30,
                String.format(java.util.Locale.ROOT, "Alcance cuerpo a cuerpo %.2f", reach)
            ));
        }
        tracker.meleeHit(now, attacker.getUniqueId(), attacker.getName()).ifPresent(module::acceptSignal);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        tracker.inventoryClick(Instant.now(), player.getUniqueId(), player.getName()).ifPresent(module::acceptSignal);
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING
            && event.getView().getTopInventory().getType() != InventoryType.PLAYER) {
            module.markInventoryGainExplained(player);
        }
        checkStack(player, event.getCurrentItem());
        checkStack(player, event.getCursor());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            module.markInventoryGainExplained(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            module.markInventoryGainExplained(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent event) {
        module.markInventoryGainExplained(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (plugin.getResourcePackModule() == null || plugin.getResourcePackModule().service()
            .map(service -> service.isBigCasaresPack(event.getID())).orElse(false) == false) {
            return;
        }
        Player player = event.getPlayer();
        Instant now = Instant.now();
        String status = event.getStatus().name();
        if ("ACCEPTED".equals(status)) {
            packAccepted.put(player.getUniqueId(), now);
        }
        Map<String, String> details = new HashMap<>();
        details.put("status", status);
        details.put("brand", String.valueOf(player.getClientBrandName()));
        details.put("protocol", String.valueOf(player.getProtocolVersion()));
        details.put("platform", plugin.resolvePlayerPlatform(player.getUniqueId()).name());
        details.put("ip", rawIp(player));
        Instant acceptedAt = packAccepted.get(player.getUniqueId());
        if (acceptedAt != null) {
            details.put("milliseconds-since-acceptance", String.valueOf(Duration.between(acceptedAt, now).toMillis()));
        }
        module.observePlayer(player, "resource-pack", details);
        module.audit().publish(new AuditEvent(
            now, status.startsWith("FAILED") ? AuditSeverity.WARNING : AuditSeverity.INFO,
            "resource-pack", "pack-" + status.toLowerCase(java.util.Locale.ROOT),
            java.util.Optional.of(player.getUniqueId()), player.getName(), "Resultado del resource pack: " + status, details
        ));
        if ("SUCCESSFULLY_LOADED".equals(status) && Boolean.TRUE.equals(firstSeen.get(player.getUniqueId()))
            && acceptedAt != null && Duration.between(acceptedAt, now).toMillis() <= 750
            && plugin.resolvePlayerPlatform(player.getUniqueId()) == dev.linqfy.bigCasares.platform.ClientPlatform.JAVA) {
            module.acceptSignal(new AbuseSignal(
                now, player.getUniqueId(), player.getName(), "pack-cache-timing", 15,
                "Carga del pack genuino en menos de 750 ms; evidencia de baja confianza"
            ));
        }
    }

    private void checkStack(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (item.getAmount() > item.getMaxStackSize()) {
            module.acceptSignal(new AbuseSignal(
                Instant.now(), player.getUniqueId(), player.getName(), "illegal-stack", 60,
                item.getType() + " x" + item.getAmount() + " supera " + item.getMaxStackSize()
            ));
        }
    }

    private String rawIp(Player player) {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
    }
}
