package dev.linqfy.bigCasares.modules.donpollos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** La ciudad convertida desde el schematic (tools/convertir_schematic.py). Orden: y, z, x. */
public final class DonPollosCityTemplate {

    private final int width;
    private final int height;
    private final int length;
    private final List<String> palette;
    private final int[] blocks;

    DonPollosCityTemplate(int width, int height, int length, List<String> palette, int[] blocks) {
        if (blocks.length != width * height * length) {
            throw new IllegalArgumentException("la ciudad tiene " + blocks.length + " bloques, se esperaban "
                + width * height * length);
        }
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = List.copyOf(palette);
        this.blocks = blocks;
    }

    public static DonPollosCityTemplate load(InputStream gzipJson) throws IOException {
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
                    throw new IOException("ciudad corrupta");
                }
                java.util.Arrays.fill(blocks, i, i + count, state);
                i += count;
            }
            return new DonPollosCityTemplate(w, h, l, palette, blocks);
        }
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int length() {
        return length;
    }

    public List<String> palette() {
        return palette;
    }

    public int stateAt(int x, int y, int z) {
        return blocks[(y * length + z) * width + x];
    }

    public String blockAt(int x, int y, int z) {
        return palette.get(stateAt(x, y, z));
    }

    public boolean isAir(int state) {
        return palette.get(state).equals("minecraft:air");
    }
}
