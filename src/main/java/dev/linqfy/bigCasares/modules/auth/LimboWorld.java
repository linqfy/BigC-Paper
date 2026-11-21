package dev.linqfy.bigCasares.modules.auth;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Empty void world where players wait until they log in, so nothing about the real worlds (terrain, coordinates,
 * nearby players) reaches an unauthenticated client.
 */
final class LimboWorld {

    static final int PLATFORM_Y = 100;

    private LimboWorld() {
    }

    static World createOrLoad(String name) {
        World existing = Bukkit.getWorld(name);
        World world = existing != null ? existing : new WorldCreator(name)
            .environment(World.Environment.NORMAL)
            .generator(new VoidGenerator())
            .biomeProvider(new VoidBiomes())
            .generateStructures(false)
            .createWorld();
        if (world == null) {
            throw new IllegalStateException("Could not create the login limbo world " + name);
        }
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.REDUCED_DEBUG_INFO, true);
        world.setTime(18_000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setAutoSave(false);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(x, PLATFORM_Y - 1, z).setType(Material.BARRIER, false);
            }
        }
        world.setSpawnLocation(0, PLATFORM_Y, 0);
        return world;
    }

    static Location spawn(World world) {
        return new Location(world, 0.5, PLATFORM_Y, 0.5, 0f, 0f);
    }

    static final class VoidGenerator extends ChunkGenerator {
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

    static final class VoidBiomes extends BiomeProvider {
        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return Biome.THE_VOID;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(Biome.THE_VOID);
        }
    }
}
