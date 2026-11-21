package dev.linqfy.bigCasares.modules.quadrants.quests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PvpQuestStoreTest {
    @TempDir Path directory;

    @Test
    void pendingPaymentSurvivesRestartAndDoesNotBlockTheOtherWeeklyQuest() throws IOException {
        UUID player = UUID.randomUUID(), opponent = UUID.randomUUID();
        PvpQuestStore store = new PvpQuestStore(directory);
        PvpQuestService first = new PvpQuestService(store, 1, 1, 1, 300, 500);
        first.start(player, "rivals", "2026-11-16");
        first.kill(player, opponent, "2026-11-16");
        assertThrows(IllegalStateException.class, () -> first.claim(player, "rivals", "2026-11-16",
            (id, amount) -> { throw new IllegalStateException("unknown outcome"); }));
        PvpQuestService restarted = new PvpQuestService(new PvpQuestStore(directory), 1, 1, 1, 300, 500);
        assertEquals(PvpQuestState.Claim.PENDING, restarted.status(player, "2026-11-23").rivalClaim());
        assertTrue(restarted.start(player, "domination", "2026-11-23"));
        AtomicInteger deposits = new AtomicInteger();
        assertFalse(restarted.claim(player, "rivals", "2026-11-23", (id, amount) -> deposits.incrementAndGet()));
        assertEquals(0, deposits.get());
    }

    @Test
    void corruptFileFailsClosed() throws IOException {
        UUID player = UUID.randomUUID();
        Files.writeString(directory.resolve(player + ".yml"), "version: 99\n");
        assertThrows(IOException.class, () -> new PvpQuestStore(directory).load(player));
        Files.writeString(directory.resolve(player + ".yml"), "version: 1\nrivals:\n  week: 2026-11-16\n");
        assertThrows(IOException.class, () -> new PvpQuestStore(directory).load(player));
    }
}
