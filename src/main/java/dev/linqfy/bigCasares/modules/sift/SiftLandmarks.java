package dev.linqfy.bigCasares.modules.sift;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The positions the Echo Golem navigates by, the port of the mod's {@code SiftLandmarkTracker}: the entrance,
 * midpoint and soul cluster of every Soul Canyon, the canyons still waiting for their golem, and the Soul
 * Blocks. Canyon landmarks are recorded when the canyon is generated (see {@code SoulCanyonLandmarks}); Soul
 * Blocks are recorded when they are generated, placed or found in a loaded chunk. Records are hints: the
 * reader checks the world, so a Soul Block that was mined is simply skipped. The ruins of abandoned portals,
 * which {@code /dimension locate} reads, are recorded when they are generated.
 *
 * <p>Generation runs on world-generation threads, so recording is thread-safe. Everything else is meant for
 * the main thread. The registry persists in a small binary file ({@link #save}, {@link #load}).</p>
 */
final class SiftLandmarks {

    /** The mod's tracker kinds; the order is the file layout. */
    enum Kind {
        SOUL_BLOCK,
        CANYON_ENTRANCE,
        CANYON_CLUSTER,
        CANYON_MIDPOINT,
        GOLEM_PENDING,
        ABANDONED_PORTAL
    }

    private static final int MAGIC = 0x5346544C;
    /** Version 2 added {@link Kind#ABANDONED_PORTAL}; version 1 files hold the first {@value #KINDS_IN_VERSION_1} kinds. */
    private static final int VERSION = 2;
    private static final int KINDS_IN_VERSION_1 = 5;

    private final Map<Kind, Set<Long>> positions = new EnumMap<>(Kind.class);
    /** Soul Blocks by chunk: golems ask for the ones near them, many times a second. */
    private final Map<Long, Set<Long>> soulBlocksByChunk = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    SiftLandmarks() {
        for (Kind kind : Kind.values()) {
            positions.put(kind, ConcurrentHashMap.newKeySet());
        }
    }

    void record(Kind kind, SiftPos pos) {
        long packed = pos.asLong();
        if (positions.get(kind).add(packed)) {
            if (kind == Kind.SOUL_BLOCK) {
                soulBlocksByChunk.computeIfAbsent(chunkKey(pos.x() >> 4, pos.z() >> 4), ignored -> ConcurrentHashMap.newKeySet())
                    .add(packed);
            }
            dirty = true;
        }
    }

    void forget(Kind kind, SiftPos pos) {
        long packed = pos.asLong();
        if (positions.get(kind).remove(packed)) {
            if (kind == Kind.SOUL_BLOCK) {
                long chunk = chunkKey(pos.x() >> 4, pos.z() >> 4);
                Set<Long> inChunk = soulBlocksByChunk.get(chunk);
                if (inChunk != null) {
                    inChunk.remove(packed);
                    if (inChunk.isEmpty()) {
                        soulBlocksByChunk.remove(chunk, inChunk);
                    }
                }
            }
            dirty = true;
        }
    }

    boolean contains(Kind kind, SiftPos pos) {
        return positions.get(kind).contains(pos.asLong());
    }

    int count(Kind kind) {
        return positions.get(kind).size();
    }

    /** A copy of every position of the kind. */
    List<SiftPos> snapshot(Kind kind) {
        List<SiftPos> result = new ArrayList<>(positions.get(kind).size());
        for (long packed : positions.get(kind)) {
            result.add(SiftPos.of(packed));
        }
        return result;
    }

    /** {@code nearestOther}: the closest position of the kind within {@code maximumDistance} (horizontal), skipping one. */
    Optional<SiftPos> nearestOther(Kind kind, SiftPos origin, double maximumDistance, SiftPos excluded) {
        long excludedPacked = excluded == null ? Long.MIN_VALUE : excluded.asLong();
        SiftPos best = null;
        double bestDistance = maximumDistance * maximumDistance;
        for (long packed : positions.get(kind)) {
            if (packed == excludedPacked) {
                continue;
            }
            SiftPos candidate = SiftPos.of(packed);
            double distance = candidate.horizontalDistanceSqr(origin);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * {@code nearestActiveSoulCanyon}: the closest canyon cluster within {@code maximumDistance} (horizontal) that
     * is not excluded (packed positions) and that {@code active} accepts, i.e. still has Soul Blocks in the world.
     */
    Optional<SiftPos> nearestActiveCluster(SiftPos origin, double maximumDistance, Collection<Long> excluded,
                                           Predicate<SiftPos> active) {
        SiftPos best = null;
        double bestDistance = maximumDistance * maximumDistance;
        for (long packed : positions.get(Kind.CANYON_CLUSTER)) {
            if (excluded.contains(packed)) {
                continue;
            }
            SiftPos candidate = SiftPos.of(packed);
            double distance = candidate.horizontalDistanceSqr(origin);
            if (distance < bestDistance && active.test(candidate)) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Recorded Soul Blocks within the horizontal radius and the vertical distance of the centre. */
    List<SiftPos> soulBlocksNear(SiftPos center, int horizontal, int vertical) {
        double horizontalSqr = (double) horizontal * (double) horizontal;
        List<SiftPos> result = new ArrayList<>();
        for (int chunkX = center.x() - horizontal >> 4; chunkX <= center.x() + horizontal >> 4; chunkX++) {
            for (int chunkZ = center.z() - horizontal >> 4; chunkZ <= center.z() + horizontal >> 4; chunkZ++) {
                Set<Long> inChunk = soulBlocksByChunk.get(chunkKey(chunkX, chunkZ));
                if (inChunk == null) {
                    continue;
                }
                for (long packed : inChunk) {
                    SiftPos candidate = SiftPos.of(packed);
                    if (candidate.horizontalDistanceSqr(center) <= horizontalSqr
                        && Math.abs(candidate.y() - center.y()) <= vertical) {
                        result.add(candidate);
                    }
                }
            }
        }
        return result;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 ^ (long) chunkZ & 0xFFFFFFFFL;
    }

    boolean isDirty() {
        return dirty;
    }

    // --- persistence ---------------------------------------------------------------------------------

    /** Writes the registry when anything changed since the last save; returns whether it wrote. */
    boolean saveIfDirty(Path file) throws IOException {
        if (!dirty) {
            return false;
        }
        save(file);
        return true;
    }

    void save(Path file) throws IOException {
        dirty = false;
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(temporary))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            for (Kind kind : Kind.values()) {
                long[] sorted = positions.get(kind).stream().mapToLong(Long::longValue).sorted().toArray();
                out.writeInt(sorted.length);
                for (long packed : sorted) {
                    out.writeLong(packed);
                }
            }
        } catch (IOException | RuntimeException failure) {
            dirty = true;
            throw failure;
        }
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Reads a saved registry; a missing file is an empty registry, a file of another format is an error. */
    static SiftLandmarks load(Path file) throws IOException {
        SiftLandmarks landmarks = new SiftLandmarks();
        if (!Files.isRegularFile(file)) {
            return landmarks;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Not a Sift landmark file: " + file);
            }
            int version = in.readInt();
            if (version != VERSION && version != 1) {
                throw new IOException("Not a Sift landmark file: " + file);
            }
            for (Kind kind : Kind.values()) {
                if (version == 1 && kind.ordinal() >= KINDS_IN_VERSION_1) {
                    break;
                }
                int count = in.readInt();
                if (count < 0 || count > 50_000_000) {
                    throw new IOException("Corrupt Sift landmark file: " + file);
                }
                for (int index = 0; index < count; index++) {
                    landmarks.record(kind, SiftPos.of(in.readLong()));
                }
            }
        }
        landmarks.dirty = false;
        return landmarks;
    }
}
