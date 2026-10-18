package dev.linqfy.bigCasares.modules.sift;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiftTramplingTest {

    @Test
    void aFallOfMoreThanHalfABlockOntoHealthySculkDestroysTheCrop() {
        assertTrue(SiftTrampling.tramples(0.6F, "healthy_sculk", Material.WHEAT));
        assertTrue(SiftTrampling.tramples(3.0F, "dry_healthy_sculk", Material.MELON_STEM));
    }

    @Test
    void halfABlockOrLessIsNotEnough() {
        assertFalse(SiftTrampling.tramples(0.5F, "healthy_sculk", Material.WHEAT), "the mod: fallDistance > 0.5");
        assertFalse(SiftTrampling.tramples(0.0F, "healthy_sculk", Material.WHEAT));
    }

    @Test
    void onlyTheTwoHealthySculkSoilsTrample() {
        assertEquals(java.util.Set.of("healthy_sculk", "dry_healthy_sculk"), SiftTrampling.SOILS);
        assertFalse(SiftTrampling.tramples(5.0F, "siftslate", Material.WHEAT));
        assertFalse(SiftTrampling.tramples(5.0F, "dry_healthy_sculk_growth", Material.WHEAT),
            "the growth variants are other classes in the mod");
        assertFalse(SiftTrampling.tramples(5.0F, "siftslate_growth", Material.WHEAT));
    }

    @Test
    void theCropsAreTheModsCropStemAndPitcherBlocks() {
        for (Material crop : new Material[]{Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.TORCHFLOWER_CROP, Material.PITCHER_CROP, Material.PUMPKIN_STEM, Material.MELON_STEM,
            Material.ATTACHED_PUMPKIN_STEM, Material.ATTACHED_MELON_STEM}) {
            assertTrue(SiftTrampling.tramples(1.0F, "healthy_sculk", crop), crop.name());
        }
        for (Material other : new Material[]{Material.SHORT_GRASS, Material.NETHER_WART, Material.AIR, Material.WARPED_FUNGUS,
            Material.PUMPKIN, Material.SWEET_BERRY_BUSH, Material.COCOA}) {
            assertFalse(SiftTrampling.tramples(1.0F, "healthy_sculk", other), other.name());
        }
    }

    @Test
    void theBlockUnderTheFeetIsTheOneThePlayerLandedOn() {
        // Standing exactly on top of the block at y 63 means feet at y 64.0.
        assertArrayEquals(new int[]{3, 63, -5}, SiftTrampling.floorUnder(3.5, 64.0, -4.5));
        assertArrayEquals(new int[]{-1, 63, 0}, SiftTrampling.floorUnder(-0.2, 64.0004, 0.9));
    }

    @Test
    void healthySculkSoilsAreRealSiftBlocks() {
        for (String path : SiftTrampling.SOILS) {
            assertTrue(SiftBlocks.definitions().stream().anyMatch(block -> block.id().path().equals(path)), path);
        }
    }
}
