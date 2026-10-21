package dev.linqfy.bigCasares.modules.mutango;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Una estructura convertida desde un litematic, como la ciudad de Don
 * Pollos. Orden de los bloques: y, z, x.
 */
final class MutangoStructure {

    private final int width;
    private final int height;
    private final int length;
    private final List<String> palette;
    private final int[] blocks;

    private MutangoStructure(int width, int height, int length, List<String> palette, int[] blocks) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = List.copyOf(palette);
        this.blocks = blocks;
    }

    static MutangoStructure load(InputStream gzipJson) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(gzipJson), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray size = root.getAsJsonArray("size");
            int w = size.get(0).getAsInt();
            int h = size.get(1).getAsInt();
            int l = size.get(2).getAsInt();
            List<String> palette = new ArrayList<>();
            for (JsonElement state : root.getAsJsonArray("palette")) {
                palette.add(state.getAsString());
            }
            int[] blocks = new int[w * h * l];
            int i = 0;
            for (JsonElement run : root.getAsJsonArray("runs")) {
                JsonArray pair = run.getAsJsonArray();
                int state = pair.get(0).getAsInt();
                int count = pair.get(1).getAsInt();
                if (state < 0 || state >= palette.size() || i + count > blocks.length) {
                    throw new IOException("estructura corrupta");
                }
                Arrays.fill(blocks, i, i + count, state);
                i += count;
            }
            if (i != blocks.length) {
                throw new IOException("estructura incompleta: " + i + " de " + blocks.length + " bloques");
            }
            return new MutangoStructure(w, h, l, palette, blocks);
        }
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int length() {
        return length;
    }

    List<String> palette() {
        return palette;
    }

    int stateAt(int x, int y, int z) {
        return blocks[(y * length + z) * width + x];
    }

    boolean isAir(int state) {
        return palette.get(state).equals("minecraft:air");
    }

    /** Pasto, pasto alto y flores: van arriba del suelo pero no lo sostienen. */
    boolean isPlant(int state) {
        String id = palette.get(state);
        int props = id.indexOf('[');
        String name = props < 0 ? id : id.substring(0, props);
        return name.endsWith("grass") || name.equals("minecraft:poppy") || name.equals("minecraft:dandelion")
            || name.endsWith("fern") || name.endsWith("tulip");
    }
}
