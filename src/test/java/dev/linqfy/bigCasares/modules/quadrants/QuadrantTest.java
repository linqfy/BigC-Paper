package dev.linqfy.bigCasares.modules.quadrants;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuadrantTest {

    private static final Optional<QuadrantZone> ANARCHY = Optional.of(QuadrantZone.ANARCHY);
    private static final Optional<QuadrantZone> CHILL = Optional.of(QuadrantZone.CHILL);
    private static final Optional<QuadrantZone> UNMANAGED = Optional.empty();

    @Test
    void exposesStableModuleId() {
        assertEquals("pvp-quadrants", new QuadrantModule(null).getId());
    }

    @Test
    void oppositeQuadrantsShareTheAnarchyRules() {
        QuadrantMap map = QuadrantSettings.defaults().map();

        assertEquals(ANARCHY, map.zoneAt("world", 100, -100), "noreste");
        assertEquals(ANARCHY, map.zoneAt("world", -100, 100), "suroeste");
        assertEquals(CHILL, map.zoneAt("world", -100, -100), "noroeste");
        assertEquals(CHILL, map.zoneAt("world", 100, 100), "sureste");
        assertEquals(UNMANAGED, map.zoneAt("world_nether", 100, -100));
    }

    @Test
    void customCenterAndDiagonalMoveTheAxes() {
        QuadrantMap map = new QuadrantSettings(Set.of("world"), 500, 500, Quadrant.NORTH_WEST, 150).map();

        assertEquals(ANARCHY, map.zoneAt("world", 499, 499));
        assertEquals(ANARCHY, map.zoneAt("world", 501, 501));
        assertEquals(CHILL, map.zoneAt("world", 501, 499));
        assertEquals(CHILL, map.zoneAt("world", 100, 900));
    }

    @Test
    void settingsLoadFromConfigAndRejectUnknownDiagonals() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("pvp-quadrants.worlds", List.of("survival"));
        yaml.set("pvp-quadrants.center-x", 10);
        yaml.set("pvp-quadrants.anarchy-diagonal", "noroeste-sureste");
        yaml.set("pvp-quadrants.combat-tag-seconds", 60);

        QuadrantSettings settings = QuadrantSettings.load(yaml);

        assertEquals(Set.of("survival"), settings.worlds());
        assertEquals(10.0, settings.centerX());
        assertEquals(Quadrant.NORTH_WEST, settings.anarchyQuadrant());
        assertEquals(60L, settings.combatTagSeconds());
        assertEquals(150L, QuadrantSettings.load(new YamlConfiguration()).combatTagSeconds());

        yaml.set("pvp-quadrants.anarchy-diagonal", "norte-sur");
        assertThrows(IllegalArgumentException.class, () -> QuadrantSettings.load(yaml));
    }

    @Test
    void pvpOnlyHappensInAnarchyUnlessAFightIsAlreadyGoing() {
        assertTrue(QuadrantPolicy.allowsPvp(ANARCHY, ANARCHY, false, false));
        assertFalse(QuadrantPolicy.allowsPvp(CHILL, CHILL, false, false));
        assertFalse(QuadrantPolicy.allowsPvp(ANARCHY, CHILL, false, false), "no se golpea hacia la zona Chill");
        assertFalse(QuadrantPolicy.allowsPvp(CHILL, ANARCHY, false, false), "ni desde la zona Chill");
        assertTrue(QuadrantPolicy.allowsPvp(CHILL, CHILL, true, true), "un combate ya iniciado sigue");
        assertFalse(QuadrantPolicy.allowsPvp(CHILL, CHILL, true, false));
        assertTrue(QuadrantPolicy.allowsPvp(UNMANAGED, UNMANAGED, false, false), "otros mundos no cambian");
    }

    @Test
    void taggedPlayersCannotEnterTheChillZone() {
        assertTrue(QuadrantPolicy.blocksEntry(ANARCHY, CHILL, true));
        assertTrue(QuadrantPolicy.blocksEntry(UNMANAGED, CHILL, true), "ni por portal o teleport");
        assertFalse(QuadrantPolicy.blocksEntry(ANARCHY, CHILL, false));
        assertFalse(QuadrantPolicy.blocksEntry(CHILL, CHILL, true), "quien ya está adentro puede moverse");
        assertFalse(QuadrantPolicy.blocksEntry(CHILL, ANARCHY, true));
    }

    @Test
    void combatTagLastsTheConfiguredBuffer() {
        AtomicLong now = new AtomicLong(1_000L);
        CombatTagTracker tracker = new CombatTagTracker(150, now::get);
        UUID player = UUID.randomUUID();

        assertTrue(tracker.tag(player));
        assertFalse(tracker.tag(player), "renovar no es entrar en combate otra vez");
        assertEquals(150L, tracker.remainingSeconds(player));

        now.addAndGet(149_500L);
        assertTrue(tracker.isTagged(player));
        assertEquals(1L, tracker.remainingSeconds(player));
        assertFalse(tracker.expire(player));

        now.addAndGet(500L);
        assertFalse(tracker.isTagged(player));
        assertTrue(tracker.expire(player));
        assertFalse(tracker.expire(player));
    }

    @Test
    void zeroSecondBufferNeverTags() {
        CombatTagTracker tracker = new CombatTagTracker(0, () -> 0L);
        UUID player = UUID.randomUUID();
        tracker.tag(player);
        assertFalse(tracker.isTagged(player));
    }
}
