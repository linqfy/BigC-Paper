package dev.linqfy.bigCasares.modules.toffy.eventos;

import dev.linqfy.bigCasares.module.runtime.PlayerDataCheckpoint;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable evidence for a reward that an operator must reconcile manually. */
final class EventRewardJournal {
    record Pending(UUID eventId, UUID playerId, String playerName, ToffyEventType type,
                   Instant time, ToffyEventSettings.Rewards rewards) {
        Pending {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(playerName, "playerName");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(rewards, "rewards");
        }
    }

    private final Path directory;

    EventRewardJournal(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    /** Whether an entry still needs a person to pay it by hand (status manual-review, from older versions). */
    boolean hasPending() throws IOException {
        if (Files.notExists(directory)) return false;
        try (var files = Files.newDirectoryStream(directory, "*.yml")) {
            for (Path file : files) {
                if (MANUAL_REVIEW.equals(YamlConfiguration.loadConfiguration(file.toFile()).getString("status"))) {
                    return true;
                }
            }
            return false;
        }
    }

    Path directory() {
        return directory;
    }

    /** Status of an entry someone has to pay by hand. Never written now: prizes are paid by the owed store. */
    static final String MANUAL_REVIEW = "manual-review";
    /** Status of an audit entry whose prize the owed store pays on the player's next join. */
    static final String AUTO_OWED = "auto-owed";

    Path record(Pending pending) throws IOException {
        return record(pending, MANUAL_REVIEW, "inventory-restoration-unconfirmed");
    }

    Path record(Pending pending, String status, String reason) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("status", status);
        yaml.set("reason", reason);
        yaml.set("event-id", pending.eventId().toString());
        yaml.set("player-id", pending.playerId().toString());
        yaml.set("player-name", pending.playerName());
        yaml.set("event-type", pending.type().name());
        yaml.set("recorded-at", pending.time().toString());
        yaml.set("rewards.items", pending.rewards().items());
        yaml.set("rewards.commands", pending.rewards().commands());
        String content = yaml.saveToString();
        Files.createDirectories(directory);
        Path target = directory.resolve(pending.eventId() + "-" + pending.playerId() + ".yml");
        if (Files.exists(target)) {
            if (!Files.readString(target, StandardCharsets.UTF_8).equals(content)) {
                throw new IOException("Conflicting pending reward evidence: " + target);
            }
        } else {
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            PlayerDataCheckpoint.forceFile(temporary);
            try {
                // A hard link publishes the fully written file without ever replacing existing evidence.
                Files.createLink(target, temporary);
            } catch (FileAlreadyExistsException conflict) {
                if (!Files.readString(target, StandardCharsets.UTF_8).equals(content)) {
                    throw new IOException("Conflicting pending reward evidence: " + target, conflict);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        PlayerDataCheckpoint.forceFile(target);
        PlayerDataCheckpoint.forceDirectory(directory);
        PlayerDataCheckpoint.forceDirectory(directory.getParent());
        return target;
    }
}
