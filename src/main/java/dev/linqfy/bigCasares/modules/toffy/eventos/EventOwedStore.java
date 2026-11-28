package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Prizes and permanent hearts that players are owed but could not get when the event ended (they were offline). They
 * are handed out when the player next joins. Stored in {@code owed.yml} of the event data folder.
 *
 * <p>This store is the only channel that pays these prizes. A file that cannot be read is moved aside (never
 * overwritten); when it cannot even be moved, the store refuses to save or pay anything until the next start.</p>
 */
final class EventOwedStore {

    /** A reward of one event: the event id and type for the message, and what to give. */
    record Reward(String eventId, ToffyEventType type, ToffyEventSettings.Rewards rewards) {
        Reward {
            rewards = rewards == null ? ToffyEventSettings.Rewards.none() : rewards;
        }
    }

    /** Everything one player is owed: permanent hearts and rewards. */
    record Owed(int hearts, List<Reward> rewards) {
        Owed {
            rewards = List.copyOf(rewards);
        }

        boolean isEmpty() {
            return hearts <= 0 && rewards.isEmpty();
        }
    }

    private final Path file;
    private final Map<UUID, Owed> owed = new LinkedHashMap<>();
    private boolean loadFailed;

    EventOwedStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    Path file() {
        return file;
    }

    /**
     * Reads the file. Never throws: a missing file means nothing is owed; a file that cannot be read is moved aside and
     * the store starts empty; invalid player keys are skipped one by one.
     *
     * @return warnings for the log
     */
    List<String> load() {
        List<String> warnings = new ArrayList<>();
        owed.clear();
        loadFailed = false;
        if (Files.notExists(file)) {
            return warnings;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException | RuntimeException broken) {
            moveAside(broken, warnings);
            return warnings;
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return warnings;
        }
        for (String id : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(id);
            } catch (IllegalArgumentException invalid) {
                warnings.add("owed.yml: la clave '" + id + "' no es un UUID; se ignora.");
                continue;
            }
            ConfigurationSection section = players.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<Reward> rewards = new ArrayList<>();
            ConfigurationSection list = section.getConfigurationSection("rewards");
            if (list != null) {
                for (String index : list.getKeys(false)) {
                    ConfigurationSection reward = list.getConfigurationSection(index);
                    if (reward != null) {
                        rewards.add(read(reward));
                    }
                }
            }
            Owed entry = new Owed(section.getInt("hearts", 0), rewards);
            if (!entry.isEmpty()) {
                owed.put(playerId, entry);
            }
        }
        return warnings;
    }

    private void moveAside(Exception cause, List<String> warnings) {
        Path broken = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
        try {
            Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING);
            warnings.add("owed.yml no se pudo leer (" + cause.getMessage() + "); se movio a " + broken
                + " y se empieza vacio.");
        } catch (IOException | RuntimeException moveFailed) {
            loadFailed = true;
            warnings.add("owed.yml no se pudo leer ni mover (" + cause.getMessage() + "): no se paga ni se guarda"
                + " nada hasta reiniciar. Revisá " + file);
        }
    }

    /** Whether the store could read its file and may save and pay. */
    boolean writable() {
        return !loadFailed;
    }

    /** Writes the whole store; a player with nothing owed is left out. Does nothing when the store is not writable. */
    void save() throws IOException {
        if (loadFailed) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Owed> entry : owed.entrySet()) {
            String base = "players." + entry.getKey();
            yaml.set(base + ".hearts", entry.getValue().hearts());
            int index = 0;
            for (Reward reward : entry.getValue().rewards()) {
                String path = base + ".rewards." + (index++);
                yaml.set(path + ".event", reward.eventId());
                yaml.set(path + ".type", reward.type().name());
                yaml.set(path + ".items", reward.rewards().items());
                yaml.set(path + ".commands", reward.rewards().commands());
            }
        }
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** What the player is owed, if anything. */
    Optional<Owed> find(UUID playerId) {
        return Optional.ofNullable(owed.get(playerId));
    }

    boolean has(UUID playerId) {
        return owed.containsKey(playerId);
    }

    /** Adds permanent hearts to what the player is owed. Does not save. */
    void addHearts(UUID playerId, int hearts) {
        if (hearts <= 0) {
            return;
        }
        Owed current = owed.getOrDefault(playerId, new Owed(0, List.of()));
        owed.put(playerId, new Owed(current.hearts() + hearts, current.rewards()));
    }

    /** Adds a reward to what the player is owed. Does not save. */
    void addReward(UUID playerId, Reward reward) {
        Owed current = owed.getOrDefault(playerId, new Owed(0, List.of()));
        List<Reward> rewards = new ArrayList<>(current.rewards());
        rewards.add(reward);
        owed.put(playerId, new Owed(current.hearts(), rewards));
    }

    /** Removes everything the player is owed and returns it. Does not save. */
    Optional<Owed> take(UUID playerId) {
        return Optional.ofNullable(owed.remove(playerId));
    }

    /** Puts back what {@link #take} removed, when the removal could not be saved. */
    void keep(UUID playerId, Owed entry) {
        owed.put(playerId, entry);
    }

    private static Reward read(ConfigurationSection section) {
        ToffyEventType type;
        try {
            type = ToffyEventType.valueOf(section.getString("type", ""));
        } catch (IllegalArgumentException unknown) {
            type = ToffyEventType.LIFESTEAL;
        }
        return new Reward(section.getString("event", ""), type, new ToffyEventSettings.Rewards(
            section.getStringList("items"), section.getStringList("commands")));
    }
}
