package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.modules.sift.SculkflowerCrop.Floor;
import dev.linqfy.bigCasares.modules.sift.SculkflowerCrop.Soil;
import dev.linqfy.bigCasares.modules.sift.SculkflowerCrop.Surroundings;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SculkflowerCropTest {

    private static Surroundings around(BiFunction<Integer, Integer, Soil> soil, BiPredicate<Integer, Integer> crops) {
        return new Surroundings() {
            @Override
            public Soil soil(int dx, int dz) {
                return soil.apply(dx, dz);
            }

            @Override
            public boolean cropAt(int dx, int dz) {
                return crops.test(dx, dz);
            }
        };
    }

    private static Surroundings everywhere(Soil soil) {
        return around((dx, dz) -> soil, (dx, dz) -> false);
    }

    @Test
    void theCropHasTwoGrowingStagesAndTheThirdIsTheFlower() {
        assertEquals(2, SculkflowerCrop.MAX_AGE);
        assertEquals(1, SculkflowerCrop.ageAfterBoneMeal(0));
        assertEquals(2, SculkflowerCrop.ageAfterBoneMeal(1), "age 2 is the flower");
        assertEquals(2, SculkflowerCrop.ageAfterBoneMeal(2));
    }

    @Test
    void growthSpeedMatchesVanillasCropBlockOnFarmland() {
        // 1 + 3 for the moist farmland under it + 8 * 3 / 4 for the ring.
        assertEquals(10.0F, SculkflowerCrop.growthSpeed(everywhere(Soil.MOIST_FARMLAND)));
        // Dry farmland: 1 + 1 + 8 * 1 / 4.
        assertEquals(4.0F, SculkflowerCrop.growthSpeed(everywhere(Soil.DRY_FARMLAND)));
        assertEquals(1.0F, SculkflowerCrop.growthSpeed(everywhere(Soil.OTHER)), "plain sculk and everything else add nothing");
    }

    @Test
    void healthySculkCountsAsFullyMoistFarmland() {
        assertEquals(SculkflowerCrop.growthSpeed(everywhere(Soil.MOIST_FARMLAND)), SculkflowerCrop.growthSpeed(everywhere(Soil.HYDRATED)));
        assertEquals(Soil.HYDRATED, Soil.of(Floor.HEALTHY_SCULK, 0));
        assertEquals(Soil.HYDRATED, Soil.of(Floor.DRY_HEALTHY_SCULK, 0));
        assertEquals(Soil.OTHER, Soil.of(Floor.SCULK, 7));
        assertEquals(Soil.MOIST_FARMLAND, Soil.of(Floor.FARMLAND, 1));
        assertEquals(Soil.DRY_FARMLAND, Soil.of(Floor.FARMLAND, 0));
        assertEquals(Soil.OTHER, Soil.of(Floor.OTHER, 0));
    }

    @Test
    void aCropUnderOnlyHealthySculkBelowItGrowsAtThreeTimesTheBase() {
        Surroundings single = around((dx, dz) -> dx == 0 && dz == 0 ? Soil.HYDRATED : Soil.OTHER, (dx, dz) -> false);
        assertEquals(4.0F, SculkflowerCrop.growthSpeed(single));
    }

    @Test
    void sameCropsInTheRowAndColumnOrDiagonallyHalveTheSpeed() {
        Surroundings base = everywhere(Soil.MOIST_FARMLAND);
        float full = SculkflowerCrop.growthSpeed(base);
        assertEquals(full, SculkflowerCrop.growthSpeed(around((dx, dz) -> Soil.MOIST_FARMLAND, (dx, dz) -> dx == 1 && dz == 0)));
        assertEquals(full, SculkflowerCrop.growthSpeed(around((dx, dz) -> Soil.MOIST_FARMLAND, (dx, dz) -> dx == 0 && dz == -1)));
        assertEquals(full / 2.0F, SculkflowerCrop.growthSpeed(around((dx, dz) -> Soil.MOIST_FARMLAND,
            (dx, dz) -> (dx == 1 && dz == 0) || (dx == 0 && dz == 1))));
        assertEquals(full / 2.0F, SculkflowerCrop.growthSpeed(around((dx, dz) -> Soil.MOIST_FARMLAND, (dx, dz) -> dx == -1 && dz == 1)));
        // Halved once, not twice, when both a diagonal and a row neighbour exist without a column neighbour.
        assertEquals(full / 2.0F, SculkflowerCrop.growthSpeed(around((dx, dz) -> Soil.MOIST_FARMLAND,
            (dx, dz) -> (dx == 1 && dz == 0) || (dx == 1 && dz == 1))));
    }

    @Test
    void aRandomTickGrowsTheCropWithOneChanceInTheTruncatedSpeedTermPlusOne() {
        // Speed 10: nextInt(3) == 0, so one tick in three.
        int grown = 0;
        Random random = new Random(7);
        for (int tick = 0; tick < 30_000; tick++) {
            if (SculkflowerCrop.growsOnRandomTick(random, 15, 10.0F)) {
                grown++;
            }
        }
        assertEquals(10_000, grown, 600);
        // Speed 1 (plain sculk): one in 26.
        grown = 0;
        for (int tick = 0; tick < 52_000; tick++) {
            if (SculkflowerCrop.growsOnRandomTick(random, 15, 1.0F)) {
                grown++;
            }
        }
        assertEquals(2_000, grown, 250);
    }

    @Test
    void nothingGrowsBelowLightLevelNine() {
        Random random = new Random(1);
        for (int tick = 0; tick < 2_000; tick++) {
            assertFalse(SculkflowerCrop.growsOnRandomTick(random, 8, 10.0F));
        }
        assertTrue(SculkflowerCrop.cropSurvivesLight(8));
        assertFalse(SculkflowerCrop.cropSurvivesLight(7));
    }

    @Test
    void onlyTheFourFloorsOfTheModAllowTheCropAndTheFlower() {
        assertEquals(Set.of(Floor.FARMLAND, Floor.SCULK, Floor.HEALTHY_SCULK, Floor.DRY_HEALTHY_SCULK),
            Set.of(Floor.values()).stream().filter(Floor::plantable).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void theChanceOfARandomTickFollowsTheGameRule() {
        assertEquals(0.0, SculkflowerCrop.randomTickChance(0, 20));
        assertEquals(0.0, SculkflowerCrop.randomTickChance(3, 0));
        // Three picks per tick among 4096 blocks, over twenty ticks.
        assertEquals(1.0 - Math.pow(1.0 - (1.0 - Math.pow(4095.0 / 4096.0, 3)), 20), SculkflowerCrop.randomTickChance(3, 20), 1.0E-12);
        assertEquals(0.0145, SculkflowerCrop.randomTickChance(3, 20), 1.0E-4);
        assertTrue(SculkflowerCrop.randomTickChance(100, 20) > SculkflowerCrop.randomTickChance(3, 20));
        assertTrue(SculkflowerCrop.randomTickChance(3, 40) > SculkflowerCrop.randomTickChance(3, 20));
    }
}
