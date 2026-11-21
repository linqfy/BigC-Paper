package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lo que el modulo le debe a un jugador que no estaba conectado: el Invocador de un jefe que desaparecio sin ser
 * derrotado, o su premio de un jefe que cayo. Se guarda en un archivo propio (escritura atomica: temporal y despues
 * {@code ATOMIC_MOVE}) y se entrega una sola vez, al entrar. Sin servidor.
 */
final class MythicPendingDeliveries {

    static final String FILE = "mythic-dungeons-pending.yml";

    enum Kind {
        /** El Invocador vuelve a quien lo uso. */
        SUMMONER,
        /** El premio (experiencia y botin) de un puesto del podio. */
        REWARD
    }

    /** Una entrega con id estable: el mismo id nunca se entrega dos veces. */
    record Delivery(UUID id, UUID playerId, Kind kind, MythicCulture culture, int placement, int xp,
                    List<MythicDungeonSettings.RewardItem> items) {

        Delivery {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(culture, "culture");
            items = List.copyOf(items);
        }

        static Delivery summoner(UUID playerId, MythicCulture culture) {
            return new Delivery(UUID.randomUUID(), playerId, Kind.SUMMONER, culture, 0, 0, List.of());
        }

        static Delivery reward(UUID playerId, MythicCulture culture, int placement, int xp,
                               List<MythicDungeonSettings.RewardItem> items) {
            return new Delivery(UUID.randomUUID(), playerId, Kind.REWARD, culture, placement, xp, items);
        }
    }

    private final Path file;
    private final Logger logger;
    private final Map<UUID, Delivery> pending = new LinkedHashMap<>();

    MythicPendingDeliveries(Path file, Logger logger) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        load();
    }

    synchronized void add(Delivery delivery) {
        pending.put(delivery.id(), Objects.requireNonNull(delivery, "delivery"));
        persist();
    }

    synchronized List<Delivery> pending(UUID playerId) {
        return pending.values().stream().filter(delivery -> delivery.playerId().equals(playerId)).toList();
    }

    synchronized int size() {
        return pending.size();
    }

    /** Saca la entrega para darla; {@code true} solo la primera vez (quien recibe {@code true} la entrega). */
    synchronized boolean take(UUID deliveryId) {
        if (pending.remove(deliveryId) == null) {
            return false;
        }
        persist();
        return true;
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("pending");
        if (root == null) {
            return;
        }
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            try {
                Delivery delivery = read(UUID.fromString(rawId), Objects.requireNonNull(section, "section"));
                pending.put(delivery.id(), delivery);
            } catch (RuntimeException invalid) {
                logger.warning("[Mazmorras] Entrega pendiente invalida en " + FILE + " (" + rawId + "): "
                    + invalid.getMessage());
            }
        }
    }

    private static Delivery read(UUID id, ConfigurationSection section) {
        UUID player = UUID.fromString(Objects.requireNonNull(section.getString("player"), "player"));
        Kind kind = Kind.valueOf(Objects.requireNonNull(section.getString("kind"), "kind").toUpperCase(Locale.ROOT));
        MythicCulture culture = MythicCulture.byId(Objects.requireNonNull(section.getString("culture"), "culture"))
            .orElseThrow(() -> new IllegalArgumentException("cultura desconocida " + section.getString("culture")));
        List<MythicDungeonSettings.RewardItem> items = new ArrayList<>();
        for (String raw : section.getStringList("items")) {
            String[] parts = raw.split(":");
            Material material = Optional.ofNullable(Material.matchMaterial(parts[0]))
                .orElseThrow(() -> new IllegalArgumentException("material desconocido " + parts[0]));
            items.add(new MythicDungeonSettings.RewardItem(material, parts.length > 1 ? Integer.parseInt(parts[1]) : 1));
        }
        return new Delivery(id, player, kind, culture, section.getInt("placement"), section.getInt("xp"), items);
    }

    private void persist() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Delivery delivery : pending.values()) {
            String path = "pending." + delivery.id();
            yaml.set(path + ".player", delivery.playerId().toString());
            yaml.set(path + ".kind", delivery.kind().name());
            yaml.set(path + ".culture", delivery.culture().id());
            if (delivery.kind() == Kind.REWARD) {
                yaml.set(path + ".placement", delivery.placement());
                yaml.set(path + ".xp", delivery.xp());
                yaml.set(path + ".items", delivery.items().stream()
                    .map(item -> item.material().name() + ":" + item.amount()).toList());
            }
        }
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            // queda en memoria: se entrega igual mientras el servidor siga prendido
            logger.log(Level.WARNING, "[Mazmorras] No se pudieron guardar las entregas pendientes en " + file, failure);
        }
    }
}
