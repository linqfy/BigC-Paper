package dev.linqfy.bigCasares.dimension.worldgen;

import dev.linqfy.bigCasares.dimension.DimensionKey;
import dev.linqfy.bigCasares.dimension.NamespacedId;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Per-dimension compilation context handed to terrain and biome providers.
 *
 * <p><b>Seeds.</b> Minecraft gives every level stem of a world the same world
 * seed; a datapack cannot choose a different one. The framework therefore
 * applies a configurable {@code seedSalt} as a deterministic coordinate offset
 * to every noise registered through {@link #noise}. Identical world seed + salt
 * reproduce identical terrain; changing the salt yields a different, equally
 * deterministic layout. Noises referenced only from surface rules are not offset.</p>
 */
public final class GenerationContext {

    private final DimensionKey dimension;
    private final long seedSalt;
    private final Map<NamespacedId, NoiseDefinition> noises = new LinkedHashMap<>();

    public GenerationContext(DimensionKey dimension, long seedSalt) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.seedSalt = seedSalt;
    }

    public DimensionKey dimension() {
        return dimension;
    }

    public long seedSalt() {
        return seedSalt;
    }

    /** Id scoped to this dimension instance, e.g. {@code bigc:the_sift/terrain/base}. */
    public NamespacedId id(String path) {
        return dimension.id().child(path);
    }

    /** Registers (or reuses) an octave layout and returns its id. */
    public NamespacedId defineNoise(String path, int firstOctave, double... amplitudes) {
        NamespacedId id = id("noise/" + path);
        NoiseDefinition definition = NoiseDefinition.of(id, firstOctave, amplitudes);
        NoiseDefinition existing = noises.putIfAbsent(id, definition);
        if (existing != null && !existing.equals(definition)) {
            throw new IllegalArgumentException("Conflicting noise definition: " + id);
        }
        return id;
    }

    /** Registers an externally defined octave layout (e.g. imported data) under its own id. */
    public NamespacedId registerNoise(NoiseDefinition definition) {
        NoiseDefinition existing = noises.putIfAbsent(definition.id(), definition);
        if (existing != null && !existing.equals(definition)) {
            throw new IllegalArgumentException("Conflicting noise definition: " + definition.id());
        }
        return definition.id();
    }

    /** A salted noise sample; see the class documentation. */
    public DensityFunction noise(NamespacedId noise, double xzScale, double yScale) {
        if (seedSalt == 0L) {
            return DensityFunctions.noise(noise, xzScale, yScale);
        }
        long hash = mix(seedSalt ^ fnv(noise.asString()));
        // Offsets stay well inside the range where Perlin wrapping is irrelevant.
        double shiftX = ((hash & 0xFFFFF) - 0x80000) * 1.0;
        double shiftZ = (((hash >>> 20) & 0xFFFFF) - 0x80000) * 1.0;
        double shiftY = yScale == 0.0 ? 0.0 : (((hash >>> 40) & 0xFFF) - 0x800) * 1.0;
        return DensityFunctions.shiftedNoise(noise, xzScale, yScale, shiftX, shiftY, shiftZ);
    }

    public List<NoiseDefinition> noises() {
        return List.copyOf(noises.values());
    }

    private static long fnv(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xFF;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
