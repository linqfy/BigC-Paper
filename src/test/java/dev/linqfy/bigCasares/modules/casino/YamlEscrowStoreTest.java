package dev.linqfy.bigCasares.modules.casino;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlEscrowStoreTest {

    private static final Logger LOGGER = Logger.getLogger("YamlEscrowStoreTest");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void chipsInPlaySurviveARestartAndAreRefundedAfterwards(@TempDir Path directory) {
        File file = directory.resolve("casino/escrow.yml").toFile();
        CasinoEscrowTest.FakeBank bank = new CasinoEscrowTest.FakeBank();
        bank.balances.put(PLAYER, 1_000L);

        CasinoEscrow before = new CasinoEscrow(bank, new YamlEscrowStore(file, LOGGER));
        assertTrue(before.take("roulette.table-1." + PLAYER, PLAYER, 300L));
        assertEquals(700L, bank.balance(PLAYER));

        CasinoEscrow after = new CasinoEscrow(bank, new YamlEscrowStore(file, LOGGER));
        assertEquals(300L, after.held("roulette.table-1." + PLAYER), "keys with dots must round-trip");
        assertEquals(1, after.refundAll());
        assertEquals(1_000L, bank.balance(PLAYER));
        assertEquals(Map.of(), new YamlEscrowStore(file, LOGGER).load());
    }

    @Test
    void savingLeavesNoTemporaryFileBehind(@TempDir Path directory) {
        File file = directory.resolve("escrow.yml").toFile();
        new YamlEscrowStore(file, LOGGER).save(Map.of("slots.1", new CasinoEscrow.Hold(PLAYER, 5L)));

        assertTrue(file.isFile());
        assertFalse(Files.exists(directory.resolve("escrow.yml.tmp")));
    }

    @Test
    void aMissingFileLoadsAsEmpty(@TempDir Path directory) {
        assertEquals(Map.of(), new YamlEscrowStore(directory.resolve("absent.yml").toFile(), LOGGER).load());
    }

    @Test
    void aCorruptEntryIsSkippedWithoutLosingTheOthers(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("escrow.yml");
        Files.writeString(file, """
            holds:
              '0':
                key: poker.a
                player: not-a-uuid
                amount: 50
              '1':
                key: poker.b
                player: %s
                amount: 75
            """.formatted(PLAYER));

        Map<String, CasinoEscrow.Hold> holds = new YamlEscrowStore(file.toFile(), LOGGER).load();

        assertEquals(Map.of("poker.b", new CasinoEscrow.Hold(PLAYER, 75L)), holds);
    }
}
