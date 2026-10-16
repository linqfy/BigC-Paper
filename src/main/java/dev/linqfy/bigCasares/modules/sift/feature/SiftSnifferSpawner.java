package dev.linqfy.bigCasares.modules.sift.feature;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Port of the mod's {@code SiftDeferredSnifferSpawner} for sniffer caves and surface
 * sculk regions.
 *
 * <p>The mod queues spawn requests while chunks generate and spawns the sniffers from
 * a server tick once every chunk of the nest is loaded (up to 80 retries per request).
 * Generation here has no later tick to hook into, so the sniffers are spawned
 * directly through {@link WorldRegion#spawn} while the feature runs. The site choice
 * is the mod's: a seeded start index into the candidate floors, then the first floor
 * with two free blocks above it and no ichor at or under the feet.</p>
 *
 * <p>Differences:</p>
 * <ul>
 *   <li>Spawning happens at generation time, not after neighbouring chunks load; there
 *       are no retries, so a request with no free site simply spawns nothing.</li>
 *   <li>The cave family's baby (33%) spawns as an adult sniffer: {@code WorldRegion.spawn}
 *       cannot set an age. Sniffers of one family never share a block.</li>
 *   <li>The mod's {@code DARK_SNIFFER} (surface sculk regions) is not ported, so the
 *       region's one or two dark sniffers are ordinary sniffers.</li>
 *   <li>The mod's random yaw, {@code finalizeSpawn} and {@code setPersistenceRequired}
 *       have no equivalent in {@code WorldRegion}.</li>
 * </ul>
 */
final class SiftSnifferSpawner {

    private static final String SNIFFER = "minecraft:sniffer";
    private static final long FAMILY_SALT = 0x534E49464645524CL;
    private static final long DARK_SALT = 0x4441524B534E4946L;

    private SiftSnifferSpawner() {
    }

    /**
     * {@code enqueueFamily}: two sniffers, plus a third with 33% chance, on the floors
     * of a sniffer-cave nest. Consumes one {@code nextFloat} from {@code random}, like
     * the mod.
     *
     * @param nest floor positions of the nest (the sniffers stand on the block above)
     * @return the number of sniffers spawned
     */
    static int spawnFamily(WorldRegion region, Random random, List<Pos> nest) {
        if (nest.isEmpty()) {
            return 0;
        }
        long familySeed = region.seed() ^ pack(nest.get(0)) ^ FAMILY_SALT;
        Set<Pos> taken = new HashSet<>();
        int spawned = spawnOne(region, nest, mix(familySeed), taken) ? 1 : 0;
        spawned += spawnOne(region, nest, mix(familySeed + 1L), taken) ? 1 : 0;
        if (random.nextFloat() < 0.33F) {
            spawned += spawnOne(region, nest, mix(familySeed + 2L), taken) ? 1 : 0;
        }
        return spawned;
    }

    /**
     * {@code enqueueDark}: one sniffer on the surface sites (sculk floors) of a sculk-region blob.
     *
     * @return whether a sniffer was spawned
     */
    static boolean spawnDark(WorldRegion region, List<Pos> sites, long blobSeed) {
        if (sites.isEmpty()) {
            return false;
        }
        return spawnOne(region, sites, mix(region.seed() ^ blobSeed ^ DARK_SALT), new HashSet<>());
    }

    /** {@code spawnOne}: walks the floors from a seeded start and spawns on the first free one. */
    private static boolean spawnOne(WorldRegion region, List<Pos> floors, long seed, Set<Pos> taken) {
        Random random = new Random(seed);
        int start = random.nextInt(floors.size());
        for (int offset = 0; offset < floors.size(); offset++) {
            Pos floor = floors.get((start + offset) % floors.size());
            Pos feet = floor.above();
            if (taken.contains(feet)
                || !region.isAir(feet.x(), feet.y(), feet.z()) || !region.isAir(feet.x(), feet.y() + 1, feet.z())
                || isIchor(region, feet.x(), feet.y(), feet.z()) || isIchor(region, feet.x(), feet.y() - 1, feet.z())) {
                continue;
            }
            if (region.spawn(SNIFFER, feet.x() + 0.5, feet.y(), feet.z() + 0.5)) {
                taken.add(feet);
                return true;
            }
        }
        return false;
    }

    /** Ichor is placed as water in the port. */
    private static boolean isIchor(WorldRegion region, int x, int y, int z) {
        return region.blockId(x, y, z).equals(SiftTerrain.ICHOR.block().asString());
    }

    /** {@code BlockPos.asLong}. */
    private static long pack(Pos pos) {
        return ((long) (pos.x() & 0x3FFFFFF) << 38) | ((long) (pos.z() & 0x3FFFFFF) << 12) | (long) (pos.y() & 0xFFF);
    }

    /** The mod's seed mixer (a splitmix64 finaliser). */
    static long mix(long value) {
        value ^= value >>> 30;
        value *= -4658895280553007687L;
        value ^= value >>> 27;
        value *= -7723592293110705685L;
        return value ^ value >>> 31;
    }
}
