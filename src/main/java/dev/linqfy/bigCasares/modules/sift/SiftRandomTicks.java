package dev.linqfy.bigCasares.modules.sift;

import dev.linqfy.bigCasares.dimension.runtime.ActiveDimension;
import dev.linqfy.bigCasares.dimension.runtime.DimensionExtension;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Random ticks for the Sift's custom blocks. A custom full-cube block is a mushroom-block state, which the server
 * never random-ticks as the mod's block, so this extension draws its own ticks: for every chunk within the world's
 * simulation distance of a player it samples {@value #DEFAULT_SAMPLES_PER_SECTION} blocks per 16x16x16 section per
 * second (the {@code samples-per-section-per-second} setting) and hands the sampled red and stem mushroom blocks to the
 * code that ports the mod's {@code randomTick}: {@link SiftSoils#randomTick} (Siftslate Growth and Dry Healthy Sculk
 * Growth) and {@link SiftFoliage#randomTick} (willow foliage decay).
 *
 * <p><b>Cost.</b> {@link RandomTickSampler} bounds a run to {@value #MAX_SAMPLES_PER_RUN} samples (a run every
 * {@value #INTERVAL_TICKS} ticks, so 1600 a tick at most, each a material lookup in a loaded chunk); a world with more
 * chunks than the budget covers is sampled less often per section, never at a higher cost. At most
 * {@value #MAX_FOLIAGE_CHECKS_PER_RUN} foliage searches (up to 377 block reads each) and
 * {@value #MAX_SOIL_CHECKS_PER_RUN} soil checks run per run; samples past those caps are skipped.</p>
 *
 * <p>Differences: vanilla's rate is the {@code randomTickSpeed} game rule (3 per section per tick, 60 a second), the
 * port's is 8 a second by default, so a block is ticked about once in 8.5 minutes instead of once in 68 seconds;
 * sections are sampled whole (including solid and empty ones) and only chunks that are loaded.</p>
 */
final class SiftRandomTicks implements DimensionExtension {

    static final int DEFAULT_SAMPLES_PER_SECTION = 8;
    static final long INTERVAL_TICKS = 5L;
    static final int MAX_SAMPLES_PER_RUN = 8000;
    static final int MAX_FOLIAGE_CHECKS_PER_RUN = 24;
    static final int MAX_SOIL_CHECKS_PER_RUN = 256;
    /** Never farther than this many chunks from a player, whatever the simulation distance. */
    static final int MAX_RADIUS_CHUNKS = 10;

    private final SiftSoils soils;
    private final SiftFoliage foliage;
    private final int samplesPerSectionPerSecond;
    private final Random random = new Random();
    private World world;
    private RandomTickSampler sampler;

    SiftRandomTicks(SiftSoils soils, SiftFoliage foliage, int samplesPerSectionPerSecond) {
        this.soils = soils;
        this.foliage = foliage;
        this.samplesPerSectionPerSecond = samplesPerSectionPerSecond;
    }

    @Override
    public void onActivate(ActiveDimension active) {
        world = active.world();
        sampler = new RandomTickSampler(world.getMinHeight(), world.getMaxHeight(), samplesPerSectionPerSecond,
            INTERVAL_TICKS, MAX_SAMPLES_PER_RUN);
        active.registrations().scheduleRepeating("sift-random-ticks", this::run, INTERVAL_TICKS, INTERVAL_TICKS);
        active.registrations().ownCleanup("sift-random-ticks-world", () -> {
            world = null;
            sampler = null;
        });
    }

    /** The chunks within the simulation distance of any player, loaded ones only, in a stable order. */
    static List<RandomTickSampler.ChunkPos> chunksAround(World sift, int radius) {
        Set<RandomTickSampler.ChunkPos> chunks = new HashSet<>();
        for (Player player : sift.getPlayers()) {
            int centerX = player.getLocation().getBlockX() >> 4;
            int centerZ = player.getLocation().getBlockZ() >> 4;
            for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
                for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
                    if (sift.isChunkLoaded(cx, cz)) {
                        chunks.add(new RandomTickSampler.ChunkPos(cx, cz));
                    }
                }
            }
        }
        List<RandomTickSampler.ChunkPos> ordered = new ArrayList<>(chunks);
        ordered.sort((a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z()));
        return ordered;
    }

    private void run() {
        World sift = world;
        RandomTickSampler current = sampler;
        if (sift == null || current == null || sift.getPlayers().isEmpty()) {
            return;
        }
        int radius = Math.min(MAX_RADIUS_CHUNKS, sift.getSimulationDistance());
        int foliageChecks = 0;
        int soilChecks = 0;
        for (SiftPos sample : current.next(chunksAround(sift, radius), random)) {
            Material type = sift.getType(sample.x(), sample.y(), sample.z());
            if (type == Material.RED_MUSHROOM_BLOCK && soilChecks < MAX_SOIL_CHECKS_PER_RUN) {
                soilChecks++;
                soils.randomTick(sift.getBlockAt(sample.x(), sample.y(), sample.z()));
            } else if (type == Material.MUSHROOM_STEM && foliageChecks < MAX_FOLIAGE_CHECKS_PER_RUN) {
                foliageChecks++;
                foliage.randomTick(sift.getBlockAt(sample.x(), sample.y(), sample.z()));
            }
        }
    }
}
