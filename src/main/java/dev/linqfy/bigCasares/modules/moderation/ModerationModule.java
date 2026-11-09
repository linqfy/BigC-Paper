package dev.linqfy.bigCasares.modules.moderation;

import dev.linqfy.bigCasares.BigCasares;
import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import dev.linqfy.bigCasares.modules.moderation.anticheat.AnticheatListener;
import dev.linqfy.bigCasares.modules.moderation.anticheat.Detection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class ModerationModule implements PluginModule {
    private static final Map<String, Integer> DEFAULT_DETECTION_POINTS = Map.of(
        "movement-speed", 25, "upward-movement", 30, "hover-flight", 35,
        "melee-reach", 30, "melee-rate", 25, "melee-multi-target", 30
    );
    private final BigCasares plugin;
    private final AuditService audit = new AuditService();
    private ModerationSettings settings;
    private AbuseScoringService scoring;
    private ObservationStorage observations;
    private ModerationListener listener;
    private AnticheatListener anticheat;
    private ModerationLogHandler logHandler;
    private BukkitTask purgeTask;
    private BukkitTask rareItemTask;
    private RareItemGainTracker rareItemTracker;
    private BukkitRuntimeRegistrations registrations;
    private RuntimeRegistrationScope compatibilityScope;
    private boolean shutdownStarted;
    /**
     * Jugadores a quienes otro modulo les dio permiso de volar solo como mecanismo tecnico (por ejemplo para detectar un doble
     * salto): ese permiso no los exime del detector de movimiento (si no, quedarian exentos todo el tiempo).
     */
    private final java.util.Set<java.util.UUID> technicalFlight = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ModerationModule(BigCasares plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "moderation-system";
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        this.compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        if (plugin == null) {
            return;
        }
        shutdownStarted = false;
        this.registrations = new BukkitRuntimeRegistrations(plugin, scope);
        scope.register("module-state", this::clearRuntimeState);
        settings = ModerationSettings.load(plugin.getConfig());
        scoring = new AbuseScoringService(
            settings.scoreWindow(), settings.warningThreshold(), settings.criticalThreshold(), settings.alertCooldown()
        );
        Path observationPath = plugin.getDataFolder().toPath().resolve("data").resolve("moderation-system");
        observations = new FileObservationStorage(observationPath);
        rareItemTracker = new RareItemGainTracker(
            settings.rareItems().stream().map(org.bukkit.Material::name).collect(java.util.stream.Collectors.toSet())
        );
        ModerationSignalTracker tracker = new ModerationSignalTracker(settings.sensitiveCommands());
        listener = new ModerationListener(plugin, this, tracker);
        registrations.registerListener("moderation-listener", listener);
        anticheat = new AnticheatListener(
            plugin.getServer(), settings.anticheat(), this::acceptDetection, this::hasTechnicalFlight,
            plugin::resolvePlayerPlatform
        );
        registrations.registerListener("anticheat-listener", anticheat);
        AnticheatListener ownedAnticheat = anticheat;
        scope.register("anticheat-state", ownedAnticheat::clear);
        registrations.scheduleRepeating("anticheat-hover-task", ownedAnticheat::sampleHover,
            AnticheatListener.HOVER_PERIOD_TICKS, AnticheatListener.HOVER_PERIOD_TICKS);
        BukkitRuntimeRegistrations activeRegistrations = registrations;
        logHandler = new ModerationLogHandler(
            () -> plugin.getServer().getOnlinePlayers(),
            signal -> activeRegistrations.guard(() -> acceptSignal(signal)).run(),
            audit
        );
        Logger.getLogger("").addHandler(logHandler);
        ModerationLogHandler ownedLogHandler = logHandler;
        scope.register("root-log-handler", () -> Logger.getLogger("").removeHandler(ownedLogHandler));
        purgeObservations();
        purgeTask = registrations.ownTask("observation-purge-task",
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, registrations.guard(this::purgeObservations),
                20L * 60L * 60L, 20L * 60L * 60L * 24L
            ));
        rareItemTask = registrations.scheduleRepeating("rare-item-task", this::sampleRareItems, 20L, 20L);
        audit.publish(AuditEvent.system(AuditSeverity.INFO, "lifecycle", "plugin-enabled", "BigCasares inició"));
    }

    @Override
    public void onDisable() {
        if (shutdownStarted) {
            return;
        }
        shutdownStarted = true;
        if (plugin != null) {
            audit.publish(AuditEvent.system(AuditSeverity.INFO, "lifecycle", "plugin-disabled", "BigCasares se detiene"));
        }
        if (purgeTask != null) {
            purgeTask.cancel();
            purgeTask = null;
        }
        if (rareItemTask != null) {
            rareItemTask.cancel();
            rareItemTask = null;
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
        if (anticheat != null) {
            HandlerList.unregisterAll(anticheat);
            anticheat.clear();
            anticheat = null;
        }
        if (logHandler != null) {
            Logger.getLogger("").removeHandler(logHandler);
            logHandler = null;
        }
        RuntimeRegistrationScope scope = compatibilityScope;
        compatibilityScope = null;
        if (scope != null) {
            scope.close();
        }
        clearRuntimeState();
    }

    /** Marca (o desmarca) que el permiso de volar de ese jugador es solo tecnico: sigue bajo el detector de movimiento. */
    public void setTechnicalFlight(java.util.UUID playerId, boolean technical) {
        if (technical) {
            technicalFlight.add(playerId);
        } else {
            technicalFlight.remove(playerId);
        }
    }

    boolean hasTechnicalFlight(java.util.UUID playerId) {
        return technicalFlight.contains(playerId);
    }

    /**
     * El movimiento de ese jugador no se mide durante ese tiempo (un impulso legitimo que no pase por setVelocity). Los
     * setVelocity, knockback, explosiones y teleports ya eximen solos: los modulos no necesitan llamar esto para eso.
     */
    public void exemptMovement(java.util.UUID playerId, java.time.Duration duration) {
        AnticheatListener active = anticheat;
        if (active != null) {
            active.grace(playerId, Math.max(1, duration.toMillis() / 50), "module");
        }
    }

    public AuditService audit() {
        return audit;
    }

    public ObservationStorage observations() {
        return observations;
    }

    private void acceptDetection(Player player, Detection detection) {
        acceptSignal(new AbuseSignal(
            Instant.now(), player.getUniqueId(), player.getName(), detection.rule(),
            DEFAULT_DETECTION_POINTS.getOrDefault(detection.rule(), 25), detection.evidence()
        ));
    }

    public void acceptSignal(AbuseSignal signal) {
        if (scoring == null) {
            return;
        }
        int configuredPoints = settings.pointsFor(signal.rule(), signal.points());
        if (configuredPoints <= 0) {
            audit.publish(new AuditEvent(
                signal.timestamp(), AuditSeverity.INFO, "abuse", "observation", java.util.Optional.of(signal.playerId()),
                signal.playerName(), "Observación (sin puntos): " + signal.rule(),
                Map.of("rule", signal.rule(), "evidence", signal.evidence())
            ));
            return;
        }
        AbuseSignal effectiveSignal = configuredPoints == signal.points() ? signal : new AbuseSignal(
            signal.timestamp(), signal.playerId(), signal.playerName(), signal.rule(), configuredPoints, signal.evidence()
        );
        AbuseScoreResult result = scoring.record(effectiveSignal);
        Map<String, String> details = new HashMap<>();
        details.put("rule", signal.rule());
        details.put("points", String.valueOf(effectiveSignal.points()));
        details.put("score", String.valueOf(result.score()));
        details.put("evidence", signal.evidence());
        audit.publish(new AuditEvent(
            signal.timestamp(), AuditSeverity.INFO, "abuse", "signal", java.util.Optional.of(signal.playerId()),
            signal.playerName(), "Señal de abuso: " + signal.rule(), details
        ));
        result.alert().ifPresent(severity -> {
            Runnable alert = () -> alertStaff(signal, result.score(), severity);
            if (plugin.getServer().isPrimaryThread()) {
                BukkitRuntimeRegistrations current = registrations;
                if (current != null) {
                    current.guard(alert).run();
                }
            } else {
                BukkitRuntimeRegistrations current = registrations;
                if (current != null) {
                    current.scheduleImmediate("moderation-staff-alert", alert);
                }
            }
        });
    }

    public void observePlayer(Player player, String type, Map<String, String> values) {
        if (observations == null) {
            return;
        }
        Map<String, String> complete = new HashMap<>(values);
        complete.put("name", player.getName());
        complete.put("uuid", player.getUniqueId().toString());
        observations.append(new Observation(Instant.now(), player.getUniqueId(), type, complete));
    }

    public void markInventoryGainExplained(Player player) {
        if (rareItemTracker != null) {
            rareItemTracker.markExplained(player.getUniqueId());
        }
    }

    public void forgetInventoryTracking(Player player) {
        if (rareItemTracker != null) {
            rareItemTracker.forget(player.getUniqueId());
        }
    }

    public void markAdminGive(String command) {
        if (rareItemTracker == null || command == null) {
            return;
        }
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        String[] parts = normalized.trim().split("\\s+");
        if (parts.length < 2 || !(parts[0].equalsIgnoreCase("give") || parts[0].endsWith(":give"))) {
            return;
        }
        Player target = plugin.getServer().getPlayerExact(parts[1]);
        if (target != null) {
            rareItemTracker.markExplained(target.getUniqueId());
        }
    }

    private void alertStaff(AbuseSignal signal, int score, AbuseSeverity severity) {
        String label = severity == AbuseSeverity.CRITICAL ? "CRÍTICA" : "ADVERTENCIA";
        String message = "§c[Moderación " + label + "] §f" + signal.playerName()
            + " §7alcanzó §e" + score + " puntos§7. " + signal.evidence();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOp()) {
                continue;
            }
            if (plugin.getServerControlModule() == null
                || plugin.getServerControlModule().service() == null
                || plugin.getServerControlModule().service().staffAlertsEnabled(player.getUniqueId())) {
                player.sendMessage(message);
            }
        }
        Map<String, String> details = Map.of(
            "rule", signal.rule(),
            "score", String.valueOf(score),
            "evidence", signal.evidence()
        );
        audit.publish(new AuditEvent(
            Instant.now(), severity == AbuseSeverity.CRITICAL ? AuditSeverity.CRITICAL : AuditSeverity.WARNING,
            "abuse", "threshold-" + severity.name().toLowerCase(java.util.Locale.ROOT),
            java.util.Optional.of(signal.playerId()), signal.playerName(), message, details
        ));
    }

    private void purgeObservations() {
        if (observations == null) {
            return;
        }
        try {
            observations.purgeOlderThan(Instant.now().minus(settings.retention()));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "No se pudieron purgar observaciones de moderación", ex);
        }
    }

    private void sampleRareItems() {
        if (rareItemTracker == null) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Map<String, Integer> counts = new HashMap<>();
            for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
                if (item != null && settings.rareItems().contains(item.getType())) {
                    counts.merge(item.getType().name(), item.getAmount(), Integer::sum);
                }
            }
            rareItemTracker.sample(Instant.now(), player.getUniqueId(), player.getName(), counts)
                .ifPresent(this::acceptSignal);
        }
    }

    private void clearRuntimeState() {
        purgeTask = null;
        rareItemTask = null;
        listener = null;
        anticheat = null;
        technicalFlight.clear();
        logHandler = null;
        registrations = null;
        rareItemTracker = null;
        scoring = null;
        observations = null;
        settings = null;
        shutdownStarted = true;
    }
}
