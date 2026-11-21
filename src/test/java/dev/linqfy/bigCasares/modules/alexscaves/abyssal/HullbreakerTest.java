package dev.linqfy.bigCasares.modules.alexscaves.abyssal;

import dev.linqfy.bigCasares.modules.alexscaves.core.mob.MobDrop;
import dev.linqfy.bigCasares.modules.alexscaves.core.mob.ModMobCatalog;
import dev.linqfy.bigCasares.modules.alexscaves.core.spawn.ModSpawnTables;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HullbreakerTest {
    @Test
    void chasingSwimsAtTheMoveControlSpeed() {
        // Far away at 1.6: f1 = 1.6 * 0.3 * 0.7, push = f1^2 * 0.98, and the drag of 0.9 makes the steady stride 10x that.
        var drive = Hullbreaker.drive(0, 0, 50, 1.6);
        assertEquals(Math.pow(1.6 * 0.3 * 0.7, 2) * 0.98, drive.push(), 1.0E-9);
        assertEquals(30.0, drive.turn());
        var momentum = new Hullbreaker.Momentum();
        Vector stride = null;
        for (int i = 0; i < 100; i++) {
            stride = momentum.advance(drive, 0.0, 1);
        }
        assertEquals(drive.push() * 10.0, stride.getZ(), 1.0E-3);
    }

    @Test
    void closeToTheDestinationItSlowsAndTurnsFaster() {
        var near = Hullbreaker.drive(3, 0, 0, 1.0);
        var far = Hullbreaker.drive(30, 0, 0, 1.0);
        assertEquals(far.push() * 0.49, near.push(), 1.0E-9);
        assertEquals(40.0, near.turn());
        assertEquals(0.0, Hullbreaker.drive(0.1, 0, 0.1, 1.0).turn());
        assertEquals(0.0, Hullbreaker.drive(0, 0, 0.2, 1.0).push());
    }

    @Test
    void liftFollowsTheSlope() {
        assertEquals(0.21 * 0.4, Hullbreaker.drive(0, 20, 0, 1.0).lift(), 1.0E-9);
        assertTrue(Hullbreaker.drive(0, -20, 0, 1.0).lift() < 0);
    }

    @Test
    void partsTrailBehindTheCentre() {
        Vector[] centers = Hullbreaker.partCenters(0, 10, 0, 0);
        assertEquals(3.5, centers[0].getZ(), 1.0E-9);
        assertEquals(-3.5, centers[1].getZ(), 1.0E-9);
        assertEquals(-5.5, centers[2].getZ(), 1.0E-9);
        assertEquals(-8.15, centers[3].getZ(), 1.0E-9);
        assertEquals(-11.15, centers[4].getZ(), 1.0E-9);
        assertEquals(12.25, centers[0].getY(), 1.0E-9);
        // Facing east (yaw -90) the same offsets go along +x.
        assertEquals(3.5, Hullbreaker.partCenters(0, 0, 0, -90)[0].getX(), 1.0E-9);
    }

    @Test
    void inspectionPhasesFollowTheMod() {
        assertEquals(60, Hullbreaker.stareTime(0));
        assertEquals(60, Hullbreaker.stareTime(5));
        assertEquals(20, Hullbreaker.stareTime(6));
        assertEquals(120, Hullbreaker.circleTime(3));
        assertEquals(12.0, Hullbreaker.circleDistance(2));
        Vector around = new Vector(10, 50, 10);
        Vector start = Hullbreaker.orbit(around, 12, false, 0, 120);
        assertEquals(new Vector(10, 49, 22), start);
        Vector half = Hullbreaker.orbit(around, 12, false, 60, 120);
        assertEquals(10 + 12 * Math.sin(1.5 * Math.PI), half.getX(), 1.0E-9);
    }

    @Test
    void projectilesDoLessAndLethalHitsAreCaught() {
        assertEquals(6.5, Hullbreaker.bodyDamage(10, true), 1.0E-9);
        assertEquals(10, Hullbreaker.bodyDamage(10, false));
        assertTrue(Hullbreaker.lethal(5, 5));
        assertFalse(Hullbreaker.lethal(5.1, 5));
    }

    @Test
    void spawnsOnlyDeepDown() {
        assertTrue(Hullbreaker.spawnAllowed(37));
        assertFalse(Hullbreaker.spawnAllowed(38));
        ModMobCatalog catalog = new ModMobCatalog();
        Hullbreaker.register(catalog);
        assertTrue(catalog.contains("hullbreaker"));
        assertTrue(ModSpawnTables.all(CaveBiomeId.ABYSSAL_CHASM).stream().anyMatch(entry -> entry.mobId().equals("hullbreaker")));
    }

    @Test
    void lootIsTheModTableWithNoConditions() {
        Random random = new Random(1);
        for (int i = 0; i < 50; i++) {
            int[] counts = new int[5];
            for (int d = 0; d < 5; d++) {
                MobDrop drop = Hullbreaker.LOOT.get(d);
                counts[d] = drop.roll(random, 3, false);
            }
            assertEquals(1, counts[0]);
            assertEquals(1, counts[1]);
            assertTrue(counts[2] >= 2 && counts[2] <= 5);
            assertTrue(counts[3] >= 2 && counts[3] <= 8);
            assertTrue(counts[4] >= 2 && counts[4] <= 4);
        }
    }

    @Test
    void rotationIsLimitedAndWraps() {
        assertEquals(30f, Hullbreaker.rotate(0f, 90f, 30f), 1.0E-4);
        assertEquals(-170f, Hullbreaker.rotate(170f, -170f, 30f) - 360f * 1, 1.0E-3);
    }
}
