package dev.linqfy.bigCasares.modules.moderation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ModerationSignalTracker {
    private final Set<String> sensitiveCommands;
    private final Map<UUID, Deque<Instant>> chatTimes = new HashMap<>();
    private final Map<UUID, Map<String, Deque<Instant>>> chatDuplicates = new HashMap<>();
    private final Map<UUID, Deque<Instant>> commandTimes = new HashMap<>();
    private final Map<UUID, Map<String, Deque<Instant>>> commandDuplicates = new HashMap<>();
    private final Map<UUID, Deque<Instant>> reconnects = new HashMap<>();
    private final Map<String, Deque<Instant>> joinsByIp = new HashMap<>();
    private final Map<String, Map<UUID, Instant>> accountsByIp = new HashMap<>();
    private final Map<UUID, Deque<Instant>> inventoryClicks = new HashMap<>();
    private final Map<UUID, String> lastKnownIps = new HashMap<>();
    private final Map<String, Set<UUID>> allAccountsByIp = new HashMap<>();

    private static final int MIN_DUPLICATE_LENGTH = 4;

    public ModerationSignalTracker(Set<String> sensitiveCommands) {
        this.sensitiveCommands = sensitiveCommands;
    }

    /** Direcciones que no identifican a un jugador: loopback, red privada/CGNAT, link-local o desconocida. */
    public static boolean sharedAddress(String ip) {
        if (ip == null || ip.isBlank() || "unknown".equals(ip)) {
            return true;
        }
        try {
            java.net.InetAddress address = java.net.InetAddress.ofLiteral(ip);
            if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()) {
                return true;
            }
            byte[] bytes = address.getAddress();
            // 100.64.0.0/10 (CGNAT, tuneles tipo playit/tailscale) y fc00::/7 (IPv6 privada)
            return bytes.length == 4 && (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64
                || bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        } catch (IllegalArgumentException invalid) {
            return true;
        }
    }

    /** Llega desde el hilo asincrono del chat (puede haber varios a la vez): por eso todo el tracker es synchronized. */
    public synchronized List<AbuseSignal> chat(Instant now, UUID playerId, String playerName, String message) {
        List<AbuseSignal> signals = new ArrayList<>();
        if (threshold(chatTimes, playerId, now, Duration.ofSeconds(5), 8)) {
            signals.add(signal(now, playerId, playerName, "chat-rate", 20, "8 mensajes en 5 segundos"));
        }
        String normalized = normalize(message);
        // respuestas cortas ("gg", "xd", "si") se repiten sin ser spam
        if (normalized.length() >= MIN_DUPLICATE_LENGTH
            && duplicate(chatDuplicates, playerId, normalized, now, Duration.ofSeconds(15), 4)) {
            signals.add(signal(now, playerId, playerName, "chat-duplicate", 25, "4 mensajes normalizados idénticos"));
        }
        return signals;
    }

    public synchronized List<AbuseSignal> command(
        Instant now, UUID playerId, String playerName, String command, boolean operator
    ) {
        List<AbuseSignal> signals = new ArrayList<>();
        if (threshold(commandTimes, playerId, now, Duration.ofSeconds(5), 12)) {
            signals.add(signal(now, playerId, playerName, "command-rate", 20, "12 comandos en 5 segundos"));
        }
        String normalized = normalizeCommand(command);
        if (duplicate(commandDuplicates, playerId, normalized, now, Duration.ofSeconds(10), 6)) {
            signals.add(signal(now, playerId, playerName, "command-duplicate", 25, "6 comandos idénticos en 10 segundos"));
        }
        String root = normalized.split(" ", 2)[0].replaceFirst("^/", "");
        if (!operator && sensitiveCommands.contains(root)) {
            signals.add(signal(now, playerId, playerName, "sensitive-command", 35, "Intento no autorizado: /" + root));
        }
        return signals;
    }

    /**
     * @param sharedAddress la IP no identifica a una persona (loopback, red local, tunel o proxy sin forwarding): todos
     *                      los jugadores la comparten, asi que las senales por IP no significan nada.
     */
    public synchronized List<AbuseSignal> join(
        Instant now, UUID playerId, String playerName, String ip, boolean sharedAddress
    ) {
        List<AbuseSignal> signals = new ArrayList<>();
        if (threshold(reconnects, playerId, now, Duration.ofSeconds(60), 4)) {
            signals.add(signal(now, playerId, playerName, "reconnect-rate", 20, "4 reconexiones en 60 segundos"));
        }
        if (sharedAddress) {
            return signals;
        }
        Deque<Instant> ipJoins = joinsByIp.computeIfAbsent(ip, ignored -> new ArrayDeque<>());
        appendAndPurge(ipJoins, now, Duration.ofSeconds(60));
        if (ipJoins.size() == 6) {
            signals.add(signal(now, playerId, playerName, "ip-join-rate", 35, "6 ingresos desde una IP en 60 segundos"));
        }
        Map<UUID, Instant> accounts = accountsByIp.computeIfAbsent(ip, ignored -> new HashMap<>());
        accounts.entrySet().removeIf(entry -> entry.getValue().isBefore(now.minus(Duration.ofMinutes(10))));
        accounts.put(playerId, now);
        if (accounts.size() == 3) {
            signals.add(signal(now, playerId, playerName, "ip-account-rate", 40, "3 UUID desde una IP en 10 minutos"));
        }

        String previousIp = lastKnownIps.get(playerId);
        if (previousIp != null && !previousIp.equals(ip)) {
            Set<UUID> otherAccounts = allAccountsByIp.getOrDefault(ip, Set.of());
            long others = otherAccounts.stream().filter(id -> !id.equals(playerId)).count();
            if (others > 0) {
                signals.add(signal(now, playerId, playerName, "ip-sudden-change", 100, "Cambio de IP abrupto cruzado con " + others + " cuenta(s) en " + ip));
            }
        }
        lastKnownIps.put(playerId, ip);
        allAccountsByIp.computeIfAbsent(ip, k -> new java.util.HashSet<>()).add(playerId);

        return signals;
    }

    public synchronized Optional<AbuseSignal> inventoryClick(Instant now, UUID playerId, String playerName) {
        // mods de inventario legitimos (arrastre con la rueda, shift-click masivo) pasan facil de 40 clics por segundo
        if (threshold(inventoryClicks, playerId, now, Duration.ofSeconds(1), 60)) {
            return Optional.of(signal(now, playerId, playerName, "inventory-click-rate", 15, "60 clics de inventario por segundo"));
        }
        return Optional.empty();
    }

    private boolean threshold(
        Map<UUID, Deque<Instant>> values,
        UUID playerId,
        Instant now,
        Duration window,
        int threshold
    ) {
        Deque<Instant> times = values.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        appendAndPurge(times, now, window);
        if (times.size() == threshold) {
            times.clear();
            return true;
        }
        return false;
    }

    private boolean duplicate(
        Map<UUID, Map<String, Deque<Instant>>> values,
        UUID playerId,
        String normalized,
        Instant now,
        Duration window,
        int threshold
    ) {
        Deque<Instant> times = values.computeIfAbsent(playerId, ignored -> new HashMap<>())
            .computeIfAbsent(normalized, ignored -> new ArrayDeque<>());
        appendAndPurge(times, now, window);
        if (times.size() == threshold) {
            times.clear();
            return true;
        }
        return false;
    }

    private void appendAndPurge(Deque<Instant> times, Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.removeFirst();
        }
        times.addLast(now);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private String normalizeCommand(String command) {
        return normalize(command);
    }

    private AbuseSignal signal(Instant now, UUID id, String name, String rule, int points, String evidence) {
        return new AbuseSignal(now, id, name, rule, points, evidence);
    }
}
