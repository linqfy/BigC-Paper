package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MythicPendingDeliveriesTest {

    private static final Logger LOGGER = Logger.getLogger("mythic-pending-test");

    @TempDir
    Path folder;

    @Test
    void survivesARestartAndIsDeliveredOnlyOnce() {
        Path file = folder.resolve("data").resolve(MythicPendingDeliveries.FILE);
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        MythicPendingDeliveries store = new MythicPendingDeliveries(file, LOGGER);
        MythicPendingDeliveries.Delivery summoner = MythicPendingDeliveries.Delivery.summoner(player,
            MythicCulture.NORSE);
        MythicPendingDeliveries.Delivery reward = MythicPendingDeliveries.Delivery.reward(player, MythicCulture.GREEK,
            2, 1125, List.of(new MythicDungeonSettings.RewardItem(Material.DIAMOND, 4),
                new MythicDungeonSettings.RewardItem(Material.TRIDENT, 1)));
        store.add(summoner);
        store.add(reward);
        store.add(MythicPendingDeliveries.Delivery.summoner(other, MythicCulture.GREEK));

        assertTrue(Files.isRegularFile(file));
        assertFalse(Files.exists(file.resolveSibling(MythicPendingDeliveries.FILE + ".tmp")));

        MythicPendingDeliveries restarted = new MythicPendingDeliveries(file, LOGGER);
        assertEquals(List.of(summoner, reward), restarted.pending(player));
        assertTrue(restarted.take(reward.id()));
        assertFalse(restarted.take(reward.id()));

        MythicPendingDeliveries again = new MythicPendingDeliveries(file, LOGGER);
        assertEquals(List.of(summoner), again.pending(player));
        assertEquals(2, again.size());
    }

    @Test
    void skipsBrokenEntriesInsteadOfLosingTheRest() throws Exception {
        Path file = folder.resolve(MythicPendingDeliveries.FILE);
        UUID player = UUID.randomUUID();
        UUID good = UUID.randomUUID();
        Files.writeString(file, "pending:\n"
            + "  " + UUID.randomUUID() + ":\n    player: " + player + "\n    kind: SUMMONER\n    culture: atlantis\n"
            + "  " + good + ":\n    player: " + player + "\n    kind: SUMMONER\n    culture: greek\n");

        MythicPendingDeliveries store = new MythicPendingDeliveries(file, LOGGER);

        assertEquals(List.of(new MythicPendingDeliveries.Delivery(good, player, MythicPendingDeliveries.Kind.SUMMONER,
            MythicCulture.GREEK, 0, 0, List.of())), store.pending(player));
    }
}
