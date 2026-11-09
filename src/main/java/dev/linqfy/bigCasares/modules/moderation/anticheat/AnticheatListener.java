package dev.linqfy.bigCasares.modules.moderation.anticheat;

import dev.linqfy.bigCasares.platform.ClientPlatform;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Adaptador Bukkit del anticheat. Traduce eventos a muestras para los checks puros y, sobre todo, abre ventanas de gracia
 * para todo lo que mueve a un jugador sin que el cliente lo decida: asi los modulos (planeador, gancho, ballestas, bosses,
 * botas de Mutango...) no necesitan avisar nada, porque cualquier setVelocity, knockback, explosion o teleport ya exime.
 */
public final class AnticheatListener implements Listener {
    static final long JOIN_GRACE = 100;
    static final long TELEPORT_GRACE = 40;
    static final long RESPAWN_GRACE = 60;
    static final long WORLD_GRACE = 60;
    static final long MIN_VELOCITY_GRACE = 30;
    static final long MAX_VELOCITY_GRACE = 140;
    static final long KNOCKBACK_GRACE = 40;
    static final long DAMAGE_GRACE = 20;
    static final long EXPLOSION_GRACE = 80;
    static final long PISTON_GRACE = 40;
    static final long STATE_GRACE = 60;
    static final long ENVIRONMENT_GRACE = 30;
    static final long BOUNCE_GRACE = 120;
    static final double EXPLOSION_RADIUS = 12;
    static final double PISTON_RADIUS = 4;
    public static final long HOVER_PERIOD_TICKS = 5;
    static final double VANILLA_GRAVITY = 0.08;

    private final Server server;
    private final AnticheatSettings settings;
    private final BiConsumer<Player, Detection> sink;
    private final Predicate<UUID> technicalFlight;
    private final Function<UUID, ClientPlatform> platform;
    private final ExemptionLedger ledger = new ExemptionLedger();
    private final MovementCheck movement = new MovementCheck();
    private final HoverCheck hover = new HoverCheck();
    private final CombatCheck combat;
    private final Map<UUID, Integer> lastMoveTick = new HashMap<>();

    public AnticheatListener(
        Server server,
        AnticheatSettings settings,
        BiConsumer<Player, Detection> sink,
        Predicate<UUID> technicalFlight,
        Function<UUID, ClientPlatform> platform
    ) {
        this.server = server;
        this.settings = settings;
        this.sink = sink;
        this.technicalFlight = technicalFlight;
        this.platform = platform;
        this.combat = new CombatCheck(settings.maxAttacksPerSecond());
    }

    /** Gracia pedida por otro modulo (por ejemplo un impulso propio que no pasa por setVelocity). */
    public void grace(UUID playerId, long ticks, String reason) {
        ledger.grant(playerId, (long) server.getCurrentTick() + Math.max(1, ticks), reason);
    }

    /** Tarea periodica (cada {@link #HOVER_PERIOD_TICKS}): el check de vuelo no puede depender de PlayerMoveEvent. */
    public void sampleHover() {
        if (!settings.checkEnabled("hover-flight")) {
            return;
        }
        long tick = server.getCurrentTick();
        for (Player player : server.getOnlinePlayers()) {
            boolean exempt = exempt(player, tick);
            boolean airborne = !exempt && EnvironmentProbe.airborne(player);
            hover.sample(player.getUniqueId(), player.getLocation().getY(), airborne, exempt)
                .ifPresent(detection -> report(player, detection));
        }
    }

    public void clear() {
        ledger.clear();
        movement.clear();
        hover.clear();
        combat.clear();
        lastMoveTick.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (!event.hasChangedPosition() || to.getWorld() != from.getWorld()) {
            return;
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        int tick = server.getCurrentTick();
        Integer previous = lastMoveTick.put(id, tick);
        if (!settings.checkEnabled("movement-speed") && !settings.checkEnabled("upward-movement")) {
            return;
        }
        int elapsed = previous == null ? MovementCheck.MAX_ELAPSED_TICKS + 1 : Math.max(1, tick - previous);
        boolean exempt = exempt(player, tick) || exemptByEnvironment(player, to, tick);
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        MovementSample sample = new MovementSample(
            elapsed,
            Math.sqrt(dx * dx + dz * dz),
            to.getY() - from.getY(),
            player.isOnGround(),
            attribute(player, Attribute.MOVEMENT_SPEED, 0.1),
            player.isSprinting(),
            jumpVelocity(player),
            attribute(player, Attribute.STEP_HEIGHT, 0.6),
            exempt
        );
        movement.sample(id, sample).ifPresent(detection -> report(player, detection));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!event.willAttack()) {
            return;
        }
        Player attacker = event.getPlayer();
        long tick = server.getCurrentTick();
        if (!settings.enabled() || bypassed(attacker) || attacker.getGameMode() == GameMode.CREATIVE
            || attacker.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Entity target = event.getAttacked();
        double distance = Double.NaN;
        double allowed = Double.NaN;
        if (target instanceof Player victim && victim.getWorld() == attacker.getWorld()
            && !attacker.isInsideVehicle() && !victim.isInsideVehicle() && !lagging()) {
            Location eye = attacker.getEyeLocation();
            BoundingBox box = victim.getBoundingBox();
            distance = CombatCheck.distanceToBox(eye.getX(), eye.getY(), eye.getZ(),
                box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
            allowed = CombatCheck.allowedReach(attribute(attacker, Attribute.ENTITY_INTERACTION_RANGE, 3.0),
                attacker.getPing());
            if (ledger.isExempt(victim.getUniqueId(), tick)) {
                allowed += 1.0; // el objetivo esta siendo empujado: el cliente lo ve donde estaba
            }
        }
        List<Detection> detections = combat.attack(attacker.getUniqueId(), System.currentTimeMillis(),
            target.getUniqueId(), distance, allowed);
        detections.forEach(detection -> report(attacker, detection));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        Vector velocity = event.getVelocity();
        long ticks = Math.max(MIN_VELOCITY_GRACE, Math.min(MAX_VELOCITY_GRACE, Math.round(velocity.length() * 40)));
        grace(event.getPlayer().getUniqueId(), ticks, "velocity");
    }

    /** Las subclases (empuje por ataque, knockback por entidad) comparten la HandlerList de este evento. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (event.getEntity() instanceof Player player) {
            grace(player.getUniqueId(), KNOCKBACK_GRACE, "knockback");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        boolean explosion = event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION;
        grace(player.getUniqueId(), explosion ? EXPLOSION_GRACE : DAMAGE_GRACE, explosion ? "explosion" : "damage");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        graceNear(event.getLocation(), EXPLOSION_RADIUS, EXPLOSION_GRACE, "explosion");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        graceNear(event.getBlock().getLocation(), EXPLOSION_RADIUS, EXPLOSION_GRACE, "explosion");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        graceNear(event.getBlock().getLocation(), PISTON_RADIUS + event.getBlocks().size(), PISTON_GRACE, "piston");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        graceNear(event.getBlock().getLocation(), PISTON_RADIUS + event.getBlocks().size(), PISTON_GRACE, "piston");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        grace(event.getPlayer().getUniqueId(), TELEPORT_GRACE, "teleport");
        resetMovement(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        grace(event.getPlayer().getUniqueId(), RESPAWN_GRACE, "respawn");
        resetMovement(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        grace(event.getPlayer().getUniqueId(), WORLD_GRACE, "world-change");
        resetMovement(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        grace(event.getPlayer().getUniqueId(), JOIN_GRACE, "join");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        grace(event.getPlayer().getUniqueId(), STATE_GRACE, "gamemode");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            grace(player.getUniqueId(), STATE_GRACE, "vehicle-exit");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        grace(event.getPlayer().getUniqueId(), STATE_GRACE, "flight-toggle");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            grace(player.getUniqueId(), STATE_GRACE, "glide-toggle");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRiptide(PlayerRiptideEvent event) {
        grace(event.getPlayer().getUniqueId(), STATE_GRACE, "riptide");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        grace(event.getPlayer().getUniqueId(), DAMAGE_GRACE, "bed");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        resetMovement(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        ledger.forget(id);
        movement.forget(id);
        hover.forget(id);
        combat.forget(id);
        lastMoveTick.remove(id);
    }

    /**
     * Estados en los que la fisica simple no aplica. Los que vienen de un estado del jugador (agua, elytra, vuelo...)
     * ademas dejan una gracia: al salir conserva la inercia unos ticks.
     */
    private boolean exempt(Player player, long tick) {
        if (!settings.enabled() || bypassed(player) || lagging()) {
            return true;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || player.isDead() || player.isSleeping()) {
            return true;
        }
        if (settings.exemptBedrock() && platform.apply(player.getUniqueId()) == ClientPlatform.BEDROCK) {
            return true;
        }
        Optional<String> state = stateReason(player);
        if (state.isPresent()) {
            grace(player.getUniqueId(), STATE_GRACE, state.get());
            return true;
        }
        return ledger.isExempt(player.getUniqueId(), tick);
    }

    private Optional<String> stateReason(Player player) {
        if (player.isFlying() || player.getAllowFlight() && !technicalFlight.test(player.getUniqueId())) {
            return Optional.of("flight");
        }
        if (player.isInsideVehicle()) {
            return Optional.of("vehicle");
        }
        if (player.isGliding() || player.isRiptiding()) {
            return Optional.of("glide");
        }
        if (player.isSwimming() || player.isInWater() || player.isInLava() || player.isInBubbleColumn()
            || player.isInPowderedSnow()) {
            return Optional.of("liquid");
        }
        if (player.isClimbing()) {
            return Optional.of("climbing");
        }
        if (player.hasPotionEffect(PotionEffectType.LEVITATION) || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
            || player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) {
            return Optional.of("effect");
        }
        if (Math.abs(attribute(player, Attribute.GRAVITY, VANILLA_GRAVITY) - VANILLA_GRAVITY) > 1e-4) {
            return Optional.of("gravity");
        }
        return Optional.empty();
    }

    private boolean exemptByEnvironment(Player player, Location to, long tick) {
        EnvironmentProbe.Surroundings surroundings = EnvironmentProbe.surroundings(player, to);
        if (surroundings == EnvironmentProbe.Surroundings.NORMAL) {
            return false;
        }
        boolean bounce = surroundings == EnvironmentProbe.Surroundings.BOUNCE;
        ledger.grant(player.getUniqueId(), tick + (bounce ? BOUNCE_GRACE : ENVIRONMENT_GRACE),
            bounce ? "bounce" : "environment");
        return true;
    }

    private void graceNear(Location center, double radius, long ticks, String reason) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        double radiusSquared = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radiusSquared) {
                grace(player.getUniqueId(), ticks, reason);
            }
        }
    }

    private void resetMovement(UUID playerId) {
        movement.forget(playerId);
        hover.forget(playerId);
        lastMoveTick.remove(playerId);
    }

    private void report(Player player, Detection detection) {
        if (settings.checkEnabled(detection.rule())) {
            sink.accept(player, detection);
        }
    }

    private boolean bypassed(Player player) {
        return !settings.bypassPermission().isEmpty() && player.hasPermission(settings.bypassPermission());
    }

    private boolean lagging() {
        double[] tps = server.getTPS();
        return tps.length > 0 && tps[0] < settings.minimumTps();
    }

    private static double jumpVelocity(Player player) {
        double jump = attribute(player, Attribute.JUMP_STRENGTH, 0.42);
        PotionEffect boost = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        return boost == null ? jump : jump + 0.1 * (boost.getAmplifier() + 1);
    }

    private static double attribute(Player player, Attribute attribute, double fallback) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }
}
