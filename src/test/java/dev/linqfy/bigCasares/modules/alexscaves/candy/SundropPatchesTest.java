package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SundropPatchesTest {
    private static final int FLOOR = 0;

    @Test
    void sundropsStandOnFrostedChocolateInAir() {
        BlockData frosted = data("frosted");
        BlockData sundrop = data("sundrop");
        World world = new World(frosted);
        CandyFeatures.SundropPatches feature = new CandyFeatures.SundropPatches(frosted, sundrop, m -> m == Material.STONE);
        for (int chunkX = 0; chunkX < 60; chunkX++) {
            feature.place(world.chunk(chunkX, CaveBiomeId.CANDY_CAVITY));
        }
        assertFalse(world.placed.isEmpty(), "a frosted chocolate floor gets sundrops");
        for (int[] pos : world.placed) {
            assertEquals(FLOOR + 1, pos[1], "right above the floor");
        }
    }

    @Test
    void generationIsDeterministicPerChunk() {
        BlockData frosted = data("frosted");
        BlockData sundrop = data("sundrop");
        List<String> runs = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            World world = new World(frosted);
            CandyFeatures.SundropPatches feature = new CandyFeatures.SundropPatches(frosted, sundrop, m -> m == Material.STONE);
            for (int chunkX = 0; chunkX < 20; chunkX++) {
                feature.place(world.chunk(chunkX, CaveBiomeId.CANDY_CAVITY));
            }
            StringBuilder out = new StringBuilder();
            world.placed.forEach(pos -> out.append(pos[0]).append(',').append(pos[1]).append(',').append(pos[2]).append(';'));
            runs.add(out.toString());
        }
        assertEquals(runs.get(0), runs.get(1));
    }

    @Test
    void otherBiomesGetNothing() {
        BlockData frosted = data("frosted");
        World world = new World(frosted);
        CandyFeatures.SundropPatches feature = new CandyFeatures.SundropPatches(frosted, data("sundrop"), m -> m == Material.STONE);
        for (int chunkX = 0; chunkX < 60; chunkX++) {
            feature.place(world.chunk(chunkX, CaveBiomeId.PRIMORDIAL_CAVES));
        }
        assertTrue(world.placed.isEmpty());
    }

    @Test
    void otherFloorsGetNothing() {
        BlockData frosted = data("frosted");
        World world = new World(data("something else"));
        CandyFeatures.SundropPatches feature = new CandyFeatures.SundropPatches(frosted, data("sundrop"), m -> m == Material.STONE);
        for (int chunkX = 0; chunkX < 60; chunkX++) {
            feature.place(world.chunk(chunkX, CaveBiomeId.CANDY_CAVITY));
        }
        assertTrue(world.placed.isEmpty());
    }

    @Test
    void worldBottomNeverReadsBelowTheRegion() {
        BlockData frosted = data("frosted");
        World world = new World(frosted) {
            @Override
            public Material getType(int x, int y, int z) {
                if (y < -64) {
                    throw new IllegalStateException("below region: " + y);
                }
                return y <= -64 ? Material.STONE : Material.AIR;
            }

            @Override
            public BlockData getBlockData(int x, int y, int z) {
                if (y < -64) {
                    throw new IllegalStateException("below region: " + y);
                }
                return y == -64 ? floorData : data("stone");
            }
        };
        CandyFeatures.SundropPatches feature = new CandyFeatures.SundropPatches(frosted, data("sundrop"), m -> m == Material.STONE);
        for (int chunkX = 0; chunkX < 200; chunkX++) {
            feature.place(world.chunk(chunkX, CaveBiomeId.CANDY_CAVITY));
        }
        assertFalse(world.placed.isEmpty());
        for (int[] pos : world.placed) {
            assertEquals(-63, pos[1]);
        }
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

    /** Stone up to the floor (its top block is {@code floorData}), air above, open region around the chunks. */
    private static class World implements CaveChunk.BlockSink {
        final BlockData floorData;
        final Map<List<Integer>, BlockData> set = new HashMap<>();
        final List<int[]> placed = new ArrayList<>();

        World(BlockData floorData) {
            this.floorData = floorData;
        }

        CaveChunk chunk(int chunkX, CaveBiomeId biome) {
            return new CaveChunk(42L, chunkX, 0, -64, new CaveChunk.BiomeMask() {
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
            return y >= -64 && y < 320;
        }

        @Override
        public Material getType(int x, int y, int z) {
            if (set.containsKey(List.of(x, y, z))) {
                return Material.SHORT_GRASS;
            }
            return y <= FLOOR ? Material.STONE : Material.AIR;
        }

        @Override
        public BlockData getBlockData(int x, int y, int z) {
            return y == FLOOR ? floorData : data("stone");
        }

        @Override
        public void setBlock(int x, int y, int z, BlockData data) {
            set.put(List.of(x, y, z), data);
            placed.add(new int[] {x, y, z});
        }
    }
}
