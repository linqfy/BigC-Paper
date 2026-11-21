package dev.linqfy.bigCasares.modules.quadrants.quests;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Un archivo por jugador. Ante archivos dañados o fallos de disco se rechaza el reclamo. */
public final class PvpQuestStore implements PvpQuestService.Store {
    private final Path directory;
    private boolean directoriesDurable;

    public PvpQuestStore(Path directory) { this.directory = directory; }

    @Override
    public PvpQuestState load(UUID player) throws IOException {
        Path file = directory.resolve(player + ".yml");
        if (!Files.exists(file)) return PvpQuestState.empty();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(file));
            if (yaml.getInt("version", -1) != 1 || !yaml.isString("rivals.week")
                || !yaml.isString("domination.week") || !yaml.isBoolean("rivals.started")
                || !yaml.isBoolean("domination.started") || !yaml.isString("rivals.claim")
                || !yaml.isString("domination.claim") || !yaml.isList("rivals.opponents")
                || !yaml.isList("domination.opponents")) {
                throw new IllegalArgumentException("registro incompleto o versión desconocida");
            }
            PvpQuestState state = PvpQuestState.empty();
            state.rivalWeek = yaml.getString("rivals.week", "");
            state.dominationWeek = yaml.getString("domination.week", "");
            state.rivalStarted = yaml.getBoolean("rivals.started");
            state.dominationStarted = yaml.getBoolean("domination.started");
            state.rivalClaim = PvpQuestState.Claim.valueOf(yaml.getString("rivals.claim"));
            state.dominationClaim = PvpQuestState.Claim.valueOf(yaml.getString("domination.claim"));
            for (String id : yaml.getStringList("rivals.opponents")) state.rivals.add(UUID.fromString(id));
            for (String id : yaml.getStringList("domination.opponents")) state.dominated.add(UUID.fromString(id));
            var section = yaml.getConfigurationSection("domination.streaks");
            if (section != null) for (String id : section.getKeys(false)) {
                int value = section.getInt(id, -1);
                if (value < 1) throw new IllegalArgumentException("racha inválida");
                state.streaks.put(UUID.fromString(id), value);
            }
            return state;
        } catch (InvalidConfigurationException | IllegalArgumentException ex) {
            throw new IOException("Datos de misión PvP inválidos para " + player, ex);
        }
    }

    @Override
    public void save(UUID player, PvpQuestState state) throws IOException {
        if (!Files.isDirectory(directory)) directoriesDurable = false;
        Files.createDirectories(directory);
        // Persist newly created data/ and pvp-quests/ directory entries before a payment can be made.
        if (!directoriesDurable && directory.getParent() != null) {
            forceDirectory(directory.getParent());
            if (directory.getParent().getParent() != null) forceDirectory(directory.getParent().getParent());
            directoriesDurable = true;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        yaml.set("rivals.week", state.rivalWeek);
        yaml.set("rivals.started", state.rivalStarted);
        yaml.set("rivals.claim", state.rivalClaim.name());
        yaml.set("rivals.opponents", state.rivals.stream().map(UUID::toString).sorted().toList());
        yaml.set("domination.week", state.dominationWeek);
        yaml.set("domination.started", state.dominationStarted);
        yaml.set("domination.claim", state.dominationClaim.name());
        yaml.set("domination.opponents", state.dominated.stream().map(UUID::toString).sorted().toList());
        state.streaks.forEach((id, streak) -> yaml.set("domination.streaks." + id, streak));
        byte[] bytes = yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        Path tmp = Files.createTempFile(directory, ".pvp-quest-", ".tmp");
        try {
            try (FileChannel out = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) out.write(buffer);
                out.force(true);
            }
            try {
                Files.move(tmp, directory.resolve(player + ".yml"), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw new IOException("El sistema de archivos no admite guardado atómico", unsupported);
            }
            forceDirectory(directory);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void forceDirectory(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }
}
