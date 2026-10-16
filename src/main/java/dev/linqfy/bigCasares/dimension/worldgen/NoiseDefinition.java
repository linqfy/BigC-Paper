package dev.linqfy.bigCasares.dimension.worldgen;

import com.google.gson.JsonElement;
import dev.linqfy.bigCasares.dimension.NamespacedId;
import dev.linqfy.bigCasares.dimension.json.Json;
import dev.linqfy.bigCasares.dimension.json.JsonSerializable;

import java.util.List;
import java.util.Objects;

/**
 * Octave layout for a vanilla {@code worldgen/noise} entry.
 *
 * <p>Octave {@code i} samples at frequency {@code 2^(firstOctave + i)} with weight
 * {@code amplitudes[i]}; zero amplitudes skip an octave. Minecraft derives each
 * noise's random state from the world seed and this id, so two different ids
 * give independent fields for the same seed.</p>
 */
public record NoiseDefinition(NamespacedId id, int firstOctave, List<Double> amplitudes) implements JsonSerializable {

    public NoiseDefinition {
        Objects.requireNonNull(id, "id");
        amplitudes = List.copyOf(amplitudes);
        if (amplitudes.isEmpty() || amplitudes.stream().allMatch(value -> value == 0.0)) {
            throw new IllegalArgumentException("Noise " + id + " needs at least one non-zero amplitude");
        }
        if (firstOctave > 0 || firstOctave < -24) {
            throw new IllegalArgumentException("Noise " + id + " has an unsupported firstOctave: " + firstOctave);
        }
    }

    public static NoiseDefinition of(NamespacedId id, int firstOctave, double... amplitudes) {
        return new NoiseDefinition(id, firstOctave, java.util.Arrays.stream(amplitudes).boxed().toList());
    }

    @Override
    public JsonElement toJson() {
        return Json.object().put("amplitudes", amplitudes).put("firstOctave", firstOctave).build();
    }
}
