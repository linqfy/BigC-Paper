package dev.linqfy.bigCasares.modules.sift;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftiteRecoveryBookTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> texts(List<byte[]> entries) {
        return entries.stream().map(entry -> new String(entry, StandardCharsets.UTF_8)).toList();
    }

    /** A store in memory that can be made to fail. */
    private static final class MemoryStore implements SiftiteRecoveryBook.Store {
        final Map<UUID, List<byte[]>> saved = new HashMap<>();
        boolean failSaves;
        int loads;

        @Override
        public List<byte[]> load(UUID player) {
            loads++;
            return saved.getOrDefault(player, List.of());
        }

        @Override
        public void save(UUID player, List<byte[]> entries) throws IOException {
            if (failSaves) {
                throw new IOException("disk full");
            }
            saved.put(player, new ArrayList<>(entries));
        }
    }

    @Test
    void deathsAccumulateInOrderAndArePersistedBeforeTheyAreKnown() throws IOException {
        MemoryStore store = new MemoryStore();
        SiftiteRecoveryBook book = new SiftiteRecoveryBook(store);
        assertFalse(book.hasPending(ALEX));
        book.capture(ALEX, List.of(bytes("sword"), bytes("ingot")));
        book.capture(ALEX, List.of(bytes("helmet")));
        book.capture(BEA, List.of(bytes("axe")));
        assertEquals(List.of("sword", "ingot", "helmet"), texts(book.pending(ALEX)));
        assertEquals(List.of("axe"), texts(book.pending(BEA)));
        assertEquals(List.of("sword", "ingot", "helmet"), texts(store.saved.get(ALEX)));
    }

    @Test
    void aFailedSaveChangesNothing() throws IOException {
        MemoryStore store = new MemoryStore();
        SiftiteRecoveryBook book = new SiftiteRecoveryBook(store);
        book.capture(ALEX, List.of(bytes("sword")));
        store.failSaves = true;
        assertThrows(IOException.class, () -> book.capture(ALEX, List.of(bytes("ingot"))));
        assertThrows(IOException.class, () -> book.delivered(ALEX, bytes("sword")));
        assertEquals(List.of("sword"), texts(book.pending(ALEX)), "memory still matches the disk");
        assertEquals(List.of("sword"), texts(store.saved.get(ALEX)));
    }

    @Test
    void capturedBytesAreCopied() throws IOException {
        SiftiteRecoveryBook book = new SiftiteRecoveryBook(new MemoryStore());
        byte[] original = bytes("sword");
        book.capture(ALEX, List.of(original));
        original[0] = 'X';
        assertEquals(List.of("sword"), texts(book.pending(ALEX)));
    }

    @Test
    void respawnSchedulesFortyTicksAheadAndReplacesButJoinKeepsTheEarlierSchedule() throws IOException {
        SiftiteRecoveryBook book = new SiftiteRecoveryBook(new MemoryStore());
        assertFalse(book.afterRespawn(ALEX, 100), "nothing owed, nothing scheduled");
        assertFalse(book.afterJoin(ALEX, 100));
        assertTrue(book.ready(1_000, id -> true).isEmpty());

        book.capture(ALEX, List.of(bytes("sword")));
        assertTrue(book.afterJoin(ALEX, 100));
        assertTrue(book.afterJoin(ALEX, 130), "join keeps the first schedule");
        assertEquals(List.of(), book.ready(139, id -> true));
        assertEquals(List.of(ALEX), book.ready(140, id -> true));

        assertTrue(book.afterRespawn(ALEX, 200), "respawn replaces it");
        assertEquals(List.of(), book.ready(239, id -> true));
        assertEquals(List.of(ALEX), book.ready(240, id -> true));
    }

    @Test
    void onlyEligiblePlayersAreReadyAndStartingClearsTheSchedule() throws IOException {
        SiftiteRecoveryBook book = new SiftiteRecoveryBook(new MemoryStore());
        book.capture(ALEX, List.of(bytes("a")));
        book.capture(BEA, List.of(bytes("b")));
        book.afterRespawn(ALEX, 0);
        book.afterRespawn(BEA, 10);
        assertEquals(List.of(ALEX, BEA), book.ready(50, id -> true));
        assertEquals(List.of(BEA), book.ready(50, id -> id.equals(BEA)), "a dead or absent player waits");
        assertTrue(book.isDue(ALEX));
        book.started(ALEX);
        assertFalse(book.isDue(ALEX));
        assertEquals(List.of(BEA), book.ready(50, id -> true));
        book.reschedule(ALEX, 60);
        assertEquals(List.of(BEA), book.ready(99, id -> true));
        assertEquals(List.of(BEA, ALEX), book.ready(100, id -> true));
    }

    @Test
    void deliveriesLeaveOnlyWhatIsStillOwedAndAPartialOneKeepsItsPlace() throws IOException {
        MemoryStore store = new MemoryStore();
        SiftiteRecoveryBook book = new SiftiteRecoveryBook(store);
        book.capture(ALEX, List.of(bytes("a"), bytes("b"), bytes("c")));
        assertTrue(book.delivered(ALEX, bytes("a")));
        assertFalse(book.delivered(ALEX, bytes("a")), "already delivered");
        assertTrue(book.partlyDelivered(ALEX, bytes("b"), bytes("b-rest")));
        assertEquals(List.of("b-rest", "c"), texts(book.pending(ALEX)));
        assertEquals(List.of("b-rest", "c"), texts(store.saved.get(ALEX)));
        assertTrue(book.delivered(ALEX, bytes("b-rest")));
        assertTrue(book.delivered(ALEX, bytes("c")));
        assertFalse(book.hasPending(ALEX));
        assertEquals(List.of(), store.saved.get(ALEX));
    }

    @Test
    void identicalStacksAreDeliveredOneAtATime() throws IOException {
        SiftiteRecoveryBook book = new SiftiteRecoveryBook(new MemoryStore());
        book.capture(ALEX, List.of(bytes("ingot"), bytes("ingot")));
        assertTrue(book.delivered(ALEX, bytes("ingot")));
        assertEquals(List.of("ingot"), texts(book.pending(ALEX)));
    }

    @Test
    void forgettingAPlayerReloadsFromTheStoreAndDropsTheSchedule() throws IOException {
        MemoryStore store = new MemoryStore();
        SiftiteRecoveryBook book = new SiftiteRecoveryBook(store);
        book.capture(ALEX, List.of(bytes("sword")));
        book.afterRespawn(ALEX, 0);
        book.forget(ALEX);
        assertFalse(book.isDue(ALEX));
        int loadsBefore = store.loads;
        assertEquals(List.of("sword"), texts(book.pending(ALEX)));
        assertEquals(loadsBefore + 1, store.loads);
        book.afterRespawn(ALEX, 0);
        book.clearTransient();
        assertFalse(book.isDue(ALEX));
        assertEquals(List.of("sword"), texts(store.saved.get(ALEX)), "the store is untouched");
    }

    // --- files -----------------------------------------------------------------------------------

    @Test
    void theFileFormatRoundTripsListsOfByteArrays() throws IOException {
        List<byte[]> entries = List.of(new byte[0], new byte[] {0, 1, -1, 127, -128}, bytes("héllo"));
        List<byte[]> decoded = SiftiteRecoveryFiles.decode(SiftiteRecoveryFiles.encode(entries));
        assertEquals(entries.size(), decoded.size());
        for (int index = 0; index < entries.size(); index++) {
            assertArrayEquals(entries.get(index), decoded.get(index));
        }
        assertEquals(List.of(), SiftiteRecoveryFiles.decode(SiftiteRecoveryFiles.encode(List.of())));
    }

    @Test
    void malformedDataIsRejected() {
        byte[] good = SiftiteRecoveryFiles.encode(List.of(bytes("abc"), bytes("def")));
        assertThrows(SiftiteRecoveryFiles.MalformedStoreException.class, () -> SiftiteRecoveryFiles.decode(new byte[0]));
        assertThrows(SiftiteRecoveryFiles.MalformedStoreException.class, () -> SiftiteRecoveryFiles.decode(bytes("not a file at all")));
        byte[] truncated = java.util.Arrays.copyOf(good, good.length - 1);
        assertThrows(SiftiteRecoveryFiles.MalformedStoreException.class, () -> SiftiteRecoveryFiles.decode(truncated));
        byte[] trailing = java.util.Arrays.copyOf(good, good.length + 1);
        assertThrows(SiftiteRecoveryFiles.MalformedStoreException.class, () -> SiftiteRecoveryFiles.decode(trailing));
        byte[] hugeLength = good.clone();
        hugeLength[12] = 0x7F; // first entry length becomes enormous
        assertThrows(SiftiteRecoveryFiles.MalformedStoreException.class, () -> SiftiteRecoveryFiles.decode(hugeLength));
    }

    @Test
    void entriesSurviveARestartThroughTheFiles(@TempDir Path directory) throws IOException {
        Path folder = directory.resolve("data/the-sift/siftite-recovery");
        SiftiteRecoveryBook first = new SiftiteRecoveryBook(new SiftiteRecoveryFiles(folder, Logger.getAnonymousLogger()));
        first.capture(ALEX, List.of(bytes("sword"), bytes("ingot")));
        first.capture(BEA, List.of(bytes("axe")));
        first.capture(ALEX, List.of(bytes("boots")));
        assertTrue(first.delivered(ALEX, bytes("sword")));

        SiftiteRecoveryBook second = new SiftiteRecoveryBook(new SiftiteRecoveryFiles(folder, Logger.getAnonymousLogger()));
        assertEquals(List.of("ingot", "boots"), texts(second.pending(ALEX)));
        assertEquals(List.of("axe"), texts(second.pending(BEA)));
        assertTrue(second.afterJoin(ALEX, 5), "a joining player who is owed items is scheduled");

        assertTrue(second.delivered(BEA, bytes("axe")));
        assertFalse(Files.exists(folder.resolve(BEA + ".bin")), "an empty list removes the file");
        assertTrue(Files.exists(folder.resolve(ALEX + ".bin")));
        try (Stream<Path> files = Files.list(folder)) {
            Set<String> names = new HashSet<>();
            files.forEach(file -> names.add(file.getFileName().toString()));
            assertEquals(Set.of(ALEX + ".bin"), names, "no temporary file is left behind");
        }
    }

    @Test
    void anUnreadableFileIsMovedAsideNotDeleted(@TempDir Path directory) throws IOException {
        SiftiteRecoveryFiles files = new SiftiteRecoveryFiles(directory, Logger.getAnonymousLogger());
        Files.write(files.file(ALEX), bytes("garbage"));
        assertEquals(List.of(), files.load(ALEX));
        assertFalse(Files.exists(files.file(ALEX)));
        try (Stream<Path> kept = Files.list(directory)) {
            List<String> names = kept.map(path -> path.getFileName().toString()).toList();
            assertEquals(1, names.size());
            assertTrue(names.get(0).startsWith(ALEX + ".bin.corrupt-"), names.get(0));
        }
        assertEquals(List.of(), files.load(BEA), "no file means nothing owed");
    }
}
