package dev.linqfy.bigCasares.modules.legendary;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Synchronous, atomic journal. Fail closed instead of forgetting ownership on IO errors. */
public final class LegendaryStateStore {
    private final Path path;
    private final Map<String, String> values = new LinkedHashMap<>();
    private boolean writeFailed;

    public LegendaryStateStore(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            Properties properties = new Properties();
            try (var input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(input);
            }
            properties.forEach((key, value) -> values.put(key.toString(), value.toString()));
        }
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(values);
    }

    public String get(String key) {
        return values.get(key);
    }

    public void put(String key, String value) throws IOException {
        if (writeFailed)
            throw new IOException(
                    "El diario necesita recuperarse antes de aceptar nuevas transacciones.");
        if (java.util.Objects.equals(values.get(key), value)) return;
        Map<String, String> next = new LinkedHashMap<>(values);
        if (value == null) {
            next.remove(key);
        } else {
            next.put(key, value);
        }
        try {
            persist(next);
        } catch (IOException failure) {
            writeFailed = true;
            throw failure;
        }
    }

    public long remaining(UUID player, LegendaryWeapon weapon, long now) {
        String value = get(cooldownKey(player, weapon));
        return value == null ? 0 : Math.max(0, Long.parseLong(value) - now);
    }

    public boolean beginCooldown(UUID player, LegendaryWeapon weapon, long now) throws IOException {
        if (remaining(player, weapon, now) > 0) {
            return false;
        }
        put(
                cooldownKey(player, weapon),
                Long.toString(Math.addExact(now, LegendaryWeapon.COOLDOWN_MILLIS)));
        return true;
    }

    public long remaining(UUID player, LegendaryWeapon weapon, boolean secondary, long now) {
        String current = get(cooldownKey(player, weapon) + (secondary ? ".secondary" : ".primary"));
        return Math.max(
                remaining(player, weapon, now),
                current == null ? 0 : Math.max(0, Long.parseLong(current) - now));
    }

    public boolean beginCooldown(
            UUID player, LegendaryWeapon weapon, boolean secondary, long now, long duration)
            throws IOException {
        if (duration <= 0) throw new IllegalArgumentException("Invalid cooldown");
        if (remaining(player, weapon, secondary, now) > 0) return false;
        put(
                cooldownKey(player, weapon) + (secondary ? ".secondary" : ".primary"),
                Long.toString(Math.addExact(now, duration)));
        return true;
    }

    public void resetCooldowns(UUID player) throws IOException {
        for (String key : snapshot().keySet())
            if (key.startsWith("cooldown." + player + ".")) put(key, null);
    }

    long abilityRemaining(UUID player, LegendaryElementalSkill skill, long now) {
        String current = get(cooldownKey(player, skill.weapon) + "." + skill.id);
        return Math.max(
                remaining(player, skill.weapon, now),
                current == null ? 0 : Math.max(0, Long.parseLong(current) - now));
    }

    boolean beginAbilityCooldown(
            UUID player, LegendaryElementalSkill skill, long now, long duration)
            throws IOException {
        if (duration <= 0) throw new IllegalArgumentException("Invalid cooldown");
        if (abilityRemaining(player, skill, now) > 0) return false;
        put(
                cooldownKey(player, skill.weapon) + "." + skill.id,
                Long.toString(Math.addExact(now, duration)));
        return true;
    }

    private static String cooldownKey(UUID player, LegendaryWeapon weapon) {
        return "cooldown." + player + "." + weapon.abilityId;
    }

    private void persist(Map<String, String> next) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Properties properties = new Properties();
        properties.putAll(next);
        java.io.StringWriter writer = new java.io.StringWriter();
        properties.store(writer, "BigCasares Legendary Weapons");
        Path temporary =
                Files.createTempFile(path.toAbsolutePath().getParent(), "legendary-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = StandardCharsets.UTF_8.encode(writer.toString());
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            // Reflect the committed rename even if directory fsync fails. No
            // subsequent write may erase an uncertain ownership transition.
            values.clear();
            values.putAll(next);
            LegendaryDurability.forceDirectory(path.toAbsolutePath().getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
