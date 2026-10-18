package dev.linqfy.bigCasares.modules.sift;

import java.util.Random;

/**
 * The rules of the mod's {@code SculkflowerCropBlock} (a {@code CropBlock} with ages 0 to 2) and
 * {@code SculkflowerBlock}, without a server. The crop has two growing stages (ages 0 and 1); growing
 * from age 1 turns it straight into the Sculkflower, so age 2 never exists as a block.
 *
 * <p>As in vanilla's {@code CropBlock}: a random tick grows the crop when the light level is at least
 * {@value #MIN_LIGHT_TO_GROW} with chance {@code 1 / ((int) (25 / growthSpeed) + 1)}; the crop breaks
 * below light level {@value #MIN_LIGHT_TO_SURVIVE}; a bone meal adds {@value #BONEMEAL_AGE_INCREASE} age
 * (the mod's {@code getBonemealAgeIncrease}). The growth speed counts the nine soil blocks under and around
 * the crop (farmland 1, moist farmland 3, the eight outer ones a quarter of that) and halves when the same
 * crop stands in the row and the column or, failing that, diagonally. The mod lets its healthy sculk soils
 * count as fully moist farmland; plain sculk counts for nothing.</p>
 */
final class SculkflowerCrop {

    static final int MAX_AGE = 2;
    static final int MIN_LIGHT_TO_GROW = 9;
    static final int MIN_LIGHT_TO_SURVIVE = 8;
    static final int BONEMEAL_AGE_INCREASE = 1;

    /** What the crop or flower stands on. */
    enum Floor {
        FARMLAND,
        SCULK,
        HEALTHY_SCULK,
        DRY_HEALTHY_SCULK,
        OTHER;

        /** {@code mayPlaceOn} of the mod's two blocks (the Sculkflower also takes vanilla's own cactus-flower floors, not ported). */
        boolean plantable() {
            return this != OTHER;
        }
    }

    /** How much a soil block adds to the growth speed. */
    enum Soil {
        OTHER,
        DRY_FARMLAND,
        MOIST_FARMLAND,
        /** The mod's healthy sculk and dry healthy sculk: farmland with moisture 7. */
        HYDRATED;

        static Soil of(Floor floor, int farmlandMoisture) {
            return switch (floor) {
                case FARMLAND -> farmlandMoisture > 0 ? MOIST_FARMLAND : DRY_FARMLAND;
                case HEALTHY_SCULK, DRY_HEALTHY_SCULK -> HYDRATED;
                default -> OTHER;
            };
        }
    }

    /** The blocks around a crop: the soil under offset (dx, dz) and whether a crop of this kind stands at it. */
    interface Surroundings {
        Soil soil(int dx, int dz);

        boolean cropAt(int dx, int dz);
    }

    private SculkflowerCrop() {
    }

    /** {@code CropBlock.getGrowthSpeed}. */
    static float growthSpeed(Surroundings around) {
        float speed = 1.0F;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                float blockSpeed = switch (around.soil(dx, dz)) {
                    case DRY_FARMLAND -> 1.0F;
                    case MOIST_FARMLAND, HYDRATED -> 3.0F;
                    case OTHER -> 0.0F;
                };
                if (dx != 0 || dz != 0) {
                    blockSpeed /= 4.0F;
                }
                speed += blockSpeed;
            }
        }
        boolean sameInRow = around.cropAt(-1, 0) || around.cropAt(1, 0);
        boolean sameInColumn = around.cropAt(0, -1) || around.cropAt(0, 1);
        if (sameInRow && sameInColumn) {
            speed /= 2.0F;
        } else if (around.cropAt(-1, -1) || around.cropAt(1, -1) || around.cropAt(1, 1) || around.cropAt(-1, 1)) {
            speed /= 2.0F;
        }
        return speed;
    }

    /** One random tick of a crop that has not finished growing: true when it grows one age. */
    static boolean growsOnRandomTick(Random random, int light, float growthSpeed) {
        return light >= MIN_LIGHT_TO_GROW && random.nextInt((int) (25.0F / growthSpeed) + 1) == 0;
    }

    /** Whether a crop or flower can stay where it is ({@code hasSufficientLight}); the Sculkflower itself needs no light. */
    static boolean cropSurvivesLight(int light) {
        return light >= MIN_LIGHT_TO_SURVIVE;
    }

    /** The age after a bone meal; 2 or more means the Sculkflower. */
    static int ageAfterBoneMeal(int age) {
        return Math.min(MAX_AGE, age + BONEMEAL_AGE_INCREASE);
    }

    /**
     * The chance that a block gets at least one random tick in {@code ticks} server ticks: each tick
     * Minecraft picks {@code randomTickSpeed} random blocks of each 16x16x16 section (4096 blocks).
     */
    static double randomTickChance(int randomTickSpeed, int ticks) {
        if (randomTickSpeed <= 0 || ticks <= 0) {
            return 0.0;
        }
        double perTick = 1.0 - Math.pow(1.0 - 1.0 / 4096.0, randomTickSpeed);
        return 1.0 - Math.pow(1.0 - perTick, ticks);
    }
}
