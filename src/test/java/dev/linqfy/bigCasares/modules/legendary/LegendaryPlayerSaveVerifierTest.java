package dev.linqfy.bigCasares.modules.legendary;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryPlayerSaveVerifierTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private static final String KEY = "bigcasares:legendary_instance";

    @Test
    void resolvesSharedPlayerDataFromTheTwentySixThreeDimensionFolder() throws Exception {
        var level = directory.resolve("level");
        var dimension = level.resolve("dimensions/minecraft/overworld");
        java.nio.file.Files.createDirectories(dimension);
        java.nio.file.Files.createDirectories(level.resolve("players/data"));
        var player = java.util.UUID.randomUUID();
        assertEquals(level.resolve("players/data").resolve(player + ".dat"),
            LegendaryPlayerSaveVerifier.playerFile(dimension, player));
    }

    @Test
    void supportsLegacyPlayerDataAndRejectsUnknownStorageLayouts() throws Exception {
        var player = java.util.UUID.randomUUID();
        assertThrows(IOException.class, () -> LegendaryPlayerSaveVerifier.playerFile(directory, player));
        java.nio.file.Files.createDirectories(directory.resolve("playerdata"));
        assertEquals(directory.resolve("playerdata").resolve(player + ".dat"),
            LegendaryPlayerSaveVerifier.playerFile(directory, player));
    }

    @Test
    void acknowledgesOnlyTheExactSavedOwnershipMarker() throws Exception {
        byte[] saved = nbt(KEY, "new-transaction");
        AtomicBoolean called = new AtomicBoolean();
        assertDoesNotThrow(() -> LegendaryPlayerSaveVerifier.saveAndVerify(() -> called.set(true),
            () -> new ByteArrayInputStream(saved), KEY, "new-transaction"));
        assertTrue(called.get());
    }

    @Test
    void silentSaveFailureCannotAcknowledgeAnOldPlayerInventory() throws Exception {
        byte[] stale = nbt(KEY, "previous-transaction");
        assertThrows(IOException.class, () -> LegendaryPlayerSaveVerifier.saveAndVerify(() -> {},
            () -> new ByteArrayInputStream(stale), KEY, "new-transaction"));
    }

    @Test
    void matchingNonceUnderAnotherKeyDoesNotProveOwnershipWasSaved() throws Exception {
        byte[] unrelated = nbt("different:key", "new-transaction");
        assertThrows(IOException.class, () -> LegendaryPlayerSaveVerifier.saveAndVerify(() -> {},
            () -> new ByteArrayInputStream(unrelated), KEY, "new-transaction"));
    }

    @Test
    void missingOrCorruptPlayerDataFailsClosed() {
        assertThrows(IOException.class, () -> LegendaryPlayerSaveVerifier.saveAndVerify(() -> {},
            () -> { throw new IOException("save unavailable"); }, KEY, "transaction"));
        assertThrows(IOException.class, () -> LegendaryPlayerSaveVerifier.saveAndVerify(() -> {},
            () -> new ByteArrayInputStream(new byte[]{1, 2, 3}), KEY, "transaction"));
    }

    private static byte[] nbt(String key, String value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes); var output = new DataOutputStream(gzip)) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(8);
            output.writeUTF(key);
            output.writeUTF(value);
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }
}
