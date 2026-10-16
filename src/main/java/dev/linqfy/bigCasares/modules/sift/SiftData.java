package dev.linqfy.bigCasares.modules.sift;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read access to the imported mod data under {@code the_sift/mielon/} on the
 * plugin classpath (see {@code tools/port_mielon_sift.py}).
 */
public final class SiftData {

    public static final String ROOT = "the_sift/mielon/";

    /** Biome files of the mod, in the order of its multi-noise source. */
    public static final List<String> BIOMES = List.of(
        "sift_wastes", "overgrown_clearing", "overgrown_forest", "siftslate_slopes", "overgrown_slopes",
        "overgrown_forest_slopes", "ichor_snowy_peaks", "siftslate_peaks", "overgrown_peaks", "sift_deep_dark");

    private final ClassLoader loader;

    public SiftData(ClassLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public static SiftData fromClasspath() {
        return new SiftData(SiftData.class.getClassLoader());
    }

    public JsonObject json(String path) {
        return optionalJson(path).orElseThrow(() -> new IllegalStateException("Missing Sift data file " + ROOT + path));
    }

    public Optional<JsonObject> optionalJson(String path) {
        try (InputStream stream = loader.getResourceAsStream(ROOT + path)) {
            if (stream == null) {
                return Optional.empty();
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                return Optional.of(element.getAsJsonObject());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read Sift data " + path, exception);
        }
    }

    public Optional<byte[]> bytes(String path) {
        try (InputStream stream = loader.getResourceAsStream(ROOT + path)) {
            return stream == null ? Optional.empty() : Optional.of(stream.readAllBytes());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read Sift data " + path, exception);
        }
    }
}
