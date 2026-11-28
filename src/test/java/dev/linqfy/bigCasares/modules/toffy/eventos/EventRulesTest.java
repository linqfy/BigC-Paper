package dev.linqfy.bigCasares.modules.toffy.eventos;

import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventRulesTest {

    @Test
    void keepInventoryEveryoneKeepsEveryDeathButNotTheArenaOrTheEndHunt() {
        assertTrue(LifestealRules.keepsLifestealDeath(true, false, false, false));
        assertFalse(LifestealRules.keepsLifestealDeath(true, false, true, false));
        assertFalse(LifestealRules.keepsLifestealDeath(true, false, false, true));
    }

    @Test
    void withoutEveryoneOnlyTheSignUpsKeepTheirInventory() {
        assertFalse(LifestealRules.keepsLifestealDeath(false, false, false, false));
        assertTrue(LifestealRules.keepsLifestealDeath(false, true, false, false));
        assertFalse(LifestealRules.keepsLifestealDeath(false, true, true, false));
    }

    @Test
    void aLeftPlayersPointsAreForgotten() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        KothScores scores = new KothScores(3);
        scores.tick(List.of(first));
        scores.tick(List.of(first));
        scores.tick(List.of(second));

        assertEquals(Optional.of(first), scores.leader());
        scores.forget(first);

        assertEquals(0, scores.points(first));
        assertEquals(Optional.of(second), scores.leader());
        assertTrue(scores.capturedBy().isEmpty());
    }

    @Test
    void theOwedStoreSurvivesARestart(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("owed.yml");
        UUID player = UUID.randomUUID();
        EventOwedStore store = new EventOwedStore(file);
        store.addHearts(player, 2);
        store.addReward(player, new EventOwedStore.Reward("evento-1", ToffyEventType.KOTH,
            new ToffyEventSettings.Rewards(List.of("nuke_shot:1"), List.of("say {player}"))));
        store.save();

        EventOwedStore reloaded = new EventOwedStore(file);
        reloaded.load();

        EventOwedStore.Owed owed = reloaded.find(player).orElseThrow();
        assertEquals(2, owed.hearts());
        assertEquals("evento-1", owed.rewards().getFirst().eventId());
        assertEquals(ToffyEventType.KOTH, owed.rewards().getFirst().type());
        assertEquals(List.of("nuke_shot:1"), owed.rewards().getFirst().rewards().items());

        reloaded.take(player);
        reloaded.save();
        EventOwedStore empty = new EventOwedStore(file);
        empty.load();
        assertFalse(empty.has(player));
    }

    @Test
    void theBundledDefaultsMatchTodayBehaviour() throws Exception {
        ToffyEventSettings settings = ToffyEventSettings.load(new YamlConfiguration());

        assertEquals(List.of(20, 10), settings.lobbyCountdownSeconds());
        assertEquals(List.of(300, 60), settings.lifesteal().warningSeconds());
        assertEquals(1, settings.lifesteal().minPlayers());
        assertEquals(1, settings.lifesteal().permanentHeartReward());
        assertTrue(settings.lifesteal().keepInventoryEveryone());
        assertEquals(5, settings.tntTag().warningSeconds());
        assertEquals(1000, settings.tntTag().tagCooldownMillis());
        assertEquals(6000, settings.arena().timeOfDay());
        assertEquals(512, settings.arena().buildBlocksPerTick());
        assertEquals(Material.SMOOTH_STONE, settings.arena().materials().kothPlatform());
        assertEquals(BarColor.RED, settings.bossBar(ToffyEventType.LIFESTEAL).color());
        assertEquals("minecraft:entity.ender_dragon.growl", settings.sound(ToffyEventSettings.SOUND_BEGIN).key());
        assertEquals(List.of("anotar", "toffy", "tcomp", "evento", "eventos"), settings.allowedCommands());
        assertEquals(List.of(5, 1), settings.tournaments().teleportWarningMinutes());
        assertEquals("HH:mm", settings.tournaments().timeFormat());
        assertTrue(settings.warnings().isEmpty());
    }

    @Test
    void outOfRangeValuesWarnAndFallBack() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("lobby-seconds: 3\nbossbar:\n  koth:\n    color: PINK_PANTS\n"
            + "torneos:\n  zona-horaria: Mars/Base\n  formato-hora: HH:mm\n");

        ToffyEventSettings settings = ToffyEventSettings.load(yaml);

        assertEquals(30, settings.lobbySeconds());
        assertEquals(BarColor.YELLOW, settings.bossBar(ToffyEventType.KOTH).color());
        assertEquals("system", settings.tournaments().zone());
        assertEquals(3, settings.warnings().size());
    }

    @Test
    void aSilentSoundKeyPlaysNothing() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("sounds:\n  begin:\n    sonido: \"\"\n");

        assertTrue(ToffyEventSettings.load(yaml).sound(ToffyEventSettings.SOUND_BEGIN).silent());
    }

    @Test
    void aMaterialThatIsNotAllowedWarns() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("arena:\n  materials:\n    wall: WHITE_CONCRETE\n");

        ToffyEventSettings settings = ToffyEventSettings.load(yaml, material -> false);

        assertEquals(Material.BARRIER, settings.arena().materials().wall());
        assertFalse(settings.warnings().isEmpty());
    }
}
