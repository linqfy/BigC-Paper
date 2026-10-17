package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.EntityBlockFormEvent;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ichor snowfall (the mod's {@code ServerLevelWeatherMixin}). While it rains in the
 * Sift, Ichor snow (snow layers) settles on Ichor Snowy Peaks only, wherever the
 * block light is below 10 and snow can lie, whatever the biome's temperature;
 * nowhere else in the Sift does snow form. Layers grow up to the snow accumulation
 * game rule, at most two.
 *
 * <p>Differences: vanilla tries one column per ticked chunk with a chance of one in
 * 16 every tick; this task tries one column of every chunk within the simulation
 * distance of a player every 16 ticks. Per-dimension weather and {@code /weather}
 * in the sender's dimension need no port: Paper keeps weather per world.</p>
 */
final class SiftIchorSnowfall implements Listener {

    static final String BIOME = SiftBlocks.NS + ":ichor_snowy_peaks";
    static final int MAX_LAYERS = 2;
    static final int MAX_BLOCK_LIGHT = 9;
    static final long INTERVAL_TICKS = 16L;

    private World world;

    void activate(ActiveDimension active) {
        this.world = active.world();
        active.registrations().registerListener("sift-ichor-snowfall", this);
        active.registrations().scheduleRepeating("sift-ichor-snowfall", this::fall, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    /**
     * Layers after one snowfall attempt, or empty when nothing changes: {@code current}
     * is 0 for air, the layer count for snow and -1 for anything else.
     */
    static OptionalInt layersAfterSnowfall(String biome, int blockLight, int current, boolean supported, int accumulation) {
        if (!BIOME.equals(biome) || blockLight > MAX_BLOCK_LIGHT || current < 0 || !supported) {
            return OptionalInt.empty();
        }
        if (current > 0 && accumulation > 0) {
            // Vanilla adds a layer below the game rule; the mod then caps the result at two layers.
            if (current >= Math.min(accumulation, 8)) {
                return OptionalInt.empty();
            }
            int next = Math.min(current + 1, MAX_LAYERS);
            return next == current ? OptionalInt.empty() : OptionalInt.of(next);
        }
        // Air gets a layer; without accumulation vanilla resets snow to one layer.
        return current == 1 ? OptionalInt.empty() : OptionalInt.of(1);
    }

    /** Vanilla snow never forms in the Sift; this task places Ichor snow instead. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSnowForm(BlockFormEvent event) {
        if (!(event instanceof EntityBlockFormEvent) && event.getNewState().getType() == Material.SNOW
            && event.getBlock().getWorld().equals(world)) {
            event.setCancelled(true);
        }
    }

    private void fall() {
        World sift = world;
        if (sift == null || !sift.hasStorm()) {
            return;
        }
        int reach = sift.getSimulationDistance();
        Set<Long> chunks = new HashSet<>();
        for (Player player : sift.getPlayers()) {
            int centerX = player.getLocation().getBlockX() >> 4;
            int centerZ = player.getLocation().getBlockZ() >> 4;
            for (int cx = centerX - reach; cx <= centerX + reach; cx++) {
                for (int cz = centerZ - reach; cz <= centerZ + reach; cz++) {
                    if (chunks.add(((long) cx << 32) ^ (cz & 0xFFFFFFFFL)) && sift.isChunkLoaded(cx, cz)) {
                        attempt(sift, cx, cz);
                    }
                }
            }
        }
    }

    private static void attempt(World sift, int chunkX, int chunkZ) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = (chunkX << 4) + random.nextInt(16);
        int z = (chunkZ << 4) + random.nextInt(16);
        int y = sift.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) + 1;
        if (y < sift.getMinHeight() || y >= sift.getMaxHeight()) {
            return;
        }
        Block block = sift.getBlockAt(x, y, z);
        BlockData data = block.getBlockData();
        int current = data instanceof Snow snow ? snow.getLayers() : block.getType().isAir() ? 0 : -1;
        Snow layer = (Snow) Material.SNOW.createBlockData();
        Integer accumulation = sift.getGameRuleValue(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT);
        OptionalInt layers = layersAfterSnowfall(sift.getBiome(x, y, z).getKey().asString(), block.getLightFromBlocks(), current,
            layer.isSupported(block), accumulation == null ? 1 : accumulation);
        if (layers.isPresent()) {
            layer.setLayers(layers.getAsInt());
            block.setBlockData(layer);
        }
    }
}
