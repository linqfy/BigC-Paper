package dev.linqfy.bigCasares.modules.mythicdungeons;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Donde entra un mob, con una grilla de bloques de mentira. */
class MobPlacementTest {

    /** Una sala cerrada: piso en y=0, aire de y=1 a y=height, paredes alrededor de [min..max] en x y z. */
    private static final class Room implements BlockGrid {
        private final int min;
        private final int max;
        private final int height;
        private final Set<List<Integer>> extra = new HashSet<>();

        Room(int min, int max, int height) {
            this.min = min;
            this.max = max;
            this.height = height;
        }

        Room block(int x, int y, int z) {
            extra.add(List.of(x, y, z));
            return this;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return y <= 0 || y > height || x < min || x > max || z < min || z > max || extra.contains(List.of(x, y, z));
        }
    }

    private final MythicMobCatalog catalog = MythicMobCatalog.defaults();

    @Test
    void aMobFitsOnlyWithFloorAndAirForItsWholeHitbox() {
        Room room = new Room(0, 4, 3);
        MobPlacement.Spot middle = new MobPlacement.Spot(2, 1, 2);
        assertTrue(MobPlacement.fits(room, middle, 0.6, 1.95));
        assertTrue(MobPlacement.fits(room, middle, 2.4, 3.0), "2.4 wide covers x 1..3, all air");
        assertFalse(MobPlacement.fits(room, middle, 2.4, 3.4), "4 blocks high hits the ceiling");
        assertFalse(MobPlacement.fits(room, new MobPlacement.Spot(0, 1, 2), 1.4, 1.0), "half of it in the wall");
        assertFalse(MobPlacement.fits(room, new MobPlacement.Spot(2, 2, 2), 0.6, 1.0), "no floor below");
        assertFalse(MobPlacement.fits(room.block(2, 2, 2), middle, 0.6, 1.95), "a block at head height");
    }

    @Test
    void theNearestFreeSpotIsUsedAndNothingWhenNoneFits() {
        Room corridor = new Room(0, 0, 2); // 1 wide, 2 high
        MobPlacement.Spot spawner = new MobPlacement.Spot(0, 1, 0);
        MobPlacement.Spot wall = new MobPlacement.Spot(3, 1, 0);
        assertEquals(Optional.of(spawner), MobPlacement.nearestFit(corridor, wall, spawner, 4, 1, 0.6, 1.95));
        assertTrue(MobPlacement.nearestFit(corridor, wall, spawner, 4, 1, 1.4, 0.9).isEmpty(), "a spider never fits");
        assertTrue(MobPlacement.nearestFit(corridor, wall, spawner, 4, 1, 0.6, 2.5).isEmpty(), "too tall");
    }

    @Test
    void aSmallRoomFallsBackToASmallerMobOfTheCulture() {
        // Egipcia: el sorteo con 0.0 da Ammit (elite, 1.56 de ancho), que no entra en un pasillo de 1
        Room tiny = new Room(0, 0, 2);
        MobPlacement.Spot spot = new MobPlacement.Spot(0, 1, 0);
        List<MythicMobType> egyptian = catalog.ofCulture(MythicCulture.EGYPTIAN);
        MobPlacement.Placement<MythicMobType> placed = MobPlacement.place(egyptian, 0.0, tiny, spot, spot, 4, 1)
            .orElseThrow();
        assertEquals("mummy", placed.mob().id());
        assertEquals(spot, placed.spot());

        Room hall = new Room(-3, 3, 5);
        assertEquals("ammit", MobPlacement.place(egyptian, 0.0, hall, spot, spot, 4, 1).orElseThrow().mob().id());
    }

    @Test
    void theAttemptLapsesWhenNothingFits() {
        Room crawlspace = new Room(0, 0, 0); // ni un bloque de aire
        MobPlacement.Spot spot = new MobPlacement.Spot(0, 1, 0);
        for (MythicCulture culture : MythicCulture.values()) {
            assertTrue(MobPlacement.place(catalog.ofCulture(culture), 0.5, crawlspace, spot, spot, 4, 1).isEmpty(),
                culture.id());
        }
    }

    @Test
    void everyMobFitsTheDungeonCorridorsAndDoorways() {
        // pasillos de 5x5 por dentro y puertas de 3 de ancho por 4 de alto
        Room corridor = new Room(0, 4, 5);
        Room doorway = new Room(0, 2, 4);
        for (MythicMobType mob : catalog.all()) {
            assertTrue(MobPlacement.fits(corridor, new MobPlacement.Spot(2, 1, 2), mob.hitboxWidth(),
                mob.hitboxHeight()), mob.id());
            assertTrue(MobPlacement.fits(doorway, new MobPlacement.Spot(1, 1, 1), mob.hitboxWidth(),
                mob.hitboxHeight()), mob.id());
        }
    }
}
