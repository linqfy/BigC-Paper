package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventRewardJournalTest {
    @TempDir Path temporary;

    @Test
    void blockedWinnerEvidenceSurvivesANewJournalInstance() throws Exception {
        EventRewardJournal.Pending entry = entry();
        EventRewardJournal journal = new EventRewardJournal(temporary.resolve("pending"));
        Path file = journal.record(entry);
        EventRewardJournal reloaded = new EventRewardJournal(temporary.resolve("pending"));
        assertTrue(reloaded.hasPending());
        assertEquals(file, reloaded.record(entry));
        YamlConfiguration stored = YamlConfiguration.loadConfiguration(file.toFile());
        assertEquals("manual-review", stored.getString("status"));
        assertEquals(entry.eventId().toString(), stored.getString("event-id"));
        assertEquals(entry.playerId().toString(), stored.getString("player-id"));
        assertEquals(entry.playerName(), stored.getString("player-name"));
        assertEquals(entry.type().name(), stored.getString("event-type"));
        assertEquals(entry.rewards().items(), stored.getStringList("rewards.items"));
        assertEquals(entry.rewards().commands(), stored.getStringList("rewards.commands"));
    }

    @Test
    void conflictingEvidenceIsNotOverwritten() throws Exception {
        EventRewardJournal.Pending original = entry();
        EventRewardJournal journal = new EventRewardJournal(temporary.resolve("pending"));
        Path file = journal.record(original);
        String saved = Files.readString(file);
        EventRewardJournal.Pending conflict = new EventRewardJournal.Pending(original.eventId(), original.playerId(),
            original.playerName(), original.type(), original.time(),
            new ToffyEventSettings.Rewards(List.of("minecraft:stone:1"), List.of()));
        assertThrows(IOException.class, () -> journal.record(conflict));
        assertEquals(saved, Files.readString(file));
    }

    @Test
    void storageFailureDoesNotPretendTheEvidenceWasRecorded() throws Exception {
        Path file = temporary.resolve("not-a-directory");
        Files.writeString(file, "occupied");
        assertThrows(IOException.class, () -> new EventRewardJournal(file).record(entry()));
        assertTrue(Files.isRegularFile(file));
    }

    private static EventRewardJournal.Pending entry() {
        return new EventRewardJournal.Pending(UUID.randomUUID(), UUID.randomUUID(), "Jugador", ToffyEventType.KOTH,
            Instant.parse("2026-11-21T00:00:00Z"),
            new ToffyEventSettings.Rewards(List.of("minecraft:diamond:2"), List.of("say ganó {player}")));
    }
}
