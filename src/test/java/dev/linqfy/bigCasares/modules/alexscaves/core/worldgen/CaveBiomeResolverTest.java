package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeResolver.SurfaceKind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaveBiomeResolverTest {
    private static final VoronoiRarity RARITY = new VoronoiRarity(1234L, VoronoiRarity.RarityParameters.MOD_DEFAULTS);

    @Test
    void nothingGeneratesInsideTheMinimumSpawnDistance() {
        CaveBiomeResolver resolver = resolver(SurfaceKind.LAND, EnumSet.allOf(CaveBiomeId.class));
        for (int x = -399; x <= 399; x += 7) {
            for (int z = -399; z <= 399; z += 7) {
                if ((long) x * x + (long) z * z < 400L * 400L) {
                    assertTrue(resolver.columnBiome(x, z).isEmpty());
                }
            }
        }
    }

    @Test
    void abyssalChasmOnlyUnderOceanAndOthersOnlyUnderLand() {
        CaveBiomeResolver land = resolver(SurfaceKind.LAND, EnumSet.allOf(CaveBiomeId.class));
        CaveBiomeResolver ocean = resolver(SurfaceKind.OCEAN, EnumSet.allOf(CaveBiomeId.class));
        CaveBiomeResolver other = resolver(SurfaceKind.OTHER, EnumSet.allOf(CaveBiomeId.class));
        boolean sawLandBiome = false;
        boolean sawAbyssal = false;
        for (int x = 2000; x < 60000; x += 211) {
            Optional<CaveBiomeId> onLand = land.columnBiome(x, 5000);
            Optional<CaveBiomeId> onOcean = ocean.columnBiome(x, 5000);
            assertFalse(onLand.isPresent() && onLand.get() == CaveBiomeId.ABYSSAL_CHASM);
            assertTrue(onOcean.isEmpty() || onOcean.get() == CaveBiomeId.ABYSSAL_CHASM);
            assertTrue(other.columnBiome(x, 5000).isEmpty());
            sawLandBiome |= onLand.isPresent();
            sawAbyssal |= onOcean.isPresent();
        }
        assertTrue(sawLandBiome);
        assertTrue(sawAbyssal);
    }

    @Test
    void disabledBiomesAreSkipped() {
        CaveBiomeResolver resolver = resolver(SurfaceKind.LAND, EnumSet.of(CaveBiomeId.CANDY_CAVITY));
        for (int x = 2000; x < 80000; x += 173) {
            resolver.columnBiome(x, -9000).ifPresent(biome -> assertEquals(CaveBiomeId.CANDY_CAVITY, biome));
        }
    }

    @Test
    void depthBandFollowsTheModRanges() {
        // Magnetic caves: depth 0.2..1.0 -> 25.6..128 blocks under the surface.
        assertFalse(CaveBiomeResolver.inDepthBand(CaveBiomeId.MAGNETIC_CAVES, 60, 70));
        assertTrue(CaveBiomeResolver.inDepthBand(CaveBiomeId.MAGNETIC_CAVES, 30, 70));
        assertTrue(CaveBiomeResolver.inDepthBand(CaveBiomeId.MAGNETIC_CAVES, -58, 70));
        assertFalse(CaveBiomeResolver.inDepthBand(CaveBiomeId.MAGNETIC_CAVES, -59, 70));
        // Primordial goes deeper (1.5 -> 192 blocks).
        assertTrue(CaveBiomeResolver.inDepthBand(CaveBiomeId.PRIMORDIAL_CAVES, -110, 70));
    }

    @Test
    void customSpawnDistanceIsHonoured() {
        CaveBiomeResolver near = new CaveBiomeResolver(RARITY, EnumSet.allOf(CaveBiomeId.class),
            Map.of(CaveBiomeId.MAGNETIC_CAVES, 0), (x, z) -> SurfaceKind.LAND);
        CaveBiomeResolver far = new CaveBiomeResolver(RARITY, EnumSet.allOf(CaveBiomeId.class),
            Map.of(CaveBiomeId.MAGNETIC_CAVES, 1_000_000), (x, z) -> SurfaceKind.LAND);
        for (int x = 0; x < 100000; x += 97) {
            far.columnBiome(x, 0).ifPresent(biome -> assertFalse(biome == CaveBiomeId.MAGNETIC_CAVES));
        }
        boolean nearMagnetic = false;
        for (int x = 0; x < 100000 && !nearMagnetic; x += 97) {
            nearMagnetic = near.columnBiome(x, 0).filter(b -> b == CaveBiomeId.MAGNETIC_CAVES).isPresent();
        }
        assertTrue(nearMagnetic);
    }

    @Test
    void configKeysAndParsingRoundTrip() {
        for (CaveBiomeId biome : CaveBiomeId.values()) {
            assertEquals(Optional.of(biome), CaveBiomeId.parse(biome.configKey()));
            assertEquals(Optional.of(biome), CaveBiomeId.parse(biome.key()));
            assertEquals(Optional.of(biome), CaveBiomeId.byRarityId(biome.rarityId()));
        }
        assertTrue(CaveBiomeId.parse("nether").isEmpty());
    }

    @Test
    void nearestFindsAColumnOfTheBiome() {
        CaveBiomeResolver resolver = resolver(SurfaceKind.LAND, EnumSet.allOf(CaveBiomeId.class));
        Optional<int[]> found = resolver.nearest(CaveBiomeId.PRIMORDIAL_CAVES, 0, 0, 6000, 32);
        assertTrue(found.isPresent(), "primordial caves exist within 6000 blocks");
        assertEquals(Optional.of(CaveBiomeId.PRIMORDIAL_CAVES), resolver.columnBiome(found.get()[0], found.get()[1]));
        // No sample of the same grid is closer.
        long best = (long) found.get()[0] * found.get()[0] + (long) found.get()[1] * found.get()[1];
        int reach = (int) Math.sqrt(best) / 32 + 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                long distance = (long) dx * 32 * dx * 32 + (long) dz * 32 * dz * 32;
                if (distance < best) {
                    assertFalse(resolver.columnBiome(dx * 32, dz * 32).filter(b -> b == CaveBiomeId.PRIMORDIAL_CAVES)
                        .isPresent(), "closer sample at " + dx * 32 + "," + dz * 32);
                }
            }
        }
        assertTrue(resolver(SurfaceKind.LAND, EnumSet.noneOf(CaveBiomeId.class))
            .nearest(CaveBiomeId.PRIMORDIAL_CAVES, 0, 0, 500, 32).isEmpty(), "disabled biome is never found");
    }

    private static CaveBiomeResolver resolver(SurfaceKind surface, EnumSet<CaveBiomeId> enabled) {
        return new CaveBiomeResolver(RARITY, enabled, Map.of(), (x, z) -> surface);
    }
}
