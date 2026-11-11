package dev.linqfy.bigCasares.modules.villagermood;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerMoodTest {

    /** Everything is solid ground up to y = -1 and air above, unless edited. */
    private static final class Grid implements BlockProbe {
        private final Set<String> solid = new HashSet<>();
        private final Set<String> air = new HashSet<>();
        private final Set<String> tall = new HashSet<>();

        Grid solid(int x, int y, int z) {
            air.remove(key(x, y, z));
            solid.add(key(x, y, z));
            return this;
        }

        Grid air(int x, int y, int z) {
            solid.remove(key(x, y, z));
            air.add(key(x, y, z));
            return this;
        }

        Grid fence(int x, int y, int z) {
            tall.add(key(x, y, z));
            return solid(x, y, z);
        }

        /** Walls two blocks high around (0, 0, 0), like a trading-hall cell. */
        Grid cell() {
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                solid(d[0], 0, d[1]);
                solid(d[0], 1, d[1]);
            }
            return this;
        }

        @Override
        public boolean passable(int x, int y, int z) {
            String key = key(x, y, z);
            if (solid.contains(key)) {
                return false;
            }
            return air.contains(key) || y >= 0;
        }

        @Override
        public boolean tall(int x, int y, int z) {
            return tall.contains(key(x, y, z));
        }

        private static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }
    }

    private final VillagerMoodPolicy policy = new VillagerMoodPolicy(VillagerMoodSettings.defaults());

    @Test
    void exposesStableModuleId() {
        assertEquals("villager-mood", new VillagerMoodModule(null).getId());
    }

    @Test
    void detectsTradingHallCells() {
        assertFalse(VillagerMoodPolicy.isConfined(new Grid(), 0, 0, 0), "open field");
        assertTrue(VillagerMoodPolicy.isConfined(new Grid().cell(), 0, 0, 0));

        Grid lowWalls = new Grid();
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            lowWalls.solid(d[0], 0, d[1]);
        }
        assertFalse(VillagerMoodPolicy.isConfined(lowWalls, 0, 0, 0), "one-high walls can be jumped");
        assertTrue(VillagerMoodPolicy.isConfined(lowWalls.solid(0, 2, 0), 0, 0, 0), "no headroom to jump");

        Grid fences = new Grid();
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            fences.fence(d[0], 0, d[1]);
        }
        assertTrue(VillagerMoodPolicy.isConfined(fences, 0, 0, 0), "fences cannot be jumped");

        assertFalse(VillagerMoodPolicy.isConfined(new Grid().cell().air(1, 0, 0).air(1, 1, 0), 0, 0, 0),
            "a full doorway is a way out");
    }

    @Test
    void findsOneByOneEscapeHoles() {
        Grid grid = new Grid().cell();
        assertEquals(Optional.empty(), VillagerMoodPolicy.findEscapeHole(grid, 0, 0, 0));

        grid.air(0, 0, -1); // feet-level hole in the north wall
        assertEquals(Optional.of(new VillagerMoodPolicy.Cell(0, 0, -2)),
            VillagerMoodPolicy.findEscapeHole(grid, 0, 0, 0));
        assertTrue(VillagerMoodPolicy.isConfined(grid, 0, 0, 0), "a 1x1 hole still keeps the villager boxed in");

        Grid blocked = new Grid().cell().air(0, 0, -1).solid(0, 0, -2).solid(0, 1, -2).solid(0, 2, -2);
        assertEquals(Optional.empty(), VillagerMoodPolicy.findEscapeHole(blocked, 0, 0, 0),
            "nowhere to stand behind the hole");

        Grid roofedHeadHole = new Grid().cell().solid(0, 2, 0).air(0, 1, -1);
        assertTrue(VillagerMoodPolicy.isConfined(roofedHeadHole, 0, 0, 0));
        assertEquals(Optional.of(new VillagerMoodPolicy.Cell(0, 0, -2)),
            VillagerMoodPolicy.findEscapeHole(roofedHeadHole, 0, 0, 0));
    }

    @Test
    void findsTheNearestCliffEdge() {
        Grid grid = new Grid();
        assertEquals(Optional.empty(), VillagerMoodPolicy.findCliffEdge(grid, 0, 0, 0, 12, 6));

        for (int y = -1; y >= -10; y--) {
            grid.air(5, y, 0);
        }
        assertEquals(Optional.of(new VillagerMoodPolicy.Cell(5, 0, 0)),
            VillagerMoodPolicy.findCliffEdge(grid, 0, 0, 0, 12, 6));
        assertEquals(Optional.empty(), VillagerMoodPolicy.findCliffEdge(grid, 0, 0, 0, 4, 6), "out of range");

        Grid shallow = new Grid().air(3, -1, 0).air(3, -2, 0);
        assertEquals(Optional.empty(), VillagerMoodPolicy.findCliffEdge(shallow, 0, 0, 0, 12, 6),
            "a two-block step is not a cliff");

        Grid walled = new Grid().solid(2, 0, 0).solid(2, 1, 0);
        for (int y = -1; y >= -10; y--) {
            walled.air(5, y, 0);
        }
        assertEquals(Optional.empty(), VillagerMoodPolicy.findCliffEdge(walled, 0, 0, 0, 12, 6),
            "walls block the way");
    }

    @Test
    void depressionBuildsWhileConfinedAndHealsWhenFree() {
        assertEquals(0, policy.depressionLevel(0L));
        assertEquals(0, policy.depressionLevel(11_999L));
        assertEquals(1, policy.depressionLevel(12_000L));
        assertEquals(2, policy.depressionLevel(18_000L));
        assertEquals(5, policy.depressionLevel(1_000_000L));

        long ticks = 0L;
        for (int i = 0; i < 1_000; i++) {
            ticks = policy.nextConfinedTicks(ticks, true, 100L);
        }
        assertEquals(42_000L, ticks, "capped one level past the maximum");
        assertEquals(5, policy.depressionLevel(ticks));

        assertEquals(41_800L, policy.nextConfinedTicks(ticks, false, 100L), "recovers twice as fast");
        assertEquals(0L, policy.nextConfinedTicks(50L, false, 100L));
    }

    @Test
    void annoyanceStacksAndDecays() {
        long ticks = policy.annoy(0L);
        assertEquals(1, policy.annoyanceLevel(ticks));
        ticks = policy.annoy(ticks);
        assertEquals(2, policy.annoyanceLevel(ticks));
        for (int i = 0; i < 10; i++) {
            ticks = policy.annoy(ticks);
        }
        assertEquals(5, policy.annoyanceLevel(ticks), "capped");

        assertEquals(1, policy.annoyanceLevel(policy.decayAnnoyance(6_000L, 100L)));
        assertEquals(0, policy.annoyanceLevel(policy.decayAnnoyance(6_000L, 6_000L)));
    }

    @Test
    void moodRaisesPrices() {
        assertEquals(0, policy.surcharge(10, 0, 0));
        assertEquals(1, policy.surcharge(1, 1, 0), "a bad mood always costs at least one more");
        assertEquals(3, policy.surcharge(10, 2, 0));
        assertEquals(5, policy.surcharge(10, 2, 2));
        assertEquals(13, policy.surcharge(10, 5, 5));
        assertEquals(15, policy.surcharge(10, 10, 10), "capped at max-surcharge");
        assertEquals(0, policy.surcharge(0, 5, 5));

        assertFalse(policy.joinsRiot(1, 0));
        assertTrue(policy.joinsRiot(1, 1));
    }

    @Test
    void spamTrackerFlagsBurstsOnlyPerVillager() {
        TradeSpamTracker tracker = new TradeSpamTracker(200L, 3);
        UUID player = UUID.randomUUID();
        UUID villager = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        assertFalse(tracker.record(player, villager, 0L));
        assertFalse(tracker.record(player, villager, 10L));
        assertFalse(tracker.record(player, other, 15L));
        assertFalse(tracker.record(player, villager, 20L));
        assertTrue(tracker.record(player, villager, 30L));
        assertFalse(tracker.record(player, villager, 40L), "window restarts after a complaint");

        TradeSpamTracker slow = new TradeSpamTracker(200L, 3);
        for (long tick = 0L; tick < 10_000L; tick += 100L) {
            assertFalse(slow.record(player, villager, tick), "steady trading is fine");
        }
    }

    @Test
    void settingsLoadAndRejectNonsense() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("villager-mood.trading.infinite", false);
        yaml.set("villager-mood.spam.max-trades-in-window", 8);
        yaml.set("villager-mood.insults", List.of("Fuera de acá."));
        VillagerMoodSettings loaded = VillagerMoodSettings.load(yaml);
        assertFalse(loaded.trading().infinite());
        assertEquals(8, loaded.spam().maxTradesInWindow());
        assertEquals(List.of("Fuera de acá."), loaded.insults());
        assertEquals(VillagerMoodSettings.defaults().riotShouts(), loaded.riotShouts());

        YamlConfiguration broken = new YamlConfiguration();
        broken.set("villager-mood.riot.chance-per-check", 4.0);
        assertEquals(VillagerMoodSettings.defaults(), VillagerMoodSettings.load(broken));
    }
}
