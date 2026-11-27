package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepOneTest {
    @Test
    void reactionThresholdsMatchTheMod() {
        assertEquals(DeepOneReaction.AGGRESSIVE, DeepOneReaction.fromReputation(-100));
        assertEquals(DeepOneReaction.AGGRESSIVE, DeepOneReaction.fromReputation(-10));
        assertEquals(DeepOneReaction.STALKING, DeepOneReaction.fromReputation(-9));
        assertEquals(DeepOneReaction.STALKING, DeepOneReaction.fromReputation(0));
        assertEquals(DeepOneReaction.STALKING, DeepOneReaction.fromReputation(10));
        assertEquals(DeepOneReaction.NEUTRAL, DeepOneReaction.fromReputation(11));
        assertEquals(DeepOneReaction.NEUTRAL, DeepOneReaction.fromReputation(30));
        assertEquals(DeepOneReaction.HELPFUL, DeepOneReaction.fromReputation(31));
    }

    @Test
    void validPlayerRules() {
        assertFalse(DeepOneReaction.STALKING.validPlayer(0, true, 16, true));
        assertTrue(DeepOneReaction.STALKING.validPlayer(0, true, 15, true));
        // A Deep One in water ignores a stalked/neutral player on land, but not an aggressive or helpful case.
        assertFalse(DeepOneReaction.NEUTRAL.validPlayer(0, true, 0, false));
        assertTrue(DeepOneReaction.AGGRESSIVE.validPlayer(0, true, 0, false));
        assertTrue(DeepOneReaction.HELPFUL.validPlayer(0, true, 0, false));
        assertTrue(DeepOneReaction.NEUTRAL.validPlayer(0, false, 0, false));
    }

    @Test
    void reputationClampsAndPersists(@TempDir File dir) {
        File file = new File(dir, "rep.yml");
        DeepOneReputation store = new DeepOneReputation(file);
        UUID player = UUID.randomUUID();
        assertEquals(0, store.get(player));
        assertEquals(100, store.add(player, 500));
        assertEquals(-100, store.add(player, -1000));
        store.set(player, -15);
        assertEquals(DeepOneReaction.AGGRESSIVE, store.reactionOf(player));
        Logger logger = Logger.getLogger("test");
        store.save(logger);
        assertFalse(store.dirty());
        DeepOneReputation loaded = new DeepOneReputation(file);
        loaded.load(logger);
        assertEquals(-15, loaded.get(player));
    }

    @Test
    void brokenReputationFileFallsBackToTheBackup(@TempDir File dir) throws Exception {
        File file = new File(dir, "rep.yml");
        Logger logger = Logger.getLogger("test");
        DeepOneReputation store = new DeepOneReputation(file);
        UUID player = UUID.randomUUID();
        store.set(player, 40);
        store.save(logger);
        store.set(player, 55);
        store.save(logger);
        // The second save kept the first file as the backup; a damaged main file falls back to it.
        java.nio.file.Files.writeString(file.toPath(), "reputation: [unclosed");
        DeepOneReputation loaded = new DeepOneReputation(file);
        loaded.load(logger);
        assertEquals(40, loaded.get(player));
    }

    @Test
    void barterTablesHaveTheModWeights() {
        assertEquals(646, DeepOneBarter.totalWeight(DeepOneBarter.DEEP_ONE));
        assertEquals(247, DeepOneBarter.totalWeight(DeepOneBarter.KNIGHT));
        assertEquals(299, DeepOneBarter.totalWeight(DeepOneBarter.MAGE));
    }

    @Test
    void barterRollStaysInCountRange() {
        Random random = new Random(7);
        for (List<DeepOneBarter.Entry> table : List.of(DeepOneBarter.DEEP_ONE, DeepOneBarter.KNIGHT, DeepOneBarter.MAGE)) {
            for (int i = 0; i < 2000; i++) {
                DeepOneBarter.Result result = DeepOneBarter.roll(table, random);
                assertTrue(result.count() >= result.entry().min() && result.count() <= result.entry().max());
            }
        }
    }

    @Test
    void barterItemsResolveToMaterials() {
        for (List<DeepOneBarter.Entry> table : List.of(DeepOneBarter.DEEP_ONE, DeepOneBarter.KNIGHT, DeepOneBarter.MAGE)) {
            for (DeepOneBarter.Entry entry : table) {
                if (!entry.item().startsWith("ac_")) {
                    org.bukkit.Material.valueOf(entry.item().toUpperCase(java.util.Locale.ROOT));
                }
            }
        }
    }
}
