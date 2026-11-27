package dev.linqfy.bigCasares.modules.alexscaves.toxic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkHoldsTest {
    @Test
    void aSharedChunkIsAddedOnceAndRemovedByTheLastHolder() {
        ChunkHolds<String> holds = new ChunkHolds<>();
        List<String> calls = new ArrayList<>();
        holds.hold("a", () -> calls.add("add"));
        holds.hold("a", () -> calls.add("add"));
        holds.release("a", () -> calls.add("remove"));
        assertEquals(List.of("add"), calls);
        holds.release("a", () -> calls.add("remove"));
        assertEquals(List.of("add", "remove"), calls);
        holds.release("a", () -> calls.add("remove"));
        assertEquals(List.of("add", "remove"), calls);
    }

    @Test
    void aTicketSomeoneElseAlreadyHadIsNeverRemoved() {
        ChunkHolds<String> holds = new ChunkHolds<>();
        List<String> calls = new ArrayList<>();
        holds.hold("casino", () -> false);
        holds.release("casino", () -> calls.add("remove"));
        assertEquals(List.of(), calls);
    }
}
