package dev.linqfy.bigCasares.modules.sift;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Picks the blocks that get an emulated random tick. Custom blocks are mushroom-block states, which the server never
 * random-ticks for them, so the Sift draws its own samples: a fixed number per chunk section per second for every
 * chunk the caller names, at most {@code maxPerRun} per run, whatever the number of chunks.
 *
 * <p>The budget is what keeps the cost bounded. When the chunks ask for more than {@code maxPerRun} in one run, the
 * run stops early and the next one continues with the chunk where this one stopped (a rotating cursor), so every
 * chunk is still visited but less often: the rate per section falls by the share the budget cannot cover and never
 * the server's cost rises. The positions are uniform inside the section, as vanilla's random ticks are.</p>
 *
 * <p>This class knows nothing about Bukkit; the caller lists the chunks in a stable order (the cursor is an index).</p>
 */
final class RandomTickSampler {

    static final int SECTION = 16;

    record ChunkPos(int x, int z) {
    }

    private final int minY;
    private final int sections;
    private final double perSectionPerRun;
    private final int maxPerChunk;
    private final int maxPerRun;
    private int cursor;

    /**
     * @param minY                       lowest block of the world
     * @param maxY                       one above the highest block of the world
     * @param samplesPerSectionPerSecond expected samples per 16x16x16 section per second
     * @param intervalTicks              ticks between runs
     * @param maxPerRun                  hard cap on the samples of one run (at least one chunk's worth)
     */
    RandomTickSampler(int minY, int maxY, double samplesPerSectionPerSecond, long intervalTicks, int maxPerRun) {
        if (maxY <= minY || samplesPerSectionPerSecond < 0.0 || intervalTicks < 1) {
            throw new IllegalArgumentException("Invalid sampler bounds");
        }
        this.minY = minY;
        this.sections = Math.max(1, (maxY - minY) / SECTION);
        this.perSectionPerRun = samplesPerSectionPerSecond * intervalTicks / 20.0;
        this.maxPerChunk = sections * (int) Math.ceil(perSectionPerRun);
        if (maxPerRun < Math.max(1, maxPerChunk)) {
            throw new IllegalArgumentException("maxPerRun " + maxPerRun + " cannot hold one chunk (" + maxPerChunk + ")");
        }
        this.maxPerRun = maxPerRun;
    }

    int sections() {
        return sections;
    }

    /** The most samples one chunk can receive in one run. */
    int maxPerChunk() {
        return maxPerChunk;
    }

    /** Where the next run starts in the chunk list. */
    int cursor() {
        return cursor;
    }

    /** The samples of one run: never more than {@code maxPerRun}, never more than one visit per chunk. */
    List<SiftPos> next(List<ChunkPos> chunks, Random random) {
        List<SiftPos> samples = new ArrayList<>();
        int count = chunks.size();
        if (count == 0 || maxPerChunk == 0) {
            cursor = 0;
            return samples;
        }
        if (cursor >= count) {
            cursor = 0;
        }
        int whole = (int) Math.floor(perSectionPerRun);
        double fraction = perSectionPerRun - whole;
        for (int visited = 0; visited < count; visited++) {
            if (samples.size() + maxPerChunk > maxPerRun) {
                break;
            }
            ChunkPos chunk = chunks.get(cursor);
            cursor = (cursor + 1) % count;
            for (int section = 0; section < sections; section++) {
                int draws = whole + (fraction > 0.0 && random.nextDouble() < fraction ? 1 : 0);
                for (int i = 0; i < draws; i++) {
                    samples.add(new SiftPos((chunk.x() << 4) + random.nextInt(SECTION),
                        minY + section * SECTION + random.nextInt(SECTION), (chunk.z() << 4) + random.nextInt(SECTION)));
                }
            }
        }
        return samples;
    }
}
