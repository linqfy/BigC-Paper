package dev.linqfy.bigCasares.modules.caichet;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** El mundo del hotel: generado entero por {@link HotelLayout}, siempre de dia, sin clima y sin mobs. */
final class HotelWorld {

    static final String NAME = "caichet_hotel";

    private HotelWorld() {
    }

    static World get() {
        return Bukkit.getWorld(NAME);
    }

    static boolean isHotel(World world) {
        return world != null && world.getName().equals(NAME);
    }

    /** Lo crea la primera vez y lo carga en los demas arranques. */
    static World load(Plugin plugin) {
        World world = get();
        if (world == null) {
            world = new WorldCreator(NAME)
                    .generator(new Generator())
                    .biomeProvider(new Biomes())
                    .generateStructures(false)
                    .createWorld();
        }
        if (world == null) {
            plugin.getLogger().warning("No se pudo crear el mundo del hotel.");
            return null;
        }
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        return world;
    }

    /** Donde se aparece: el centro de la celda (0,0), que siempre es el nivel 0. */
    static Location spawn(World world) {
        return new Location(world, 16.5, HotelLayout.FLOOR + 1, 16.5, 0f, 0f);
    }

    private static final class Generator extends ChunkGenerator {
        /** Los datos de cada bloque propio, armados una vez: el generador corre en varios hilos. */
        private static final Map<HotelBlock, BlockData> CUSTOM = new EnumMap<>(HotelBlock.class);
        /** {@code minecraft:light} por nivel (el indice es el nivel; el 0 no se usa). */
        private static final BlockData[] LIGHTS = new BlockData[16];
        /** Las bombillas de cobre encendidas de las zonas de cobre. */
        private static final BlockData LIT_BULB = Bukkit.createBlockData("minecraft:waxed_copper_bulb[lit=true]");
        private static final BlockData LIT_DIM_BULB =
            Bukkit.createBlockData("minecraft:waxed_weathered_copper_bulb[lit=true]");

        static {
            for (HotelBlock block : HotelBlock.values()) {
                CUSTOM.put(block, Bukkit.createBlockData(block.state().asString()));
            }
            for (int level = 1; level < LIGHTS.length; level++) {
                LIGHTS[level] = Bukkit.createBlockData("minecraft:light[level=" + level + "]");
            }
        }

        @Override
        public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
            HotelLayout.generate(info.getSeed(), chunkX, chunkZ, (x, y, z, cell) -> {
                switch (cell) {
                    case HotelLayout.Cell.Vanilla vanilla -> data.setBlock(x, y, z, vanilla.material());
                    case HotelLayout.Cell.Custom custom -> data.setBlock(x, y, z, CUSTOM.get(custom.block()));
                    case HotelLayout.Cell.Panel _ -> data.setBlock(x, y, z, CUSTOM.get(HotelBlock.LIGHT_PANEL));
                    case HotelLayout.Cell.Light light -> data.setBlock(x, y, z, LIGHTS[light.level()]);
                    case HotelLayout.Cell.Bulb bulb -> data.setBlock(x, y, z,
                        bulb.material() == Material.WAXED_COPPER_BULB ? LIT_BULB : LIT_DIM_BULB);
                }
            });
        }

        @Override
        public boolean shouldGenerateNoise() {
            return false;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return false;
        }

        @Override
        public boolean shouldGenerateCaves() {
            return false;
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return false;
        }

        @Override
        public boolean shouldGenerateMobs() {
            return false;
        }

        @Override
        public boolean shouldGenerateStructures() {
            return false;
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return spawn(world);
        }
    }

    private static final class Biomes extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return Biome.THE_VOID;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo info) {
            return List.of(Biome.THE_VOID);
        }
    }
}
