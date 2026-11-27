package dev.linqfy.bigCasares.modules.alexscaves.candy;

import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveBiomeId;
import dev.linqfy.bigCasares.modules.alexscaves.core.worldgen.CaveChunk;
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

class CeilingFrostmintTest {
    private static final int CEILING = 50;
    private static final BlockData CAKE = data("cake");
    private static final BlockData CYAN = data("cyan");
    private static final BlockData WHITE = data("white");
    private static final BlockData TOP = data("top");
    private static final BlockData DOUBLE = data("double");
    private static final BlockData ROCK = data("rock");

    private static CandyDecorations.CeilingFrostmint feature() {
        return new CandyDecorations.CeilingFrostmint(CAKE::equals, CYAN, WHITE, TOP, DOUBLE, m -> m == Material.STONE);
    }

    private static World generate(World world, CaveBiomeId biome) {
        CandyDecorations.CeilingFrostmint feature = feature();
        for (int chunkX = 0; chunkX < 80; chunkX++) {
            feature.place(world.chunk(chunkX, biome));
        }
        return world;
    }

    @Test
    void ceilingsOverSodaGetRockCandyAndHangingFrostmint() {
        World world = generate(new World(true), CaveBiomeId.CANDY_CAVITY);
        assertTrue(world.set.containsValue(DOUBLE), "double slabs hang from the ceiling");
        assertTrue(world.set.containsValue(CYAN) || world.set.containsValue(WHITE), "the ceiling grows rock candy");
        for (Map.Entry<List<Integer>, BlockData> entry : world.set.entrySet()) {
            int y = entry.getKey().get(1);
            BlockData data = entry.getValue();
            if (data == CYAN || data == WHITE) {
                assertTrue(Math.abs(y - CEILING) <= 2, "blobs stay around the ceiling block: " + y);
            } else {
                // Columns start one block above the ceiling block and are at most six blocks long.
                assertTrue(y <= CEILING + 1 && y >= CEILING + 1 - 5, "frostmint height " + y);
            }
        }
    }

    @Test
    void generationMakesTopSlabsToo() {
        World world = generate(new World(true), CaveBiomeId.CANDY_CAVITY);
        assertTrue(world.set.containsValue(TOP), "some columns end in a top slab");
    }

    @Test
    void aColumnHangsFromAboveTheCeilingBlockAndOnlyItsEndCanBeATopSlab() {
        World world = new World(true);
        feature().hang(world, 4, CEILING, 4, 3, true);
        assertEquals(4, world.set.size());
        assertEquals(DOUBLE, world.set.get(List.of(4, CEILING + 1, 4)));
        assertEquals(DOUBLE, world.set.get(List.of(4, CEILING, 4)));
        assertEquals(DOUBLE, world.set.get(List.of(4, CEILING - 1, 4)));
        assertEquals(TOP, world.set.get(List.of(4, CEILING - 2, 4)));
        World doubles = new World(true);
        feature().hang(doubles, 4, CEILING, 4, 3, false);
        assertFalse(doubles.set.containsValue(TOP));
    }

    @Test
    void frostmintAlreadyThereIsKept() {
        World world = new World(true);
        feature().hang(world, 4, CEILING, 4, 2, true);
        feature().hang(world, 4, CEILING, 4, 4, false);
        assertEquals(TOP, world.set.get(List.of(4, CEILING - 1, 4)), "the first column's end stays");
        assertEquals(DOUBLE, world.set.get(List.of(4, CEILING - 3, 4)));
    }

    @Test
    void rockCandyOnlyReplacesTheCeilingMaterial() {
        World world = generate(new World(true), CaveBiomeId.CANDY_CAVITY);
        for (Map.Entry<List<Integer>, BlockData> entry : world.set.entrySet()) {
            if (entry.getValue() == CYAN || entry.getValue() == WHITE) {
                assertEquals(CAKE, world.original(entry.getKey()), "only cake becomes rock candy");
            }
        }
    }

    @Test
    void withoutPurpleSodaBelowNothingGrows() {
        World world = generate(new World(false), CaveBiomeId.CANDY_CAVITY);
        assertTrue(world.set.isEmpty());
    }

    @Test
    void otherBiomesGetNothing() {
        World world = generate(new World(true), CaveBiomeId.PRIMORDIAL_CAVES);
        assertTrue(world.set.isEmpty());
    }

    @Test
    void generationIsDeterministicPerChunk() {
        World first = generate(new World(true), CaveBiomeId.CANDY_CAVITY);
        World second = generate(new World(true), CaveBiomeId.CANDY_CAVITY);
        assertEquals(first.set, second.set);
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

    /** Air between a floor (soda over it when {@code soda}) and a ceiling of cake from {@link #CEILING} up. */
    private static final class World implements CaveChunk.BlockSink {
        final boolean soda;
        final Map<List<Integer>, BlockData> set = new HashMap<>();

        World(boolean soda) {
            this.soda = soda;
        }

        CaveChunk chunk(int chunkX, CaveBiomeId biome) {
            return new CaveChunk(11L, chunkX, 0, -64, new CaveChunk.BiomeMask() {
                @Override
                public Optional<CaveBiomeId> biomeAt(int x, int y, int z) {
                    return Optional.of(biome);
                }

                @Override
                public int surfaceY(int x, int z) {
                    return 200;
                }
            }, this);
        }

        BlockData original(List<Integer> pos) {
            return pos.get(1) >= CEILING ? CAKE : ROCK;
        }

        @Override
        public boolean isInRegion(int x, int y, int z) {
            return y >= -64 && y < 320 && x >= 0 && x < 16 * 80 && z >= 0 && z < 16;
        }

        @Override
        public Material getType(int x, int y, int z) {
            if (y >= CEILING) {
                return Material.STONE;
            }
            if (y < -50) {
                return Material.STONE;
            }
            return soda && y < -40 ? Material.WATER : Material.AIR;
        }

        @Override
        public BlockData getBlockData(int x, int y, int z) {
            BlockData placed = set.get(List.of(x, y, z));
            return placed != null ? placed : original(List.of(x, y, z));
        }

        @Override
        public void setBlock(int x, int y, int z, BlockData data) {
            set.put(List.of(x, y, z), data);
        }
    }
}
