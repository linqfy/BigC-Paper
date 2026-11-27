package dev.linqfy.bigCasares.modules.alexscaves.core.worldgen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlantPatchFeatureTest {
    private static final int FLOOR = 0;
    private static final int CEILING = 6;
    private static final java.util.function.Predicate<Material> STONE = material -> material == Material.STONE;

    @Test
    void plantsOnlyGrowOnTheFloorInAir() {
        BlockData plant = data("plant");
        World world = new World();
        PlantPatchFeature feature = new PlantPatchFeature("test_plant", CaveBiomeId.PRIMORDIAL_CAVES, 1, 300, 300, 40, 7, 3,
            List.of(plant), STONE);
        feature.place(world.chunk(CaveBiomeId.PRIMORDIAL_CAVES));
        assertFalse(world.placed.isEmpty(), "a cave floor gets plants");
        world.placed.forEach((pos, data) -> {
            assertEquals(plant, data);
            assertEquals(FLOOR + 1, pos[1], "right above the floor");
        });
    }

    @Test
    void twoBlockPlantsNeedRoomForBothHalves() {
        BlockData bottom = data("bottom");
        BlockData top = data("top");
        World world = new World();
        world.ceiling = FLOOR + 2; // only one block of air
        new PlantPatchFeature("tall", CaveBiomeId.PRIMORDIAL_CAVES, 1, 300, 300, 40, 7, 3, List.of(bottom, top), STONE)
            .place(world.chunk(CaveBiomeId.PRIMORDIAL_CAVES));
        assertTrue(world.placed.isEmpty());

        World roomy = new World();
        new PlantPatchFeature("tall", CaveBiomeId.PRIMORDIAL_CAVES, 1, 300, 300, 40, 7, 3, List.of(bottom, top), STONE)
            .place(roomy.chunk(CaveBiomeId.PRIMORDIAL_CAVES));
        assertFalse(roomy.placed.isEmpty());
        roomy.placed.forEach((pos, data) -> assertEquals(pos[1] == FLOOR + 1 ? bottom : top, data));
    }

    @Test
    void otherBiomesGetNothing() {
        World world = new World();
        new PlantPatchFeature("test_plant", CaveBiomeId.PRIMORDIAL_CAVES, 1, 300, 300, 40, 7, 3, List.of(data("plant")), STONE)
            .place(world.chunk(CaveBiomeId.CANDY_CAVITY));
        assertTrue(world.placed.isEmpty());
    }

    private static BlockData data(String name) {
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[] {BlockData.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> name;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    /** One chunk: stone up to the floor, air up to the ceiling, stone above; open sky far above. */
    private static final class World implements CaveChunk.BlockSink {
        final Map<List<Integer>, BlockData> set = new HashMap<>();
        final Map<int[], BlockData> placed = new HashMap<>();
        int ceiling = CEILING;

        CaveChunk chunk(CaveBiomeId biome) {
            return new CaveChunk(42L, 0, 0, -64, new CaveChunk.BiomeMask() {
                @Override
                public Optional<CaveBiomeId> biomeAt(int x, int y, int z) {
                    return Optional.of(biome);
                }

                @Override
                public int surfaceY(int x, int z) {
                    return 100;
                }
            }, this);
        }

        @Override
        public boolean isInRegion(int x, int y, int z) {
            return x >= -8 && x < 24 && z >= -8 && z < 24 && y >= -64 && y < 320;
        }

        @Override
        public Material getType(int x, int y, int z) {
            if (set.containsKey(List.of(x, y, z))) {
                return Material.SHORT_GRASS;
            }
            return y <= FLOOR || y >= ceiling ? Material.STONE : Material.AIR;
        }

        @Override
        public BlockData getBlockData(int x, int y, int z) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setBlock(int x, int y, int z, BlockData data) {
            assertTrue(getType(x, y, z) == Material.AIR, "only into air");
            set.put(List.of(x, y, z), data);
            placed.put(new int[] {x, y, z}, data);
        }
    }
}
