package dev.linqfy.bigCasares.modules.donpollos;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonPollosBossTest {

    @Test
    void bossModelHasLasersThatOnlyShowInTheLaserAnimation() throws IOException {
        JsonObject model = load();
        Set<String> groups = new HashSet<>();
        model.getAsJsonArray("groups").forEach(g -> groups.add(g.getAsJsonObject().get("name").getAsString()));
        assertTrue(groups.containsAll(Set.of("auto", "torso", "hi_head", "glow_laser", "rightarm", "leftarm")));

        Set<String> animations = new HashSet<>();
        for (JsonElement animation : model.getAsJsonArray("animations")) {
            animations.add(animation.getAsJsonObject().get("name").getAsString());
        }
        assertTrue(animations.containsAll(Set.of("idle", "walk", "laser", "golpe")));
        assertTrue(DonPollosModelInstaller.allModels().contains(DonPollosBossManager.MODEL));
    }

    @Test
    void bossSettingsComeFromTheBundledConfig() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("don-pollos.yml")) {
            var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            DonPollosBossSettings boss = new DonPollosSettingsLoader().load(config).boss();
            assertTrue(boss.health() >= 100);
            assertEquals("DIAMOND", boss.reward().material());
            assertEquals(10, boss.keepDistance());
            assertEquals(7.5, boss.chickenWaveSeconds());
            assertEquals(2, boss.chickensPerWave());
            assertEquals(3, boss.chickenDamage());
            assertEquals(7, boss.phase3().levitateSeconds());
            assertEquals(20, boss.phase3().levitateHeight());
            assertEquals(6, boss.phase3().pitDepth());
            assertEquals(java.util.List.of(10, 30, 50),
                boss.phase3().rounds().stream().map(DonPollosCubeRound::labubus).toList());
            assertEquals(1.5, boss.phase3().rounds().get(2).colonelSpeed());
            assertEquals(10, boss.phase4().colonelSeconds());
            assertEquals(2, boss.phase4().colonelsPerWave());
            assertEquals(8, boss.phase4().jumpLandDistance());
        }
    }

    @Test
    void meteorArcLandsWhereThePlayerWasAndRisesOnTheWay() {
        org.bukkit.util.Vector from = new org.bukkit.util.Vector(0, 66, 0);
        org.bukkit.util.Vector to = new org.bukkit.util.Vector(10, 64.5, 0);
        org.bukkit.util.Vector velocity = DonPollosBossMeteors.launchVelocity(from, to);
        int ticks = DonPollosBossMeteors.flightTicks(10);
        org.bukkit.util.Vector position = from.clone();
        double highest = position.getY();
        for (int i = 0; i < ticks; i++) {
            position.add(velocity);
            velocity.setY(velocity.getY() - DonPollosBossMeteors.GRAVITY);
            highest = Math.max(highest, position.getY());
        }
        assertEquals(0, position.distance(to), 1e-6);
        assertTrue(highest > from.getY() + 1, "el meteorito tiene que subir un poco");
    }

    @Test
    void shockwaveHitsHarderAndHigherTheCloserYouAre() {
        double near = DonPollosBossShockwaves.damageAt(0, 12, 12);
        double mid = DonPollosBossShockwaves.damageAt(6, 12, 12);
        double edge = DonPollosBossShockwaves.damageAt(12, 12, 12);
        assertEquals(12, near, 1e-9);
        assertEquals(4, edge, 1e-9);
        assertTrue(near > mid && mid > edge);
        assertTrue(DonPollosBossShockwaves.liftAt(0, 12) > DonPollosBossShockwaves.liftAt(10, 12));
    }

    @Test
    void whatsappChickenModelIsInstalled() {
        for (DonPollosBossMinions.Kind kind : DonPollosBossMinions.Kind.values()) {
            assertTrue(DonPollosModelInstaller.allModels().contains(kind.model), kind.name());
        }
        assertEquals(4, DonPollosBossMinions.Kind.LABUBUS.length);
    }

    @Test
    void rejectsSillyBossSettings() {
        assertThrows(IllegalArgumentException.class, () -> new DonPollosBossSettings(0, 1, 1, 1, 20, 5,
            new DonPollosItem("DIAMOND", 1), 10, 6, 2.5, 7.5, 2, 8, 8, 3, DonPollosBossPhase3Settings.defaults(),
            DonPollosBossPhase4Settings.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new DonPollosBossSettings(100, 1, 1, 1, 200, 5,
            new DonPollosItem("DIAMOND", 1), 10, 6, 2.5, 7.5, 2, 8, 8, 3, DonPollosBossPhase3Settings.defaults(),
            DonPollosBossPhase4Settings.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new DonPollosBossSettings(100, 1, 1, 1, 20, 5,
            new DonPollosItem("DIAMOND", 1), 1, 6, 2.5, 7.5, 2, 8, 8, 3, DonPollosBossPhase3Settings.defaults(),
            DonPollosBossPhase4Settings.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new DonPollosBossPhase4Settings(3, 6, 10, 32, 9, 8, 12, 12,
            10, 2, 6, 1.6, 8, 40));
        assertThrows(IllegalArgumentException.class, () -> new DonPollosBossPhase3Settings(7, 100, 6, 20, 3,
            DonPollosBossPhase3Settings.defaults().rounds()));
        assertThrows(IllegalArgumentException.class, () -> new DonPollosBossPhase3Settings(7, 20, 6, 20, 3,
            java.util.List.of()));
    }

    private JsonObject load() throws IOException {
        try (InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("bettermodel/models/" + DonPollosBossManager.MODEL + ".bbmodel")) {
            assertNotNull(stream);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
