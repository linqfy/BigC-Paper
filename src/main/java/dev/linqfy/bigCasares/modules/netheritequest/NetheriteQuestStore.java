package dev.linqfy.bigCasares.modules.netheritequest;

import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.ActiveQuest;
import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.PendingSoul;
import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.PlayerView;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Keeps quest progress, used souls and souls owed to offline players in one YAML file. */
public final class NetheriteQuestStore {

    private final Path file;

    public NetheriteQuestStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public void loadInto(NetheriteQuestBook book) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file));
        } catch (InvalidConfigurationException invalid) {
            throw new IOException("Archivo de misiones de netherita inválido: " + file, invalid);
        }
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection section = players.getConfigurationSection(key);
            if (section != null) {
                book.restore(playerId, read(section));
            }
        }
    }

    public void save(NetheriteQuestBook book) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        book.snapshot().forEach((playerId, view) -> write(yaml.createSection("players." + playerId), view));
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, yaml.saveToString());
        Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static PlayerView read(ConfigurationSection section) {
        EnumMap<ArmorQuestPiece, ActiveQuest> active = new EnumMap<>(ArmorQuestPiece.class);
        ConfigurationSection activeSection = section.getConfigurationSection("active");
        if (activeSection != null) {
            for (String key : activeSection.getKeys(false)) {
                ConfigurationSection quest = activeSection.getConfigurationSection(key);
                String soul = quest == null ? null : quest.getString("soul");
                ArmorQuestPiece.parse(key).filter(ignored -> soul != null).ifPresent(piece -> active.put(piece,
                    new ActiveQuest(quest.getLong("started"), soul, Math.max(0.0, quest.getDouble("progress")))));
            }
        }
        EnumMap<ArmorQuestPiece, String> souls = new EnumMap<>(ArmorQuestPiece.class);
        ConfigurationSection soulSection = section.getConfigurationSection("souls");
        if (soulSection != null) {
            for (String key : soulSection.getKeys(false)) {
                String boss = soulSection.getString(key);
                ArmorQuestPiece.parse(key).filter(ignored -> boss != null).ifPresent(piece -> souls.put(piece, boss));
            }
        }
        EnumMap<ArmorQuestPiece, Integer> completions = new EnumMap<>(ArmorQuestPiece.class);
        ConfigurationSection completionSection = section.getConfigurationSection("completions");
        if (completionSection != null) {
            for (String key : completionSection.getKeys(false)) {
                int count = completionSection.getInt(key);
                ArmorQuestPiece.parse(key).filter(ignored -> count > 0)
                    .ifPresent(piece -> completions.put(piece, count));
            }
        }
        Map<String, PendingSoul> pending = new LinkedHashMap<>();
        ConfigurationSection pendingSection = section.getConfigurationSection("pending-souls");
        if (pendingSection != null) {
            for (String boss : pendingSection.getKeys(false)) {
                int amount = pendingSection.getInt(boss + ".amount");
                if (amount > 0) {
                    pending.put(boss, new PendingSoul(pendingSection.getString(boss + ".name", boss), amount));
                }
            }
        }
        return new PlayerView(section.getInt("activations"), active, souls, completions, pending);
    }

    private static void write(ConfigurationSection section, PlayerView view) {
        section.set("activations", view.activations());
        view.active().forEach((piece, quest) -> {
            String path = "active." + piece.id();
            section.set(path + ".started", quest.startedAt());
            section.set(path + ".soul", quest.soulBoss());
            section.set(path + ".progress", quest.progress());
        });
        view.souls().forEach((piece, boss) -> section.set("souls." + piece.id(), boss));
        view.completions().forEach((piece, count) -> section.set("completions." + piece.id(), count));
        view.pendingSouls().forEach((boss, soul) -> {
            section.set("pending-souls." + boss + ".name", soul.bossName());
            section.set("pending-souls." + boss + ".amount", soul.amount());
        });
    }
}
