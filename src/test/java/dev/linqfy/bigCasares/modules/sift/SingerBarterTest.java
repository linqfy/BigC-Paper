package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.SingerBarter.Entry;
import dev.linqfy.bigCasares.modules.sift.SingerBarter.Reward;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingerBarterTest {

    /** A random whose {@code nextInt} answers are scripted in order. */
    private static final class Scripted extends Random {
        private final int[] answers;
        private int next;

        Scripted(int... answers) {
            this.answers = answers;
        }

        @Override
        public int nextInt(int bound) {
            return answers[next++] % bound;
        }
    }

    @Test
    void theTableAndTheWoolShareTwoHundredAndTheWoolIsNine() {
        int total = SingerBarter.TABLE.stream().mapToInt(Entry::weight).sum();
        assertEquals(191, total);
        assertEquals(200 - total, 9, "what no entry catches is the wool");
        assertEquals(16, SingerBarter.WOOL.size());
        assertEquals("minecraft:white_wool", SingerBarter.WOOL.get(0));
        assertEquals("minecraft:black_wool", SingerBarter.WOOL.get(15));
    }

    @Test
    void rollsLandOnTheModsBoundaries() {
        // 0..29 iron block, 30..49 template, 50..68 debris, 69..71 the disc, 72..91 gold block.
        assertEquals("minecraft:iron_block", SingerBarter.roll(new Scripted(0, 0)).item());
        assertEquals("minecraft:iron_block", SingerBarter.roll(new Scripted(29, 0)).item());
        assertEquals("minecraft:netherite_upgrade_smithing_template", SingerBarter.roll(new Scripted(30, 0)).item());
        assertEquals("minecraft:ancient_debris", SingerBarter.roll(new Scripted(50, 0)).item());
        assertEquals(SingerBarter.MUSIC_DISC_WELLSPRING, SingerBarter.roll(new Scripted(69, 0)).item());
        assertEquals("minecraft:gold_block", SingerBarter.roll(new Scripted(72, 0)).item());
        assertEquals("minecraft:string", SingerBarter.roll(new Scripted(190, 0)).item());
    }

    @Test
    void theLastNineRollsAreAWoolChosenBeforeTheCount() {
        Reward reward = SingerBarter.roll(new Scripted(191, 14, 5));
        assertEquals("minecraft:red_wool", reward.item());
        assertEquals(9 + 5, reward.count());
        assertEquals("minecraft:white_wool", SingerBarter.roll(new Scripted(199, 0, 0)).item());
    }

    @Test
    void countsStayInsideEachEntrysRange() {
        Random random = new Random(7);
        Map<String, int[]> seen = new HashMap<>();
        for (int index = 0; index < 20_000; index++) {
            Reward reward = SingerBarter.roll(random);
            int[] range = seen.computeIfAbsent(reward.item(), item -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
            range[0] = Math.min(range[0], reward.count());
            range[1] = Math.max(range[1], reward.count());
        }
        for (Entry entry : SingerBarter.TABLE) {
            int[] range = seen.get(entry.item());
            assertEquals(entry.minimum(), range[0], entry.item());
            assertEquals(entry.maximum(), range[1], entry.item());
        }
        List<String> wools = new ArrayList<>(seen.keySet());
        wools.removeIf(item -> !item.endsWith("_wool"));
        assertEquals(16, wools.size());
        for (String wool : wools) {
            assertTrue(seen.get(wool)[0] >= 9 && seen.get(wool)[1] <= 24, wool);
        }
    }

    @Test
    void anUnavailableOutcomeIsRolledAgain() {
        // The disc (69..71) is not available: the second roll catches the iron block.
        Reward reward = SingerBarter.rollAvailable(new Scripted(70, 0, 3, 1), item -> !item.equals(SingerBarter.MUSIC_DISC_WELLSPRING));
        assertEquals("minecraft:iron_block", reward.item());
        assertEquals(3, reward.count());
    }

    @Test
    void nothingAvailableEndsInWhiteWool() {
        Reward reward = SingerBarter.rollAvailable(new Random(1), item -> false);
        assertEquals("minecraft:white_wool", reward.item());
        assertTrue(reward.count() >= 9 && reward.count() <= 24);
    }

    @Test
    void theDiscIsRareAmongRolls() {
        Random random = new Random(11);
        int discs = 0;
        int rolls = 200_000;
        for (int index = 0; index < rolls; index++) {
            if (SingerBarter.roll(random).item().equals(SingerBarter.MUSIC_DISC_WELLSPRING)) {
                discs++;
            }
        }
        assertEquals(3.0 / 200.0, (double) discs / rolls, 0.002);
    }
}
