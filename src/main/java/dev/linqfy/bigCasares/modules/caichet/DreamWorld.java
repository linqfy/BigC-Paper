package dev.linqfy.bigCasares.modules.caichet;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * El mundo del Sueño: lo genera {@link DreamLayout}, con los biomas propios del datapack (cielo azul saturado, pasto
 * verde intenso, bruma pastel, musica suave). Siempre es mediodia, nunca llueve y no aparecen mobs.
 */
final class DreamWorld {

    static final String NAME = "caichet_sueno";

    private DreamWorld() {
    }

    static World get() {
        return Bukkit.getWorld(NAME);
    }

    static boolean isDream(World world) {
        return world != null && world.getName().equals(NAME);
    }

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
            plugin.getLogger().warning("No se pudo crear el mundo del Sueño.");
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

    /** Donde se llega: el origen, en la pradera de los ojos. */
    static Location spawn(World world) {
        int y = DreamLayout.height(world.getSeed(), 0, 0) + 1;
        return new Location(world, 0.5, y, 0.5, 180f, 0f);
    }

    private static final class Generator extends ChunkGenerator {
        @Override
        public void generateNoise(WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
            DreamLayout.generate(info.getSeed(), chunkX, chunkZ, new DreamLayout.Sink() {
                @Override
                public void set(int x, int y, int z, Material material) {
                    if (material == Material.OAK_LEAVES) {
                        // hojas que no se caen solas
                        Leaves leaves = (Leaves) material.createBlockData();
                        leaves.setPersistent(true);
                        data.setBlock(x, y, z, leaves);
                    } else {
                        data.setBlock(x, y, z, material);
                    }
                }

                @Override
                public void door(int x, int y, int z, Material door, int facing) {
                    BlockFace face = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}[facing];
                    for (Bisected.Half half : Bisected.Half.values()) {
                        Door d = (Door) door.createBlockData();
                        d.setFacing(face);
                        d.setHalf(half);
                        d.setOpen(true);
                        data.setBlock(x, half == Bisected.Half.BOTTOM ? y : y + 1, z, d);
                    }
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

    /** Cada zona del Sueño usa el bioma de su foto (colores de cielo, pasto y bruma). */
    private static final class Biomes extends BiomeProvider {
        private final Map<DreamLayout.Theme, Biome> biomes = new EnumMap<>(DreamLayout.Theme.class);

        Biomes() {
            Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
            for (DreamLayout.Theme theme : DreamLayout.Theme.values()) {
                Biome biome = registry.get(new NamespacedKey("caichet", theme.biome));
                biomes.put(theme, biome != null ? biome : Biome.MEADOW);
            }
        }

        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return biomes.get(DreamLayout.theme(info.getSeed(), x, z));
        }

        @Override
        public List<Biome> getBiomes(WorldInfo info) {
            return new ArrayList<>(biomes.values());
        }
    }
}
