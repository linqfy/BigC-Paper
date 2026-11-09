package dev.linqfy.bigCasares.modules.moderation;

import dev.linqfy.bigCasares.modules.moderation.anticheat.AnticheatSettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Map;

public record ModerationSettings(
    Duration scoreWindow,
    int warningThreshold,
    int criticalThreshold,
    Duration alertCooldown,
    Duration retention,
    Set<String> sensitiveCommands,
    Set<Material> rareItems,
    Map<String, Integer> signalPoints,
    Set<String> auditOnlyRules,
    AnticheatSettings anticheat
) {
    /**
     * Reglas que se registran en la auditoria pero no suman puntos: no prueban un cheat y en un servidor normal saltan
     * solas (premios de modulos, IP compartida por un tunel o una casa, logs de Paper causados por teleports de modulos).
     */
    static final Set<String> DEFAULT_AUDIT_ONLY_RULES = Set.of(
        "rare-item-gain", "pack-cache-timing", "security-log", "ip-sudden-change", "ip-account-rate", "reconnect-rate",
        "xray-ratio"
    );

    /** Puntos para una regla: 0 significa solo auditoria. */
    public int pointsFor(String rule, int fallback) {
        if (auditOnlyRules.contains(rule)) {
            return 0;
        }
        return signalPoints.getOrDefault(rule, fallback);
    }

    public static ModerationSettings load(ConfigurationSection config) {
        String root = "moderation-system.";
        Set<String> sensitive = new LinkedHashSet<>(config.getStringList(root + "sensitive-command-roots"));
        if (sensitive.isEmpty()) {
            sensitive.addAll(Set.of("op", "deop", "stop", "restart", "reload", "ban", "ban-ip", "whitelist", "lp", "luckperms", "pex", "execute"));
        }
        sensitive = sensitive.stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Material> rare = new LinkedHashSet<>();
        for (String value : config.getStringList(root + "rare-items")) {
            Material material = Material.matchMaterial(value);
            if (material != null) {
                rare.add(material);
            }
        }
        if (rare.isEmpty()) {
            rare.addAll(Set.of(Material.NETHERITE_INGOT, Material.ELYTRA, Material.TOTEM_OF_UNDYING, Material.ENCHANTED_GOLDEN_APPLE));
        }
        Map<String, Integer> points = new java.util.HashMap<>();
        Map<String, Integer> defaults = Map.ofEntries(
            Map.entry("chat-rate", 20), Map.entry("chat-duplicate", 25),
            Map.entry("command-rate", 20), Map.entry("command-duplicate", 25),
            Map.entry("sensitive-command", 35), Map.entry("blocked-end", 20),
            Map.entry("blocked-rocket", 20), Map.entry("reconnect-rate", 20),
            Map.entry("ip-join-rate", 35), Map.entry("ip-account-rate", 40),
            Map.entry("pack-cache-timing", 15), Map.entry("movement-speed", 25),
            Map.entry("upward-movement", 30), Map.entry("melee-reach", 30),
            Map.entry("melee-rate", 25), Map.entry("combat-logout", 20),
            Map.entry("illegal-stack", 60), Map.entry("inventory-click-rate", 15),
            Map.entry("rare-item-gain", 25), Map.entry("security-log", 40),
            Map.entry("hover-flight", 35), Map.entry("melee-multi-target", 30),
            Map.entry("ground-spoof", 30), Map.entry("killaura-angle", 30), Map.entry("killaura-wall", 30),
            Map.entry("killaura-inventory", 25), Map.entry("killaura-use-item", 25),
            Map.entry("block-break-rate", 30), Map.entry("block-place-face", 25),
            Map.entry("xray-ratio", 20), Map.entry("item-duplicate", 60)
        );
        defaults.forEach((rule, fallback) -> points.put(
            rule, Math.max(0, config.getInt(root + "signals." + rule + ".points", fallback))
        ));
        Set<String> auditOnly = config.isList(root + "audit-only-rules")
            ? config.getStringList(root + "audit-only-rules").stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet())
            : DEFAULT_AUDIT_ONLY_RULES;
        return new ModerationSettings(
            Duration.ofSeconds(config.getLong(root + "score-window-seconds", 60)),
            config.getInt(root + "warning-threshold", 50),
            config.getInt(root + "critical-threshold", 90),
            Duration.ofSeconds(config.getLong(root + "alert-cooldown-seconds", 120)),
            Duration.ofDays(config.getLong(root + "observation-retention-days", 30)),
            sensitive,
            Set.copyOf(rare),
            Map.copyOf(points),
            auditOnly,
            AnticheatSettings.load(config, root)
        );
    }
}
