package dev.linqfy.bigCasares.modules.alexscaves.primordial;

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

class CycadFeatureTest {
    private static final BlockData TIP = data("tip");
    private static final BlockData TRUNK = data("trunk");

    @Test
    void aStackStartsOnTheGroundAndOnlyItsLastBlockIsTheTip() {
        World world = new World();
        PrimordialFeatures.Cycads feature = new PrimordialFeatures.Cycads(TIP, TRUNK, m -> m == Material.DIRT);
        feature.plant(world.chunk(0, CaveBiomeId.PRIMORDIAL_CAVES), 3, World.GROUND, 3, 0.99F);
        assertEquals(3, world.placed.size());
        assertEquals(TRUNK, world.set.get(List.of(3, World.GROUND + 1, 3)));
        assertEquals(TRUNK, world.set.get(List.of(3, World.GROUND + 2, 3)));
        assertEquals(TIP, world.set.get(List.of(3, World.GROUND + 3, 3)));
    }

    @Test
    void shortRollsMakeShortStacks() {
        World world = new World();
        PrimordialFeatures.Cycads feature = new PrimordialFeatures.Cycads(TIP, TRUNK, m -> m == Material.DIRT);
        CaveChunk chunk = world.chunk(0, CaveBiomeId.PRIMORDIAL_CAVES);
        feature.plant(chunk, 1, World.GROUND, 1, 0.0F);
        assertTrue(world.placed.isEmpty(), "a zero roll only reaches the ground block itself");
        feature.plant(chunk, 2, World.GROUND, 2, 0.2F);
        assertEquals(1, world.placed.size());
        assertEquals(TIP, world.set.get(List.of(2, World.GROUND + 1, 2)), "a single block is the tip");
    }

    @Test
    void generationFillsDirtFloorsAboveTheGround() {
        World world = new World();
        PrimordialFeatures.Cycads feature = new PrimordialFeatures.Cycads(TIP, TRUNK, m -> m == Material.DIRT);
        for (int chunkX = 0; chunkX < 20; chunkX++) {
            feature.place(world.chunk(chunkX, CaveBiomeId.PRIMORDIAL_CAVES));
        }
        assertFalse(world.placed.isEmpty(), "dirt floors get cycads");
        for (int[] pos : world.placed) {
            assertTrue(pos[1] > World.GROUND, "never inside the ground");
        }
    }

    @Test
    void nothingGrowsWithoutDirtBelow() {
        World world = new World();
        PrimordialFeatures.Cycads feature = new PrimordialFeatures.Cycads(TIP, TRUNK, m -> false);
        for (int chunkX = 0; chunkX < 20; chunkX++) {
            feature.place(world.chunk(chunkX, CaveBiomeId.PRIMORDIAL_CAVES));
        }
        assertTrue(world.placed.isEmpty());
    }

    @Test
    void otherBiomesGetNothing() {
        World world = new World();
        PrimordialFeatures.Cycads feature = new PrimordialFeatures.Cycads(TIP, TRUNK, m -> m == Material.DIRT);
        for (int chunkX = 0; chunkX < 20; chunkX++) {
            feature.place(world.chunk(chunkX, CaveBiomeId.CANDY_CAVITY));
        }
        assertTrue(world.placed.isEmpty());
    }

    @Test
    void aStackOnlyReplacesAirAndSoftPlants() {
        World world = new World();
        PrimordialFeatures.Cycads feature = new PrimordialFeatures.Cycads(TIP, TRUNK, m -> m == Material.DIRT);
        // Stone where the tip would go: the rest of the stack is built, and nothing replaces the stone.
        world.blocker = Material.STONE;
        feature.plant(world.chunk(0, CaveBiomeId.PRIMORDIAL_CAVES), 3, World.GROUND, 3, 0.99F);
        assertEquals(2, world.placed.size());
        assertEquals(TRUNK, world.set.get(List.of(3, World.GROUND + 1, 3)));
        assertEquals(TRUNK, world.set.get(List.of(3, World.GROUND + 2, 3)));
        assertFalse(world.set.containsKey(List.of(3, World.GROUND + 3, 3)));
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

    /** Dirt up to {@link #GROUND} (grass on top), air above; {@code blocker} fills the block three above the ground. */
    private static final class World implements CaveChunk.BlockSink {
        static final int GROUND = 0;
        final Map<List<Integer>, BlockData> set = new HashMap<>();
        final List<int[]> placed = new ArrayList<>();
        Material blocker;

        CaveChunk chunk(int chunkX, CaveBiomeId biome) {
            return new CaveChunk(7L, chunkX, 0, -64, new CaveChunk.BiomeMask() {
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
                return Material.JUNGLE_LOG;
            }
            if (blocker != null && y == GROUND + 3) {
                return blocker;
            }
            if (y == GROUND) {
                return Material.GRASS_BLOCK;
            }
            return y < GROUND ? Material.DIRT : Material.AIR;
        }

        @Override
        public BlockData getBlockData(int x, int y, int z) {
            return data("block");
        }

        @Override
        public void setBlock(int x, int y, int z, BlockData data) {
            set.put(List.of(x, y, z), data);
            placed.add(new int[] {x, y, z});
        }
    }
}
