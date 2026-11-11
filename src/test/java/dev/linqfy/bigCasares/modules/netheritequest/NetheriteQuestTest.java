package dev.linqfy.bigCasares.modules.netheritequest;

import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.Activation;
import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.ActivationStatus;
import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.Progress;
import dev.linqfy.bigCasares.modules.netheritequest.NetheriteQuestBook.ProgressStatus;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetheriteQuestTest {

    private static final long HOUR = 60L * 60L * 1000L;
    private final UUID player = UUID.randomUUID();

    @Test
    void exposesStableModuleId() {
        assertEquals("netherite-quests", new NetheriteQuestModule(null).getId());
    }

    @Test
    void firstActivationIsFreeThenEachCostsTwentyMoreLevels() {
        NetheriteQuestBook book = new NetheriteQuestBook(NetheriteQuestSettings.defaults());

        assertEquals(0, book.nextActivationCost(player));
        Activation first = book.activate(player, ArmorQuestPiece.HELMET, List.of("a"), 0, 0L);
        assertEquals(new Activation(ActivationStatus.STARTED, 0, "a"), first);

        assertEquals(20, book.nextActivationCost(player));
        assertEquals(ActivationStatus.NOT_ENOUGH_LEVELS,
            book.activate(player, ArmorQuestPiece.CHESTPLATE, List.of("b"), 19, 0L).status());
        assertEquals(new Activation(ActivationStatus.STARTED, 20, "b"),
            book.activate(player, ArmorQuestPiece.CHESTPLATE, List.of("b"), 20, 0L));
        assertEquals(40, book.nextActivationCost(player));
    }

    @Test
    void activationNeedsASoulOfABossNotUsedByAnotherPiece() {
        NetheriteQuestBook book = new NetheriteQuestBook(NetheriteQuestSettings.defaults());

        assertEquals(ActivationStatus.NO_SOUL,
            book.activate(player, ArmorQuestPiece.HELMET, List.of(), 100, 0L).status());
        book.activate(player, ArmorQuestPiece.HELMET, List.of("sahur"), 100, 0L);
        assertEquals(ActivationStatus.ALREADY_ACTIVE,
            book.activate(player, ArmorQuestPiece.HELMET, List.of("abyss"), 100, 0L).status());
        assertEquals(ActivationStatus.SOUL_ALREADY_USED,
            book.activate(player, ArmorQuestPiece.BOOTS, List.of("sahur"), 100, 0L).status());
        assertEquals("abyss",
            book.activate(player, ArmorQuestPiece.BOOTS, List.of("sahur", "abyss"), 100, 0L).soulBoss());
    }

    @Test
    void completedPiecesKeepTheirBossButMayReuseItThemselves() {
        NetheriteQuestBook book = new NetheriteQuestBook(NetheriteQuestSettings.defaults());
        book.activate(player, ArmorQuestPiece.LEGGINGS, List.of("sahur"), 0, 0L);
        book.addProgress(player, ArmorQuestPiece.LEGGINGS, 3, 1L);

        assertFalse(book.soulUsable(player, ArmorQuestPiece.HELMET, "sahur"));
        assertTrue(book.soulUsable(player, ArmorQuestPiece.LEGGINGS, "sahur"));
        assertEquals(ActivationStatus.STARTED,
            book.activate(player, ArmorQuestPiece.LEGGINGS, List.of("sahur"), 20, 2L).status());
    }

    @Test
    void progressCompletesAtTheTarget() {
        NetheriteQuestBook book = new NetheriteQuestBook(NetheriteQuestSettings.defaults());
        book.activate(player, ArmorQuestPiece.LEGGINGS, List.of("sahur"), 0, 0L);

        assertEquals(ProgressStatus.ADVANCED,
            book.addProgress(player, ArmorQuestPiece.LEGGINGS, 1, 1L).orElseThrow().status());
        assertTrue(book.addProgress(player, ArmorQuestPiece.HELMET, 1, 1L).isEmpty(), "not active");
        book.addProgress(player, ArmorQuestPiece.LEGGINGS, 1, 1L);
        Progress done = book.addProgress(player, ArmorQuestPiece.LEGGINGS, 1, 1L).orElseThrow();

        assertEquals(ProgressStatus.COMPLETED, done.status());
        assertEquals(3, done.target());
        assertFalse(book.hasActive(player, ArmorQuestPiece.LEGGINGS));
        assertEquals("sahur", book.view(player).souls().get(ArmorQuestPiece.LEGGINGS));
        assertEquals(1, book.view(player).completions().get(ArmorQuestPiece.LEGGINGS));
    }

    @Test
    void questsExpireAfterTwentyFourHours() {
        NetheriteQuestBook book = new NetheriteQuestBook(NetheriteQuestSettings.defaults());
        book.activate(player, ArmorQuestPiece.BOOTS, List.of("sahur"), 0, 0L);

        assertEquals(ProgressStatus.ADVANCED,
            book.addProgress(player, ArmorQuestPiece.BOOTS, 5.5, 24 * HOUR - 1).orElseThrow().status());
        assertEquals(ProgressStatus.EXPIRED,
            book.addProgress(player, ArmorQuestPiece.BOOTS, 5.5, 24 * HOUR).orElseThrow().status());
        assertFalse(book.hasActive(player, ArmorQuestPiece.BOOTS));
        assertTrue(book.soulUsable(player, ArmorQuestPiece.HELMET, "sahur"), "expired soul is not bound");

        book.activate(player, ArmorQuestPiece.CHESTPLATE, List.of("abyss"), 20, 0L);
        assertEquals(List.of(new NetheriteQuestBook.Expired(player, ArmorQuestPiece.CHESTPLATE)),
            book.expireAll(25 * HOUR));
    }

    @Test
    void cancelledActivationIsNotCharged() {
        NetheriteQuestBook book = new NetheriteQuestBook(NetheriteQuestSettings.defaults());
        book.activate(player, ArmorQuestPiece.HELMET, List.of("sahur"), 0, 0L);
        book.cancelActivation(player, ArmorQuestPiece.HELMET);

        assertEquals(0, book.nextActivationCost(player));
        assertFalse(book.hasActive(player, ArmorQuestPiece.HELMET));
    }

    @Test
    void pendingSoulsAccumulateUntilDrained() {
        NetheriteQuestBook book = new NetheriteQuestBook(NetheriteQuestSettings.defaults());
        book.addPendingSoul(player, "sahur", "Sahur", 1);
        book.addPendingSoul(player, "sahur", "Sahur", 1);

        assertEquals(Map.of("sahur", new NetheriteQuestBook.PendingSoul("Sahur", 2)), book.drainPendingSouls(player));
        assertTrue(book.drainPendingSouls(player).isEmpty());
    }

    @Test
    void storeRoundTripsEveryField(@TempDir Path folder) throws Exception {
        NetheriteQuestBook book = new NetheriteQuestBook(NetheriteQuestSettings.defaults());
        book.activate(player, ArmorQuestPiece.LEGGINGS, List.of("sahur"), 0, 0L);
        book.addProgress(player, ArmorQuestPiece.LEGGINGS, 3, 1L);
        book.activate(player, ArmorQuestPiece.BOOTS, List.of("abyss"), 20, 10L);
        book.addProgress(player, ArmorQuestPiece.BOOTS, 123.25, 11L);
        book.addPendingSoul(player, "mutango", "Mutango", 2);

        NetheriteQuestStore store = new NetheriteQuestStore(folder.resolve("data").resolve("quests.yml"));
        store.save(book);
        NetheriteQuestBook loaded = new NetheriteQuestBook(NetheriteQuestSettings.defaults());
        store.loadInto(loaded);

        assertEquals(book.view(player), loaded.view(player));
        assertEquals(40, loaded.nextActivationCost(player));
    }

    @Test
    void settingsLoadFromConfigAndRejectNonsense() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("netherite-quests.duration-hours", 12);
        yaml.set("netherite-quests.level-cost-step", -5);
        yaml.set("netherite-quests.targets.peto", 250);
        yaml.set("netherite-quests.targets.botas", 0);
        NetheriteQuestSettings settings = NetheriteQuestSettings.load(yaml);

        assertEquals(12L, settings.durationHours());
        assertEquals(20, settings.levelCostStep());
        assertEquals(250, settings.target(ArmorQuestPiece.CHESTPLATE));
        assertEquals(40_000, settings.target(ArmorQuestPiece.BOOTS));
        assertEquals(3, settings.target(ArmorQuestPiece.HELMET));
        assertThrows(IllegalArgumentException.class,
            () -> new NetheriteQuestSettings(0L, 20, true, true, 10.0, Map.of()));
    }

    @Test
    void piecesMatchTheirQuests() {
        assertEquals("elder_guardian", ArmorQuestPiece.HELMET.killTarget().orElseThrow());
        assertEquals("creeper", ArmorQuestPiece.CHESTPLATE.killTarget().orElseThrow());
        assertEquals("warden", ArmorQuestPiece.LEGGINGS.killTarget().orElseThrow());
        assertTrue(ArmorQuestPiece.BOOTS.killTarget().isEmpty());
        assertEquals(ArmorQuestPiece.LEGGINGS, ArmorQuestPiece.parse(" Pantalones ").orElseThrow());
        assertTrue(ArmorQuestPiece.parse("guantes").isEmpty());
    }

    @Test
    void soulsGoToTheThreeHighestDamageDealers() {
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        UUID third = new UUID(0, 3);
        UUID fourth = new UUID(0, 4);
        Map<UUID, Double> damage = new java.util.HashMap<>(Map.of(
            fourth, 10.0, second, 80.0, first, 120.0, third, 40.0, UUID.randomUUID(), 0.0));
        damage.put(UUID.randomUUID(), Double.NaN);

        assertEquals(List.of(first, second, third), BossSoulRanking.topThree(damage));
        assertEquals(List.of(second), BossSoulRanking.topThree(Map.of(second, 1.0)));
        assertTrue(BossSoulRanking.topThree(Map.of()).isEmpty());
    }

    @Test
    void walkingIgnoresTeleports() {
        assertEquals(5.0, WalkDistance.horizontal(0, 0, 3, 4, 10.0), 1e-9);
        assertEquals(0.0, WalkDistance.horizontal(0, 0, 30, 40, 10.0));
        assertEquals(0.0, WalkDistance.horizontal(1, 1, 1, 1, 10.0));
    }
}
